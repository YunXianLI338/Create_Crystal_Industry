package com.minecart.yunxian.block.budding;

import java.util.function.Supplier;

import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.GrowthDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 脚本（KubeJS）注册的母岩方块：生长参数由脚本给的 {@link GrowthDefinition} 决定
 * （不像自带家族那样来自 {@code BuddingFamilies} 那张表）。
 * <p>
 * 之所以要有这个类，而不是直接用 KubeJS 的通用方块：护目镜信息挂在**方块实体**上
 * （{@link BuddingGrowthBlockEntity} 实现 Create 的 {@code IHaveGoggleInformation}），
 * KubeJS 的方块没有实体，戴上护目镜看它就是一片空白。
 * <p>
 * 定义用 {@link Supplier} 惰性取：脚本执行时四个阶段方块还没注册完，只有等真正要用的时候
 * （首次随机刻、或护目镜读参数）才解析。
 */
public class ScriptedBuddingBlock extends BuddingAmethystBlock implements EntityBlock {

    private final Supplier<GrowthDefinition> definition;

    public ScriptedBuddingBlock(Supplier<GrowthDefinition> definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    /** 当前生效的生长参数（护目镜会读它显示概率/光照/含水） */
    public GrowthDefinition growthDefinition() {
        return definition.get();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BuddingGrowthEngine.tryGrow(level, pos, random, definition.get(), null);
    }

    // 纯展示用 BE：不 tick、不存数据，仅支撑护目镜信息（与自带家族共用同一个）
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuddingGrowthBlockEntity(pos, state);
    }
}
