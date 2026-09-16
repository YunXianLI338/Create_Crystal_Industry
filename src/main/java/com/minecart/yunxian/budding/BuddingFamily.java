package com.minecart.yunxian.budding;

import java.util.List;
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
 * 一个母岩家族的「全部特点」——光照要求、含水要求、充能要求、生长概率、方块转化，
 * 以及各级芽与晶簇的亮度/音效、用哪个方块实体、进不进创造标签、要不要世界生成开关。
 * <p>
 * 新增一个母岩家族 = 在 {@link BuddingFamilies} 的表里加一条；方块注册、生长逻辑、
 * 护目镜提示、创造模式标签、配置开关、ponder 条目都由这一条派生。
 * <p>
 * 本类刻意保持「纯数据」：不持有注册表对象，也不出现任何 AE2 类型。
 * 常量加载的类一旦在字段/方法描述符里出现缺失的类，JVM 校验就会崩溃
 * （原因见 {@code integration.ae2.AE2BlockEntities} 的类注释）。
 */
public record BuddingFamily(
        /** 方块 id 前缀，同时是配置项 generate_&lt;id&gt; 的键名 */
        String id,
        /** 方块模型形态（数据生成用）：普通六面体或带侧面/顶面的柱体 */
        BuddingModel buddingModel,
        /** 挖掘等级（数据生成用） */
        ToolTier toolTier,
        /** 是否在世界中生成：决定是否产出一个 generate_&lt;id&gt; 配置开关 */
        boolean generateInWorld,
        /** 是否只在 AE2 存在时注册（仅福鲁伊克斯母岩） */
        boolean ae2Gated,
        /** 母岩被打碎时掉落什么（数据生成用：比母岩低一档的方块） */
        Supplier<? extends ItemLike> buddingDrop,
        /** 生长特点 */
        Growth growth,
        /** 外观与注册特点 */
        Appearance appearance) {

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
     * 生长特点：概率、规则、光照/能量门槛、随机刻副作用、信号。
     */
    public record Growth(
            /** 生长概率基数：每次随机刻有 1/chance 概率推进一次 */
            int chance,
            /** 通用，或「只有目标格含水才生长」的可燃冰式 */
            GrowthRule rule,
            /** 生长位的光照要求 */
            LightRequirement light,
            /** 生长（及付费转化）是否需要 AE 能量 */
            EnergyRequirement energy,
            /** 随机刻副作用：转化/传播规则，按顺序各消耗一次随机数 */
            List<BlockConversion> conversions,
            /** 芽与晶簇的方块类型 */
            ClusterKind clusterKind,
            /** 母岩自身输出的红石强度，0 = 不输出（红石母岩为 15） */
            int buddingSignal) {
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
            /** 创造模式标签里紧跟在晶簇之后追加的物品（可燃冰的装饰方块与燃料） */
            List<Supplier<? extends ItemLike>> tabExtras) {
    }

    // ==================== 嵌套枚举 ====================

    public enum BuddingModel {
        CUBE_ALL,
        CUBE_COLUMN
    }

    public enum ToolTier {
        NONE,
        STONE,
        IRON,
        DIAMOND
    }

    /** 生长规则 */
    public enum GrowthRule {
        /** 通用：母岩自身不在液体中即可生长 */
        STANDARD,
        /** 水下：只有目标格含水时才长新芽/进阶（可燃冰） */
        SUBMERGED
    }

    /** 生长能量来源 */
    public enum EnergyRequirement {
        /** 免费生长 */
        FREE,
        /** 需要 AE 网格供电（福鲁伊克斯母岩） */
        AE2_GRID
    }

    /** 芽/晶簇的方块类型 */
    public enum ClusterKind {
        /** 普通晶簇 */
        STANDARD,
        /** 红石晶簇：各级输出 3/7/11/15 的信号 */
        REDSTONE
    }

    /** 母岩使用的方块实体类型 */
    public enum BlockEntityKind {
        /** 共享的「生长速度」展示 BE（绝大多数母岩） */
        SHARED_GROWTH,
        /** 回响母岩专用展示 BE（多一行监守者警告与生长状态） */
        ECHO_DISPLAY,
        /** 可燃冰母岩专用展示 BE（多一行含水提示） */
        ICE_DISPLAY,
        /** 福鲁伊克斯母岩专用 BE（持 ME 网格节点，负责扣 AE） */
        AE2_GRID
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
     * 一条随机刻副作用规则：每随机刻有 1/chance 概率触发一次尝试，
     * 在母岩周围半径 radius 的立方体内随机取一格，命中第一条替换规则即写入。
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
     * 一条替换规则：匹配输入方块（方块本身或标签）→ 写入输出方块。
     * {@code output} 为 null 表示写入本母岩自身（粗矿块/平滑石英 → 母岩的再生传播）。
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
