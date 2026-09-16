package com.minecart.yunxian;

import com.minecart.yunxian.attachment.EchoAttachments;
import com.minecart.yunxian.behaviour.SmartDrillMovementBehaviour;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.client.ModRenderers;
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
        modEventBus.addListener(Yunxian::commonSetup);
        ModRenderers.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModArmInteractionPointTypes.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
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