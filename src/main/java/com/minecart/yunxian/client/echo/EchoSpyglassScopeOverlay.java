package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class EchoSpyglassScopeOverlay {

    private static final ResourceLocation SCOPE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/misc/echo_spyglass_scope.png");

    /** 镜片从 1/2 长到全尺寸的用时(tick),20 = 1 秒 */
    private static final int GROW_TICKS = 4;

    private EchoSpyglassScopeOverlay() {
    }

    /** 客户端初始化时调用一次(放在 ModRenderers.register 里) */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoSpyglassScopeOverlay::onRenderGui);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!CameraSync.isFirstPerson()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (!player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof EchoSpyglassItem)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // ===== 平滑动画进度 =====
        // 关键:getTicksUsingItem() 是整数,只按 20Hz 跳变;
        // 加上帧间插值 partialTicks 后,progress 在 60/120 帧下每帧连续变化,消除跳帧。
        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float ticksUsing = player.getTicksUsingItem() + partialTicks;
        float progress = Mth.clamp(ticksUsing / (float) GROW_TICKS, 0.0F, 1.0F);

        // ease-out 缓动:先快后慢(起始速度快,接近全尺寸时减速)
        float remaining = 1.0F - progress;
        float ease = 1.0F - remaining * remaining * remaining;

        // 从 1/2 放大到 1.0
        float START = 0.3F;                          // 起始大小:0.3 = 屏幕短边的 30%
        float scale = START + (1.0F - START) * ease; // 从 START 平滑放大到 1.0

        int size = Math.max(0, Mth.floor(Math.min(width, height) * scale));
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        int right = x + size;
        int bottom = y + size;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (size > 0) {
            graphics.blit(SCOPE_TEXTURE, x, y, 0.0F, 0.0F, size, size, size, size);
        }

        // 镜片外涂黑:黑框跟着一起从小变大
        graphics.fill(RenderType.guiOverlay(), 0, bottom, width, height, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), 0, 0, width, y, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), 0, 0, x, height, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), right, 0, width, height, 0xFF000000);
    }
}