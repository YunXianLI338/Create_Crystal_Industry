package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.blockentity.MechanicalCleanerBlockEntity;
import com.minecart.yunxian.blockentity.MechanicalCleanerBlockEntity.SuckPhantom;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class MechanicalCleanerRenderer extends KineticBlockEntityRenderer<MechanicalCleanerBlockEntity> {

    /** 旋转的扇叶（含内部轴） */
    public static final PartialModel PROPELLER =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/mechanical_cleaner/propeller"));

    /** 外部传动杆 */
    public static final PartialModel SHAFT =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/mechanical_cleaner/shaft"));

    public MechanicalCleanerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(MechanicalCleanerBlockEntity be, BlockState state) {
        // 和智能钻头 getRotatedModel 返回 HEAD 对应：这里返回扇叶
        return CachedBuffers.partialFacing(PROPELLER, state);
    }

    @Override
    protected void renderSafe(MechanicalCleanerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 过滤物品渲染（与 Flywheel 无关，始终执行）：画出侧面过滤槽里的物品模型
        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);

        // 吸入幻影动画：纯视觉，不影响实际库存逻辑
        renderSuckPhantoms(be, partialTicks, ms, buffer, light);

        BlockState state = getRenderedBlockState(be);
        RenderType type = getRenderType(be, state);
        VertexConsumer vc = buffer.getBuffer(type);

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            // Flywheel 已画扇叶，vanilla 只补传动杆（和智能钻头补 SHAFT 完全一致）
            renderShaft(be, state, ms, vc, light);
        } else {
            // 无 Flywheel：vanilla 画扇叶 + 传动杆
            renderPropeller(be, state, ms, vc, light);
            renderShaft(be, state, ms, vc, light);
        }
    }

    /**
     * 渲染"吸入幻影"：物品从被吸位置飞向方块中心，越近越小并自旋，到终点消失。
     * 纯视觉，不影响实际库存逻辑（库存仍是瞬间进入）。
     */
    private void renderSuckPhantoms(MechanicalCleanerBlockEntity be, float partialTicks, PoseStack ms,
                                    MultiBufferSource buffer, int light) {
        List<SuckPhantom> phantoms = be.getActivePhantoms();
        if (phantoms == null || phantoms.isEmpty())
            return;

        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        BlockPos pos = be.getBlockPos();
        Vec3 target = VecHelper.getCenterOf(pos);
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();

        List<SuckPhantom> toRemove = new ArrayList<>();

        for (SuckPhantom p : phantoms) {
            float t = (now + partialTicks - p.startTime) / (float) SuckPhantom.DURATION;
            if (t >= 1.0f) {
                toRemove.add(p);
                continue;
            }

            // 缓动：前慢后快，模拟被加速吸入口中
            float eased = t * t;
            Vec3 cur = p.start.lerp(target, eased);

            ms.pushPose();
            // renderSafe 的坐标系原点在方块坐标处，世界点 p → 局部 (p - pos)
            ms.translate(cur.x - pos.getX(), cur.y - pos.getY(), cur.z - pos.getZ());

            // 越靠近越小（0.4 → 0.15），带自旋
            float scale = Mth.lerp(eased, 0.6f, 0.15f);
            ms.scale(scale, scale, scale);
            ms.mulPose(Axis.YP.rotationDegrees((now + partialTicks) * 60f));

            itemRenderer.renderStatic(p.stack, ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, ms, buffer, be.getLevel(), 0);

            ms.popPose();
        }

        phantoms.removeAll(toRemove);
    }

    /** 扇叶：按有效转速旋转（红石锁定时冻结） */
    private void renderPropeller(MechanicalCleanerBlockEntity be, BlockState state, PoseStack ms,
                                 VertexConsumer vc, int light) {
        SuperByteBuffer propeller = CachedBuffers.partialFacing(PROPELLER, state);
        standardKineticRotationTransform(propeller, be, light).renderInto(ms, vc);
    }

    /** 传动杆：始终按真实转速旋转（红石锁定时仍转，与智能钻头一致） */
    private void renderShaft(MechanicalCleanerBlockEntity be, BlockState state, PoseStack ms,
                             VertexConsumer vc, int light) {
        SuperByteBuffer shaft;
        try {
            shaft = CachedBuffers.partialFacing(SHAFT, state);
        } catch (Exception e) {
            return;
        }
        // 注意：这里是 net.minecraft.core.Direction.Axis（传动杆旋转轴），
        // 不是 com.mojang.math.Axis（幻影自旋用）。两者同名，这里用全限定名区分。
        net.minecraft.core.Direction.Axis axis = getRotationAxisOf(be);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
        float angle = ((time * be.getTrueSpeed() * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;
        shaft.light(light);
        shaft.rotateCentered(angle, Direction.get(AxisDirection.POSITIVE, axis));
        shaft.renderInto(ms, vc);
    }
}