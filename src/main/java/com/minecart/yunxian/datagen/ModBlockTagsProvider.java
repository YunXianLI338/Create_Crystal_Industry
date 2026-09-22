package com.minecart.yunxian.datagen;

import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.battery.CrystalTier;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * 生成方块标签：通用母岩标签 c:budding_blocks、挖掘工具标签、挖掘等级标签，
 * 以及水晶电池的晶体档位标签（内容由 {@link CrystalTier} 派生）。
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
        // 挖掘工具：全部家族方块 + 可燃冰装饰方块 + 四种机器 + 水晶电池
        IntrinsicTagAppender<Block> pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        pickaxe.add(ModBlocks.FLAMMABLE_ICE_BLOCK.get(), ModBlocks.ACCELERATOR.get(),
                ModBlocks.SMART_DRILL.get(), ModBlocks.MECHANICAL_ACCELERATOR.get(),
                ModBlocks.MECHANICAL_CLEANER.get(), ModBlocks.CRYSTAL_BATTERY.get());
        // 挖掘等级：只有指定了等级的家族才登记（荧石与可燃冰不设等级）
        IntrinsicTagAppender<Block> needsStone = tag(BlockTags.NEEDS_STONE_TOOL);
        // 水晶电池底子取的是铜块属性（requiresCorrectToolForDrops），
        // 等级必须跟着一起登记，否则任何工具都算不上"正确"，挖掉一格都不掉东西
        needsStone.add(ModBlocks.CRYSTAL_BATTERY.get());
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

        addBatteryCrystalTags(provider);
    }

    /**
     * 水晶电池的晶体档位标签：内容全部来自 {@link CrystalTier#defaultBlocks()}。
     * <p>
     * 物品侧不在这里生成，而是由 {@link ModItemTagsProvider} 用 {@code ItemTagsProvider#copy}
     * 把这些标签原样复制过去——标签不能跨方块/物品互相引用，只能这样避免两份列表各写一遍。
     * {@code copy} 要求被复制的方块标签由本次生成产出，所以这些标签必须是数据生成的，
     * 不能留在 resources 下手写（同路径会与生成结果冲突，构建直接失败）。
     */
    private void addBatteryCrystalTags(HolderLookup.Provider provider) {
        HolderGetter<Block> blocks = provider.lookupOrThrow(Registries.BLOCK);

        for (CrystalTier tier : CrystalTier.values()) {
            IntrinsicTagAppender<Block> tierTag = tag(tier.tag());
            for (ResourceLocation id : tier.defaultBlocks()) {
                // 用 add(Block) 而不是 addOptional(id)：默认成员全是硬依赖（原版/机械动力/本模组），
                // 写错就该当场抛异常，而不是生成一条 required:false 把错误静默吞掉。
                tierTag.add(blocks.get(ResourceKey.create(Registries.BLOCK, id))
                        .orElseThrow(() -> new IllegalStateException(
                                "晶体容量档 " + tier + " 的默认方块不存在：" + id))
                        .value());
            }
        }

        // 汇总标签：引用四个档位标签，而不是把方块再列一遍，所以永远不会和档位表脱节
        IntrinsicTagAppender<Block> crystal = tag(ModTags.BATTERY_CRYSTAL);
        for (CrystalTier tier : CrystalTier.values()) {
            crystal.addTag(tier.tag());
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
