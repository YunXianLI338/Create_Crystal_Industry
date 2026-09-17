package com.minecart.yunxian.budding;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.minecart.yunxian.block.budding.EchoConvertingBuddingBlock;
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
import com.minecart.yunxian.budding.BuddingFamily.LightRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.budding.BuddingFamily.ToolTier;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
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
    // 例外：生长概率不在本类，由配置文件按 GrowthSpeed 四档决定（默认全部「正常」）。

    /** 石头/深板岩 → 对应矿石 */
    private static final int ORE_CONVERSION_CHANCE = 20;
    /** 粗矿块（下界岩 → 石英矿同档）/平滑石英/福鲁伊克斯块 → 母岩自身 */
    private static final int SPREAD_CHANCE = 25_000;

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

    /** 沿用原版紫水晶芽/簇亮度的占位（null = 不显式设置） */
    private static final List<Integer> INHERIT_BUD_LIGHT = Arrays.asList(null, null, null);

    // ==================== 常用组合 ====================

    /** 绝大多数母岩共用的生长特点：无光照/能量要求，无转化，不输出信号 */
    private static final Growth PLAIN_GROWTH = new Growth(GrowthRule.STANDARD,
            LightRequirement.ANY, EnergyRequirement.FREE, List.of(), ClusterKind.STANDARD, 0);

    /** 绝大多数母岩共用的外观：沿用原版亮度/音效，使用共享展示 BE */
    private static final Appearance PLAIN_APPEARANCE = new Appearance(INHERIT_BUD_LIGHT, null, null,
            BlockEntityKind.SHARED_GROWTH, 0, null, null, List.of());

    // ==================== 中央定义表 ====================

    /**
     * 全部母岩家族。顺序 = 创造模式标签顺序；AE2 联动的家族固定排最后
     * （标签里它们在机器与工具之后）。
     */
    public static final List<RegisteredFamily> ALL = List.of(
            plain("rose_quartz", BuddingModel.CUBE_COLUMN, ToolTier.STONE,
                    () -> externalBlock("create:rose_quartz_block")),

            ore("raw_iron", ToolTier.STONE,
                    () -> Blocks.IRON_ORE, () -> Blocks.DEEPSLATE_IRON_ORE, () -> Blocks.RAW_IRON_BLOCK),
            ore("raw_gold", ToolTier.IRON,
                    () -> Blocks.GOLD_ORE, () -> Blocks.DEEPSLATE_GOLD_ORE, () -> Blocks.RAW_GOLD_BLOCK),
            ore("raw_copper", ToolTier.STONE,
                    () -> Blocks.COPPER_ORE, () -> Blocks.DEEPSLATE_COPPER_ORE, () -> Blocks.RAW_COPPER_BLOCK),
            ore("raw_zinc", ToolTier.IRON,
                    () -> externalBlock("create:zinc_ore"), () -> externalBlock("create:deepslate_zinc_ore"),
                    () -> externalBlock("create:raw_zinc_block")),
            ore("diamond", ToolTier.IRON,
                    () -> Blocks.DIAMOND_ORE, () -> Blocks.DEEPSLATE_DIAMOND_ORE, () -> Blocks.DIAMOND_BLOCK),
            ore("lapis", ToolTier.IRON,
                    () -> Blocks.LAPIS_ORE, () -> Blocks.DEEPSLATE_LAPIS_ORE, () -> Blocks.LAPIS_BLOCK),
            ore("emerald", ToolTier.IRON,
                    () -> Blocks.EMERALD_ORE, () -> Blocks.DEEPSLATE_EMERALD_ORE, () -> Blocks.EMERALD_BLOCK),

            echo(),
            quartz(),
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
     * 强制本类初始化。方块注册表事件之前必须调用一次
     * （{@code Yunxian} 的构造器里调用），否则家族方块会注册过晚而整体缺失。
     */
    public static void bootstrap() {
    }

    // ==================== 家族工厂 ====================

    /** 普通母岩：只有通用生长，没有转化与额外要求 */
    private static RegisteredFamily plain(String id, BuddingModel model, ToolTier tier,
                                          Supplier<? extends ItemLike> drop) {
        return register(new BuddingFamily(id, model, tier, false, false, drop, PLAIN_GROWTH, PLAIN_APPEARANCE));
    }

    /**
     * 矿石母岩：相邻石头/深板岩 → 对应矿石；相邻粗矿块（钻石/绿宝石/青金石为矿物块）→ 本母岩。
     * 被打碎时掉落的也是同一档矿物块。
     *
     * @param veinBlock 会再生出本母岩的方块，同时是它的掉落物
     */
    private static RegisteredFamily ore(String id, ToolTier tier, Supplier<Block> stoneOre,
                                        Supplier<Block> deepslateOre, Supplier<Block> veinBlock) {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ORE_CONVERSION_CHANCE, 1,
                        Replacement.of(() -> Blocks.STONE, stoneOre),
                        Replacement.of(() -> Blocks.DEEPSLATE, deepslateOre)),
                BlockConversion.of(SPREAD_CHANCE, 1, Replacement.toSelf(veinBlock)));

        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, conversions, ClusterKind.STANDARD, 0);
        return register(new BuddingFamily(id, BuddingModel.CUBE_ALL, tier, true, false, veinBlock,
                growth, PLAIN_APPEARANCE));
    }

    /** 回响母岩：要求生长位完全无光，并把周围可转化方块变成幽匿 */
    private static RegisteredFamily echo() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ECHO_CONVERSION_CHANCE, ECHO_CONVERSION_RADIUS,
                        Replacement.of(ModTags.ECHO_CONVERTIBLE, () -> Blocks.SCULK)));

        Growth growth = new Growth(GrowthRule.STANDARD,
                LightRequirement.below(ECHO_GROWTH_LIGHT_THRESHOLD), EnergyRequirement.FREE,
                conversions, ClusterKind.STANDARD, 0);
        // 芽/簇不自发光：一旦发光就会顶掉自己的生长位
        Appearance appearance = new Appearance(
                List.of(DARK_LIGHT, DARK_LIGHT, DARK_LIGHT), DARK_LIGHT, null,
                BlockEntityKind.ECHO_DISPLAY, 0, null, null, List.of());
        return register(new BuddingFamily("echo", BuddingModel.CUBE_ALL, ToolTier.DIAMOND, true, false,
                () -> Blocks.SCULK, growth, appearance));
    }

    /** 石英母岩：下界岩 → 石英矿；平滑石英 → 本母岩 */
    private static RegisteredFamily quartz() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ORE_CONVERSION_CHANCE, 1,
                        Replacement.of(() -> Blocks.NETHERRACK, () -> Blocks.NETHER_QUARTZ_ORE)),
                BlockConversion.of(SPREAD_CHANCE, 1, Replacement.toSelf(() -> Blocks.SMOOTH_QUARTZ)));

        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, conversions, ClusterKind.STANDARD, 0);
        return register(new BuddingFamily("quartz", BuddingModel.CUBE_COLUMN, ToolTier.STONE, true, false,
                () -> Blocks.SMOOTH_QUARTZ, growth, PLAIN_APPEARANCE));
    }

    /** 红石母岩：矿石母岩的转化规则 + 母岩与各级芽/簇都输出红石信号 */
    private static RegisteredFamily redstone() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(ORE_CONVERSION_CHANCE, 1,
                        Replacement.of(() -> Blocks.STONE, () -> Blocks.REDSTONE_ORE),
                        Replacement.of(() -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE_REDSTONE_ORE)),
                BlockConversion.of(SPREAD_CHANCE, 1, Replacement.toSelf(() -> Blocks.REDSTONE_BLOCK)));

        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.FREE, conversions, ClusterKind.REDSTONE, REDSTONE_BUDDING_SIGNAL);
        return register(new BuddingFamily("redstone", BuddingModel.CUBE_ALL, ToolTier.IRON, true, false,
                () -> Blocks.REDSTONE_BLOCK, growth, PLAIN_APPEARANCE));
    }

    /** 荧石母岩：母岩与各级芽/簇发光 */
    private static RegisteredFamily glowstone() {
        Appearance appearance = new Appearance(GLOWSTONE_BUD_LIGHT, GLOWSTONE_CLUSTER_LIGHT, null,
                BlockEntityKind.SHARED_GROWTH, GLOWSTONE_BUDDING_LIGHT, null, null, List.of());
        return register(new BuddingFamily("glowstone", BuddingModel.CUBE_ALL, ToolTier.NONE, true, false,
                () -> Blocks.GLOWSTONE, PLAIN_GROWTH, appearance));
    }

    /** 可燃冰母岩：只有目标格含水才生长；冰音效 + 蓝冰摩擦 */
    private static RegisteredFamily flammableIce() {
        Growth growth = new Growth(GrowthRule.SUBMERGED, LightRequirement.ANY,
                EnergyRequirement.FREE, List.of(), ClusterKind.STANDARD, 0);
        Appearance appearance = new Appearance(INHERIT_BUD_LIGHT, null, SoundType.GLASS,
                BlockEntityKind.ICE_DISPLAY, 0, SoundType.GLASS, ICE_FRICTION,
                List.of(() -> ModBlocks.FLAMMABLE_ICE_BLOCK.get(), () -> ModItems.FLAMMABLE_ICE.get()));
        return register(new BuddingFamily("flammable_ice", BuddingModel.CUBE_ALL, ToolTier.NONE, true, false,
                () -> ModBlocks.FLAMMABLE_ICE_BLOCK.get(), growth, appearance));
    }

    /** 福鲁伊克斯母岩：只在 AE2 存在时注册；生长与传播都要求 AE 供电 */
    private static RegisteredFamily fluix() {
        List<BlockConversion> conversions = List.of(
                BlockConversion.of(SPREAD_CHANCE, 1,
                        Replacement.toSelf(() -> externalBlock("ae2:fluix_block"))).gated());

        Growth growth = new Growth(GrowthRule.STANDARD, LightRequirement.ANY,
                EnergyRequirement.AE2_GRID, conversions, ClusterKind.STANDARD, 0);
        Appearance appearance = new Appearance(INHERIT_BUD_LIGHT, null, null,
                BlockEntityKind.AE2_GRID, 0, null, null, List.of());
        return register(new BuddingFamily("fluix", BuddingModel.CUBE_ALL, ToolTier.STONE, false, true,
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
                () -> newBuddingBlock(spec, buddingProperties(spec), smallBud, mediumBud, largeBud, cluster));

        return new RegisteredFamily(spec, budding, smallBud, mediumBud, largeBud, cluster);
    }

    private static DeferredBlock<Block> stage(BuddingFamily spec, Stage stage) {
        return ModBlocks.registerBlock(spec.stageId(stage.key), () -> newStageBlock(spec, stage));
    }

    private static GenericBuddingBlock newBuddingBlock(BuddingFamily spec, BlockBehaviour.Properties properties,
                                                       DeferredBlock<Block> smallBud, DeferredBlock<Block> mediumBud,
                                                       DeferredBlock<Block> largeBud, DeferredBlock<Block> cluster) {
        // 回响母岩需要 CAN_SUMMON 状态，只能由子类注册（见 EchoConvertingBuddingBlock 的类注释）
        if (spec.appearance().blockEntity() == BlockEntityKind.ECHO_DISPLAY) {
            return new EchoConvertingBuddingBlock(spec, properties,
                    smallBud.get(), mediumBud.get(), largeBud.get(), cluster.get());
        }
        return new GenericBuddingBlock(spec, properties,
                smallBud.get(), mediumBud.get(), largeBud.get(), cluster.get());
    }

    private static YunxianClusterBlock newStageBlock(BuddingFamily spec, Stage stage) {
        BlockBehaviour.Properties properties = stageProperties(spec, stage);
        if (spec.growth().clusterKind() == ClusterKind.REDSTONE) {
            return new RedstoneClusterBlock(stage.stage, stage.height, properties, stage.key,
                    REDSTONE_STAGE_SIGNAL[stage.ordinal()]);
        }
        return new YunxianClusterBlock(stage.stage, stage.height, properties, stage.key);
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

    /** 四个生长阶段的形状与方块 id 后缀，全部母岩一致 */
    public enum Stage {
        SMALL_BUD("small_bud", 1, 1, Blocks.SMALL_AMETHYST_BUD),
        MEDIUM_BUD("medium_bud", 3, 2, Blocks.MEDIUM_AMETHYST_BUD),
        LARGE_BUD("large_bud", 5, 3, Blocks.LARGE_AMETHYST_BUD),
        CLUSTER("cluster", 7, 3, Blocks.AMETHYST_CLUSTER);

        public final String key;
        public final int stage;
        public final int height;
        /** 属性拷贝来源 */
        public final Block template;

        Stage(String key, int stage, int height, Block template) {
            this.key = key;
            this.stage = stage;
            this.height = height;
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
