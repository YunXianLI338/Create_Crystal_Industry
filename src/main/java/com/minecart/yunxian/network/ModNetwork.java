package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Yunxian.MODID)
public final class ModNetwork {

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                EchoRevealPayload.TYPE,
                EchoRevealPayload.STREAM_CODEC,
                EchoRevealPayload::handle
        );
        registrar.playToClient(                                 // 远距离时服务端替客户端解析共振网络
                ResonanceRulesPayload.TYPE,
                ResonanceRulesPayload.STREAM_CODEC,
                ResonanceRulesPayload::handle
        );
        registrar.playToServer(                                 // ← 新增
                SetFilterPayload.TYPE,
                SetFilterPayload.STREAM_CODEC,
                SetFilterPayload::handle
        );
        registrar.playToServer(
                SetNetworkNamePayload.TYPE,
                SetNetworkNamePayload.STREAM_CODEC,
                SetNetworkNamePayload::handle
        );
        registrar.playToServer(
                SetFilterModePayload.TYPE,
                SetFilterModePayload.STREAM_CODEC,
                SetFilterModePayload::handle
        );
        registrar.playToServer(
                NightVisionTogglePayload.TYPE,
                NightVisionTogglePayload.STREAM_CODEC,
                NightVisionTogglePayload::handle
        );
        registrar.playToServer(CameraModePayload.TYPE, CameraModePayload.STREAM_CODEC, CameraModePayload::handle);
    }
}