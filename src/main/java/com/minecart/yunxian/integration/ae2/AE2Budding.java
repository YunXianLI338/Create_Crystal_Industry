package com.minecart.yunxian.integration.ae2;

import com.minecart.yunxian.blockentity.budding.FluixBuddingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
}
