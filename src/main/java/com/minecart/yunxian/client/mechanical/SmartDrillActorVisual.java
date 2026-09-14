package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.block.SmartDrillBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SmartDrillActorVisual extends ActorVisual {

    private final TransformedInstance drillHead;
    private final Direction facing;

    private double rotation;
    private double previousRotation;

    public SmartDrillActorVisual(VisualizationContext visualizationContext,
                                 VirtualRenderWorld simulationWorld,
                                 MovementContext context) {
        super(visualizationContext, simulationWorld, context);

        BlockState state = context.state;
        facing = state.getValue(SmartDrillBlock.FACING);

        // 唯一与 DrillerActorVisual 不同的地方：用你自己的 HEAD 模型
        drillHead = instancerProvider
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(SmartDrillRenderer.HEAD_FULL))
                .createInstance();
    }

    @Override
    public void tick() {
        previousRotation = rotation;

        if (context.disabled
                || VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite()))
            return;

        float deg = context.getAnimationSpeed();
        rotation += deg / 20;
        rotation %= 360;
    }

    @Override
    public void beginFrame() {
        drillHead.setIdentityTransform()
                .translate(context.localPos)
                .center()
                .rotateToFace(facing.getOpposite())
                .rotateZDegrees((float) getRotation())
                .uncenter()
                .setChanged();
    }

    protected double getRotation() {
        return AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks(), previousRotation, rotation);
    }

    @Override
    protected void _delete() {
        drillHead.delete();
    }
}