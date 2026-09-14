package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 复刻 vanilla PlayerItemInHandLayer#renderArmWithSpyglass：
 * 使用期间把回响望远镜锚定在玩家头部模型上，而不是手上。
 * 全程只调用公开 API，不需要 Mixin。
 */
public final class EchoSpyglassHeadLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** 基础物品模型（未解析 override 的版本），供头部锚定渲染使用 */
    private static final ModelResourceLocation BASE_MODEL =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_spyglass"),
                    "inventory");

    public EchoSpyglassHeadLayer(PlayerRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {

        ItemStack stack = player.getUseItem();
        // 与原版触发条件一致：使用中 + 使用的物品是我们的望远镜 + 不在挥臂
        if (!player.isUsingItem()
                || !(stack.getItem() instanceof EchoSpyglassItem)
                || player.swingTime != 0) {
            return;
        }

        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        poseStack.pushPose();

        // ↓↓↓ 原样复制 vanilla renderArmWithSpyglass 的 4 步 ↓↓↓
        ModelPart head = this.getParentModel().head;
        float oldXRot = head.xRot;
        head.xRot = Mth.clamp(head.xRot, (float) (-Math.PI / 6), (float) (Math.PI / 2));
        head.translateAndRotate(poseStack);
        head.xRot = oldXRot;

        CustomHeadLayer.translateToHead(poseStack, false);

        boolean leftHanded = arm == HumanoidArm.LEFT;
        poseStack.translate((leftHanded ? -2.5F : 2.5F) / 16.0F, -0.0625F, 0.0F);

        // 显式取基础模型渲染（绕过物品 override，避免渲染出"隐藏模型"）
        BakedModel base = Minecraft.getInstance().getModelManager().getModel(BASE_MODEL);
        Minecraft.getInstance().getItemRenderer().render(
                stack, ItemDisplayContext.HEAD, false,
                poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, base);

        poseStack.popPose();
    }
}