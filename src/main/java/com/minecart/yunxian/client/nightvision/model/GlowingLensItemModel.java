package com.minecart.yunxian.client.nightvision.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/**
 * 开启态护目镜的「自定义渲染器标记」模型。
 * <p>
 * {@link net.minecraft.client.renderer.entity.ItemRenderer#render} 只要拿到一个
 * {@code isCustomRenderer()} 为真的模型，就会改走
 * {@code IClientItemExtensions#getCustomRenderer()} 里的
 * {@link net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer}——只有那条路
 * 才能拿到调用方的 {@code MultiBufferSource}，从而自己决定每个面的光照值。
 * 渲染本身由 {@code NightVisionGogglesBewlr} 负责。
 * <p>
 * {@link #applyTransform} 仍要把底下模型的 display 变换（HEAD 的位移与缩放）应用上去，
 * 再把「真正要画的模型」暂存到 {@link #PENDING_SOURCE}：原版紧接着就会调用
 * {@code renderByItem}，两者在同一线程同一调用栈里，取走即清，不会串到别的物品。
 */
public class GlowingLensItemModel extends BakedModelWrapper<BakedModel> {

    /** 真正要画的模型：applyTransform 时填好，renderByItem 取走 */
    public static final ThreadLocal<BakedModel> PENDING_SOURCE = new ThreadLocal<>();

    public GlowingLensItemModel(BakedModel model) {
        super(model);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHanded) {
        originalModel.applyTransform(displayContext, poseStack, leftHanded);
        PENDING_SOURCE.set(originalModel);
        return this;
    }
}
