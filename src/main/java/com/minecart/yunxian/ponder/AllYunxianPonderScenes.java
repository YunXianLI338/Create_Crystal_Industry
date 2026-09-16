package com.minecart.yunxian.ponder;

import java.util.ArrayList;
import java.util.List;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.ponder.scenes.CrystalScenes;
import com.minecart.yunxian.ponder.scenes.MechanicalCleanerScenes;
import com.minecart.yunxian.ponder.scenes.SmartDrillScenes;
import com.minecart.yunxian.registry.ModBlocks;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AllYunxianPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 把所有“方块 → ResourceLocation”的注册统一换成 Block 视角
        PonderSceneRegistrationHelper<Block> blocks = helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);

        // 1) 母岩通用分镜：原版紫水晶 + 本模组全部母岩共用同一份蓝图/脚本
        //    名单由中央定义表派生；AE2 缺席时福鲁伊克斯那条 isRegistered() 为 false，自然跳过
        List<Block> buddingBlocks = new ArrayList<>();
        buddingBlocks.add(Blocks.BUDDING_AMETHYST);
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (family.isRegistered()) {
                buddingBlocks.add(family.budding().get());
            }
        }
        blocks.forComponents(buddingBlocks)
                .addStoryBoard("budding/accelerated_growth", CrystalScenes::buddingGrowth, AllYunxianPonderTags.BUDDING);

        // 2) 电力催生器
        blocks.forComponents(ModBlocks.ACCELERATOR.get())
                .addStoryBoard("accelerator/electric", CrystalScenes::electricAccelerator, AllYunxianPonderTags.ACCELERATORS);

        // 3) 动力催生器
        blocks.forComponents(ModBlocks.MECHANICAL_ACCELERATOR.get())
                .addStoryBoard("accelerator/mechanical", CrystalScenes::mechanicalAccelerator, AllYunxianPonderTags.ACCELERATORS);

        // 4) 智能钻头
        blocks.forComponents(ModBlocks.SMART_DRILL.get())
                .addStoryBoard("smart_drill/smart_drill", SmartDrillScenes::smartDrill, AllYunxianPonderTags.MACHINES);

        // 5) 动力吸尘器
        blocks.forComponents(ModBlocks.MECHANICAL_CLEANER.get())
                .addStoryBoard("mechanical_cleaner/mechanical_cleaner", MechanicalCleanerScenes::mechanicalCleaner,
                        AllYunxianPonderTags.MACHINES);
    }

}