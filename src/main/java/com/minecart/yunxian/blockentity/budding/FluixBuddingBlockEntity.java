package com.minecart.yunxian.blockentity.budding;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import com.minecart.yunxian.util.BuddingGrowthHelper;
import com.minecart.yunxian.Yunxian;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

/**
 * 福鲁伊克斯母岩的专用方块实体：让母岩成为真正的 ME 网格节点。
 * <p>
 * 服务端权威的“网格激活”状态通过 writeToStream/readFromStream 同步到客户端，
 * 护目镜提示读取的是同步值（客户端上的 IManagedGridNode 永远不可用，
 * 不能直接用它判断供电）。
 */
public class FluixBuddingBlockEntity extends AENetworkedPoweredBlockEntity
        implements IHaveGoggleInformation {

    /** 每次成功生长消耗的 AE 能量（想改数值改这里即可）。 */
    public static final double AE_COST_PER_GROWTH = 200.0;

    /** 服务端权威、随 writeToStream 同步到客户端的“网格已激活（供电+频道+启动）”状态。 */
    private boolean gridActive;

    public FluixBuddingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0);
        this.setInternalMaxPower(1000.0);
        this.setPowerSides(getGridConnectableSides(getOrientation()));
    }

    public static FluixBuddingBlockEntity create(BlockPos pos, BlockState state) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(
                ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "fluix_budding"));
        return new FluixBuddingBlockEntity(type, pos, state);
    }

    /**
     * 自定义节点监听器：节点供电/频道/网格启动状态变化时，刷新并同步 gridActive。
     */
    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, new IGridNodeListener<FluixBuddingBlockEntity>() {
            @Override
            public void onSaveChanges(FluixBuddingBlockEntity nodeOwner, IGridNode node) {
                nodeOwner.setChanged();
            }

            @Override
            public void onStateChanged(FluixBuddingBlockEntity nodeOwner, IGridNode node,
                                       IGridNodeListener.State state) {
                nodeOwner.refreshGridActive();
            }
        });
    }

    /** 服务端：首个 tick 节点创建完成后，把初始状态同步给客户端。 */
    @Override
    public void onReady() {
        super.onReady();
        refreshGridActive();
    }

    /** 重算 gridActive，仅在变化时标记更新（触发 writeToStream 发给客户端）。 */
    private void refreshGridActive() {
        if (level == null || level.isClientSide()) {
            return; // 客户端没有网格节点，状态只由服务端推进
        }
        boolean active = getMainNode().isReady() && getMainNode().isActive();
        if (this.gridActive != active) {
            this.gridActive = active;
            markForUpdate();
            setChanged();
        }
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.gridActive);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean active = data.readBoolean();
        if (this.gridActive != active) {
            this.gridActive = active;
            changed = true;
        }
        return changed;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    public boolean tryConsumeGrowthEnergy() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        IManagedGridNode mainNode = getMainNode();
        if (!mainNode.isReady() || !mainNode.isActive()) {
            return false;
        }
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energy = grid.getEnergyService();
        if (energy == null) {
            return false;
        }
        double simulated = energy.extractAEPower(AE_COST_PER_GROWTH, Actionable.SIMULATE, PowerMultiplier.ONE);
        if (simulated >= AE_COST_PER_GROWTH - 0.001) {
            energy.extractAEPower(AE_COST_PER_GROWTH, Actionable.MODULATE, PowerMultiplier.ONE);
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);
        }
        // 读客户端已同步的 gridActive，而不是 getMainNode().isActive()（客户端恒为 false）
        if (this.gridActive) {
            tooltip.add(Component.translatable("create_crystal_industry.fluix_budding.powered")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("create_crystal_industry.fluix_budding.unpowered")
                    .withStyle(ChatFormatting.RED));
        }
        // 消耗写定性话：具体 AE 数额不上浮窗（与其它母岩信息一个规矩），想算清楚就读配置或挂个能量表
        tooltip.add(Component.translatable("create_crystal_industry.fluix_budding.cost")
                .withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        return getMainNode().getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }
}