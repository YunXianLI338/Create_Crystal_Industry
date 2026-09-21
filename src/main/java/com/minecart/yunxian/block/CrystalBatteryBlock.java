package com.minecart.yunxian.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.minecart.yunxian.battery.CrystalCapacities;
import com.minecart.yunxian.blockentity.CrystalBatteryBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 水晶电池方块。多方块骨架、方块状态（top / bottom / shape）与外观模型都照搬
 * 机械动力的流体储罐，只有「装什么」从流体换成了晶体与电。
 * <p>
 * 三段交互各管一件事，互不冲突：
 * <ul>
 *   <li>手持晶体方块右键 → 往这一格塞晶体（{@link #useItemOn}）；</li>
 *   <li>扳手右键 → 切换整座结构的有窗 / 无窗外观（{@link #onWrenched}）；</li>
 *   <li>扳手潜行右键 → 快速拆卸，掉空格电池 + 这一格的晶体（{@code IWrenchable} 默认实现，
 *       掉落物走 {@link #getDrops}）。</li>
 * </ul>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CrystalBatteryBlock extends Block implements IWrenchable, IBE<CrystalBatteryBlockEntity> {

    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public CrystalBatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(TOP, true)
                .setValue(BOTTOM, true)
                .setValue(SHAPE, Shape.WINDOW));
    }

    public static boolean isBattery(BlockState state) {
        return state.getBlock() instanceof CrystalBatteryBlock;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM, SHAPE);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (oldState.getBlock() == state.getBlock()) {
            return;
        }
        if (moved) {
            return;
        }
        withBlockEntityDo(level, pos, CrystalBatteryBlockEntity::updateConnectivity);

        // 组成多方块会就地改写方块状态，这会让 NeoForge 在放置物品后自己那一次
        // markAndNotifyBlock 变成空操作，所以这里补一次广播
        BlockState newState = level.getBlockState(pos);
        if (state != newState && newState.getBlock() == this) {
            level.markAndNotifyBlock(pos, level.getChunkAt(pos), oldState, newState, UPDATE_ALL_IMMEDIATE, 512);
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        withBlockEntityDo(context.getLevel(), context.getClickedPos(),
                CrystalBatteryBlockEntity::toggleWindows);
        return InteractionResult.SUCCESS;
    }

    /**
     * 手持晶体方块右键空格子 → 塞入。手里不是晶体方块（例如扳手）时一律放行，
     * 让扳手的拆卸 / 切窗逻辑接手。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty() || !CrystalCapacities.isCrystal(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return onBlockEntityUseItemOn(level, pos, battery -> {
            // 已经塞过晶体的格子不再接受第二颗：要换先拆
            if (!battery.getCrystal().isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                battery.insertCrystal(stack);
            }
            return ItemInteractionResult.SUCCESS;
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof CrystalBatteryBlockEntity battery)) {
                return;
            }
            level.removeBlockEntity(pos);
            ConnectivityHandler.splitMulti(battery);
        }
    }

    /**
     * 掉落物 = 空的水晶电池 + 这一格里塞着的晶体（没塞就只有空电池）。
     * <p>
     * 走 {@code getDrops} 而不是掉落表：晶体是每格的方块实体数据，物品/方块状态都表达不了。
     * 好处是玩家挖掉、扳手潜行拆卸（{@code IWrenchable} 默认实现直接调 {@code Block.getDrops}）、
     * 爆炸、钻头之类所有销毁路径都自动一致。方块属性里配了 {@code noLootTable()}，
     * 掉落表那份空实现不会被走到。
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(2);
        drops.add(new ItemStack(this));
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof CrystalBatteryBlockEntity battery) {
            ItemStack crystal = battery.getCrystal();
            if (!crystal.isEmpty()) {
                drops.add(crystal.copy());
            }
        }
        return drops;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos)
                .map(battery -> ComparatorUtil.fractionToRedstoneLevel(battery.getChargeFraction()))
                .orElse(0);
    }

    @Override
    public Class<CrystalBatteryBlockEntity> getBlockEntityClass() {
        return CrystalBatteryBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CrystalBatteryBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CRYSTAL_BATTERY.get();
    }

    /** 窗口形状：与 {@code FluidTankBlock.Shape} 同名同性，模型文件因此可以直接照搬储罐 */
    public enum Shape implements StringRepresentable {
        PLAIN, WINDOW, WINDOW_NW, WINDOW_SW, WINDOW_NE, WINDOW_SE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
