package com.minecart.yunxian.client;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.mechanical.SmartDrillRenderer;
import com.minecart.yunxian.resonance.ResonanceRulesCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SMART_DRILL.get(), SmartDrillRenderer::new);
    }

    /** 退出世界/换服时清掉共振网络的快照，免得把上一个存档的过滤结果带过去。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ResonanceRulesCache.clear();
    }
}
