package com.minecart.yunxian.client.ponder;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.ponder.YunxianPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT, bus = Bus.MOD)
public class YunxianPonderClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new YunxianPonderPlugin());
    }

}