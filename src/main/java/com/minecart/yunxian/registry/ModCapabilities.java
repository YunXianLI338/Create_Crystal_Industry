package com.minecart.yunxian.registry;

import com.minecart.yunxian.integration.curios.CuriosIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    private ModCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ACCELERATOR.get(),
                (blockEntity, side) -> blockEntity.getEnergyCapability(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MECHANICAL_CLEANER.get(),
                (be, context) -> be.getInventory()
        );
        // 水晶电池：整座多方块结构共用一个 FE 接口，从任意一格接出去看到的都是同一池电
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CRYSTAL_BATTERY.get(),
                (blockEntity, side) -> blockEntity.getEnergyCapability(side)
        );

        // ★ 软依赖门控：只有 Curios 已加载才触碰 Curios 类
        if (ModList.get().isLoaded("curios")) {
            CuriosIntegration.registerCapabilities();
        }
    }
}