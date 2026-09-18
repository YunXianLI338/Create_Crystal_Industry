package com.minecart.yunxian.budding;

import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * 母岩生长引擎：把「长成什么、多久长一次、要不要无光/含水」这套判定与放置做成<b>无状态的公开入口</b>。
 * <p>
 * 三类调用方共用同一条代码路径，避免两套逻辑：
 * <ul>
 *   <li>本模组自带的母岩（{@code GenericBuddingBlock} 合成定义后调用）；</li>
 *   <li>附属模组：自己的方块在 {@code randomTick} 里调用 {@link #tryGrow}；</li>
 *   <li>KubeJS 脚本：注册自己的方块后在 {@code randomTick} 回调里调用，
 *       定义用 {@link GrowthDefinition#of(String, String, String, String, int)} 按方块 id 构造。</li>
 * </ul>
 * 引擎<b>不含</b>两样东西，它们由调用方的方块自己负责：随机刻副作用（转化/传播规则）与
 * 生长能量（AE2 那种付费），后者通过 {@link GrowthGate} 在放置前回调。
 * <p>
 * 判定顺序与随机数消耗顺序是本类的对外契约的一部分：改顺序会改变各母岩的生长速率，
 * 也会让附属模组的既有手感变化。改动前请对照 {@code GenericBuddingBlock} 的历史实现。
 */
public final class BuddingGrowthEngine {

    private static final Direction[] DIRECTIONS = Direction.values();

    private BuddingGrowthEngine() {
    }

    /** 放置前的付费钩子：返回 false 表示放弃这次生长（AE2 福鲁伊克斯母岩用它扣能量） */
    @FunctionalInterface
    public interface GrowthGate {
        boolean canGrow(ServerLevel level, BlockPos pos);
    }

    /**
     * 免费生长（没有付费钩子）的便利重载。
     * <p>
     * 参数类型是 {@link Level} 而不是 {@code ServerLevel}：KubeJS 脚本拿到的往往是 {@code Level}
     * （随机刻只会在服务端触发，这里替它判断类型；客户端直接返回 false）。
     */
    public static boolean tryGrow(Level level, BlockPos pos, RandomSource random, GrowthDefinition definition) {
        return level instanceof ServerLevel serverLevel && tryGrow(serverLevel, pos, random, definition, null);
    }

    /**
     * 按给定定义生长一级：返回是否真的放置了方块。
     *
     * @param gate 放置前的付费钩子，null = 免费
     */
    public static boolean tryGrow(ServerLevel level, BlockPos pos, RandomSource random,
                                  GrowthDefinition definition, @Nullable GrowthGate gate) {
        Pending pending = definition.requiresWater()
                ? submerged(level, pos, random, definition)
                : standard(level, pos, random, definition);
        if (pending == null) {
            return false;
        }
        if (gate != null && !gate.canGrow(level, pos)) {
            return false;
        }
        place(level, pos.relative(pending.side()), pending.block(), pending.side(), pending.waterlogged());
        return true;
    }

    /** 生长位是否满足光照要求（护目镜在客户端也用它判断状态） */
    public static boolean lightAllows(Level level, BlockPos neighborPos, GrowthDefinition definition) {
        return definition.lightAllows(level.getMaxLocalRawBrightness(neighborPos));
    }

    // ==================== 两条生长路径 ====================

    /**
     * 通用生长：母岩自身不在液体中、抽中概率、生长位满足条件时，随机选一面推进一级。
     * <p>
     * 注意随机数消耗顺序：液体检查在 {@code nextInt(chance)} 之前，调换会改变生长速率。
     */
    @Nullable
    private static Pending standard(ServerLevel level, BlockPos pos, RandomSource random, GrowthDefinition definition) {
        if (!level.getFluidState(pos).isEmpty() || random.nextInt(definition.chance()) != 0) {
            return null;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);

        if (!lightAllows(level, neighborPos, definition)) {
            return null;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        Block nextBlock = nextStage(neighborState, side, definition, false);
        if (nextBlock == null) {
            return null;
        }

        return new Pending(nextBlock, side, neighborState.getFluidState().getType() == Fluids.WATER);
    }

    /**
     * 水下生长（可燃冰式）：长新芽与芽体进阶都要求目标格含水；
     * 长新芽还额外要求那是水源方块（waterlogged 的芽/簇其流体状态即水，不受影响）。
     */
    @Nullable
    private static Pending submerged(ServerLevel level, BlockPos pos, RandomSource random, GrowthDefinition definition) {
        if (random.nextInt(definition.chance()) != 0) {
            return null;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getFluidState().getType() != Fluids.WATER) {
            return null;
        }

        if (!lightAllows(level, neighborPos, definition)) {
            return null;
        }

        Block nextBlock = nextStage(neighborState, side, definition, true);
        if (nextBlock == null) {
            return null;
        }

        return new Pending(nextBlock, side, true);
    }

    /**
     * 相邻格决定的下一个阶段；无需变化时返回 null。
     *
     * @param submerged 水下生长：空位必须同时是水源才能长新芽
     */
    @Nullable
    private static Block nextStage(BlockState neighborState, Direction side, GrowthDefinition definition, boolean submerged) {
        Block small = definition.smallBud();
        Block medium = definition.mediumBud();
        Block large = definition.largeBud();

        if (submerged ? canGrowNewBud(neighborState) : canGrowAt(neighborState)) {
            return small;
        }
        if (isStageAt(neighborState, small, side)) {
            return medium;
        }
        if (isStageAt(neighborState, medium, side)) {
            return large;
        }
        if (isStageAt(neighborState, large, side)) {
            return definition.cluster();
        }
        return null;
    }

    /**
     * 相邻方块是否正是本母岩的第 n 阶段：方块对得上；方块带 {@code FACING} 时还要求朝向一致
     * （没有该属性的方块——例如 KubeJS 里默认创建的普通方块——按"任意相邻的它都算"处理）。
     */
    private static boolean isStageAt(BlockState state, Block stage, Direction side) {
        if (!state.is(stage)) {
            return false;
        }
        return !state.hasProperty(AmethystClusterBlock.FACING)
                || state.getValue(AmethystClusterBlock.FACING) == side;
    }

    private static boolean canGrowAt(BlockState state) {
        return BuddingFamily.isFree(state);
    }

    /** 长新芽的条件：空位且为水源方块 */
    private static boolean canGrowNewBud(BlockState state) {
        return BuddingFamily.isFree(state)
                && state.getFluidState().getType() == Fluids.WATER
                && state.getFluidState().isSource();
    }

    /**
     * 放置下一阶段。阶段方块可以是任何方块（附属模组 / KubeJS 指定），所以每个属性都先确认存在：
     * 带 {@code FACING} 的写入朝向（看起来才是"朝母岩长"），带 {@code WATERLOGGED} 的写入含水状态，
     * 两个都没有的普通方块也能放——只是没有朝向。
     */
    private static void place(ServerLevel level, BlockPos pos, Block nextBlock, Direction side, boolean waterlogged) {
        BlockState state = nextBlock.defaultBlockState();
        if (state.hasProperty(AmethystClusterBlock.FACING)) {
            state = state.setValue(AmethystClusterBlock.FACING, side);
        }
        if (state.hasProperty(AmethystClusterBlock.WATERLOGGED)) {
            state = state.setValue(AmethystClusterBlock.WATERLOGGED, waterlogged);
        }
        level.setBlockAndUpdate(pos, state);
    }

    /** 已判定通过、等待放置的一步 */
    private record Pending(Block block, Direction side, boolean waterlogged) {
    }
}
