package com.minecart.yunxian.client.nightvision.model;

import com.minecart.yunxian.attachment.EchoAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public class NightVisionGogglesModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel goggles3d;
    /** 开启态：交给自定义渲染器画，四个镜片面固定满亮度 */
    private final BakedModel goggles3dOnGlow;

    /** 当前正在被渲染的、佩戴这副护目镜的生物（玩家/盔甲架等） */
    public static final ThreadLocal<LivingEntity> RENDERING_ENTITY = new ThreadLocal<>();

    public NightVisionGogglesModel(BakedModel itemModel, BakedModel goggles3d, BakedModel goggles3dOn) {
        super(itemModel);
        this.goggles3d = goggles3d;
        this.goggles3dOnGlow = new GlowingLensItemModel(goggles3dOn);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHanded) {
        if (displayContext == ItemDisplayContext.HEAD) {
            LivingEntity entity = RENDERING_ENTITY.get();
            BakedModel active = shouldShowOn(entity) ? goggles3dOnGlow : goggles3d;
            return active.applyTransform(displayContext, poseStack, leftHanded);
        }
        return super.applyTransform(displayContext, poseStack, leftHanded);
    }

    /** 判定某个佩戴者是否应显示"开启"模型 */
    private static boolean shouldShowOn(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Player player) {
            // 玩家：读服务端同步的夜视 attachment
            return player.getData(EchoAttachments.NIGHT_VISION);
        }
        if (entity instanceof ArmorStand) {
            // 盔甲架：固定显示"戴上"的形态（即原先光照暗时的样子）。
            // 原先按环境光每帧现算一次，既随光照闪烁、又要在渲染热路径上查光照；
            // 展示用的盔甲架不需要这个区分，直接常亮。
            return true;
        }
        // 其他生物（僵尸等）：默认 OFF
        return false;
    }
}