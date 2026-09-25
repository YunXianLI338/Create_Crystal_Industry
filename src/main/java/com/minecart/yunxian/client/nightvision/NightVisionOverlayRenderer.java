package com.minecart.yunxian.client.nightvision;

import com.minecart.yunxian.util.NightVisionWearHelper;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class NightVisionOverlayRenderer {

    // 完整路径：textures/misc/ 前缀 + .png 后缀，缺一不可
    private static final ResourceLocation ECHO_OUTLINE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/misc/echo__outline.png");

    /**
     * 全屏四边形用的 z，和回响望远镜镜筒取同一个值（原版 renderSpyglassOverlay 也用 -90）。
     * 别用默认的 0：HUD 元素默认都在 z = 0，比 -90 <b>更近</b>，会把镜筒挡掉；
     * 取同一个 z 之后，"谁后画谁在上"就成了确定的规则——我们在镜筒那一层的 Pre 画，镜筒在 Post 画。
     */
    private static final float Z = -90.0F;

    private NightVisionOverlayRenderer() {}

    /**
     * 挂在原版画望远镜镜筒的那一层（{@code camera_overlays}）的 <b>Pre</b>：位置固定在
     * 回响望远镜镜筒的<b>之前</b>，所以镜筒会盖在这层夜视贴图之上。
     * <p>
     * 配合下面的 {@link #Z}：两者处在同一个 z 上，谁后画谁在上，于是先后关系是确定的。
     * 原先挂在 {@code RenderGuiEvent.Pre}（HUD 图层之前）并且用默认的 z = 0，比镜筒的
     * z = -90 <b>更近</b>、又写了深度，反而把镜筒挡掉了——玩家看到的就是夜视贴图压在望远镜之上。
     */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!NightVisionToggle.isEnabled()) return;
        // ★ 修复：改成公共 helper，同时覆盖头盔槽 + 首饰栏
        if (!NightVisionWearHelper.isWearingGoggles(mc.player)) return;

        int screenWidth = event.getGuiGraphics().guiWidth();
        int screenHeight = event.getGuiGraphics().guiHeight();

        // 1. 绑定贴图 + 顶点着色器
        RenderSystem.setShaderTexture(0, ECHO_OUTLINE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // 2. 关键：显式开启混合（标准 alpha 混合）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 2.5 应用配置的整体透明度（0.0 完全透明，1.0 完全不透明）
        float alpha = (float) (double) ModConfig.Client.GOGGLES_OVERLAY_ALPHA.get();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        // 3. 画全屏四边形（UV 0..1，整张贴图）
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(0.0F, screenHeight, Z).setUv(0.0F, 1.0F);
        builder.addVertex(screenWidth, screenHeight, Z).setUv(1.0F, 1.0F);
        builder.addVertex(screenWidth, 0.0F, Z).setUv(1.0F, 0.0F);
        builder.addVertex(0.0F, 0.0F, Z).setUv(0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        // 4. 收尾：关闭混合 + 重置颜色（避免影响后续 GUI 绘制）
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}