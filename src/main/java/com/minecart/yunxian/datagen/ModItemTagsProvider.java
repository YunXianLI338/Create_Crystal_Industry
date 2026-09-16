package com.minecart.yunxian.datagen;

import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
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
 * 生成物品标签 c:budding_blocks。
 * <p>
 * 与方块标签一样逐个家族登记，而不是用 {@code copy(...)}：可选条目（AE2 缺席时的福鲁伊克斯）
 * 经过 copy 之后的行为不易核对，逐条登记与手写原件完全一致。
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
    }
}
