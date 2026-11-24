package com.minecart.yunxian.datagen;

import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.battery.CrystalTier;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.data.tags.TagsProvider.TagLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * 生成物品标签。
 * <p>
 * 母岩 / 芽 / 晶簇那三份（c:budding_blocks、c:buds、c:clusters）逐个家族登记，而不是用
 * {@code copy(...)}：它们都带可选条目（AE2 缺席时的福鲁伊克斯），经过 copy 之后的行为不易核对，
 * 逐条登记与手写原件完全一致。
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
        // 物品侧的芽 / 晶簇标签：与方块侧逐条对应（标签不能跨方块/物品互相引用，
        // 所以这里和 ModBlockTagsProvider 用同一份家族表各登记一遍）
        IntrinsicTagAppender<Item> buds = tag(ModTags.BUDS_ITEM);
        IntrinsicTagAppender<Item> clusters = tag(ModTags.CLUSTERS_ITEM);
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;
            }
            boolean optional = family.spec().ae2Gated();

            add(budding, family.budding(), optional);
            add(buds, family.smallBud(), optional);
            add(buds, family.mediumBud(), optional);
            add(buds, family.largeBud(), optional);
            add(clusters, family.cluster(), optional);
        }

        // 水晶电池的晶体：物品标签直接复制方块标签的内容，两个列表不会各写一份、也不会脱节
        for (CrystalTier tier : CrystalTier.values()) {
            copy(tier.tag(), tier.itemTag());
        }
        copy(ModTags.BATTERY_CRYSTAL, ModTags.BATTERY_CRYSTAL_ITEM);
        // 可燃冰墙的 minecraft:walls：方块侧在 ModBlockTagsProvider 里生成，这里原样复制
        copy(BlockTags.WALLS, ItemTags.WALLS);

        // 通用桶标签 c:buckets：别的模组按「这是不是桶」筛物品时读它（见 ModTags#BUCKETS）
        tag(ModTags.BUCKETS).add(ModItems.FLAMMABLE_ICE_SLURRY_BUCKET.get());

        // 可燃冰沙瓶在置物台与传送带上竖直摆放：机械动力用 create:upright_on_belt 决定这件事
        // （BeltHelper#isItemUpright，命中即不躺平），玻璃瓶、药水都在这个标签里。
        tag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("create", "upright_on_belt")))
                .add(ModItems.FLAMMABLE_ICE_SLURRY_BOTTLE.get());

        // 智能钻头要能被放进动态结构控制器的过滤器槽位：那个槽位用 create:contraption_controlled
        // 筛物品（理由与判定链见 ModTags#CONTRAPTION_CONTROLLED），不在标签里就提示「过滤器无效」。
        tag(ModTags.CONTRAPTION_CONTROLLED).add(ModBlocks.SMART_DRILL.get().asItem());

        // 可燃冰的石材类型标签：整套装饰方块里除台阶外全部登记（判定规则与理由见
        // ModTags#FLAMMABLE_ICE_STONE_TYPE）。直接遍历唯一清单派生，以后加变体不用动这里。
        IntrinsicTagAppender<Item> stoneType = tag(ModTags.FLAMMABLE_ICE_STONE_TYPE);
        for (DeferredBlock<? extends Block> block : ModBlocks.FLAMMABLE_ICE_DECO) {
            if (!(block.get() instanceof SlabBlock)) {
                stoneType.add(block.get().asItem());
            }
        }
    }

    private static void add(IntrinsicTagAppender<Item> tag, DeferredBlock<Block> block, boolean optional) {
        if (optional) {
            tag.addOptional(block.getId());
        } else {
            tag.add(block.get().asItem());
        }
    }
}
