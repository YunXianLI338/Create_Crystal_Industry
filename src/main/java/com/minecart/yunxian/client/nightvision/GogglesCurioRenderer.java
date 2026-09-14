package com.minecart.yunxian.client.nightvision;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class GogglesCurioRenderer implements ICurioRenderer {

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack matrixStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource renderTypeBuffer,
            int light, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {

        LivingEntity entity = slotContext.entity();
        if (entity.isInvisible()) return;

        matrixStack.pushPose();

        // ===== ① 绑定头部（跟随头部旋转）=====
        if (renderLayerParent.getModel() instanceof HumanoidModel<?> humanoid) {
            humanoid.head.translateAndRotate(matrixStack);
        }

        // ===== ② 原版 translateToHead（玩家 flag=false）=====
        // 这就是 vanilla 让物品正确戴在头上的全部变换：
        //   translate(0,-0.25,0) + mulPose(YP 180°) + scale(0.625, -0.625, -0.625)
        // 之后 renderStatic 内部会应用 head 显示变换（scale 1.6 + translation [0,5.75,0]）
        matrixStack.translate(0.0F, -0.25F, 0.0F);
        matrixStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        matrixStack.scale(0.625F, -0.625F, -0.625F);

        // ===== ③ 渲染（与原版 ItemInHandRenderer.renderItem 完全一致）=====
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        itemRenderer.renderStatic(
                entity, stack, ItemDisplayContext.HEAD, false,
                matrixStack, renderTypeBuffer, entity.level(), light,
                OverlayTexture.NO_OVERLAY, entity.getId() + ItemDisplayContext.HEAD.ordinal());

        matrixStack.popPose();
    }
}