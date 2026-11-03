package com.minecart.yunxian.budding;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一个母岩家族的「全部特点」——生长速度、光照要求、含水要求、充能要求、生长规则、方块转化，
 * 以及各级芽与晶簇的亮度/音效、用哪个方块实体、进不进创造标签、要不要世界生成开关。
 * <p>
 * 生长概率写在 {@link Growth#speed()} 里（曾经在配置文件的四张 id 列表里，已挪回本表）；
 * 脚本可以用 {@code CustomBudding.modify(...)} 改掉某一块母岩的档位。
 * <p>
 * 新增一个母岩家族 = 在 {@link BuddingFamilies} 的表里加一条；方块注册、生长逻辑、
 * 护目镜提示、创造模式标签、配置开关、ponder 条目都由这一条派生。
 * <p>
 * 本类刻意保持「纯数据」：不持有注册表对象，也不出现任何 AE2 类型。
 * 常量加载的类一旦在字段/方法描述符里出现缺失的类，JVM 校验就会崩溃
 * （原因见 {@code integration.ae2.AE2BlockEntities} 的类注释）。
 */
public record BuddingFamily(
        /** 方块 id 前缀，同时是配置项 worldgen.generate_&lt;id&gt; 的键名 */
        String id,
        /** 方块模型形态（数据生成用）：普通六面体或带侧面/顶面的柱体 */
        BuddingModel buddingModel,
        /** 挖掘等级（数据生成用） */
        ToolTier toolTier,
        /** 是否在世界中生成：决定是否产出一个 worldgen.generate_&lt;id&gt; 配置开关 */
        boolean generateInWorld,
        /**
         * 世界生成 JSON 的出处；null = 本模组没给它发世界生成 JSON。
         * <p>
         * 供 JEI 的母岩信息页读取真实的高度/稀有度/生物群系用（见 {@code compat.jei}），
         * 与实际生成逻辑是同一份数据，改了 JSON、重新构建后页面就跟着变，不需要同步任何文案。
         * 注意它与 {@link #generateInWorld()} 不是一回事：荧石母岩开着世界生成，却没有自己的
         * feature（它走原版荧石团的底部替换），所以这里是 null。
         */
        @Nullable WorldGen worldGen,
        /** 是否只在 AE2 存在时注册（仅福鲁伊克斯母岩） */
        boolean ae2Gated,
        /** 母岩被打碎时掉落什么（数据生成用：比母岩低一档的方块） */
        Supplier<? extends ItemLike> buddingDrop,
        /** 生长特点 */
        Growth growth,
        /** 外观与注册特点 */
        Appearance appearance,
        /**
         * 母岩、三档芽与晶簇的<b>物品</b>是否耐火：true 时它们的 {@code BlockItem} 带
         * {@code minecraft:fire_resistant} 组件，掉进熔岩与火里不会被烧掉，与原版下界合金物品同一档
         * （走原版 {@code Item.Properties#fireResistant()}，判定在 {@code ItemEntity#fireImmune()}）。
         * <p>
         * 只管物品形态——方块本身原版就不会被熔岩毁掉。目前只有远古残骸家族为 true：它的晶簇掉
         * 下界合金碎片，整条就是下界合金那条线；其余母岩保持原版物品的表现。
         */
        boolean fireResistant) {

    /**
     * 不耐火的家族（默认）：除了远古残骸之外的母岩全是这种。
     * <p>
     * 留着这个构造器是为了别让「耐火」这一个开关把十处家族声明都撑长一行；要耐火的家族
     * （目前只有远古残骸）在声明处显式写 {@code true}，一眼能看出它和别的家族不同。
     */
    public BuddingFamily(String id, BuddingModel buddingModel, ToolTier toolTier, boolean generateInWorld,
                         @Nullable WorldGen worldGen, boolean ae2Gated, Supplier<? extends ItemLike> buddingDrop,
                         Growth growth, Appearance appearance) {
        this(id, buddingModel, toolTier, generateInWorld, worldGen, ae2Gated, buddingDrop, growth, appearance, false);
    }

    /** 母岩方块 id：&lt;id&gt;_budding */
    public String buddingId() {
        return id + "_budding";
    }

    /** 芽/晶簇方块 id：&lt;id&gt;_&lt;stageKey&gt; */
    public String stageId(String stageKey) {
        return id + "_" + stageKey;
    }

    // ==================== 生长特点 ====================

    /**
     * 生长特点：规则、光照/能量门槛、随机刻副作用、信号、生长速度、流体消耗。
     * <p>
     * 生长概率就在这一条里（{@link #speed()}），不再由配置文件决定；脚本还能用
     * {@code CustomBudding.modify} 单独改某一块方块。
     */
    public record Growth(
            /** 通用，或「只有目标格含水才生长」的可燃冰式 */
            GrowthRule rule,
            /** 生长位的光照要求 */
            LightRequirement light,
            /**
             * 生长（及付费转化）怎么付费：免费、AE 网格能量，或 FE。
             * <p>
             * <b>烧流体不在这里</b>：那是 {@link #fluid()}，与免费/电费可以叠加判断（见
             * {@code GenericBuddingBlock#payGrowthCost}）。
             */
            EnergyRequirement energy,
            /** 芽与晶簇的方块类型 */
            ClusterKind clusterKind,
            /** 母岩自身输出的红石强度，0 = 不输出（红石母岩为 15） */
            int buddingSignal,
            /**
             * 生长环境要求（维度 + 群系）：只在列出的维度/群系里正常生长，出了地盘每次判定通过后再掷一次、
             * 只剩 {@link GrowthEnvironment#outsideGrowthChance()} 的概率继续生长。
             * {@link GrowthEnvironment#ANY}（大部分家族）= 哪里都一样长。
             * <p>
             * 石英母岩与荧石母岩写「只在下界、其它地方一半被打回」——它们是下界特产，搬去主世界就该减产。
             */
            GrowthEnvironment growthEnvironment,
            /**
             * 生长速度档位：每次随机刻有 {@code 1/speed().chance()} 的概率推进一级。
             * 这里给的是<b>出厂设置</b>——脚本能用 {@code CustomBudding.modify(...)}
             * 把它改成别的档位（或任意整数概率），所以运行时以 {@code GrowthDefinition#chance()} 为准。
             */
            GrowthSpeed speed,
            /**
             * 生长要消耗的流体（母岩自带一个小罐）；{@code null} = 不烧流体。
             * <p>
             * 远古残骸母岩（熔岩，1 B 罐、每次 250 mB）走的就是这一项；脚本母岩用
             * {@code CustomBuddingOptions#needfluid} 声明，两者运行时是同一条路——方块实体是
             * {@code FluidTankBuddingBlockEntity}，付费钩子在 {@code GenericBuddingBlock#payGrowthCost}。
             * 脚本还能用 {@code CustomBudding.modify} 给别的母岩加、改、取消它
             * （运行时以 {@code GrowthDefinition#fluid()} 为准）。
             * <p>
             * <b>附属模组注意</b>：罐的合法方块表只按本模组自己的家族表生成，所以自己的家族声明了
             * 流体之后还得在自己的构造器里补一次
             * {@code BuddingRegistration.declareFluidBuddingBlock(方块 id)}，否则区块重载时
             * 方块实体会被 {@code isValid} 丢掉。
             */
            @Nullable FluidRequirement fluid) {
    }

    // ==================== 外观与注册特点 ====================

    /**
     * 外观与注册特点：亮度、音效、摩擦、方块实体、创造标签追加项。
     */
    public record Appearance(
            /** 三档芽的亮度，长度 3；元素为 null 表示沿用原版紫水晶芽的亮度 */
            List<Integer> budLightLevels,
            /** 晶簇亮度，null 表示沿用原版紫水晶簇的亮度 */
            @Nullable Integer clusterLightLevel,
            /** 芽与晶簇音效，null 表示沿用原版 */
            @Nullable SoundType stageSound,
            /** 母岩使用的方块实体 */
            BlockEntityKind blockEntity,
            /** 母岩自身亮度，0 = 沿用原版母岩（荧石母岩为 15） */
            int buddingLightLevel,
            /** 母岩音效，null 表示沿用原版 */
            @Nullable SoundType buddingSound,
            /** 母岩摩擦系数，null 表示沿用原版 */
            @Nullable Float buddingFriction,
            /**
             * 创造模式标签里紧跟在晶簇之后追加的物品（可燃冰家族是燃料与可燃冰圣代）。
             * <p>
             * 这里的方块<b>只</b>进本模组自己的标签页。可燃冰的整套装饰方块是个例外：
             * 它们不走这条通道，而是统一陈列在机械动力的「建筑方块」页
             * （清单见 {@code ModBlocks.FLAMMABLE_ICE_DECO}）。
             */
            List<Supplier<? extends ItemLike>> tabExtras,
            /**
             * 方块周围是否冒电火花粒子（弧光石家族为 true）。
             * 走原版 {@code Block#animateTick}：只在客户端、只对玩家附近的方块调用，不需要自己扫区块。
             */
            boolean sparkParticles) {
    }

    // ==================== 嵌套枚举 ====================

    public enum BuddingModel {
        CUBE_ALL,
        CUBE_COLUMN
    }

    /**
     * 一个家族的世界生成 JSON 出处——都是本模组自己数据包里的文件名（不含 {@code .json}）：
     * <ul>
     *   <li>{@code feature}：{@code data/create_crystal_industry/worldgen/placed_feature/<feature>.json}，
     *       里面写高度范围与每区块概率；</li>
     *   <li>{@code biomeModifier}：{@code data/create_crystal_industry/neoforge/biome_modifier/<biomeModifier>.json}，
     *       里面写这个 feature 加进哪些生物群系。</li>
     * </ul>
     * 两个名字都由 JEI 的信息页在运行时现读（见 {@code compat.jei.GenerationInfoReader}）。
     */
    public record WorldGen(String feature, String biomeModifier) {

        public static WorldGen of(String feature, String biomeModifier) {
            return new WorldGen(feature, biomeModifier);
        }
    }

    public enum ToolTier {
        NONE,
        STONE,
        IRON,
        DIAMOND
    }

    /**
     * 生长速度档位：随机刻抽中母岩时，有 {@code 1/chance()} 的概率推进一级。
     * <p>
     * 一个母岩属于哪一档由家族表的 {@link Growth#speed()} 声明——红石与青金石是快档、
     * 远古残骸是慢档、其余都是正常档（极慢档没有出厂成员）；脚本还能用 {@code CustomBudding.modify(...)}
     * 把某一块母岩改成别的档位（甚至任意整数概率，那时界面改用 {@link #nearest(int)} 的定性说法）。
     * <p>
     * 本枚举同时也是<b>界面词汇</b>：护目镜与 JEI 的概率行只说「缓慢」「很快」这类话，
     * {@link #langSuffix()} 就是那几个语言键的后缀。
     */
    public enum GrowthSpeed {
        /** 极慢：1/50 */
        VERY_SLOW(50),
        /** 慢：1/20 */
        SLOW(20),
        /** 正常：1/5，与原版紫水晶母岩同速（出厂默认档） */
        NORMAL(5),
        /** 快：1/1，被抽中必定生长 */
        FAST(1);

        private final int chance;

        GrowthSpeed(int chance) {
            this.chance = chance;
        }

        /** 概率基数 n：每次随机刻有 1/n 的概率推进一级 */
        public int chance() {
            return chance;
        }

        /**
         * 把概率基数归到最接近的档位——给<b>不是按档位声明</b>的概率（脚本给的具体数字、
         * 附属模组声明的定义、原版紫水晶）用：界面只报定性说法（「缓慢」「很快」），不写具体概率。
         * <p>
         * 例如 2–5 归 {@link #NORMAL}、6–20 归 {@link #SLOW}、大于 50 归 {@link #VERY_SLOW}。
         */
        public static GrowthSpeed nearest(int chance) {
            for (GrowthSpeed tier : FAST_TO_SLOW) {
                if (chance <= tier.chance) {
                    return tier;
                }
            }
            return VERY_SLOW;
        }

        /**
         * 界面语言键的后缀（枚举名的小写）：{@code speed.tier.<后缀>}、{@code speed.word.<后缀>}
         * 与 {@code goggles.scripted.speed.<后缀>} 都拼它，免得三处各写一份 switch。
         */
        public String langSuffix() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 由快到慢，供 {@link #nearest(int)} 顺序查找（枚举常量初始化后才由静态块赋值） */
        private static final GrowthSpeed[] FAST_TO_SLOW = {FAST, NORMAL, SLOW};
    }

    /** 生长规则 */
    public enum GrowthRule {
        /** 通用：母岩自身不在液体中即可生长 */
        STANDARD,
        /** 水下：只有目标格含水时才长新芽/进阶（可燃冰） */
        SUBMERGED
    }

    /**
     * 生长能量来源。
     * <p>
     * <b>「烧流体」不在这里</b>：那种付费写在 {@link Growth#fluid()} 里（远古残骸母岩烧熔岩、
     * 脚本用 {@code CustomBudding.modify} 给别的母岩加流体消耗，走的都是同一条路）。
     */
    public enum EnergyRequirement {
        /** 免费生长 */
        FREE,
        /** 需要 AE 网格供电（福鲁伊克斯母岩） */
        AE2_GRID,
        /**
         * 需要母岩方块自己存的 FE（弧光石母岩）：方块是 1 M FE 的能量容器，
         * 每次成功生长扣 {@code ArclightBuddingBlockEntity#COST_PER_GROWTH}，电量不够就放弃这次生长。
         */
        FE
    }

    /** 芽/晶簇的方块类型 */
    public enum ClusterKind {
        /** 普通晶簇 */
        STANDARD,
        /** 红石晶簇：各级输出 3/7/11/15 的信号 */
        REDSTONE
    }

    /**
     * 母岩使用的方块实体类型。
     * <p>
     * <b>带流体罐的母岩不在这里</b>：罐由 {@link Growth#fluid()} 决定——定义里有流体需求就用通用罐
     * （见 {@code FluidTankBuddingBlockEntity}），方块实体类型对"有没有罐"没有投票权。
     */
    public enum BlockEntityKind {
        /** 共享的「生长速度」展示 BE（绝大多数母岩） */
        SHARED_GROWTH,
        /** 回响母岩专用展示 BE（多一行监守者警告与生长状态） */
        ECHO_DISPLAY,
        /** 可燃冰母岩专用展示 BE（多一行含水提示） */
        ICE_DISPLAY,
        /** 福鲁伊克斯母岩专用 BE（持 ME 网格节点，负责扣 AE） */
        AE2_GRID,
        /** 弧光石母岩专用 BE（存 1 M FE，负责扣电） */
        FE_TANK
    }

    /**
     * 生长位的光照要求。
     * 默认 {@link #ANY}（无要求）；回响母岩要求目标格亮度为 0，即 {@code below(1)}。
     */
    public record LightRequirement(Kind kind, int threshold) {

        public enum Kind {
            ANY,
            BELOW
        }

        public static final LightRequirement ANY = new LightRequirement(Kind.ANY, 0);

        /** 目标格亮度必须 &lt; threshold 才允许生长 */
        public static LightRequirement below(int threshold) {
            return new LightRequirement(Kind.BELOW, threshold);
        }

        /** 客户端也要判（回响母岩的护目镜提示），因此接受 {@link Level} 而非 ServerLevel */
        public boolean allows(Level level, BlockPos pos) {
            return kind == Kind.ANY || level.getMaxLocalRawBrightness(pos) < threshold;
        }
    }

    /**
     * 家族表<b>声明</b>的一条侵染规则：每随机刻有 1/chance 概率触发一次尝试，
     * 在母岩周围半径 radius 的立方体内随机取一格，命中第一条替换规则即写入。
     * <p>
     * 声明写在这里（每个家族工厂一处），由 {@code ModRecipeProvider} 翻成
     * {@code budding_conversion} 配方 JSON；<b>运行时读的是配方，不是这两个 record</b>
     * ——所以改一条转化既可以改家族表再跑数据生成，也可以直接在整合包里覆盖那份 JSON。
     */
    public record BlockConversion(int chance, int radius, List<Replacement> replacements, boolean energyGated) {

        public static BlockConversion of(int chance, int radius, Replacement... replacements) {
            return new BlockConversion(chance, radius, List.of(replacements), false);
        }

        /** 该转化同样需要支付生长能量（福鲁伊克斯传播） */
        public BlockConversion gated() {
            return new BlockConversion(chance, radius, replacements, true);
        }
    }

    /**
     * 声明里的一条替换：匹配输入方块（方块本身或标签）→ 写入输出方块。
     * {@code output} 为 null 表示写入本母岩自身（粗矿块/平滑石英 → 母岩的再生传播）；
     * 生成配方时它会落成 {@code output} 字段缺席，由引擎在运行时展开。写法见 {@link BlockConversion}。
     */
    public record Replacement(@Nullable Supplier<Block> input, @Nullable TagKey<Block> inputTag,
                              @Nullable Supplier<Block> output) {

        public static Replacement of(Supplier<Block> input, Supplier<Block> output) {
            return new Replacement(input, null, output);
        }

        public static Replacement of(TagKey<Block> inputTag, Supplier<Block> output) {
            return new Replacement(null, inputTag, output);
        }

        /** 输入方块 → 本母岩自身 */
        public static Replacement toSelf(Supplier<Block> input) {
            return new Replacement(input, null, null);
        }
    }

    /** 是否把该方块状态视为「空位」 */
    public static boolean isFree(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
