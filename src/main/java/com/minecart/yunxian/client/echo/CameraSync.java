package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.network.CameraModePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CameraSync {

    private static boolean lastFirstPerson = true;
    private static boolean initialSendDone = false;

    private CameraSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CameraSync::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            initialSendDone = false;
            return;
        }
        boolean now = isFirstPerson();
        // 进世界后先发一次；之后只在 F5 切换时发
        if (!initialSendDone || now != lastFirstPerson) {
            initialSendDone = true;
            lastFirstPerson = now;
            PacketDistributor.sendToServer(new CameraModePayload(now));
        }
    }

    /** 供所有覆盖层/渲染器做门控：非第一人称直接跳过绘制 */
    public static boolean isFirstPerson() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.options.getCameraType().isFirstPerson();
    }
}