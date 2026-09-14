package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.EchoSpyglassItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class EchoSpyglassFrameRenderer {

    /**
     * 附加平面模型。两处关键:
     * 1. variant 必须是 "standalone" —— NeoForge 1.21.1 的
     *    ModelEvent.RegisterAdditional 强制要求,写 "inventory" 会直接抛
     *    "Side-loaded models must use the 'standalone' variant" 导致启动崩溃;
     * 2. 路径 = models/ 目录下相对路径,文件在 models/item/ 下所以带 "item/" 前缀。
     *    和项目里 NIGHT_VISION_GOGGLES_3D("block/..." + "standalone") 同款写法。
     */
    public static final ModelResourceLocation FLAT_MODEL =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "item/echo_spyglass_flat"),
                    "standalone");

    private EchoSpyglassFrameRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoSpyglassFrameRenderer::onRenderItemInFrame);
    }

    public static void onRenderItemInFrame(RenderItemInFrameEvent event) {
        if (!(event.getItemStack().getItem() instanceof EchoSpyglassItem)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BakedModel flat = mc.getModelManager().getModel(FLAT_MODEL);
        mc.getItemRenderer().render(
                event.getItemStack(),
                ItemDisplayContext.FIXED,
                false,
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY,
                flat);
    }
}