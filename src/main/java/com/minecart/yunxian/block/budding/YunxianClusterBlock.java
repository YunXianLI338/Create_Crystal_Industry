package com.minecart.yunxian.block.budding;

import net.minecraft.world.level.block.AmethystClusterBlock;

public class YunxianClusterBlock extends AmethystClusterBlock {
    @SuppressWarnings("unused")
    private final String stageKey;

    /**
     * 参数即原版 {@code AmethystClusterBlock} 的那两个：碰撞箱由它们算出，
     * 取值见 {@code BuddingFamilies.Stage}（照抄原版，别自己改比例）。
     */
    public YunxianClusterBlock(float height, float aabbOffset, Properties properties, String stageKey) {
        super(height, aabbOffset, properties);
        this.stageKey = stageKey;
    }
}
