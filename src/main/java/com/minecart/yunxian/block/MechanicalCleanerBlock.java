package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.MechanicalCleanerBlockEntity;
import com.minecart.yunxian.behaviour.MechanicalCleanerFilterBehaviour;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MechanicalCleanerBlock extends DirectionalKineticBlock
        implements IBE<MechanicalCleanerBlockEntity>, ProperWaterloggedBlock {

    /** 红石锁：true = 收到红石信号，停转无风 */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /** 基准形状（FACING=UP）：顶面中心 12×12×8 凹陷 = 底部实心 + 四周 2 格宽边缘 */
    private static final VoxelShape TOP_RECESS_BASE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),          // 底部实心 (y 0~8)
            Shapes.box(0.0, 0.5, 0.0, 1.0, 1.0, 0.125),        // 北缘 (z 0~2)
            Shapes.box(0.0, 0.5, 0.875, 1.0, 1.0, 1.0),        // 南缘 (z 14~16)
            Shapes.box(0.0, 0.5, 0.125, 0.125, 1.0, 0.875),    // 西缘 (x 0~2)
            Shapes.box(0.875, 0.5, 0.125, 1.0, 1.0, 0.875)     // 东缘 (x 14~16)
    );

    /** 按方块朝向（FACING）旋转后的 6 个变体：凹陷始终在"模型顶面" */
    private static final VoxelShaper TOP_RECESS_SHAPER =
            VoxelShaper.forDirectional(TOP_RECESS_BASE, Direction.UP);

    public MechanicalCleanerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, WATERLOGGED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /** 传动杆在 FACING 背面（应力输入面） */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // 放置后重建气流
        withBlockEntityDo(level, pos, MechanicalCleanerBlockEntity::blockInFrontChanged);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        // 红石锁：同步信号状态，仅在变化时更新，避免递归
        boolean powered = hasLockingSignal(level, pos, state);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
        }
        // 前方方块变化 → 重建气流（阻挡判定会变）
        withBlockEntityDo(level, pos, MechanicalCleanerBlockEntity::blockInFrontChanged);
    }

    /**
     * 方块被破坏/替换时：把容器内 27 格库存全部掉落出来（类似原版箱子），
     * 避免内容物随方块实体一起消失。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 方块确实被替换成其它方块（不是自己状态变化），且不是被活塞等整体搬动
        if (!state.is(newState.getBlock()) && !isMoving) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!level.isClientSide && be instanceof MechanicalCleanerBlockEntity cleaner) {
                ItemStackHandler inv = cleaner.getInventory();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty())
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // 含水支持：水流邻居变化时调度水的 tick（含水状态自动开/关）
        updateWater(level, state, pos);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = withWater(super.getStateForPlacement(context), context);
        boolean powered = hasLockingSignal(context.getLevel(), context.getClickedPos(), state);
        return state.setValue(POWERED, powered);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // FACING 的背面是传动杆接入面（见 hasShaftTowards）：这一面不提供界面，右键原样放行。
        // 否则正对着传动杆右键也会把界面顶出来，挡住这一侧本该给动力元件的交互。
        if (hitResult.getDirection() == state.getValue(FACING).getOpposite()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        // 命中侧面且命中点确实落在配置栏位（值框）上：才交给基类行为系统处理
        // 侧面非栏位区域则继续往下走：空手打开容器
        if (isConfigSlotSide(state, hitResult.getDirection()) && isHitOnConfigSlot(level, pos, hitResult)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        // 其余位置（含侧面非栏位区域）：无论手持什么，都打开容器
        if (level.getBlockEntity(pos) instanceof MechanicalCleanerBlockEntity blockEntity) {
            // 网络包：方块位置 + 风力格数 + 当前风向（反转？）
            player.openMenu(blockEntity, buf -> {
                buf.writeBlockPos(pos);
                buf.writeInt(blockEntity.getSuckRange());
                buf.writeBoolean(blockEntity.getRotationDirection()
                        == MechanicalCleanerFilterBehaviour.RotationDirection.REVERSED);
            });
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 命中点是否真的落在侧面的过滤/方向配置栏位（值框）上 */
    private boolean isHitOnConfigSlot(Level level, BlockPos pos, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof MechanicalCleanerBlockEntity be) {
            return be.isHitOnConfigSlot(hitResult.getLocation());
        }
        return false;
    }

    /** 配置槽只在垂直于朝向的 4 个侧面（与 ValueBoxTransform.isSideActive 一致） */
    private boolean isConfigSlotSide(BlockState state, Direction side) {
        return side.getAxis() != state.getValue(FACING).getAxis();
    }

    /** 红石锁判定：忽略扇叶朝向（FACING）的红石信号，只统计其余 5 个方向 */
    private boolean hasLockingSignal(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (Direction side : Direction.values()) {
            if (side == facing) {
                continue;
            }
            if (level.getSignal(pos.relative(side), side) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Class<MechanicalCleanerBlockEntity> getBlockEntityClass() {
        return MechanicalCleanerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalCleanerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.MECHANICAL_CLEANER.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TOP_RECESS_SHAPER.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TOP_RECESS_SHAPER.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TOP_RECESS_SHAPER.get(state.getValue(FACING));
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}