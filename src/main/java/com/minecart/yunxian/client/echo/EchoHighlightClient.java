package com.minecart.yunxian.client.echo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class EchoHighlightClient {

    private static final List<BlockPos> POSITIONS = new ArrayList<>();
    private static ResourceKey<Level> dimension = Level.OVERWORLD;

    /** 每次 replace 都 +1，渲染器用它判断是否需要重新做轮廓合并 */
    private static int version;

    private EchoHighlightClient() {
    }

    public static void replace(ResourceKey<Level> dim, List<BlockPos> positions) {
        dimension = dim;
        POSITIONS.clear();
        POSITIONS.addAll(positions);
        version++;
    }

    /** 维度对不上返回空列表；对得上直接返回内部列表（只读使用） */
    public static List<BlockPos> positionsFor(ResourceKey<Level> currentDimension) {
        return currentDimension.equals(dimension) ? POSITIONS : List.of();
    }

    public static int version() {
        return version;
    }
}