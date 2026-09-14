package com.minecart.yunxian.client;

import com.minecart.yunxian.registry.ModMenus;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.echo.EchoSpyglassFilterScreen;
import com.minecart.yunxian.client.mechanical.MechanicalCleanerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class ModMenuScreens {

    private ModMenuScreens() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ECHO_FILTER_MENU.get(), EchoSpyglassFilterScreen::new);
        event.register(ModMenus.MECHANICAL_CLEANER.get(), MechanicalCleanerScreen::new);
    }
}