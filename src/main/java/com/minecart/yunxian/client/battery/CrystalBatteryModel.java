package com.minecart.yunxian.client.battery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.CreateClient;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;

import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelData.Builder;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 水晶电池的模型：逐条照搬储罐的 {@code FluidTankModel}——相邻同结构方块用连接材质，
 * 并剔除朝内那一面的四边形。
 * <p>
 * <b>材质目前直接借用 Create 的 fluid_tank 系列</b>（{@link com.simibubi.create.AllSpriteShifts}），
 * 与 resources 下模型文件里写的贴图路径保持一致。之后画好自己的材质时，
 * 要改两处：模型文件里的贴图路径，以及这里换成一整套自家的 {@link CTSpriteShiftEntry}。
 */
public class CrystalBatteryModel extends CTModel {

    protected static final ModelProperty<CullData> CULL_PROPERTY = new ModelProperty<>();

    /** 在客户端初始化时挂到 Create 的模型替换器上，让本方块用这个模型而不是普通烘培模型 */
    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.CRYSTAL_BATTERY.getId(), CrystalBatteryModel::new);
    }

    private CrystalBatteryModel(BakedModel originalModel) {
        this(originalModel, AllSpriteShifts.FLUID_TANK, AllSpriteShifts.FLUID_TANK_TOP,
                AllSpriteShifts.FLUID_TANK_INNER);
    }

    private CrystalBatteryModel(BakedModel originalModel, CTSpriteShiftEntry side, CTSpriteShiftEntry top,
                                CTSpriteShiftEntry inner) {
        super(originalModel, new FluidTankCTBehaviour(side, top, inner));
    }

    @Override
    protected Builder gatherModelData(Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
                                      ModelData blockEntityData) {
        super.gatherModelData(builder, world, pos, state, blockEntityData);
        CullData cullData = new CullData();
        for (Direction d : Iterate.horizontalDirections) {
            cullData.setCulled(d, ConnectivityHandler.isConnected(world, pos, pos.relative(d)));
        }
        return builder.with(CULL_PROPERTY, cullData);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
                                    RenderType renderType) {
        if (side != null) {
            return Collections.emptyList();
        }

        List<BakedQuad> quads = new ArrayList<>();
        for (Direction d : Iterate.directions) {
            if (extraData.has(CULL_PROPERTY) && extraData.get(CULL_PROPERTY).isCulled(d)) {
                continue;
            }
            quads.addAll(super.getQuads(state, d, rand, extraData, renderType));
        }
        quads.addAll(super.getQuads(state, null, rand, extraData, renderType));
        return quads;
    }

    private static class CullData {
        private final boolean[] culledFaces;

        CullData() {
            culledFaces = new boolean[4];
            Arrays.fill(culledFaces, false);
        }

        void setCulled(Direction face, boolean cull) {
            if (face.getAxis().isVertical()) {
                return;
            }
            culledFaces[face.get2DDataValue()] = cull;
        }

        boolean isCulled(Direction face) {
            if (face.getAxis().isVertical()) {
                return false;
            }
            return culledFaces[face.get2DDataValue()];
        }
    }
}
