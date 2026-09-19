package com.minecart.yunxian.integration.kubejs;

import java.util.Map;
import java.util.function.Supplier;

import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.block.budding.YunxianClusterBlock;
import com.minecart.yunxian.budding.BuddingFamilies.Stage;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.BuddingRegistration;
import com.minecart.yunxian.budding.GrowthDefinition;
import com.minecart.yunxian.registry.ModCreativeTabs;
import com.minecart.yunxian.registry.ScriptedBlockDrops;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import dev.latvian.mods.kubejs.block.BlockRenderType;
import dev.latvian.mods.kubejs.block.custom.BasicKubeBlock;
import dev.latvian.mods.kubejs.client.ModelGenerator;
import dev.latvian.mods.kubejs.client.VariantBlockStateGenerator;
import dev.latvian.mods.kubejs.registry.RegistryKubeEvent;
import dev.latvian.mods.kubejs.script.SourceLine;
import dev.latvian.mods.kubejs.util.ID;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 给 KubeJS 脚本用的一行注册：<b>注册一个母岩，自动带出整套五样方块</b>
 * （母岩本体 + 小芽 + 中芽 + 大芽 + 晶簇）。
 * <p>
 * 脚本里就是这么用（`CustomBudding` 由 {@link YunxianKubeJSPlugin} 绑定，无需 {@code Java.loadClass}）：
 * <pre>{@code
 * CustomBudding.create(event, 'my_crystal', 20)          // 一行：概率 1/20
 * // 或带选项（逐字段赋值、链式两种写法等价，见 Options）：
 * CustomBudding.create(event, 'my_crystal', new CustomBuddingOptions()
 *     .chance(20)
 *     .maxLight(7)
 *     .requiresWater())
 * }</pre>
 * 生成 {@code <命名空间>:<id>_budding} 与 {@code _small_bud} / {@code _medium_bud} / {@code _large_bud} / {@code _cluster}；
 * 母岩的随机刻直接接到本模组的生长引擎上，芽/簇带 {@code FACING} 属性（引擎会写朝向）。
 * <p>
 * 默认贴图借用原版紫水晶那一套（零资源即可跑通），要自己的外观就改
 * {@link Options#buddingTexture} / {@link Options#stageTextures}，或者用资源包。
 */
public final class CustomBudding {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.kubejs");

    /** 四个阶段的后缀，顺序：小芽 → 中芽 → 大芽 → 晶簇 */
    private static final String[] STAGE_KEYS = {"small_bud", "medium_bud", "large_bud", "cluster"};

    /** 默认贴图（原版紫水晶那套），包作者可用 Options 覆盖 */
    private static final String[] DEFAULT_STAGE_TEXTURES = {
            "minecraft:block/small_amethyst_bud",
            "minecraft:block/medium_amethyst_bud",
            "minecraft:block/large_amethyst_bud",
            "minecraft:block/amethyst_cluster"};
    private static final String DEFAULT_BUDDING_TEXTURE = "minecraft:block/budding_amethyst";

    /** 物品模型：平面图标（与本模组自带芽/簇的物品模型一致） */
    private static final ResourceLocation ITEM_GENERATED = ResourceLocation.withDefaultNamespace("item/generated");

    /** 通用母岩标签（与本模组自带母岩一致：智能钻头精准采集、AE2 晶体催生器都读它） */
    private static final ResourceLocation BUDDING_BLOCKS_TAG = ResourceLocation.fromNamespaceAndPath("c", "budding_blocks");
    private static final ResourceLocation MINEABLE_PICKAXE_TAG =
            ResourceLocation.withDefaultNamespace("mineable/pickaxe");

    /**
     * 阶段方块继承的原版模型 id（与上面的默认贴图同名，但语义是"模型"）：
     * 借它们才拿到芽/簇该有的 cross 几何与尺寸，而不是一个整块立方体。
     */
    private static final String[] STAGE_MODELS = {
            "minecraft:block/small_amethyst_bud",
            "minecraft:block/medium_amethyst_bud",
            "minecraft:block/large_amethyst_bud",
            "minecraft:block/amethyst_cluster"};

    private CustomBudding() {
    }

    /** 最常用的一行：注册一整族，概率基数 n（每次随机刻 1/n） */
    public static Family create(RegistryKubeEvent<Block> event, String id, int chance) {
        Options options = new Options();
        options.chance = chance;
        return create(event, id, options);
    }

    /**
     * 带选项的版本；选项在调用时读取一次（之后改 Options 对象不会影响已注册的方块）。
     *
     * @param id 家族 id，可带命名空间（{@code "mypack:my_crystal"}），不带则落在 {@code kubejs} 命名空间
     */
    public static Family create(RegistryKubeEvent<Block> event, String id, Options options) {
        ResourceLocation base = parse(id);
        String namespace = base.getNamespace();

        // 四个阶段方块：建成模组自己的簇方块（见 StageBuilder），朝向形状、支撑判定、音效都是原版行为
        ResourceLocation[] stages = new ResourceLocation[STAGE_KEYS.length];
        for (int i = 0; i < STAGE_KEYS.length; i++) {
            ResourceLocation stageId = ResourceLocation.fromNamespaceAndPath(namespace, base.getPath() + "_" + STAGE_KEYS[i]);
            stages[i] = stageId;

            // 形状/朝向/支撑判定都由簇方块自己管（FACING、WATERLOGGED 是它自带的属性），
            // 所以这里不再额外 .property(FACING) / .noCollision()，只补音效与渲染类型：
            // cross 模型有大量透明像素，必须 cutout，否则空白处会渲染成黑色
            BlockBuilder builder = new StageBuilder(stageId, Stage.values()[i], options.stageTextures[i],
                    ResourceLocation.parse(STAGE_MODELS[i]));
            builder.sourceLine = SourceLine.UNKNOWN;
            builder.soundType(SoundType.AMETHYST);
            builder.renderType(BlockRenderType.CUTOUT);
            // 名字：不指定就交给 KubeJS 按 id 自动命名（snake_case 转英文标题，写进 en_us 虚拟语言文件）；
            // 方块物品与方块共用同一个语言键（BlockItemBuilder#getTranslationKeyGroup 返回 "block"），
            // 所以这里设一次，背包/掉落物/创造栏一起变
            String stageName = stageDisplayName(options, i);
            if (stageName != null) {
                builder.displayName(Component.literal(stageName));
            }
            builder.tag(new ResourceLocation[]{MINEABLE_PICKAXE_TAG});
            forceItem(builder, false);
            registerBlock(event, builder);

            // 掉落规则交给运行时：精准采集掉本体，否则只有晶簇掉 Options.dropItem × dropCount（默认什么都不掉）。
            // KubeJS 的掉落 API 表达不了精准采集，覆写 generateLootTable() 又没人调用，见 ScriptedBlockDrops
            boolean cluster = Stage.values()[i] == Stage.CLUSTER;
            ScriptedBlockDrops.register(stageId, cluster ? itemId(options.dropItem) : null,
                    cluster ? options.dropCount : 1);
        }

        // 母岩本体：用模组自己的方块类（它自带随机刻 → 生长引擎，且带共享展示 BE，护目镜才显示信息）
        ResourceLocation buddingId = ResourceLocation.fromNamespaceAndPath(namespace, base.getPath() + "_budding");
        LazyDefinition definition = new LazyDefinition(stages, options);
        BlockBuilder budding = new MotherBuilder(buddingId, definition);
        budding.sourceLine = SourceLine.UNKNOWN;
        budding.texture(options.buddingTexture);
        budding.soundType(SoundType.AMETHYST);
        // 母岩本体：进通用母岩标签（方块）+ 可被镐挖掘；物品标签在下面 forceItem 里补
        budding.tag(new ResourceLocation[]{BUDDING_BLOCKS_TAG, MINEABLE_PICKAXE_TAG});
        if (options.displayName != null) {
            budding.displayName(Component.literal(options.displayName));
        }
        forceItem(budding, true);
        registerBlock(event, budding);

        // 护目镜信息挂在方块实体上：把母岩声明给共享展示 BE（按 id——此刻方块还没建出来）
        BuddingRegistration.declareBuddingBlock(buddingId);

        // 母岩：普通破坏/普通采集什么都不掉（与原版紫水晶母岩一致），精准采集才掉本体——
        // 所以智能钻头的普通模式拿不到母岩，精准模式才拿得到（它读 c:budding_blocks 直接掉本体）
        ScriptedBlockDrops.register(buddingId, null, 1);

        // 创造栏：整族五个物品一起登记（延后到标签页构建时注入）
        registerTabEntries(options, buddingId, stages[0], stages[1], stages[2], stages[3]);

        LOGGER.info("[KubeJS] 已注册母岩家族 {}：{} / {} / {} / {} / {}（创造栏：{}）",
                base, buddingId, stages[0], stages[1], stages[2], stages[3], options.group);

        return new Family(buddingId, stages[0], stages[1], stages[2], stages[3]);
    }

    /**
     * 明确建一次物品构建器：KubeJS 的方块物品是「按需创建」的，脚本路径（{@code event.create}）
     * 会替它建好，而我们是手工构造 builder，显式调用一次 {@code item(...)} 最稳妥
     * （物品建不出来时，方块存在但 {@code /give} 与创造栏都找不到，很难排查）。
     *
     * @param buddingItem 母岩的物品要额外进通用母岩标签（方块标签在 {@code tag(...)} 里加了）
     */
    private static void forceItem(BlockBuilder builder, boolean buddingItem) {
        builder.item(item -> {
            if (buddingItem) {
                item.defaultTags.add(BUDDING_BLOCKS_TAG);
            }
        });
    }

    /**
     * 把方块登记进创造模式标签页。KubeJS 注册的方块**默认不进任何标签页**（玩家会以为没注册成功），
     * 所以这里默认让它们进 KubeJS 那一页；{@link Options#group} 可以换成别的（如 {@code "building_blocks"}），
     * 设成 null 就完全不进标签页（只能用 {@code /give} 取）。
     * <p>
     * 注意不能用 {@code ItemBuilder#group()}——KubeJS 2101 已移除它（会直接报错），改为在标签页构建时
     * 由 NeoForge 的 {@code BuildCreativeModeTabContentsEvent} 注入（见 {@code ModCreativeTabs}）。
     */
    private static void registerTabEntries(Options options, ResourceLocation... itemIds) {
        if (options.group == null) {
            return;
        }
        for (ResourceLocation itemId : itemIds) {
            ModCreativeTabs.addScriptedItem(options.group, itemId);
        }
    }

    /**
     * {@code "mypack:my_crystal"} → 原样；{@code "my_crystal"} → {@code kubejs:my_crystal}
     * （与 KubeJS 自己的 {@code event.create} 约定一致）。
     * <p>
     * 注意不能用 {@code ResourceLocation.tryParse} 的返回值来判断"有没有命名空间"：
     * 它在 1.21 里会给裸 id 补上 {@code minecraft:} 而不是返回 null。
     */
    private static ResourceLocation parse(String id) {
        if (!id.contains(":")) {
            return ResourceLocation.fromNamespaceAndPath("kubejs", id);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            throw new IllegalArgumentException("不是合法的方块 id：" + id);
        }
        return parsed;
    }

    /**
     * 母岩专用的 builder：建出<b>本模组的脚本母岩方块</b>，而不是 KubeJS 的通用方块。
     * 只有这样它才带共享展示方块实体——护目镜的生长信息挂在实体上
     * （{@code BuddingGrowthBlockEntity} 实现 Create 的 {@code IHaveGoggleInformation}）。
     * 随机刻也因此由方块自己处理（见 {@code ScriptedBuddingBlock}），不必再挂 KubeJS 回调。
     */
    private static final class MotherBuilder extends BasicKubeBlock.Builder {

        private final Supplier<GrowthDefinition> definition;

        MotherBuilder(ResourceLocation id, Supplier<GrowthDefinition> definition) {
            super(id);
            this.definition = definition;
        }

        @Override
        public Block createObject() {
            return new ScriptedBuddingBlock(definition, createProperties());
        }
    }

    /** 把物品 id 字符串转成 ResourceLocation；null 或非法一律当没有 */
    @Nullable
    private static ResourceLocation itemId(@Nullable String id) {
        return id == null ? null : ResourceLocation.tryParse(id);
    }

    /**
     * 取第 {@code index} 个阶段（顺序：小 → 中 → 大 → 簇）的显示名。
     * <p>
     * 数组没配、比四个短、或那一项是 {@code null}/空白，都返回 {@code null}——
     * 表示这一项交给 KubeJS 按 id 自动命名，所以可以只给其中几个起名。
     */
    @Nullable
    private static String stageDisplayName(Options options, int index) {
        String[] names = options.stageDisplayNames;
        if (names == null || index >= names.length) {
            return null;
        }

        String name = names[index];
        return name == null || name.isBlank() ? null : name;
    }

    /**
     * 注册方块：{@code add} 只把它放进注册表，物品是在 KubeJS 的 {@code afterPosted} 里
     * 遍历 {@code created} 时才创建的（{@code createAdditionalObjects}）——
     * 脚本的 {@code event.create(...)} 会同时放这两处，我们手工走 {@code add} 必须自己补上，
     * 否则会出现"方块在、物品没有"（{@code /give} 报未知物品、创造栏里也没有）。
     */
    private static void registerBlock(RegistryKubeEvent<Block> event, BlockBuilder builder) {
        event.add(Registries.BLOCK, builder);
        event.created.add(builder);
    }

    /**
     * 芽/簇专用的 builder：<b>建成模组自己的簇方块</b>（{@link YunxianClusterBlock}，
     * 即 {@code AmethystClusterBlock} 的子类），而不是 KubeJS 的通用方块。
     * 这样朝向形状、支撑方块消失就掉落、挖掘音效这些原版行为全都来自它本身。
     * <p>
     * 资源侧还得自己接管两点（KubeJS 默认给的是「一条无旋转变体 + 立方体模型」）：
     * <ul>
     *   <li>模型继承原版对应的芽/簇模型（几何、尺寸、贴图键 {@code cross} 全对上）；
     *       默认的模型生成会读取 {@link #parentModel} / {@link #textures}，所以不用覆写；
     *       模型路径由 {@code blockModel} 内部按 {@code ID.BLOCK_MODEL} 拼，与下面 blockstate
     *       引用的 {@code ID.BLOCK} 是同一套约定；</li>
     *   <li>blockstate 写成六个朝向各一条、带旋转的变体——与模组自带芽/簇的数据生成用同一套公式。</li>
     * </ul>
     */
    private static final class StageBuilder extends BasicKubeBlock.Builder {

        private final Stage stage;
        private final ResourceLocation model;
        /** 阶段贴图；构造期 KubeJS 会提前调一次 getOrCreateItemBuilder，那时它还是 null */
        private String texture;

        StageBuilder(ResourceLocation id, Stage stage, String texture, ResourceLocation vanillaModel) {
            super(id);
            this.stage = stage;
            this.texture = texture;
            this.parentModel = vanillaModel;
            this.textures = Map.of("cross", texture);
            this.model = id.withPath(ID.BLOCK);
        }

        @Override
        public Block createObject() {
            return new YunxianClusterBlock(stage.height, stage.aabbOffset, createProperties(), stage.key);
        }

        /**
         * 物品模型：{@code item/generated} + 该阶段贴图，与本模组自带芽/簇的物品模型完全一致
         * （物品栏与掉落物都是平面图标，而不是把 cross 模型渲染成 3D）。
         * <p>
         * 必须覆写这里，而不是去设 {@code ItemBuilder.parentModel}/{@code textures}：
         * KubeJS 给**方块物品**生成模型走的是 {@code BlockBuilder.generateItemModel}，
         * 里面硬编码了 {@code parent(方块模型)}，压根不读 ItemBuilder 的那两个字段。
         */
        @Override
        protected void generateItemModel(ModelGenerator generator) {
            if (texture == null) {
                super.generateItemModel(generator);
                return;
            }
            generator.parent(ITEM_GENERATED);
            generator.texture("layer0", texture);
        }

        @Override
        protected void generateBlockState(VariantBlockStateGenerator generator) {
            for (Direction facing : Direction.values()) {
                generator.variant("facing=" + facing.getSerializedName(), variant -> variant.model(model)
                        .x(facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0)
                        // 模型默认朝北：北 0、东 90、南 180、西 270
                        .y(facing.getAxis().isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360));
            }
        }
    }

    /**
     * 可选项。两种写法等价、也可以混用：
     * <pre>{@code
     * // 一、逐字段赋值
     * const opts = new CustomBuddingOptions()
     * opts.chance = 20
     * opts.requiresWater = true
     * CustomBudding.create(event, 'my_crystal', opts)
     *
     * // 二、链式（整条链作为 create 的第三个实参）
     * CustomBudding.create(event, 'my_crystal', new CustomBuddingOptions()
     *     .chance(20)
     *     .requiresWater()
     *     .maxLight(0))
     * }</pre>
     * 链式方法都返回 {@code this}，名字与字段同名——脚本里 {@code opts.chance(20)} 与
     * {@code opts.chance = 20} 各走各的，互不影响。
     * <p>
     * <b>链必须写在 {@code create} 的实参里</b>：选项只在 {@code create} 调用时读一次，
     * 而 {@code create} 返回的是注册结果 {@link Family}，不是 builder——
     * {@code CustomBudding.create(event, id).chance(20)} 那种写法不会生效（方块那时已经建好了）。
     */
    public static final class Options {
        /** 概率基数 n：每次随机刻有 1/n 的概率推进一级 */
        public int chance = 5;
        /** 生长位允许的最大亮度（0–15）；负数 = 不限制 */
        public int maxLight = -1;
        /** 生长位要求的最低亮度（0–15）；负数 = 不限制。不能高于 {@link #maxLight}，否则永远长不出来 */
        public int minLight = -1;
        /** 目标格必须含水（可燃冰式） */
        public boolean requiresWater = false;
        /** 母岩的显示名；null = 交给 KubeJS 按 id 自动命名 */
        public @Nullable String displayName = null;
        /**
         * 四个阶段的显示名，顺序：小芽 → 中芽 → 大芽 → 晶簇。
         * <p>
         * 不配、比四个短、或某一项是 {@code null}/空白，那一项就交给 KubeJS 按 id 自动命名
         * （{@code example_crystal_small_bud} → "Example Crystal Small Bud"，写进 en_us 虚拟语言文件），
         * 所以可以只给其中几个起名。
         */
        public @Nullable String[] stageDisplayNames = null;
        /**
         * 创造模式标签页：默认 {@code "kubejs"}（KubeJS 自己那一页）。
         * 可以换成原版页（{@code "building_blocks"} / {@code "natural_blocks"} / {@code "functional_blocks"} …，用方块 id 里那套下划线写法），
         * 设成 null 则不进标签页（只能用 {@code /give} 取）。
         */
        public @Nullable String group = "kubejs";
        /** 母岩贴图 */
        public String buddingTexture = DEFAULT_BUDDING_TEXTURE;
        /**
         * 晶簇被普通破坏时掉落的物品 id（精准采集始终掉晶簇本体）；null = 什么都不掉。
         * 芽无论怎么破坏都只有精准采集才掉本体（与本模组自带的芽一致）。
         */
        public @Nullable String dropItem = null;
        /** {@link #dropItem} 的掉落数量（小于 1 按 1 处理） */
        public int dropCount = 1;
        /** 四个阶段的贴图，顺序：小芽 → 中芽 → 大芽 → 晶簇 */
        public String[] stageTextures = DEFAULT_STAGE_TEXTURES.clone();

        // ==================== 链式设置（与上面的字段等价，可混用） ====================

        public Options chance(int chance) {
            this.chance = chance;
            return this;
        }

        public Options maxLight(int maxLight) {
            this.maxLight = maxLight;
            return this;
        }

        public Options minLight(int minLight) {
            this.minLight = minLight;
            return this;
        }

        /** 等价于 {@code requiresWater = true} */
        public Options requiresWater() {
            return requiresWater(true);
        }

        /** 显式给值：{@code requiresWater(false)} 可以改回不需要水 */
        public Options requiresWater(boolean value) {
            this.requiresWater = value;
            return this;
        }

        public Options displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** 四个阶段的显示名，顺序：小芽 → 中芽 → 大芽 → 晶簇；传 {@code null} 的那项保持自动命名 */
        public Options stageDisplayNames(@Nullable String... stageDisplayNames) {
            this.stageDisplayNames = stageDisplayNames;
            return this;
        }

        /** 传 {@code null} = 不进任何创造栏（与字段直接赋 null 一致） */
        public Options group(@Nullable String group) {
            this.group = group;
            return this;
        }

        /** 晶簇普通破坏的掉落物；等价于只设 {@code dropItem}，数量沿用默认 1 */
        public Options dropItem(@Nullable String itemId) {
            this.dropItem = itemId;
            return this;
        }

        /** 掉落物 + 数量一次设完 */
        public Options dropItem(@Nullable String itemId, int count) {
            this.dropItem = itemId;
            this.dropCount = count;
            return this;
        }

        public Options dropCount(int dropCount) {
            this.dropCount = dropCount;
            return this;
        }

        public Options buddingTexture(String buddingTexture) {
            this.buddingTexture = buddingTexture;
            return this;
        }

        /** 四个阶段贴图，顺序：小芽 → 中芽 → 大芽 → 晶簇 */
        public Options stageTextures(String... stageTextures) {
            this.stageTextures = stageTextures;
            return this;
        }
    }

    /** 注册结果：五样方块的 id，方便脚本接着写配方、标签、战利品表 */
    public record Family(ResourceLocation budding, ResourceLocation smallBud, ResourceLocation mediumBud,
                         ResourceLocation largeBud, ResourceLocation cluster) {
    }

    /**
     * 生长定义要等首次随机刻才构造：KubeJS 启动脚本执行时，其它模组（含本模组）的方块还没注册完
     * （模组方块在注册事件里才入表），所以只能到时候按 id 解析一次并缓存。
     */
    private static final class LazyDefinition implements Supplier<GrowthDefinition> {
        private final ResourceLocation[] stages;
        private final int chance;
        private final int maxLight;
        private final int minLight;
        private final boolean requiresWater;

        private GrowthDefinition cached;

        LazyDefinition(ResourceLocation[] stages, Options options) {
            this.stages = stages;
            // 选项只在注册时读一次，之后脚本再改 Options 不影响这个家族
            this.chance = options.chance;
            this.maxLight = options.maxLight;
            this.minLight = options.minLight;
            this.requiresWater = options.requiresWater;
        }

        @Override
        public GrowthDefinition get() {
            GrowthDefinition definition = cached;
            if (definition == null) {
                definition = GrowthDefinition.of(stages[0].toString(), stages[1].toString(),
                        stages[2].toString(), stages[3].toString(), chance, maxLight, minLight,
                        requiresWater);
                cached = definition;
            }
            return definition;
        }
    }
}
