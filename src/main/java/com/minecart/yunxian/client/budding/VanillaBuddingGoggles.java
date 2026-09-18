package com.minecart.yunxian.client.budding;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 原版紫水晶母岩的护目镜信息。
 * <p>
 * 本模组的母岩都实现了 {@code EntityBlock}，区块会为它们建出展示用方块实体，
 * 护目镜因此有东西可读；原版 {@code minecraft:budding_amethyst} 不是 {@code EntityBlock}，
 * 区块永远不建方块实体，它就一行信息都显示不出来。
 * <p>
 * 这里在客户端查询方块实体时按需合成一个实例返回：只在物理客户端生效，
 * <b>不写进区块、不落盘、不改动原版方块的任何行为</b>——所以天然晶洞里的母岩、
 * 别人开的服务器上的母岩都能立刻显示，存档里也不会多出任何数据。
 * <p>
 * 合成出来的实例只服务于 Create 的 {@code GoggleOverlayRenderer}（拿到实例后
 * 立刻读一次 tooltip），因此刻意不做缓存：缓存下来的实例会与方块状态脱节——
 * 母岩被挖走后它会留在区块里，让护目镜对着空气继续报生长速度。
 */
public final class VanillaBuddingGoggles {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 合法方块表失配时的提示只打一次，别在每帧都走到的路径上刷屏 */
    private static boolean warnedMissingValidBlock;

    private VanillaBuddingGoggles() {
    }

    /**
     * 该位置若是原版紫水晶母岩，返回一个仅供护目镜读取的展示用方块实体。
     *
     * @return 非原版母岩、处于服务端、或合法方块表失配时返回 {@code null}
     */
    @Nullable
    public static BlockEntity displayBlockEntity(LevelChunk chunk, BlockPos pos) {
        Level level = chunk.getLevel();
        if (!level.isClientSide) {
            return null;
        }

        BlockState state = chunk.getBlockState(pos);
        if (!state.is(Blocks.BUDDING_AMETHYST)) {
            return null;
        }

        BlockEntityType<?> type = ModBlockEntities.BUDDING_GROWTH.get();
        if (!type.isValid(state)) {
            // 方块实体的构造器会校验合法方块表，这里先拦住，免得热路径抛异常。
            // 走到这里说明 ModBlockEntities.goggleInfoBlocks() 没把原版母岩登记进去。
            if (!warnedMissingValidBlock) {
                warnedMissingValidBlock = true;
                LOGGER.warn("原版紫水晶母岩不在共享护目镜方块实体的合法方块表里，护目镜信息不会显示；"
                        + "请检查 ModBlockEntities.goggleInfoBlocks()");
            }
            return null;
        }

        BlockEntity blockEntity = type.create(pos, state);
        if (blockEntity != null) {
            blockEntity.setLevel(level);
        }
        return blockEntity;
    }
}
