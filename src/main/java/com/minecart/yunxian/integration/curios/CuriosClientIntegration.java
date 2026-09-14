package com.minecart.yunxian.integration.curios;

import com.minecart.yunxian.registry.ModItems;
import com.minecart.yunxian.client.nightvision.GogglesCurioRenderer;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

public final class CuriosClientIntegration {
    private CuriosClientIntegration() {}

    public static void registerRenderers() {
        CuriosRendererRegistry.register(ModItems.NIGHT_VISION_GOGGLES.get(), GogglesCurioRenderer::new);
    }
}