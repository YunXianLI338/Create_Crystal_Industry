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
    private final BakedModel goggles3dOn;

    /** 当前正在被渲染的、佩戴这副护目镜的生物（玩家/盔甲架等） */
    public static final ThreadLocal<LivingEntity> RENDERING_ENTITY = new ThreadLocal<>();

    public NightVisionGogglesModel(BakedModel itemModel, BakedModel goggles3d, BakedModel goggles3dOn) {
        super(itemModel);
        this.goggles3d = goggles3d;
        this.goggles3dOn = goggles3dOn;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHanded) {
        if (displayContext == ItemDisplayContext.HEAD) {
            LivingEntity entity = RENDERING_ENTITY.get();
            BakedModel active = shouldShowOn(entity) ? goggles3dOn : goggles3d;
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
            // 盔甲架：环境光暗时显示 ON（夜视主题）
            return entity.level().getMaxLocalRawBrightness(entity.blockPosition()) <= 7;
        }
        // 其他生物（僵尸等）：默认 OFF
        return false;
    }
}