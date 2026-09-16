package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Block> ECHO_REVEALS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_reveals"));

    /** 回响母岩会转化为幽匿的方块（见 BuddingFamilies 的 echo 家族） */
    public static final TagKey<Block> ECHO_CONVERTIBLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_convertible"));

    /**
     * 通用母岩标签 c:budding_blocks（方块 / 物品两份）。
     * 智能钻头的精准采集与 AE2 晶体催生器都认这个标签，新增母岩必须登记。
     */
    public static final TagKey<Block> BUDDING_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "budding_blocks"));

    public static final TagKey<Item> BUDDING_BLOCKS_ITEM = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "budding_blocks"));

    // 新增：标记"免疫鼓风机/喷头风力"的盔甲
    public static final TagKey<Item> FAN_IMMUNE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "fan_immune"));

    private ModTags() {
    }
}