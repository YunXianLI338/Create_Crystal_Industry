package com.minecart.yunxian.client;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.mechanical.SmartDrillRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SMART_DRILL.get(), SmartDrillRenderer::new);
    }
}
