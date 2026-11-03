package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.AcceleratorBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 电力催生器。
 * <p>
 * 扳手（{@link IWrenchable}）：只放行<b>潜行</b>右键的快速拆除（默认实现：掉落物进背包 +
 * 机械动力的拆卸音效）。普通右键改成 PASS——本机的 {@code FACING} 目前没有被模型或逻辑用到
 * （{@code blockstates/accelerator.json} 只按 {@code powered} 选模型），按默认实现转它只会
 * 改一个看不见的属性，转完毫无反馈，像是扳手失灵；没有朝向可换，就别转。
 */
public class AcceleratorBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<AcceleratorBlock> CODEC = simpleCodec(AcceleratorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public AcceleratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<AcceleratorBlock> codec() {
        return CODEC;
    }

    /** 扳手右键不作为，只放行潜行拆除（见类注释：本机没有可转的朝向）。 */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POWERED);
    }

    /**
     * 催生状态下的粒子效果（仅客户端调用）。
     * 随机选一个面，在表面外侧生成粒子并向外飘散。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }

        Direction face = Direction.getRandom(random);

        // 粒子位置：面中心外侧 0.75（原 0.55），带 ±0.25 的随机散布
        double x = pos.getX() + 0.5 + face.getStepX() * 0.75 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.5 + face.getStepY() * 0.75 + (random.nextDouble() - 0.5) * 0.5;
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.75 + (random.nextDouble() - 0.5) * 0.5;

        // 速度：沿面的法线向外飘，带轻微上升
        double vx = face.getStepX() * 0.01;
        double vy = face.getStepY() * 0.01 + 0.02;
        double vz = face.getStepZ() * 0.01;

        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, vx, vy, vz);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AcceleratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ACCELERATOR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AcceleratorBlockEntity accelerator) {
                accelerator.tick(tickLevel, pos, tickState);
            }
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}