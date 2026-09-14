package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

public final class EchoSpyglassUseRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registered;

    private EchoSpyglassUseRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(EchoSpyglassUseRenderer::onRenderHand);
        LOGGER.info("[EchoSpyglass] 使用渲染器已注册");
    }

    /** 第一人称使用中：隐藏手臂与物品（等价于原版 renderArmWithItem 里 isScoping() 的分支） */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null
                && player.isUsingItem()
                && player.getUseItem().getItem() instanceof EchoSpyglassItem) {
            event.setCanceled(true);
        }
    }
}