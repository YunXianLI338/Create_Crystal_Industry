package com.minecart.yunxian.client.nightvision;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 给夜视护目镜挂上自定义物品渲染器。
 * <p>
 * 只有开启态的头部模型会要求自定义渲染器（见
 * {@code GlowingLensItemModel#isCustomRenderer()}），其余上下文一律走原版路径，
 * 所以这里注册的渲染器不会被 GUI、手持、掉落物用到。
 */
@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class GogglesClientExtensions {

    private GogglesClientExtensions() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return NightVisionGogglesBewlr.INSTANCE;
            }
        }, ModItems.NIGHT_VISION_GOGGLES.get());
    }
}
