package com.minecart.yunxian.datagen;

import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * 生成方块标签：通用母岩标签 c:budding_blocks、挖掘工具标签，以及挖掘等级标签。
 * 新增母岩家族时这里不需要改动，全部由 {@link BuddingFamilies#ALL} 派生。
 */
public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Yunxian.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // c:budding_blocks —— 智能钻头精准采集与 AE2 晶体催生器都认它
        IntrinsicTagAppender<Block> budding = tag(ModTags.BUDDING_BLOCKS);
        // 挖掘工具：全部家族方块 + 可燃冰装饰方块 + 四种机器
        IntrinsicTagAppender<Block> pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        pickaxe.add(ModBlocks.FLAMMABLE_ICE_BLOCK.get(), ModBlocks.ACCELERATOR.get(),
                ModBlocks.SMART_DRILL.get(), ModBlocks.MECHANICAL_ACCELERATOR.get(),
                ModBlocks.MECHANICAL_CLEANER.get());
        // 挖掘等级：只有指定了等级的家族才登记（荧石与可燃冰不设等级）
        IntrinsicTagAppender<Block> needsStone = tag(BlockTags.NEEDS_STONE_TOOL);
        IntrinsicTagAppender<Block> needsIron = tag(BlockTags.NEEDS_IRON_TOOL);
        IntrinsicTagAppender<Block> needsDiamond = tag(BlockTags.NEEDS_DIAMOND_TOOL);

        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;
            }
            // AE2 缺席时这些方块根本不存在，标签里必须写成可选，否则加载时报缺失
            boolean optional = family.spec().ae2Gated();

            IntrinsicTagAppender<Block> tier = switch (family.spec().toolTier()) {
                case STONE -> needsStone;
                case IRON -> needsIron;
                case DIAMOND -> needsDiamond;
                case NONE -> null;
            };

            // c:budding_blocks 只登记母岩本体：智能钻头用它判断"精准模式直接掉方块自身"，
            // AE2 晶体催生器用它决定哪些方块可以被加速——芽与晶簇不属于这个语义。
            add(budding, family.budding(), optional);

            for (DeferredBlock<Block> block : family.blocks()) {
                add(pickaxe, block, optional);
                if (tier != null) {
                    add(tier, block, optional);
                }
            }
        }
    }

    private static void add(IntrinsicTagAppender<Block> tag, DeferredBlock<Block> block, boolean optional) {
        if (optional) {
            tag.addOptional(block.getId());
        } else {
            tag.add(block.get());
        }
    }
}
