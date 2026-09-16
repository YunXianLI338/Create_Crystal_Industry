package com.minecart.yunxian.worldgen;

import com.mojang.serialization.Codec;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.GlowstoneFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GlowstoneBuddingBlobFeature extends GlowstoneFeature {

    // 一个候选位置：晶簇要放的空气格 + 朝向（从支撑块向外）
    private record BudCandidate(BlockPos target, Direction facing) {
    }

    public GlowstoneBuddingBlobFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        boolean placed = super.place(context);

        if (placed
                && ModConfig.Common.enabled("glowstone")
                && context.random().nextFloat() < ModConfig.Common.GLOWSTONE_BUDDING_CHANCE.get()) {

            BlockPos origin = context.origin();
            BlockPos.MutableBlockPos lowest = null;
            int lowestY = Integer.MAX_VALUE;
            for (int x = -8; x <= 8; x++) {
                for (int y = -16; y <= 0; y++) {
                    for (int z = -8; z <= 8; z++) {
                        BlockPos p = origin.offset(x, y, z);
                        if (context.level().getBlockState(p).is(Blocks.GLOWSTONE) && p.getY() < lowestY) {
                            lowestY = p.getY();
                            lowest = p.mutable();
                        }
                    }
                }
            }

            if (lowest != null) {
                // 母岩本体。
                context.level().setBlock(lowest, BuddingFamilies.GLOWSTONE.budding().get().defaultBlockState(), 2);

                if (ModConfig.Common.GLOWSTONE_GENERATE_BUDS.get()) {
                    int buds = ModConfig.Common.GLOWSTONE_BUD_COUNT.get();
                    boolean onNearbyGlowstone = ModConfig.Common.GLOWSTONE_BUDS_ON_GLOWSTONE.get();

                    // 收集所有可放置晶簇的位置：
                    // - 母岩本身的水平邻面（总是允许）
                    // - 附近荧石的水平邻面（由开关控制）
                    // 要求：支撑块是荧石或母岩，且目标格是空气。
                    List<BudCandidate> candidates = new ArrayList<>();
                    for (int dx = -8; dx <= 8; dx++) {
                        for (int dy = 0; dy <= 16; dy++) {
                            for (int dz = -8; dz <= 8; dz++) {
                                BlockPos support = lowest.offset(dx, dy, dz);
                                BlockState supportState = context.level().getBlockState(support);
                                boolean isBudding = supportState.is(BuddingFamilies.GLOWSTONE.budding().get());
                                boolean isGlowstone = supportState.is(Blocks.GLOWSTONE);
                                if (isBudding || (onNearbyGlowstone && isGlowstone)) {
                                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                                        BlockPos target = support.relative(dir);
                                        if (context.level().getBlockState(target).isAir()) {
                                            candidates.add(new BudCandidate(target.immutable(), dir));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 打乱后依次放置，直到放够数量。
                    Collections.shuffle(candidates, new Random(context.random().nextLong()));
                    int placedBuds = 0;
                    for (BudCandidate cand : candidates) {
                        if (placedBuds >= buds) {
                            break;
                        }
                        // 同一空气格可能被两个相邻荧石同时算到，跳过已填充的。
                        if (!context.level().getBlockState(cand.target()).isAir()) {
                            continue;
                        }
                        BlockState budState = randomBudState(context.random());
                        budState = applyFacingIfPresent(budState, cand.facing());
                        context.level().setBlock(cand.target(), budState, 2);
                        placedBuds++;
                    }
                }
            }
        }
        return placed;
    }

    private static BlockState randomBudState(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> BuddingFamilies.GLOWSTONE.smallBud().get().defaultBlockState();
            case 1 -> BuddingFamilies.GLOWSTONE.mediumBud().get().defaultBlockState();
            case 2 -> BuddingFamilies.GLOWSTONE.largeBud().get().defaultBlockState();
            default -> BuddingFamilies.GLOWSTONE.cluster().get().defaultBlockState();
        };
    }

    // 防御式设置 facing：方块有该属性才设，没有就保留默认状态（避免之前的崩溃）。
    private static BlockState applyFacingIfPresent(BlockState state, Direction dir) {
        if (state.getBlock().getStateDefinition().getProperty("facing")
                instanceof EnumProperty<?> prop
                && prop.getValueClass() == Direction.class) {
            @SuppressWarnings("unchecked")
            EnumProperty<Direction> facing = (EnumProperty<Direction>) prop;
            return state.setValue(facing, dir);
        }
        return state;
    }
}