package com.minecart.yunxian.client.nightvision;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.nightvision.model.NightVisionGogglesModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class NightVisionGogglesRender {
    private NightVisionGogglesRender() {}

    @SubscribeEvent
    public static void onEntityPre(RenderLivingEvent.Pre<?, ?> event) {
        // 玩家、盔甲架等所有活体实体渲染前：记录"正在被渲染的佩戴者"
        NightVisionGogglesModel.RENDERING_ENTITY.set(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityPost(RenderLivingEvent.Post<?, ?> event) {
        NightVisionGogglesModel.RENDERING_ENTITY.remove();
    }
}