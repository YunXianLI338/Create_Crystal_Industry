package com.minecart.yunxian.battery;

import java.util.List;

import com.minecart.yunxian.registry.ModTags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 水晶电池的容量档位：某块晶体方块塞进电池后，每一格电池能存多少 FE。
 * <p>
 * <b>哪些方块算晶体、算哪一档，完全由标签决定</b>（{@link ModTags#BATTERY_CRYSTAL_LOW} 等四个）：
 * 一个方块出现在哪个标签里就按哪一档算，四个标签都没进就不是晶体方块，右键电池不生效。
 * 标签是数据包内容，模组只负责给出默认值——默认值写在本枚举的 {@link #defaultBlocks()}，
 * 由数据生成产出成对的方块标签与物品标签（见 {@code datagen/ModBlockTagsProvider} 与
 * {@code ModItemTagsProvider}）。整合包可以照常用数据包或 KubeJS 覆写，不必改配置。
 * <p>
 * 每档具体的电容量写死在枚举里——平衡性以代码为准，改数值就改这里。
 * 枚举声明顺序 = 判定顺序：一个方块同时出现在多个标签里时取更靠前的那档，并记一条警告。
 */
public enum CrystalTier {
    /** 低容量档 */
    LOW(ModTags.BATTERY_CRYSTAL_LOW, ModTags.BATTERY_CRYSTAL_LOW_ITEM, 20_000),
    /** 中容量档：机械动力自带的玫瑰石英块与原版紫水晶块默认在这里 */
    MEDIUM(ModTags.BATTERY_CRYSTAL_MEDIUM, ModTags.BATTERY_CRYSTAL_MEDIUM_ITEM, 80_000,
            "minecraft:amethyst_block", "create:rose_quartz_block"),
    /** 高容量档：本模组的可燃冰块默认在这里 */
    HIGH(ModTags.BATTERY_CRYSTAL_HIGH, ModTags.BATTERY_CRYSTAL_HIGH_ITEM, 400_000,
            "create_crystal_industry:flammable_ice_block"),
    /** 极高容量档：留给后续更强的水晶 */
    EXTREME(ModTags.BATTERY_CRYSTAL_EXTREME, ModTags.BATTERY_CRYSTAL_EXTREME_ITEM, 2_000_000);

    private final TagKey<Block> tag;
    private final TagKey<Item> itemTag;
    private final int capacityPerBlock;
    private final List<ResourceLocation> defaultBlocks;

    CrystalTier(TagKey<Block> tag, TagKey<Item> itemTag, int capacityPerBlock, String... defaultBlocks) {
        this.tag = tag;
        this.itemTag = itemTag;
        this.capacityPerBlock = capacityPerBlock;
        this.defaultBlocks = List.of(defaultBlocks).stream().map(ResourceLocation::parse).toList();
    }

    /** 该档对应的方块标签（判定用的就是这个） */
    public TagKey<Block> tag() {
        return tag;
    }

    /** 该档方块标签对应的物品标签，内容由数据生成从方块标签复制过去 */
    public TagKey<Item> itemTag() {
        return itemTag;
    }

    /** 该档晶体每格电池的电容量（FE） */
    public int capacityPerBlock() {
        return capacityPerBlock;
    }

    /**
     * 该档默认收录的方块 id，数据生成据此产出标签。
     * <p>
     * 这里写的就是模组发给玩家的默认值；整合包照常用数据包覆写生成出来的标签即可。
     * id 写错会在数据生成阶段直接抛异常（不是静默丢弃），开发期立刻暴露。
     */
    public List<ResourceLocation> defaultBlocks() {
        return defaultBlocks;
    }
}
