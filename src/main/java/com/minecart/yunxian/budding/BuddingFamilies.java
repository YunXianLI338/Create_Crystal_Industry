package com.minecart.yunxian.budding;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.minecart.yunxian.block.budding.EchoConvertingBuddingBlock;
import com.minecart.yunxian.block.budding.FueledBuddingBlock;
import com.minecart.yunxian.block.budding.GenericBuddingBlock;
import com.minecart.yunxian.block.budding.RedstoneClusterBlock;
import com.minecart.yunxian.block.budding.YunxianClusterBlock;
import com.minecart.yunxian.budding.BuddingFamily.Appearance;
import com.minecart.yunxian.budding.BuddingFamily.BlockConversion;
import com.minecart.yunxian.budding.BuddingFamily.BlockEntityKind;
import com.minecart.yunxian.budding.BuddingFamily.BuddingModel;
import com.minecart.yunxian.budding.BuddingFamily.ClusterKind;
import com.minecart.yunxian.budding.BuddingFamily.EnergyRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Growth;
import com.minecart.yunxian.budding.BuddingFamily.GrowthRule;
import com.minecart.yunxian.budding.BuddingFamily.GrowthSpeed;
import com.minecart.yunxian.budding.BuddingFamily.LightRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.budding.BuddingFamily.ToolTier;
import com.minecart.yunxian.budding.BuddingFamily.WorldGen;
import com.minecart.yunxian.budding.FluidRequirement;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * 母岩家族的中央定义表 —— <b>新增母岩只需要改这一个文件</b>。
 * <p>
 * 表里的 {@link #ALL} 每一条就是一个家族的「特点」（{@link BuddingFamily}），
 * 本类据此完成方块注册；{@code ModBlockEntities}、{@code ModCreativeTabs}、
 * {@code ModConfig}、ponder 注册同样遍历这张表，不再各自维护名单。
 * <p>
 * 新增一个普通母岩的步骤：
 * <ol>
 *   <li>在 {@link #ALL} 里加一条（普通母岩直接用 {@code plain(...)}，矿石母岩用 {@code ore(...)}）；</li>
 *   <li>补 5 张材质、5 个 blockstate/模型、5 个掉落表、世界生成 JSON、语言键
 *       （数据生成的部分见 {@code datagen} 包）。</li>
 * </ol>
 * <p>
 * <b>静态初始化方向</b>：本类引用 {@link ModBlocks}（借它的 {@code BLOCKS} 注册表与
 * {@code registerBlock}），而 ModBlocks <b>不得</b>反向引用本类，否则初始化成环，
 * 启动时 {@code ALL} 会是 null。
 */
public final class BuddingFamilies {

    private BuddingFamilies() {
    }

    // ==================== 每型数值常量 ====================
    // 原先散落在各个母岩子类里，集中在此：调整任何一个母岩的手感都只改这一处。
    // 生长速度也在这一节：写在每个家族自己的 Growth.speed 里（见各工厂方法），
    // 红石与青金石是快档、远古残骸是慢档，其余都是正常档——脚本还能用
    // CustomBudding.modify(...) 单独改某一块母岩。

    /** 石头/深板岩 → 对应矿石 */
    private static final int ORE_CONVERSION_CHANCE = 20;
    /**
     * 粗矿块 / 平滑石英 / 福鲁伊克斯块 → 母岩自身（也就是再生传播）的概率基数与半径。
     * 数值本身定义在 {@link BuddingConversions} 里：脚本的 {@code .transform} 用的是同一对常量，
     * 两处必须一致——这是母岩会自己变多的唯一途径。
     */
    private static final int SPREAD_CHANCE = BuddingConversions.INFECTION_CHANCE;
    private static final int SPREAD_RADIUS = BuddingConversions.INFECTION_RADIUS;

    /** 回响母岩：幽匿转化的概率与半径 */
    private static final int ECHO_CONVERSION_CHANCE = 4;
    private static final int ECHO_CONVERSION_RADIUS = 2;
    /** 回响母岩：生长位亮度必须低于此值（即完全无光）才生长 */
    private static final int ECHO_GROWTH_LIGHT_THRESHOLD = 1;

    /** 无光家族的芽/簇亮度（母岩自身发光会挡住它自己的生长位） */
    private static final int DARK_LIGHT = 0;

    /** 荧石：母岩 15；小/中/大芽 3/7/11；晶簇 15 */
    private static final int GLOWSTONE_BUDDING_LIGHT = 15;
    private static final List<Integer> GLOWSTONE_BUD_LIGHT = Arrays.asList(3, 7, 11);
    private static final int GLOWSTONE_CLUSTER_LIGHT = 15;

    /** 红石：母岩自身输出 15；小/中/大芽与晶簇 3/7/11/15 */
    private static final int REDSTONE_BUDDING_SIGNAL = 15;
    private static final int[] REDSTONE_STAGE_SIGNAL = {3, 7, 11, 15};

    /** 可燃冰：与蓝冰相同的摩擦系数 */
    private static final float ICE_FRICTION = 0.989F;

    /**
     * 远古残骸母岩的熔岩罐：只认熔岩（按流体标签判定，静止与流动变体都算），
     * 每次成功生长（以及标了 {@code gated()} 的再生传播）扣 250 mB，罐最多存 1 B（正好一桶）。
     * <p>
     * 这是"给母岩加流体消耗"的样板：脚本用 {@code CustomBuddingOptions#needfluid} 声明的是同一个
     * {@link FluidRequirement}，方块实体、付费钩子、护目镜与比较器全都共用同一条路。
     * 脚本还能用 {@code CustomBudding.modify} 给别的母岩加一个、或者把它取消掉。
     */
    private static final FluidRequirement LAVA_TANK =
            FluidRequirement.ofTag(FluidTags.LAVA, 250, 1000);

    /** 沿用原版紫水晶芽/簇亮度的占位（null = 不显式设置） */
    private static final List<Integer> INHERIT_BUD_LIGHT = Arrays.asList(null, null, null);

    // ==================== 常用组合 ====================

    /** 绝大多数母岩共用的生长特点：无光照/能量要求，无转化，不输出信号，所有维度同速，正常速度 */
    private static final Growth PLAIN_GROWTH = new Growth(GrowthRule.STANDARD,
            LightRequirement.ANY, EnergyRequirement.FREE, ClusterKind.STANDARD, 0,
            GrowthEnvironment.ANY, GrowthSpeed.NORMAL, null);

    /** 下界特产母岩（石英、荧石）的生长环境：只在下界满速，别的地方每次判定还剩一半的概率生长 */
    private static final GrowthEnvironment NETHER_ONLY = GrowthEnvironment.of(0.5, List.of(Level.NETHER));

    /** 下界特产母岩（石英、荧石）的生长特点：和 {@link #PLAIN_GROWTH} 一样从简，但只在下界满速 */
    private static final Growth NETHER_GROWTH = new Growth(GrowthRule.STANDARD,
            LightRequirement.ANY, EnergyRequirement.FREE, ClusterKind.STANDARD, 0,
            NETHER_ONLY, GrowthSpeed.NORMAL, null);

    /** 绝大多数母岩共用的外观：沿用原版亮度/音效，使用共享展示 BE */
    private static final Appearance PLAIN_APPEARANCE = new Appearance(INHERIT_BUD_LIGHT, null, null,
            BlockEntityKind.SHARED_GROWTH, 0, null, null, List.of(), false);

    // ==================== 声明收集（数据生成用） ====================

    /**
     * 家族表声明过的侵染规则：母岩方块 id → 规则表（按声明顺序）。
     * <p>
     * <b>只是数据生成的输入</b>——运行时不读它，运行时读的是由它生成出来的
     * {@code budding_conversion} 配方（见 {@code ModRecipeProvider} 与 {@code BuddingRecipes}）。
     * 声明仍写在各自的家族工厂里（一个家族一处），由 {@link #register} 顺手收进来。
     * <p>
     * 必须声明在 {@link #ALL} <b>之前</b>：家族是静态初始化时就注册的，字段顺序反了这里还是 null。
     */
    private static final Map<String, List<BlockConversion>> DECLARED_CONVERSIONS = new LinkedHashMap<>();

    /** 家族表声明过的侵染规则（母岩方块 id → 规则表）：只给数据生成读 */
    public static Map<String, List<BlockConversion>> declaredConversions() {
        return Collections.unmodifiableMap(DECLARED_CONVERSIONS);
    }

    /** 收一笔家族声明的侵染规则（各家族工厂在声明处调一次）；数据生成按它吐配方，运行时不读这份表 */
    private static void declareConversions(String buddingId, List<BlockConversion> conversions) {
        if (!conversions.isEmpty()) {
            DECLARED_CONVERSIONS.put(buddingId, conversions);
        }
    }

    // ==================== 中央定义表 ====================

    /**
     * 全部母岩家族。顺序 = 创造模式标签顺序；AE2 联动的家族固定排最后
     * （标签里它们在机器与工具之后）。
     */
    public static final List<RegisteredFamily> ALL = List.of(
            plain("rose_quartz", BuddingModel.CUBE_COLUMN, ToolTier.STONE,
                    () -> externalBlock("create:rose_quartz_block")),

            ore("raw_iron", ToolTier.STONE, GrowthSpeed.NORMAL,
                    () -> Blocks.IRON_ORE, () -> Blocks.DEEPSLATE_IRON_ORE, () -> Blocks.RAW_IRON_BLOCK),
            ore("raw_gold", ToolTier.IRON, GrowthSpeed.NORMAL,
                    () -> Blocks.GOLD_ORE, () -> Blocks.DEEPSLATE_GOLD_ORE, () -> Blocks.RAW_GOLD_BLOCK),
            ore("raw_copper", ToolTier.STONE, GrowthSpeed.NORMAL,
                    () -> Blocks.COPPER_ORE, () -> Blocks.DEEPSLATE_COPPER_ORE, () -> Blocks.RAW_COPPER_BLOCK),
            ore("raw_zinc", ToolTier.IRON, GrowthSpeed.NORMAL,
                    () -> externalBlock("create:zinc_ore"), () -> externalBlock("create:deepslate_zinc_ore"),
                    () -> externalBlock("create:raw_zinc_block")),
            ore("diamond", ToolTier.IRON, GrowthSpeed.NORMAL,
                    () -> Blocks.DIAMOND_ORE, () -> Blocks.DEEPSLATE_DIAMOND_ORE, () -> Blocks.DIAMOND_BLOCK),
            ore("lapis", ToolTier.IRON, GrowthSpeed.FAST,
                    () -> Blocks.LAPIS_ORE, () -> Blocks.DEEPSLATE_LAPIS_ORE, () -> Blocks.LAPIS_BLOCK),
            ore("emerald", ToolTier.IRON, GrowthSpeed.NORMAL,
                    () -> Blocks.EMERALD_ORE, () -> Blocks.DEEPSLATE_EMERALD_ORE, () -> Blocks.EMERALD_BLOCK),

            echo(),
            quartz(),
            ancientDebris(),
            arclight(),
            redstone(),
            glowstone(),
            flammableIce(),

            fluix());

    private static final Map<String, RegisteredFamily> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(family -> family.spec().id(), family -> family));

    // ==================== 便于引用的具名条目 ====================

    public static final RegisteredFamily ROSE_QUARTZ = of("rose_quartz");
    public static final RegisteredFamily RAW_IRON = of("raw_iron");
    public static final RegisteredFamily RAW_GOLD = of("raw_gold");
    public static final RegisteredFamily ECHO = of("echo");
    public static final RegisteredFamily ANCIENT_DEBRIS = of("ancient_debris");
    public static final RegisteredFamily ARCLIGHT = of("arclight");
    public static final RegisteredFamily GLOWSTONE = of("glowstone");
    public static final RegisteredFamily FLAMMABLE_ICE = of("flammable_ice");
    public static final RegisteredFamily FLUIX = of("fluix");

    /** 按 id 取家族条目；id 写错时直接抛异常（开发期立即暴露） */
    public static RegisteredFamily of(String id) {
        RegisteredFamily family = BY_ID.get(id);
        if (family == null) {
            throw new IllegalArgumentException("未知的母岩家族: " + id);
        }
        return family;
    }

    /**
     * 半成品家族：方块与物品照常注册（{@code /give} 拿得到、存档与脚本引用都不会坏），
     * 但不进创造物品栏，也不进 JEI 的母岩信息页、物品列表与催化剂——玩家在正常游戏里见不到它。
     * <p>
     * 目前只有弧光石那条链（母岩 / 三档芽 / 晶簇 / 弧光石 / 失活母岩，以及配套的电流浆与桶）。
     * 做完之后删掉这里的判断，这几处会一起把它放出来。
     */
    public static boolean isHidden(RegisteredFamily family) {
        return family == ARCLIGHT;
    }

    /**
     * 强制本类初始化。方块注册表事件之前必须调用一次
     * （{@code Yunxian} 的构造器里调用），否则家族方块会注册过晚而整体缺失。
     */
    public static void bootstrap() {
    }

    // ==================== 家族工厂 ====================

    /** 普通母岩：只有通用生长，没有转化与额外要求 */
    private static RegisteredFamily plain(String id, BuddingModel model, ToolTier tier,
                                          Supplier<? extends ItemLike> drop) {
        return register(new BuddingFamily(id, model, tier, false, null, false, drop, PLAIN_GROWTH, PLAIN_APPEARANCE));
    }

    /**
     * 矿石母岩：相邻石头/深板岩 → 对应矿石；相邻粗矿块（钻石/绿宝石/青金石为矿物块）→ 本母岩。
     * 被打碎时掉落的也是同一档矿物块。
     *
     * @param speed     生长速度档位；青金石是快档（它的晶簇掉 4–9 个，产量起点高但级别低），其余是正常档
     * @param veinBlock 会再生出本母岩的方块，同时是它的掉落物
     */
    private static RegisteredFamily ore(String id, ToolTier tier, GrowthSpeed speed, Supplier<Block> stoneOre,
                                        Supplier<Block> deepslateOre, Supplier<Block> veinBlock) {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ORE_CONVERSION_CHANCE, 1,
                        Replacement.of(() -> Blocks.STONE, stoneOre),
                        Replacement.of(() -> Blocks.DEEPSLATE, deepslateOre)),
                BlockConversion.of(SPREAD_CHANCE, SPREAD_RADIUS, Replacement.toSelf(veinBlock)));
        declareConversions(id + "_budding", conversions);

        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, ClusterKind.STANDARD, 0, GrowthEnvironment.ANY, speed, null);
        // 矿石族的 placed_feature 一律是 <id>_budding_vein，生物群系统一来自 ore_budding_veins
        // （见 data/create_crystal_industry 下的 worldgen/placed_feature 与 neoforge/biome_modifier）
        return register(new BuddingFamily(id, BuddingModel.CUBE_ALL, tier, true,
                WorldGen.of(id + "_budding_vein", "ore_budding_veins"), false,
                veinBlock, growth, PLAIN_APPEARANCE));
    }

    /** 回响母岩：要求生长位完全无光，并把周围可转化方块变成幽匿 */
    private static RegisteredFamily echo() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ECHO_CONVERSION_CHANCE, ECHO_CONVERSION_RADIUS,
                        Replacement.of(ModTags.ECHO_CONVERTIBLE, () -> Blocks.SCULK)));
        declareConversions("echo_budding", conversions);

        Growth growth = new Growth(GrowthRule.STANDARD,
                LightRequirement.below(ECHO_GROWTH_LIGHT_THRESHOLD), EnergyRequirement.FREE,
                ClusterKind.STANDARD, 0, GrowthEnvironment.ANY, GrowthSpeed.NORMAL, null);
        // 芽/簇不自发光：一旦发光就会顶掉自己的生长位
        Appearance appearance = new Appearance(
                List.of(DARK_LIGHT, DARK_LIGHT, DARK_LIGHT), DARK_LIGHT, null,
                BlockEntityKind.ECHO_DISPLAY, 0, null, null, List.of(), false);
        return register(new BuddingFamily("echo", BuddingModel.CUBE_ALL, ToolTier.DIAMOND, true,
                WorldGen.of("echo_budding_in_sculk", "echo_budding_in_deep_dark"), false,
                () -> Blocks.SCULK, growth, appearance));
    }

    /** 石英母岩：下界岩 → 石英矿；平滑石英 → 本母岩 */
    private static RegisteredFamily quartz() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ORE_CONVERSION_CHANCE, 1,
                        Replacement.of(() -> Blocks.NETHERRACK, () -> Blocks.NETHER_QUARTZ_ORE)),
                BlockConversion.of(SPREAD_CHANCE, SPREAD_RADIUS, Replacement.toSelf(() -> Blocks.SMOOTH_QUARTZ)));
        declareConversions("quartz_budding", conversions);

        // 石英是下界特产：只有在下界才满速，搬到别的维度每次判定通过后再掷 1/2 失败
        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, ClusterKind.STANDARD, 0, NETHER_ONLY, GrowthSpeed.NORMAL, null);
        return register(new BuddingFamily("quartz", BuddingModel.CUBE_COLUMN, ToolTier.STONE, true,
                WorldGen.of("quartz_budding_vein", "quartz_budding_vein"), false,
                () -> Blocks.SMOOTH_QUARTZ, growth, PLAIN_APPEARANCE));
    }

    /**
     * 远古残骸母岩：下界特产（和石英母岩一样只在下界满速），挖掘等级同远古残骸（钻石），
     * 模型也是柱体——原版远古残骸的顶面与侧面贴图不同。
     * <p>
     * 它是自带家族里唯一<b>吃熔岩</b>的：方块自己就是容量 1 B 的流体容器（见 {@link #LAVA_TANK}），
     * 每次成功生长扣 250 mB，罐里不够就当这次生长没发生。熔岩用管道（NeoForge 流体能力）或
     * 手持熔岩桶右键通入，空桶可以舀出来，容器里的熔岩不掉落。
     * <p>
     * 侵染同理<b>只有</b>「远古残骸 → 本母岩」这一条再生传播，<b>没有</b>「石头 → 矿石」那一档
     * ——它不会把任何方块变成远古残骸，只是自己会从远古残骸里长出来；这条传播也标了
     * {@code gated()}，一样要付 250 mB。因此想复制一块母岩，得拿远古残骸贴着它、罐里还得有熔岩。
     * <p>
     * 母岩、三档芽与晶簇的<b>物品</b>都是耐火的（{@code minecraft:fire_resistant}），掉进熔岩与火里
     * 不会被烧掉，和原版下界合金物品同一档——家族表里唯一把末位开关打开的就是它。
     */
    private static RegisteredFamily ancientDebris() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(SPREAD_CHANCE, SPREAD_RADIUS, Replacement.toSelf(() -> Blocks.ANCIENT_DEBRIS)).gated());
        declareConversions("ancient_debris_budding", conversions);

        // 慢档（1/20）：它的晶簇掉下界合金碎片，按稀缺资源处理，所以比其余母岩慢一档。
        // 再往下的极慢档（1/50）现在没有出厂成员，留给脚本与附属模组；
        // 想让下界合金量产就把这里改掉，或用脚本
        // CustomBudding.modify('create_crystal_industry:ancient_debris_budding', ...) 单独改
        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, ClusterKind.STANDARD, 0, NETHER_ONLY, GrowthSpeed.SLOW,
                LAVA_TANK);
        // 方块实体按"定义里有没有流体需求"选（见 GenericBuddingBlock#newBlockEntity），
        // 所以这里用不上专用的 BE 类型，共享展示 BE 只在没罐时才会被建出来
        Appearance appearance = new Appearance(INHERIT_BUD_LIGHT, null, null,
                BlockEntityKind.SHARED_GROWTH, 0, null, null, List.of(), false);
        // 世界生成：下界 Y 8–22 的矿脉（与原版远古残骸同一高度带），自然是空罐，要用得自己浇，见
        // data/create_crystal_industry/worldgen 与 neoforge/biome_modifier 下的同名 JSON
        return register(new BuddingFamily("ancient_debris", BuddingModel.CUBE_COLUMN, ToolTier.DIAMOND, true,
                WorldGen.of("ancient_debris_budding_vein", "ancient_debris_budding_vein"), false,
                () -> Blocks.ANCIENT_DEBRIS, growth, appearance, true));
    }

    /**
     * 弧光石母岩：吃 FE 的母岩——方块自己是个 1 M FE 的能量容器（见
     * {@code ArclightBuddingBlockEntity}），每次成功生长扣 10 000 FE，电量不够就当这次生长没发生。
     * <p>
     * 它没有侵染（不会把任何方块变成弧光石），也<b>不自然生成</b>：获取方式还没定，
     * 定下来之前在 JEI 里只显示「暂无自然生成与合成配方」。晶簇掉 1 个弧光石，
     * 弧光石再拿去工作盆冲压成电流浆（配方见 data/create_crystal_industry/recipe）。
     */
    private static RegisteredFamily arclight() {
        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FE, ClusterKind.STANDARD, 0, GrowthEnvironment.ANY, GrowthSpeed.NORMAL, null);
        // 只在晶簇之后追加两件：弧光石（母岩的产物，不是一个方块家族）与失活母岩
        // （藏在 ModBlocks 里的待激活形态，靠雷劈变成上面这块母岩，见 LightningActivation）
        Appearance appearance = new Appearance(INHERIT_BUD_LIGHT, null, null,
                BlockEntityKind.FE_TANK, 0, null, null,
                List.of(() -> ModItems.ARCLIGHT.get(), () -> ModBlocks.INACTIVE_ARCLIGHT_BUDDING.get()), true);
        return register(new BuddingFamily("arclight", BuddingModel.CUBE_ALL, ToolTier.IRON, false, null, false,
                () -> ModItems.ARCLIGHT.get(), growth, appearance));
    }

    /** 红石母岩：矿石母岩的转化规则 + 母岩与各级芽/簇都输出红石信号 */
    private static RegisteredFamily redstone() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ORE_CONVERSION_CHANCE, 1,
                        Replacement.of(() -> Blocks.STONE, () -> Blocks.REDSTONE_ORE),
                        Replacement.of(() -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE_REDSTONE_ORE)),
                BlockConversion.of(SPREAD_CHANCE, SPREAD_RADIUS, Replacement.toSelf(() -> Blocks.REDSTONE_BLOCK)));
        declareConversions("redstone_budding", conversions);

        // 快档：红石是基础材料，且它的晶簇只掉 1 个红石，产量高一点才好用
        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, ClusterKind.REDSTONE, REDSTONE_BUDDING_SIGNAL,
                GrowthEnvironment.ANY, GrowthSpeed.FAST, null);
        return register(new BuddingFamily("redstone", BuddingModel.CUBE_ALL, ToolTier.IRON, true,
                WorldGen.of("redstone_budding_vein", "ore_budding_veins"), false,
                () -> Blocks.REDSTONE_BLOCK, growth, PLAIN_APPEARANCE));
    }

    /** 荧石母岩：母岩与各级芽/簇发光；与石英一样只在下界满速 */
    private static RegisteredFamily glowstone() {
        Appearance appearance = new Appearance(GLOWSTONE_BUD_LIGHT, GLOWSTONE_CLUSTER_LIGHT, null,
                BlockEntityKind.SHARED_GROWTH, GLOWSTONE_BUDDING_LIGHT, null, null, List.of(), false);
        // 世界生成没有自己的 placed_feature：它替换的是原版荧石团（create_crystal_industry:glowstone_budding_blob
        // 覆写了 minecraft:glowstone_extra），所以这里传 null，页面改用语言文件里的手写说明
        return register(new BuddingFamily("glowstone", BuddingModel.CUBE_ALL, ToolTier.NONE, true, null, false,
                () -> Blocks.GLOWSTONE, NETHER_GROWTH, appearance));
    }

    /** 可燃冰母岩：只有目标格含水才生长；冰音效 + 蓝冰摩擦 */
    private static RegisteredFamily flammableIce() {
        Growth growth = new Growth(GrowthRule.SUBMERGED, LightRequirement.ANY,
                EnergyRequirement.FREE, ClusterKind.STANDARD, 0, GrowthEnvironment.ANY, GrowthSpeed.NORMAL, null);
        // 创造栏追加项只剩这两件物品：整套装饰方块统一陈列在机械动力的「建筑方块」页
        // （唯一清单见 ModBlocks.FLAMMABLE_ICE_DECO，注入见 ModCreativeTabs#addDecoToCreateTabs），
        // 本模组自己的标签页里不再重复放一份
        Appearance appearance = new Appearance(INHERIT_BUD_LIGHT, null, SoundType.GLASS,
                BlockEntityKind.ICE_DISPLAY, 0, SoundType.GLASS, ICE_FRICTION,
                List.of(() -> ModItems.FLAMMABLE_ICE.get(), () -> ModBlocks.FLAMMABLE_SUNDAE.get()), false);
        return register(new BuddingFamily("flammable_ice", BuddingModel.CUBE_ALL, ToolTier.NONE, true,
                WorldGen.of("flammable_ice", "add_flammable_ice"), false,
                () -> ModBlocks.FLAMMABLE_ICE_BLOCK.get(), growth, appearance));
    }

    /** 福鲁伊克斯母岩：只在 AE2 存在时注册；生长与传播都要求 AE 供电 */
    private static RegisteredFamily fluix() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(SPREAD_CHANCE, SPREAD_RADIUS,
                        Replacement.toSelf(() -> externalBlock("ae2:fluix_block"))).gated());
        declareConversions("fluix_budding", conversions);

        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.AE2_GRID, ClusterKind.STANDARD, 0, GrowthEnvironment.ANY, GrowthSpeed.NORMAL, null);
        Appearance appearance = new Appearance(INHERIT_BUD_LIGHT, null, null,
                BlockEntityKind.AE2_GRID, 0, null, null, List.of(), false);
        return register(new BuddingFamily("fluix", BuddingModel.CUBE_ALL, ToolTier.STONE, false, null, true,
                () -> externalBlock("ae2:fluix_block"), growth, appearance));
    }

    // ==================== 注册 ====================

    /**
     * 把一条家族定义变成注册好的方块。
     * 顺序要求：三档芽与晶簇必须先于母岩注册（母岩构造器要拿它们做实例）。
     */
    private static RegisteredFamily register(BuddingFamily spec) {
        // AE2 缺席时整条家族不存在（连方块都不注册，标签与创造标签自然也不会提到它）
        if (spec.ae2Gated() && !ModBlocks.AE2_LOADED) {
            return new RegisteredFamily(spec, null, null, null, null, null);
        }

        DeferredBlock<Block> smallBud = stage(spec, Stage.SMALL_BUD);
        DeferredBlock<Block> mediumBud = stage(spec, Stage.MEDIUM_BUD);
        DeferredBlock<Block> largeBud = stage(spec, Stage.LARGE_BUD);
        DeferredBlock<Block> cluster = stage(spec, Stage.CLUSTER);

        DeferredBlock<Block> budding = ModBlocks.registerBlock(spec.buddingId(),
                () -> newBuddingBlock(spec, buddingProperties(spec), smallBud, mediumBud, largeBud, cluster),
                familyItem(spec));

        return new RegisteredFamily(spec, budding, smallBud, mediumBud, largeBud, cluster);
    }

    private static DeferredBlock<Block> stage(BuddingFamily spec, Stage stage) {
        return ModBlocks.registerBlock(spec.stageId(stage.key), () -> newStageBlock(spec, stage), familyItem(spec));
    }

    /**
     * 母岩与芽/簇共用的物品工厂：耐火家族（目前只有远古残骸）的物品带 {@code minecraft:fire_resistant}
     * 组件，掉进熔岩或火里不会被烧掉，与原版下界合金物品同一档。
     * <p>
     * 开关在家族表里（{@link BuddingFamily#fireResistant()}），这里只负责把它翻成物品属性；
     * 方块本身不受影响——原版方块本来就不会被熔岩毁掉。
     */
    private static BiFunction<Block, Item.Properties, BlockItem> familyItem(BuddingFamily spec) {
        return (block, properties) -> new BlockItem(block,
                spec.fireResistant() ? properties.fireResistant() : properties);
    }

    private static GenericBuddingBlock newBuddingBlock(BuddingFamily spec, BlockBehaviour.Properties properties,
                                                       DeferredBlock<Block> smallBud, DeferredBlock<Block> mediumBud,
                                                       DeferredBlock<Block> largeBud, DeferredBlock<Block> cluster) {
        // 回响母岩需要 CAN_SUMMON 状态，只能由子类注册（见 EchoConvertingBuddingBlock 的类注释）
        if (spec.appearance().blockEntity() == BlockEntityKind.ECHO_DISPLAY) {
            return new EchoConvertingBuddingBlock(spec, properties,
                    smallBud.get(), mediumBud.get(), largeBud.get(), cluster.get());
        }
        // 烧流体的母岩多一个 fueled 状态（同样只能由子类注册），客户端据此换 _unpowered 材质。
        // 判据用家族表而不是运行时定义：方块类只能在构造期定，而脚本的 modify 是启动期才跑的
        // （边界见 FueledBuddingBlock 的类注释）
        if (spec.growth().fluid() != null) {
            return new FueledBuddingBlock(spec, properties,
                    smallBud.get(), mediumBud.get(), largeBud.get(), cluster.get());
        }
        return new GenericBuddingBlock(spec, properties,
                smallBud.get(), mediumBud.get(), largeBud.get(), cluster.get());
    }

    private static YunxianClusterBlock newStageBlock(BuddingFamily spec, Stage stage) {
        BlockBehaviour.Properties properties = stageProperties(spec, stage);
        boolean sparks = spec.appearance().sparkParticles();
        if (spec.growth().clusterKind() == ClusterKind.REDSTONE) {
            return new RedstoneClusterBlock(stage.height, stage.aabbOffset, properties, stage.key,
                    REDSTONE_STAGE_SIGNAL[stage.ordinal()], sparks);
        }
        return new YunxianClusterBlock(stage.height, stage.aabbOffset, properties, stage.key, sparks);
    }

    /** 芽/晶簇属性：从对应的原版紫水晶方块拷贝，再按家族覆盖亮度/音效 */
    private static BlockBehaviour.Properties stageProperties(BuddingFamily spec, Stage stage) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(stage.template);
        Appearance appearance = spec.appearance();

        Integer lightLevel = stage == Stage.CLUSTER
                ? appearance.clusterLightLevel()
                : appearance.budLightLevels().get(stage.ordinal());
        if (lightLevel != null) {
            properties = properties.lightLevel(state -> lightLevel);
        }
        if (appearance.stageSound() != null) {
            properties = properties.sound(appearance.stageSound());
        }
        if (stage == Stage.CLUSTER) {
            properties = properties.noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false);
        }
        return properties;
    }

    /** 母岩属性：从原版母岩拷贝，再按家族覆盖亮度/音效/摩擦 */
    private static BlockBehaviour.Properties buddingProperties(BuddingFamily spec) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST);
        Appearance appearance = spec.appearance();

        if (appearance.buddingLightLevel() > 0) {
            properties = properties.lightLevel(state -> appearance.buddingLightLevel());
        }
        if (appearance.buddingSound() != null) {
            properties = properties.sound(appearance.buddingSound());
        }
        if (appearance.buddingFriction() != null) {
            properties = properties.friction(appearance.buddingFriction());
        }
        return properties;
    }

    /** 取其它模组的方块（完整 id，如 create:zinc_ore、ae2:fluix_block） */
    private static Block externalBlock(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    // ==================== 生长阶段 ====================

    /**
     * 四个生长阶段的碰撞箱参数与方块 id 后缀，全部母岩一致。
     * <p>
     * 两个数值是原版 {@code Blocks} 里对应紫水晶芽/簇的构造实参，语义就是
     * {@code AmethystClusterBlock(float height, float aabbOffset, Properties)} 的那两个形参：
     * 芽/簇的六个朝向碰撞箱全由它们算出，朝上时是
     * {@code box(aabbOffset, 0, aabbOffset, 16-aabbOffset, height, 16-aabbOffset)}
     * （见 {@code AmethystClusterBlock} 构造器）。
     * <p>
     * <b>顺序不能反、数值不能"顺手修"</b>：原版小芽是 (3, 4)、中芽是 (4, 3)——小芽的
     * aabbOffset 比中芽还大、height 比中芽还小，看着不成比例，但照抄才能和原版碰撞箱一致。
     */
    public enum Stage {
        SMALL_BUD("small_bud", 3.0F, 4.0F, Blocks.SMALL_AMETHYST_BUD),
        MEDIUM_BUD("medium_bud", 4.0F, 3.0F, Blocks.MEDIUM_AMETHYST_BUD),
        LARGE_BUD("large_bud", 5.0F, 3.0F, Blocks.LARGE_AMETHYST_BUD),
        CLUSTER("cluster", 7.0F, 3.0F, Blocks.AMETHYST_CLUSTER);

        public final String key;
        /** 竖直高度，原版构造器的第一个参数 */
        public final float height;
        /** 水平内缩量，原版构造器的第二个参数 */
        public final float aabbOffset;
        /** 属性拷贝来源 */
        public final Block template;

        Stage(String key, float height, float aabbOffset, Block template) {
            this.key = key;
            this.height = height;
            this.aabbOffset = aabbOffset;
            this.template = template;
        }
    }

    // ==================== 已注册的家族 ====================

    /**
     * 表里一条已注册的家族：{@code spec} 是「特点」，其余是注册出来的方块。
     * AE2 缺席时被 gate 掉的家族整条为 null（{@link #isRegistered()} 为 false）。
     */
    public record RegisteredFamily(BuddingFamily spec,
                                   DeferredBlock<Block> budding,
                                   DeferredBlock<Block> smallBud,
                                   DeferredBlock<Block> mediumBud,
                                   DeferredBlock<Block> largeBud,
                                   DeferredBlock<Block> cluster) {

        public boolean isRegistered() {
            return budding != null;
        }

        public DeferredBlock<Block> stage(Stage stage) {
            return switch (stage) {
                case SMALL_BUD -> smallBud;
                case MEDIUM_BUD -> mediumBud;
                case LARGE_BUD -> largeBud;
                case CLUSTER -> cluster;
            };
        }

        /** 该家族的全部方块，顺序：母岩 → 小/中/大芽 → 晶簇 */
        public List<DeferredBlock<Block>> blocks() {
            return isRegistered() ? List.of(budding, smallBud, mediumBud, largeBud, cluster) : List.of();
        }
    }
}
