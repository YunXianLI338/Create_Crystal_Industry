package com.minecart.yunxian.client.nightvision;

import com.minecart.yunxian.attachment.EchoAttachments;
import com.minecart.yunxian.util.NightVisionWearHelper;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.ModKeybinds;
import com.minecart.yunxian.network.NightVisionTogglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class NightVisionToggle {
    private NightVisionToggle() {}

    /** 任意玩家（本地已知实体）的夜视状态 —— 读取同步后的 attachment */
    public static boolean isEnabled(Player player) {
        return player != null && player.getData(EchoAttachments.NIGHT_VISION);
    }

    /** 本地玩家的夜视状态（兼容旧的无参调用，如遮罩/着色器） */
    public static boolean isEnabled() {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getData(EchoAttachments.NIGHT_VISION);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        while (ModKeybinds.TOGGLE_NIGHT_VISION.consumeClick()) {
            boolean wearing = NightVisionWearHelper.isWearingGoggles(player);
            if (!wearing) {
                player.displayClientMessage(
                        Component.translatable("message." + Yunxian.MODID + ".goggles_not_worn"), true);
                continue;
            }
            // 真正的翻转在服务端，改完自动同步回来；这里只发请求 + 乐观提示
            PacketDistributor.sendToServer(new NightVisionTogglePayload());
            boolean newState = !isEnabled(player);
            player.displayClientMessage(
                    Component.translatable(newState
                            ? "message." + Yunxian.MODID + ".night_vision.on"
                            : "message." + Yunxian.MODID + ".night_vision.off"),
                    true);
        }

        boolean enabled = isEnabled(player);
        boolean wearing = NightVisionWearHelper.isWearingGoggles(player);
        if (wearing && enabled) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 210, 0, false, false, true));
            if (player.hasEffect(MobEffects.DARKNESS)) {
                player.removeEffect(MobEffects.DARKNESS);
            }
        } else {
            removeOwnNightVision(player);
        }
    }

    private static void removeOwnNightVision(Player player) {
        var effect = player.getEffect(MobEffects.NIGHT_VISION);
        if (effect != null
                && effect.getDuration() <= 210
                && effect.getAmplifier() == 0
                && !effect.isAmbient()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}