package com.minecart.yunxian.battery;

import com.minecart.yunxian.registry.ModTags;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 水晶电池的容量档位：某块晶体方块塞进电池后，每一格电池能存多少 FE。
 * <p>
 * <b>哪些方块算晶体、算哪一档，完全由方块标签决定</b>（{@link ModTags#BATTERY_CRYSTAL_LOW} 等四个）：
 * 一个方块出现在哪个标签里就按哪一档算，四个标签都没进就不是晶体方块，右键电池不生效。
 * 标签是数据包内容，模组只负责给出默认值（见 resources 下 {@code tags/block/battery_crystal/}），
 * 整合包可以用数据包或 KubeJS 直接覆写，不必改配置。
 * <p>
 * 每档具体的电容量写死在枚举里——平衡性以代码为准，改数值就改这里。
 * 枚举声明顺序 = 判定顺序：一个方块同时出现在多个标签里时取更靠前的那档，并记一条警告。
 */
public enum CrystalTier {
    /** 低容量档 */
    LOW(ModTags.BATTERY_CRYSTAL_LOW, 20_000),
    /** 中容量档：机械动力自带的玫瑰石英块默认在这里 */
    MEDIUM(ModTags.BATTERY_CRYSTAL_MEDIUM, 80_000),
    /** 高容量档：本模组的可燃冰块默认在这里 */
    HIGH(ModTags.BATTERY_CRYSTAL_HIGH, 400_000),
    /** 极高容量档：留给后续更强的水晶 */
    EXTREME(ModTags.BATTERY_CRYSTAL_EXTREME, 2_000_000);

    private final TagKey<Block> tag;
    private final int capacityPerBlock;

    CrystalTier(TagKey<Block> tag, int capacityPerBlock) {
        this.tag = tag;
        this.capacityPerBlock = capacityPerBlock;
    }

    /** 该档对应的方块标签 */
    public TagKey<Block> tag() {
        return tag;
    }

    /** 该档晶体每格电池的电容量（FE） */
    public int capacityPerBlock() {
        return capacityPerBlock;
    }
}
