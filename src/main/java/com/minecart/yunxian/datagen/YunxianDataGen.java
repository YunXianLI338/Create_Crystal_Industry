package com.minecart.yunxian.datagen;

import java.util.List;
import java.util.Set;

import com.minecart.yunxian.registry.ModBlocks;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 数据生成入口（{@code ./gradlew runData}）。
 * <p>
 * 只生成可机械推导的部分：blockstate、方块模型、物品模型、母岩与芽的掉落表、标签。
 * 世界生成 JSON、语言文件、材质与 .mcmeta、晶簇与福鲁伊克斯的掉落表仍为手写。
 * <p>
 * 在 {@code Yunxian} 的构造器里挂到 mod 事件总线上（本模组这版 NeoForge 没有可用的
 * {@code @EventBusSubscriber} 注解，显式注册更稳妥）。
 */
public final class YunxianDataGen {

    private YunxianDataGen() {
    }

    public static void gatherData(GatherDataEvent event) {
        // 无 AE2 时绝不能跑：福鲁伊克斯家族的方块句柄为 null，生成器拿不到它，
        // 而 HashCache 会把本次没有重新生成的 fluix 资产当作过期文件从输出目录删掉。
        if (!ModBlocks.AE2_LOADED) {
            throw new IllegalStateException("数据生成需要 AE2：请确认 run/mods 里有 AE2 后再跑 runData");
        }

        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFiles = event.getExistingFileHelper();

        if (event.includeClient()) {
            generator.addProvider(true, new ModBlockStateProvider(output, existingFiles));
        }

        if (event.includeServer()) {
            generator.addProvider(true, new LootTableProvider(output, Set.of(),
                    List.of(new LootTableProvider.SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK)),
                    event.getLookupProvider()));

            ModBlockTagsProvider blockTags =
                    new ModBlockTagsProvider(output, event.getLookupProvider(), existingFiles);
            generator.addProvider(true, blockTags);
            generator.addProvider(true, new ModItemTagsProvider(
                    output, blockTags.contentsGetter(), event.getLookupProvider(), existingFiles));
        }
    }
}
