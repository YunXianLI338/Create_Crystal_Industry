package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.blockentity.SmartDrillBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class SmartDrillRenderer extends KineticBlockEntityRenderer<SmartDrillBlockEntity> {

    /** 旋转头部（不含传动杆） */
    public static final PartialModel HEAD =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/smart_drill/head"
            ));

    /** 后方传动杆：始终按真实网络转速旋转 */
    public static final PartialModel SHAFT =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/smart_drill/shaft"
            ));

    public SmartDrillRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(
            SmartDrillBlockEntity be,
            BlockState state
    ) {
        return CachedBuffers.partialFacing(HEAD, state);
    }

    @Override
    protected void renderSafe(SmartDrillBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 过滤物品渲染（与 Flywheel 无关，始终执行）
        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = getRenderedBlockState(be);
        RenderType type = getRenderType(be, state);
        VertexConsumer vc = buffer.getBuffer(type);

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            // Flywheel 已画头部（有效转速，停转冻结）；
            // vanilla 只补画传动杆（真实转速）
            renderShaft(be, state, ms, vc, light);
        } else {
            // 无 Flywheel：vanilla 画头部（有效转速）+ 传动杆（真实转速）
            renderHead(be, state, ms, vc, light);
            renderShaft(be, state, ms, vc, light);
        }
    }

    /** 头部：按有效转速旋转（红石锁/过滤拦截时冻结） */
    private void renderHead(SmartDrillBlockEntity be, BlockState state, PoseStack ms,
                            VertexConsumer vc, int light) {
        SuperByteBuffer head = CachedBuffers.partialFacing(HEAD, state);
        standardKineticRotationTransform(head, be, light).renderInto(ms, vc);
    }

    /** 传动杆：始终按真实网络转速旋转 */
    private void renderShaft(SmartDrillBlockEntity be, BlockState state, PoseStack ms,
                             VertexConsumer vc, int light) {
        SuperByteBuffer shaft;
        try {
            shaft = CachedBuffers.partialFacing(SHAFT, state);
        } catch (Exception e) {
            // 模型资源缺失时静默跳过，不让一个方块崩掉整个游戏
            return;
        }

        Axis axis = getRotationAxisOf(be);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
        float angle = ((time * be.getTrueSpeed() * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;

        shaft.light(light);
        shaft.rotateCentered(angle, Direction.get(AxisDirection.POSITIVE, axis));
        shaft.renderInto(ms, vc);
    }

    /** 动态结构专用：头部 + 传动杆合并模型（整体旋转） */
    public static final PartialModel HEAD_FULL =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/smart_drill/head_full"
            ));


}