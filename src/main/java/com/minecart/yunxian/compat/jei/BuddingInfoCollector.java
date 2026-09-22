package com.minecart.yunxian.compat.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.minecart.yunxian.block.budding.GenericBuddingBlock;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamily;
import com.minecart.yunxian.budding.BuddingFamily.BlockConversion;
import com.minecart.yunxian.budding.BuddingFamily.EnergyRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Growth;
import com.minecart.yunxian.budding.BuddingFamily.GrowthRule;
import com.minecart.yunxian.budding.BuddingFamily.GrowthSpeed;
import com.minecart.yunxian.budding.BuddingFamily.LightRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.budding.BuddingRegistration;
import com.minecart.yunxian.budding.GrowthDefinition;
import com.minecart.yunxian.budding.GrowthEnvironment;
import com.minecart.yunxian.client.budding.EnvironmentDisplay;
import com.minecart.yunxian.compat.jei.BuddingInfo.Row;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModTags;
import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 把"场上有哪些母岩、各自什么脾气"收成 JEI 的信息页，一块母岩一页。
 * <p>
 * 收录范围与分派顺序：
 * <ol>
 *   <li>自带家族（{@code BuddingFamilies.ALL} 的顺序，也就是创造栏的顺序）；</li>
 *   <li>原版紫水晶母岩——本模组已经在给它显示护目镜信息（{@code client.budding.VanillaBuddingGoggles}），
 *       信息页同样收它；</li>
 *   <li>其余一切母岩方块：KubeJS 脚本注册的、附属模组用 {@link GenericBuddingBlock} 建的、
 *       以及别的模组登记进 {@code #c:budding_blocks} 的，按 id 排序追加。</li>
 * </ol>
 * <p>
 * 三类方块的参数来源各不一样，但走的是同一张模板（生长条件 / 生长速度 / 生成条件）：
 * 前两类带着自己的定义（{@code BuddingFamily} 或 {@code GrowthDefinition}），
 * 第三类读不出规则就如实写"未知"，绝不猜。
 */
public final class BuddingInfoCollector {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LANG = BuddingInfoText.LANG;

    /** 原版紫水晶母岩的随机刻概率：与配置里的「正常」档同为 1/5（{@code GrowthSpeed.NORMAL}） */
    private static final int VANILLA_CHANCE = 5;

    private BuddingInfoCollector() {
    }

    /** 全部要展示的母岩（构建期调用一次，之后由 JEI 缓存） */
    public static List<BuddingInfo> collect() {
        List<BuddingInfo> infos = new ArrayList<>();
        Set<Block> seen = new HashSet<>();

        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;   // AE2 缺席时福鲁伊克斯整条家族都不存在
            }
            Block block = family.budding().get();
            seen.add(block);
            infos.add(describe(block));
        }

        if (seen.add(Blocks.BUDDING_AMETHYST)) {
            infos.add(vanilla());
        }

        // 别的方块：先扫全注册表（instanceof 与标签两个来源都要，别只认标签），再按 id 排序
        List<Block> others = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!seen.contains(block) && isBuddingBlock(block)) {
                others.add(block);
            }
        }
        others.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        for (Block block : others) {
            infos.add(describe(block));
        }

        return List.copyOf(infos);
    }

    /**
     * 是不是我们要展示的母岩方块。
     * <p>
     * 不只看 {@code #c:budding_blocks} 标签：标签万一没加载出来（或某个包忘了登记），
     * 自带与脚本母岩也得照样出现在页面里，所以方块类型本身也算一条判据。
     */
    private static boolean isBuddingBlock(Block block) {
        if (block instanceof GenericBuddingBlock || block instanceof ScriptedBuddingBlock) {
            return true;
        }
        if (block.defaultBlockState().is(ModTags.BUDDING_BLOCKS)) {
            return true;
        }
        return declaredDefinition(block) != null;
    }

    // ==================== 三类来源 ====================

    /**
     * 解析单个方块。
     * <p>
     * 整批配方是一次性交给 JEI 的，所以<b>一块坏方块不能让整页全灭</b>：脚本母岩的定义要按 id
     * 解析四个阶段方块，id 写错就直接抛异常（见 {@code GrowthDefinition#of}），
     * 附属模组声明的定义也可能在解析时出错——这里兜住，退化成"规则未知"那一页。
     */
    private static BuddingInfo describe(Block block) {
        try {
            return describeUnchecked(block);
        } catch (RuntimeException e) {
            LOGGER.warn("[JEI] 读取母岩 {} 的生长信息失败，这一页按「规则未知」处理",
                    BuiltInRegistries.BLOCK.getKey(block), e);
            return foreign(block);
        }
    }

    private static BuddingInfo describeUnchecked(Block block) {
        if (block instanceof GenericBuddingBlock generic) {
            return fromFamily(generic, generic.family(), generic.stages());
        }
        if (block instanceof ScriptedBuddingBlock scripted) {
            return fromDefinition(block, scripted.growthDefinition(), LANG + "origin.scripted");
        }

        GrowthDefinition declared = declaredDefinition(block);
        if (declared != null) {
            return fromDefinition(block, declared, LANG + "origin.declared");
        }

        if (block == Blocks.BUDDING_AMETHYST) {
            return vanilla();
        }
        return foreign(block);
    }

    /** 带家族特点的母岩：本模组的 13 个家族与附属模组用 {@link GenericBuddingBlock} 建的方块共用这一条路径 */
    private static BuddingInfo fromFamily(GenericBuddingBlock block, BuddingFamily spec, List<Block> stages) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.header(section("growth")));
        rows.addAll(conditions(spec.growth()));

        rows.add(Row.header(section("speed")));
        String id = spec.id();
        rows.addAll(speedRows(ModConfig.Common.growthChance(id), ModConfig.Common.speedFor(id)));

        rows.add(Row.header(section("generation")));
        rows.addAll(GenerationInfoReader.rows(spec));

        return info(block, stages, rows);
    }

    /** 带生长定义的母岩：KubeJS 的 {@code CustomBudding} 与声明过定义的低阶方块 */
    private static BuddingInfo fromDefinition(Block block, GrowthDefinition definition, String originKey) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.header(section("growth")));
        rows.addAll(conditions(definition));

        rows.add(Row.header(section("speed")));
        // 脚本/外部定义的概率不由配置文件四档决定，所以没有档位可写，只给一个最接近的定性词
        rows.addAll(speedRows(definition.chance(), null));

        rows.add(Row.header(section("generation")));
        rows.add(Row.note(Component.translatable(LANG + "generation.none")));
        rows.add(Row.note(Component.translatable(originKey)));

        return info(block, definition.stages(), rows);
    }

    /** 原版紫水晶母岩：规则写死在这里（它没有家族定义，也不受本模组的配置影响） */
    private static BuddingInfo vanilla() {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.header(section("growth")));
        rows.add(Row.line(Component.translatable(LANG + "growth.none")));

        rows.add(Row.header(section("speed")));
        rows.addAll(speedRows(VANILLA_CHANCE, null));

        rows.add(Row.header(section("generation")));
        rows.add(Row.note(Component.translatable(LANG + "origin.amethyst_geode")));

        List<Block> stages = List.of(Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER);
        return info(Blocks.BUDDING_AMETHYST, stages, rows);
    }

    /** 读不出规则的母岩：只说明它是什么、以及它进没进通用母岩标签 */
    private static BuddingInfo foreign(Block block) {
        List<Row> rows = new ArrayList<>(2);
        rows.add(Row.note(Component.translatable(LANG + "unknown.foreign")));
        if (block.defaultBlockState().is(ModTags.BUDDING_BLOCKS)) {
            rows.add(Row.note(Component.translatable(LANG + "unknown.tag")));
        }
        return new BuddingInfo(block.defaultBlockState(), null, ItemStack.EMPTY, lookupItems(block, List.of()), rows);
    }

    // ==================== 生长条件 ====================

    /** 家族定义里的生长条件：只列非默认项，一条都没有时写「无额外要求」 */
    private static List<Row> conditions(Growth growth) {
        List<Row> rows = new ArrayList<>(4);

        LightRequirement light = growth.light();
        if (light.kind() == LightRequirement.Kind.BELOW) {
            // 只写定性说法：具体亮度阈值留在地图里自己试（below(t) 即"必须比 t 更暗"）
            rows.add(Row.line(Component.translatable(LANG + "growth.light")));
        }
        if (growth.rule() == GrowthRule.SUBMERGED) {
            rows.add(Row.line(Component.translatable(LANG + "growth.water")));
        }
        if (growth.growthEnvironment().restricts()) {
            rows.addAll(environmentRows(growth.growthEnvironment()));
        }
        if (growth.energy() == EnergyRequirement.AE2_GRID) {
            rows.add(Row.line(Component.translatable(LANG + "growth.energy")));
        }

        // 转化规则合并成一行：矿石族有两条（矿石 + 母岩再生），分行写会把版面撑满
        List<BlockConversion> conversions = growth.conversions();
        if (!conversions.isEmpty()) {
            List<Component> parts = new ArrayList<>(conversions.size());
            for (BlockConversion conversion : conversions) {
                parts.add(describeConversion(conversion));
            }
            rows.add(Row.line(Component.translatable(LANG + "growth.conversion",
                    BuddingInfoText.join(parts, BuddingInfoText.sentenceSeparator()))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.none")));
        }
        return rows;
    }

    /** 生长定义（脚本/外部声明）里的生长条件，与家族定义共用同一套文案键 */
    private static List<Row> conditions(GrowthDefinition definition) {
        List<Row> rows = new ArrayList<>(4);
        if (definition.minLight().isPresent()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.light.min")));
        }
        if (definition.maxLight().isPresent()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.light")));
        }
        if (definition.requiresWater()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.water")));
        }
        if (definition.growthEnvironment().restricts()) {
            rows.addAll(environmentRows(definition.growthEnvironment()));
        }
        if (rows.isEmpty()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.none")));
        }
        return rows;
    }

    /**
     * 「只在这些地方生长得最快」那两条：生长维度一行、生长群系一行，写了几个就并列几个。
     * 名字与护目镜浮窗共用 {@link EnvironmentDisplay}；地盘外还剩多少概率生长<b>不写进页面</b>
     * （只说「会受抑制」）。
     */
    private static List<Row> environmentRows(GrowthEnvironment environment) {
        List<Row> rows = new ArrayList<>(3);
        if (environment.hasDimensions()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.dimensions",
                    EnvironmentDisplay.dimensions(environment))));
        }
        if (environment.hasIncludedBiomes()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.biomes",
                    EnvironmentDisplay.biomes(environment))));
        }
        // 否定条件（! 前缀）单独一行：只写否定时没有「只在…」那一行，这一行自己也要读得通
        if (environment.hasExcludedBiomes()) {
            rows.add(Row.line(Component.translatable(LANG + "growth.biomes.excluded",
                    EnvironmentDisplay.excludedBiomes(environment))));
        }
        return rows;
    }

    /** 「石头 → 铁矿石 / 深板岩 → 深层铁矿石」；同一母岩的多条规则各自成句，由调用方连接 */
    private static Component describeConversion(BlockConversion conversion) {
        List<Component> pairs = new ArrayList<>(conversion.replacements().size());
        for (Replacement replacement : conversion.replacements()) {
            pairs.add(describeReplacement(replacement));
        }
        return BuddingInfoText.join(pairs);
    }

    /** 一条替换规则：输入 → 输出；输出为 null 表示"写成母岩自身"（母岩的再生传播） */
    private static Component describeReplacement(Replacement replacement) {
        Component input = replacement.inputTag() != null
                ? describeTag(replacement.inputTag())
                : blockName(replacement.input());
        Component output = replacement.output() == null
                ? Component.translatable(LANG + "growth.conversion.self")
                : blockName(replacement.output());
        return Component.translatable(LANG + "growth.conversion.pair", input, output);
    }

    /** 标签尽量展开成具体方块（前几个 + 等 N 种），展开不了就显示标签名 */
    private static Component describeTag(TagKey<Block> tag) {
        Optional<HolderSet.Named<Block>> members = BuiltInRegistries.BLOCK.getTag(tag);
        if (members.isEmpty()) {
            return Component.literal("#" + tag.location());
        }

        List<Component> names = new ArrayList<>();
        for (Holder<Block> holder : members.get()) {
            names.add(holder.value().getName());
        }
        return BuddingInfoText.summarize(names);
    }

    private static Component blockName(@Nullable Supplier<Block> supplier) {
        Block block = supplier == null ? null : supplier.get();
        // 方块解析不出来（另一个模组没装、id 写错）：如实说"未知方块"，而不是显示成空气
        return block == null || block == Blocks.AIR
                ? Component.translatable(LANG + "unknown.block")
                : block.getName();
    }

    // ==================== 生长速度 ====================

    /**
     * 「生长速度」小节——只讲<b>自然</b>生长，而且只给定性说法：档位名（配置文件里的那一档）
     * 或最接近档位的定性词。催生器带来的倍率不在这里（那是玩家自己摆出来的环境，不是方块固有的脾气）。
     * <p>
     * 具体概率（每随机刻 1/n）与「平均多少秒一级」<b>故意不写</b>：数值交给玩家自己在游戏里体会，
     * 信息页只说「快 / 慢」。
     *
     * @param tier 配置文件里的档位；null = 该母岩的概率不由四档决定（脚本/外部定义/原版），
     *             那时按 {@link GrowthSpeed#nearest(int)} 归一个定性词
     */
    private static List<Row> speedRows(int chance, @Nullable GrowthSpeed tier) {
        return List.of(Row.line(Component.translatable(LANG + "speed.rate",
                Component.translatable(tier == null ? wordKey(GrowthSpeed.nearest(chance)) : tierKey(tier)))));
    }

    /** 档位名（配置文件里的那一档）：极慢档 / 慢档 / 正常档 / 快档 */
    private static String tierKey(GrowthSpeed tier) {
        return LANG + "speed.tier." + tier.langSuffix();
    }

    /** 定性词（不进配置文件的母岩用它）：极其缓慢 / 缓慢 / 普通 / 很快 */
    private static String wordKey(GrowthSpeed tier) {
        return LANG + "speed.word." + tier.langSuffix();
    }

    // ==================== 组装 ====================

    private static BuddingInfo info(Block budding, List<Block> stages, List<Row> rows) {
        Block cluster = stages.isEmpty() ? null : stages.get(stages.size() - 1);
        return new BuddingInfo(budding.defaultBlockState(), stateOf(cluster),
                ClusterProductReader.productOf(cluster), lookupItems(budding, stages), List.copyOf(rows));
    }

    @Nullable
    private static BlockState stateOf(@Nullable Block block) {
        return block == null ? null : block.defaultBlockState();
    }

    /**
     * 参与 JEI 检索的物品：母岩 + 各级芽/晶簇。它们不显示，只保证玩家对着母岩按 R/U、
     * 或把物品拖进收藏书签时能翻到这一页。
     * <p>
     * 没有物品的方块（{@code asItem()} 是空气）不塞：空的 ItemStack 会让 JEI 直接报错。
     */
    private static List<ItemStack> lookupItems(Block budding, List<Block> stages) {
        List<ItemStack> items = new ArrayList<>(stages.size() + 1);
        addItem(items, budding);
        for (Block stage : stages) {
            addItem(items, stage);
        }
        return List.copyOf(items);
    }

    private static void addItem(List<ItemStack> items, Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) {
            items.add(item.getDefaultInstance());
        }
    }

    private static Component section(String key) {
        return Component.translatable(LANG + "section." + key);
    }

    @Nullable
    private static GrowthDefinition declaredDefinition(Block block) {
        try {
            return BuddingRegistration.declaredDefinition(block);
        } catch (RuntimeException e) {
            // 声明方（附属模组/脚本）给的定义解析失败：这一块退回"规则未知"，绝不让它拖垮整个收集
            return null;
        }
    }
}
