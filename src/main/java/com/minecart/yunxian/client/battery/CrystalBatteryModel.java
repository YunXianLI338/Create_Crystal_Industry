package com.minecart.yunxian.client.battery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.CreateClient;
import com.minecart.yunxian.Yunxian;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
 * 材质全部来自本模组自己的 {@code textures/block/crystal_battery/}，与 resources 下模型文件里写的
 * 贴图路径一一对应。改材质时只要保持「&lt;名字&gt; 与 &lt;名字&gt;_connected」这个配对命名，
 * 代码一行都不用动；如果连文件名也要改，改下面三个 ctShift(...) 的入参即可。
 * <p>
 * 连接材质必须走自己的一套 {@link CTSpriteShiftEntry}：CT 的贴图重映射只在
 * 「面当前用的贴图 == shift 的 original」时才会发生（见 {@code CTModel#getQuads}），
 * 换了贴图路径却沿用 Create 的 shift，连接效果会静默失效、退回一张平贴图。
 */
public class CrystalBatteryModel extends CTModel {

    protected static final ModelProperty<CullData> CULL_PROPERTY = new ModelProperty<>();

    /** 侧面/顶面/内面三张贴图对；CT 类型与储罐一致（RECTANGLE，4x4 图集），所以贴图布局可以照抄 */
    private static final CTSpriteShiftEntry SIDE_SHIFT = ctShift("crystal_battery");
    private static final CTSpriteShiftEntry TOP_SHIFT = ctShift("crystal_battery_top");
    private static final CTSpriteShiftEntry INNER_SHIFT = ctShift("crystal_battery_inner");

    /** 在客户端初始化时挂到 Create 的模型替换器上，让本方块用这个模型而不是普通烘培模型 */
    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.CRYSTAL_BATTERY.getId(), CrystalBatteryModel::new);
    }

    private static CTSpriteShiftEntry ctShift(String name) {
        String dir = "block/crystal_battery/";
        return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
                ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, dir + name),
                ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, dir + name + "_connected"));
    }

    private CrystalBatteryModel(BakedModel originalModel) {
        super(originalModel, new FluidTankCTBehaviour(SIDE_SHIFT, TOP_SHIFT, INNER_SHIFT));
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
