package com.minecart.yunxian.block.budding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class RedstoneClusterBlock extends YunxianClusterBlock {
    private final int signalStrength;

    public RedstoneClusterBlock(float height, float aabbOffset, BlockBehaviour.Properties properties,
                                String stageKey, int signalStrength) {
        super(height, aabbOffset, properties, stageKey);
        this.signalStrength = signalStrength;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return this.signalStrength > 0;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.signalStrength;
    }
}