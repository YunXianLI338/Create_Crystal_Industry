package com.minecart.yunxian;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.attachment.EchoAttachments;
import com.minecart.yunxian.behaviour.SmartDrillMovementBehaviour;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.client.ModRenderers;
import com.minecart.yunxian.datagen.YunxianDataGen;
import com.minecart.yunxian.registry.*;
import com.minecart.yunxian.util.NightVisionWearHelper;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Yunxian.MODID)
public class Yunxian {
    public static final String MODID = "create_crystal_industry";

    public Yunxian(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, com.minecart.yunxian.config.ModConfig.Common.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, com.minecart.yunxian.config.ModConfig.Client.SPEC); // ← 新增

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        // 母岩家族由中央定义表注册：必须在这里触发一次类初始化，
        // 否则方块会晚于注册表事件才入表，启动后整批母岩缺失
        BuddingFamilies.bootstrap();
        EchoAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        // 脚本（KubeJS）注册的方块要进创造栏：KubeJS 的方块默认不进任何标签页，这里补上
        modEventBus.addListener(ModCreativeTabs::addScriptedEntries);
        // 脚本注册的芽/簇的掉落规则（精准采集掉本体、否则掉配置物品）：KubeJS 的掉落 API 表达不了，运行时接管
        NeoForge.EVENT_BUS.addListener(ScriptedBlockDrops::onBlockDrops);
        modEventBus.addListener(Yunxian::commonSetup);
        ModRenderers.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModArmInteractionPointTypes.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
        // 数据生成（./gradlew runData）：只在 data 运行里触发，正常游戏不受影响
        modEventBus.addListener(YunxianDataGen::gatherData);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 成就：母岩每长出一级都会回调一次，用来判定「被催生出来的」那些时刻
            BuddingGrowthEngine.setGrowthListener(YunxianAdvancements::onBuddingGrown);
            BlockStressValues.IMPACTS.register(ModBlocks.SMART_DRILL.get(), () -> 8.0);
            BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_ACCELERATOR.get(), () -> 32.0);
            BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_CLEANER.get(), () -> 4.0);
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.SMART_DRILL.get(),
                    new SmartDrillMovementBehaviour()
            );
            GogglesItem.addIsWearingPredicate(player ->
                    NightVisionWearHelper.isWearingGoggles(player));
        });
    }
}