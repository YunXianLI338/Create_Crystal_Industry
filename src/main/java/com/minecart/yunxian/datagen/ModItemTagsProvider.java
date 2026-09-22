package com.minecart.yunxian.datagen;

import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.battery.CrystalTier;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.data.tags.TagsProvider.TagLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * 生成物品标签。
 * <p>
 * 母岩那一份（c:budding_blocks）逐个家族登记，而不是用 {@code copy(...)}：它带可选条目
 * （AE2 缺席时的福鲁伊克斯），经过 copy 之后的行为不易核对，逐条登记与手写原件完全一致。
 * <p>
 * 水晶电池的晶体档位标签则<b>正是</b>用 {@code copy(...)}：那几个列表里没有可选条目，
 * 而标签不能跨方块/物品互相引用，复制是唯一能避免"方块一份、物品一份"的做法。
 * {@code copy} 要求被复制的方块标签由本次生成产出（否则会抛 Missing block tag），
 * 所以那些标签在 {@link ModBlockTagsProvider} 里生成，不在 resources 下手写。
 */
public class ModItemTagsProvider extends net.minecraft.data.tags.ItemTagsProvider {

    public ModItemTagsProvider(PackOutput output, CompletableFuture<TagLookup<Block>> blockTags,
                               CompletableFuture<HolderLookup.Provider> lookupProvider,
                               @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Yunxian.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        IntrinsicTagAppender<Item> budding = tag(ModTags.BUDDING_BLOCKS_ITEM);
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;
            }
            DeferredBlock<Block> block = family.budding();
            if (family.spec().ae2Gated()) {
                budding.addOptional(block.getId());
            } else {
                budding.add(block.get().asItem());
            }
        }

        // 水晶电池的晶体：物品标签直接复制方块标签的内容，两个列表不会各写一份、也不会脱节
        for (CrystalTier tier : CrystalTier.values()) {
            copy(tier.tag(), tier.itemTag());
        }
        copy(ModTags.BATTERY_CRYSTAL, ModTags.BATTERY_CRYSTAL_ITEM);
    }
}
