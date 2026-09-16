package com.minecart.yunxian.integration.ae2;

import com.minecart.yunxian.blockentity.budding.FluixBuddingBlockEntity;

import appeng.block.misc.GrowthAcceleratorBlock;
import appeng.core.AEConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 母岩侧的 AE2 桥接：福鲁伊克斯母岩的生长付费。
 * <p>
 * 这是母岩相关代码里<b>唯一</b>允许引用 {@code FluixBuddingBlockEntity}（及其父类
 * {@code appeng.*}）的类，且它的两个方法的签名只用到本模组与原版类型。
 * 之所以要这样隔离：{@code GenericBuddingBlock} 这类「常量加载」的类一旦在
 * 字段/方法描述符或 lambda 描述符里出现缺失的类，JVM 字节码校验就会抛
 * {@code NoClassDefFoundError}——即使那段代码永远不会执行。方法体里的
 * {@code invokestatic} 则是延迟解析的，只有真正执行时才会加载本类，因此
 * 无 AE2 时不会出问题（与 {@link AE2BlockEntities} 同一套做法）。
 */
public final class AE2Budding {

    private AE2Budding() {
    }

    /**
     * 尝试为一次生长/传播扣除 AE 能量。
     *
     * @return 母岩不是已激活的 ME 网格节点（无电/无频道）或电量不足时返回 false，表示放弃本次生长
     */
    public static boolean tryConsumeGrowthEnergy(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluixBuddingBlockEntity budding
                && budding.tryConsumeGrowthEnergy();
    }

    /**
     * 该位置是否为「运行中」的 AE2 晶体催生器（护目镜用）。
     * <p>
     * 客户端可安全调用：AE2 的 {@code AEBaseBlockEntity.markForUpdate()} 内部会用
     * {@code setBlockAndUpdate} 把 {@code powered} 写回方块状态（它自己的开/关模型就靠这个属性），
     * 所以客户端的方块状态与运行状态一致——与模组自己的电力催生器读 POWERED 是同一套办法。
     */
    public static boolean isActiveGrowthAccelerator(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof GrowthAcceleratorBlock
                && state.getValue(GrowthAcceleratorBlock.POWERED);
    }

    /**
     * AE2 晶体催生器的随机刻速率（次/秒／每个相邻方块）。
     * <p>
     * 它按自己的配置 {@code growthAccelerator}（两次施加之间的 tick 数，默认 10）被唤醒，
     * 每次唤醒对 6 个相邻的可加速方块各施加一次随机刻，故单格速率为 {@code 20 / speed}。
     * <p>
     * 注意：多人游戏里这里读的是**本地**配置，服务端把该值改过时会与实际速率不符——只影响显示。
     */
    public static double growthAcceleratorRandomTicksPerSecond() {
        int ticksBetween = Math.max(1, AEConfig.instance().getGrowthAcceleratorSpeed());
        return 20.0 / ticksBetween;
    }
}
