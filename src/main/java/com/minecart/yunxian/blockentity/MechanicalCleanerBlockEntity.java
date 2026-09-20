package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.behaviour.MechanicalCleanerFilterBehaviour;
import com.minecart.yunxian.menu.MechanicalCleanerMenu;
import com.minecart.yunxian.behaviour.MechanicalCleanerValueBoxTransform;
import com.minecart.yunxian.block.MechanicalCleanerBlock;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CKinetics;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class MechanicalCleanerBlockEntity extends KineticBlockEntity
        implements MenuProvider, IAirCurrentSource {

    /** 容器容量：27 格 */
    public static final int INVENTORY_SIZE = 27;

    /** 目标气流长度最小值（格） */
    public static final int SUCK_RANGE_MIN = 1;

    /** 目标气流长度最大值（格）：与鼓风机 256 转速下的最大距离一致（fanPushDistance 默认 20） */
    public static final int SUCK_RANGE_MAX = 20;

    /** 目标气流长度（格）：GUI 调节的数值，实际长度还会受转速上限约束 */
    private int suckRange = SUCK_RANGE_MIN;

    /** 吸尘器内部库存 */
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);

    /** 侧面的过滤 / 方向配置槽（吹出数量与模式也由它承载，见 FilteringBehaviour 的 showCount 机制） */
    private MechanicalCleanerFilterBehaviour filtering;

    // ===== 气流（复用鼓风机 AirCurrent）=====

    /** 气流对象：推/吸力、长度、阻挡、粒子全部与鼓风机一致 */
    public AirCurrent airCurrent;

    /** 气流重建冷却（fanBlockCheckRate 配置，默认 30 tick） */
    protected int airCurrentUpdateCooldown;

    /** 实体搜索冷却（5 tick，与鼓风机一致） */
    protected int entitySearchCooldown;

    /** 需要重建气流 */
    protected boolean updateAirFlow;

    /** 吹出模式下的发射冷却：每次发射后按转速重置 */
    private int ejectCooldown;

    // ===== 吸入幻影（纯视觉，不影响真实库存逻辑） =====

    /** 服务端：待同步给客户端的吸入事件 */
    private final List<SuckPhantom> pendingPhantoms = new ArrayList<>();
    /** 客户端：正在渲染的幻影 */
    private final List<SuckPhantom> activePhantoms = new ArrayList<>();

    public MechanicalCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_CLEANER.get(), pos, state);
        airCurrent = new AirCurrent(this);
        updateAirFlow = true;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /** 客户端渲染队列（供 Renderer 读取） */
    public List<SuckPhantom> getActivePhantoms() {
        return activePhantoms;
    }

    /** 一次"吸入"的视觉事件：从被吸位置飞向方块中心 */
    public static class SuckPhantom {
        public static final int DURATION = 10;   // 视觉飞行时长（tick）
        public final ItemStack stack;
        public final Vec3 start;                  // 世界坐标（物品被吸前的位置）
        public long startTime;                    // 客户端收到时填入本地 gameTime

        public SuckPhantom(ItemStack stack, Vec3 start) {
            this.stack = stack;
            this.start = start;
        }
    }

    // ==================== IAirCurrentSource ====================

    @Override
    public AirCurrent getAirCurrent() {
        return airCurrent;
    }

    @Override
    public Level getAirCurrentWorld() {
        return level;
    }

    @Override
    public BlockPos getAirCurrentPos() {
        return worldPosition;
    }

    /** 气流起点面：方块朝向 */
    @Override
    public Direction getAirflowOriginSide() {
        return getBlockState().getValue(MechanicalCleanerBlock.FACING);
    }

    /**
     * 气流方向由"扇叶旋转方向"决定，与应力旋转方向无关：
     * NORMAL = 吹出（FACING 方向）；REVERSED = 吸入（FACING 反方向）。
     * 转速为 0 时无风。
     */
    @Override
    public Direction getAirFlowDirection() {
        if (getSpeed() == 0)
            return null;
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        return isPulling() ? facing.getOpposite() : facing;
    }

    /**
     * 气流长度 = min(GUI 目标值, 转速决定的最大距离)。
     * 与鼓风机一致：fanRotationArgmax=256 时距离因子=1，长度 = fanPushDistance/fanPullDistance（默认 20）。
     */
    @Override
    public float getMaxDistance() {
        float speed = Math.abs(getSpeed());
        CKinetics config = AllConfigs.server().kinetics;
        float distanceFactor = Math.min(speed / config.fanRotationArgmax.get(), 1);
        float maxBySpeed = isPulling()
                ? Mth.lerp(distanceFactor, 3f, config.fanPullDistance.get())
                : Mth.lerp(distanceFactor, 3, config.fanPushDistance.get());
        return Math.min(suckRange, maxBySpeed);
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    // ==================== 行为注册（仿智能钻头 + 开放容器） ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        // 开放容器输入判定（传送带 / 工作盆共用）：
        // - 顶面朝上（FACING = UP）：两者都允许
        // - 顶面朝向传送带（FACING 指向传送带所在方向）：仅传送带允许
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(this::canReceiveDirectInput));

        filtering = (MechanicalCleanerFilterBehaviour) new MechanicalCleanerFilterBehaviour(
                this,
                new MechanicalCleanerValueBoxTransform()
        ).withDirectionCallback(direction -> {
            // 方向切换 = 吹/吸切换：重建气流、同步客户端，并通知相邻溜槽重算
            updateAirFlow = true;
            sendData();
            updateChute();
        }).showCountWhen(() -> true);   // 始终显示数量配置（智能溜槽同款机制）
        filtering.setLabel(Component.translatable("create_crystal_industry.mechanical_cleaner.filter"));
        behaviours.add(filtering);
    }

    /**
     * 判断是否允许传送带 / 工作盆直接输入。
     *
     * <p>调用约定（由实测 + 源码推导）：</p>
     * <ul>
     *   <li>传送带：side = 传送带→吸尘器方向 = facing.getOpposite()；传送带本体位于 relative(facing)。</li>
     *   <li>工作盆：side = 盆→邻格方向；吸尘器水平朝向盆时该方向恰等于 facing。</li>
     * </ul>
     *
     * <p>规则：</p>
     * <ul>
     *   <li>FACING = UP：工作盆与任意方向传送带都允许。</li>
     *   <li>FACING 指向某方向：仅接受"顶面正对着的传送带"（side = facing.getOpposite() 且 relative(facing) 是传送带）。</li>
     * </ul>
     */
    private boolean canReceiveDirectInput(Direction side) {
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        // 顶面朝上：工作盆与传送带都允许
        if (facing == Direction.UP)
            return true;
        // 非 UP：只接受顶面正对着的传送带
        // 传送带在 relative(facing)，它传入的 side = 传送带→吸尘器 = facing.getOpposite()
        if (side != facing.getOpposite())
            return false;
        BlockEntity source = level.getBlockEntity(worldPosition.relative(facing));
        return source instanceof BeltBlockEntity;
    }

    // ==================== 过滤与方向 ====================

    /** 是否允许吸取该物品：过滤为空 = 全部吸取；否则仅吸过滤匹配的物品 */
    public boolean canSuck(ItemStack stack) {
        if (filtering == null)
            return true;
        if (filtering.getFilter().isEmpty())
            return true;
        return filtering.test(stack);
    }

    /** 是否允许吹出该物品：过滤为空 = 全部吹出；否则仅吹出过滤匹配的物品 */
    public boolean canEject(ItemStack stack) {
        if (filtering == null)
            return true;
        if (filtering.getFilter().isEmpty())
            return true;
        return filtering.test(stack);
    }

    /** 当前扇叶旋转方向：NORMAL = 吹出，REVERSED = 吸入 */
    public MechanicalCleanerFilterBehaviour.RotationDirection getRotationDirection() {
        if (filtering == null)
            return MechanicalCleanerFilterBehaviour.RotationDirection.NORMAL;
        return filtering.getDirection();
    }

    /** 是否处于"吸入"模式（气流方向与朝向相反） */
    private boolean isPulling() {
        return getRotationDirection() == MechanicalCleanerFilterBehaviour.RotationDirection.REVERSED;
    }

    /** 渲染用转速：方向反转时取负，仅影响视觉旋转方向（不改变动能网络） */
    public float getRenderSpeed() {
        float base = getTrueSpeed();
        return isPulling() ? -base : base;
    }

    // ==================== 气流长度（GUI 配置） ====================

    public int getSuckRange() {
        return suckRange;
    }

    public void setSuckRange(int range) {
        suckRange = Math.max(SUCK_RANGE_MIN, Math.min(SUCK_RANGE_MAX, range));
        setChanged();
        sendData();
        updateAirFlow = true;
    }

    // ==================== 容器 UI ====================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.create_crystal_industry.mechanical_cleaner");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MechanicalCleanerMenu(id, playerInventory, inventory, worldPosition,
                suckRange, getRotationDirection());
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        // 两端都读：客户端需要计算气流长度（SuckRange）与 GUI 初始值
        suckRange = Math.max(SUCK_RANGE_MIN, Math.min(SUCK_RANGE_MAX, compound.getInt("SuckRange")));
        if (clientPacket) {
            rebuildAirBounds();

            // 解析服务端发来的"吸入事件"，加入客户端幻影渲染队列
            if (level != null && level.isClientSide && compound.contains("SuckPhantoms")) {
                CompoundTag list = compound.getCompound("SuckPhantoms");
                int count = list.getInt("Count");
                for (int i = 0; i < count; i++) {
                    CompoundTag t = list.getCompound(String.valueOf(i));
                    ItemStack stack = ItemStack.parseOptional(registries, t.getCompound("Stack"));
                    if (stack.isEmpty())
                        continue;
                    SuckPhantom p = new SuckPhantom(stack,
                            new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")));
                    p.startTime = level.getGameTime();
                    activePhantoms.add(p);
                    if (activePhantoms.size() > 16)
                        activePhantoms.remove(0);
                }
            }
            return;
        }
        if (compound.contains("Inventory")) {
            inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("SuckRange", suckRange);
        if (clientPacket) {
            // 客户端包：携带待播放的吸入事件并清空待发队列
            if (!pendingPhantoms.isEmpty()) {
                CompoundTag list = new CompoundTag();
                int i = 0;
                for (SuckPhantom p : pendingPhantoms) {
                    CompoundTag t = new CompoundTag();
                    t.put("Stack", p.stack.saveOptional(registries));
                    t.putDouble("X", p.start.x);
                    t.putDouble("Y", p.start.y);
                    t.putDouble("Z", p.start.z);
                    list.put(String.valueOf(i++), t);
                }
                list.putInt("Count", pendingPhantoms.size());
                compound.put("SuckPhantoms", list);
                pendingPhantoms.clear();
            }
            return;
        }
        compound.put("Inventory", inventory.serializeNBT(registries));
    }

    // ==================== 红石锁 ====================

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(MechanicalCleanerBlock.POWERED);
    }

    /** 红石锁定时转速汇报为 0：无风、不吸不吹 */
    @Override
    public float getSpeed() {
        if (isRedstoneLocked())
            return 0;
        return super.getSpeed();
    }

    /** 真实网络转速：不受红石锁影响，供传动杆渲染使用 */
    public float getTrueSpeed() {
        return super.getSpeed();
    }

    // ==================== 气流驱动（仿鼓风机） ====================

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        updateAirFlow = true;
        updateChute();
    }

    @Override
    public void remove() {
        super.remove();
        updateChute();
    }

    /** 前方方块变化时调用：重建气流（阻挡判定会变），并通知相邻溜槽重算 */
    public void blockInFrontChanged() {
        updateAirFlow = true;
        updateChute();
    }

    /**
     * 仿 EncasedFanBlockEntity.updateChute：
     * 吸尘器垂直朝向时，通知正前方（FACING 方向）紧贴的溜槽重算风力。
     * 朝 DOWN → 溜槽 updatePull；朝 UP → 溜槽 updatePush。
     */
    public void updateChute() {
        Direction direction = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        if (!direction.getAxis().isVertical())
            return;
        BlockEntity poweredChute = level.getBlockEntity(worldPosition.relative(direction));
        if (!(poweredChute instanceof ChuteBlockEntity chuteBE))
            return;
        if (direction == Direction.DOWN)
            chuteBE.updatePull();
        else
            chuteBE.updatePush(1);
    }

    private void rebuildAirBounds() {
        airCurrent.rebuild();
        // 无动力/红石锁（getSpeed()==0）时 rebuild 已把 maxDistance 置 0，bounds 保持空
        if (getSpeed() == 0) {
            airCurrent.bounds = new AABB(0, 0, 0, 0, 0, 0);
            return;
        }

        // rebuild() 之后 airCurrent.maxDistance = getFlowLimit(...) = 前方可穿过的格数
        // （阻挡块在第 B 格时 = B-1）。从方块自身格开始铺风力范围：
        // 覆盖 [自身格, 自身格+maxDistance]，遇到阻挡恰好停在阻挡前一格，
        // 且风力与方块融为一体（起点在凹陷上）。
        Direction facing = getAirflowOriginSide();
        int reach = Math.max(0, (int) airCurrent.maxDistance);
        BlockPos first = worldPosition;
        BlockPos last = first.relative(facing, reach);

        Vec3 minCorner = new Vec3(
                Math.min(first.getX(), last.getX()),
                Math.min(first.getY(), last.getY()),
                Math.min(first.getZ(), last.getZ()));
        Vec3 maxCorner = new Vec3(
                Math.max(first.getX(), last.getX()) + 1,
                Math.max(first.getY(), last.getY()) + 1,
                Math.max(first.getZ(), last.getZ()) + 1);
        airCurrent.bounds = new AABB(minCorner, maxCorner);
    }

    @Override
    public void tick() {
        super.tick();

        boolean server = !level.isClientSide;

        // 周期性检查阻挡：每 fanBlockCheckRate(30) tick 重建一次气流
        if (server && airCurrentUpdateCooldown-- <= 0) {
            airCurrentUpdateCooldown = AllConfigs.server().kinetics.fanBlockCheckRate.get();
            updateAirFlow = true;
        }

        if (updateAirFlow) {
            updateAirFlow = false;
            rebuildAirBounds();
            sendData();
        }

        boolean hasPower = getTrueSpeed() != 0;   // 是否有动力输入（网络转速，不受红石锁影响）
        boolean locked = isRedstoneLocked();      // 红石锁：完全停止

        // 有动力且未锁定：驱动气流
        if (hasPower && !locked) {
            // 每 5 tick 刷新气流中的实体列表
            if (entitySearchCooldown-- <= 0) {
                entitySearchCooldown = 5;
                airCurrent.findEntities();
            }
            airCurrent.tick();
        }

        if (server) {
            if (locked)
                return;   // 红石锁：不吸不吹

            if (isPulling()) {
                if (hasPower) {
                    // 有动力：气流范围 + 喷嘴覆盖范围 + 面前容器直吸 + 内部凹陷吸取
                    collectItemsInFlow();
                    collectItemsFromNozzle();
                    collectItemsFromContainer();
                    collectItemsInInterior();
                } else {
                    // 无动力：只被动吸掉落物（不依赖风）；容器直吸不生效；凹陷内仍可吸
                    collectItemsPassive();
                    collectItemsInInterior();
                }
            } else if (hasPower) {
                // 吹出模式需要动力
                ejectItems();
            }
        }
    }

    // ==================== 收集 / 喷射逻辑 ====================

    /**
     * 吸入模式：扫描整个气流范围（airCurrent.bounds，已含阻挡截断与自身凹陷），
     * 范围内所有掉落物直接收入 27 格容器。
     * 容器满时物品原地堆积（由玩家产线处理）。
     */
    private void collectItemsInFlow() {
        // 无有效气流时不收集
        if (airCurrent == null || airCurrent.maxDistance <= 0)
            return;
        AABB flowBounds = airCurrent.bounds;
        if (flowBounds == null || flowBounds.getSize() <= 0)
            return;

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, flowBounds);
        for (ItemEntity itemEntity : items) {
            suckItem(itemEntity);
        }
    }

    /**
     * 无动力时的被动吸取：不依赖 AirCurrent 的风力，
     * 按配置的吸取距离（suckRange）扫描前方范围，把掉落物直接收入容器。
     * 与有动力时一致：过滤生效；完整方块（isSuffocating）阻挡。
     */
    private void collectItemsPassive() {
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);

        // 逐格扫描，遇到完整方块截断（与早期 getEffectiveSuckRange 行为一致）
        int effective = 0;
        for (int i = 1; i <= suckRange; i++) {
            BlockPos check = worldPosition.relative(facing, i);
            if (level.getBlockState(check).isSuffocating(level, check))
                break;
            effective = i;
        }
        if (effective <= 0)
            return;   // 前方紧贴完整方块，一格都吸不到

        BlockPos first = worldPosition.relative(facing);
        BlockPos last = first.relative(facing, effective - 1);

        // 两角构造 AABB：对任意朝向都正确
        Vec3 minCorner = new Vec3(
                Math.min(first.getX(), last.getX()),
                Math.min(first.getY(), last.getY()),
                Math.min(first.getZ(), last.getZ()));
        Vec3 maxCorner = new Vec3(
                Math.max(first.getX(), last.getX()) + 1,
                Math.max(first.getY(), last.getY()) + 1,
                Math.max(first.getZ(), last.getZ()) + 1);
        AABB area = new AABB(minCorner, maxCorner);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity itemEntity : items) {
            suckItem(itemEntity);
        }
    }

    /**
     * 收集掉进方块自身凹陷内的掉落物。
     * 由于凹陷是方块内部唯一的开放空间（其余实心），扫描整块 AABB 是安全的。
     * 只供吸入模式调用；吹出模式下凹陷内的物品应由风力吹出，不应被吸回。
     */
    private void collectItemsInInterior() {
        AABB interior = new AABB(worldPosition);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, interior);
        for (ItemEntity itemEntity : items) {
            suckItem(itemEntity);
        }
    }

    /**
     * 分散网（喷嘴）兼容：吸尘器正前方挂有喷嘴时，
     * 喷嘴把气流分散成以自身为中心的球形风场（半径 = 吸尘器气流长度）。
     * 吸入模式下，该球体内、且有视线的掉落物直接收入容器。
     */
    private void collectItemsFromNozzle() {
        if (airCurrent == null || getMaxDistance() <= 0)
            return;

        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        BlockPos nozzlePos = worldPosition.relative(facing);

        // 正前方必须是喷嘴才走分散网逻辑
        if (!(level.getBlockEntity(nozzlePos) instanceof NozzleBlockEntity))
            return;

        // 喷嘴覆盖范围：以喷嘴中心为球心，半径 = 吸尘器气流长度（与喷嘴 calcRange 一致）
        Vec3 center = VecHelper.getCenterOf(nozzlePos);
        float radius = getMaxDistance();
        AABB coverage = new AABB(center, center).inflate(radius);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, coverage);
        for (ItemEntity itemEntity : items) {
            // 与喷嘴自身行为一致：超出半径或无视线的不吸
            if (itemEntity.position().distanceTo(center) > radius)
                continue;
            if (!canSeeNozzle(itemEntity, nozzlePos))
                continue;
            suckItem(itemEntity);
        }
    }

    /**
     * 与 NozzleBlockEntity.canSee 相同：从物品位置到喷嘴中心做碰撞射线，
     * 命中喷嘴自身才视为可见（风能真正覆盖到）。
     */
    private boolean canSeeNozzle(ItemEntity entity, BlockPos nozzlePos) {
        ClipContext context = new ClipContext(entity.position(), VecHelper.getCenterOf(nozzlePos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        return nozzlePos.equals(level.clip(context).getBlockPos());
    }

    /**
     * 吸入模式：识别正前方（FACING 方向）的容器，按过滤直接把其中物品吸进自身库存，
     * 不产生掉落物实体。与吹出模式"面前容器直送"对称。
     * 仅在"有动力"时由 tick 调用（被动模式不生效）。
     * 每次吸取（每 tick）按"吹出个数"配置限制总吸取数量；数量设为 0（anyAmount）时不限。
     * 只走物品过滤（canSuck）；数量精确/小于等于模式只影响吹出，不影响吸取上限。
     */
    private void collectItemsFromContainer() {
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        // 访问前方面对吸尘器的那个面（与 ejectItems 直送容器时一致）
        IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, frontPos, facing.getOpposite());
        if (source == null)
            return;

        // 每次吸取（本次 tick）的总额度：-1 = 不限
        int amountLimit = getEjectAmountCap();
        int extractedThisTick = 0;
        // 有没有真的把物品从容器搬进自身库存（成就用；见方法末尾）
        boolean transferred = false;

        for (int slot = 0; slot < source.getSlots(); slot++) {
            if (amountLimit >= 0 && extractedThisTick >= amountLimit)
                break;

            ItemStack stackInSlot = source.getStackInSlot(slot);
            if (stackInSlot.isEmpty())
                continue;
            if (!canSuck(stackInSlot))
                continue;

            // 本次可从该槽取出的数量 = min(剩余额度, 自身库存可容纳量)
            int remainingBudget = amountLimit >= 0 ? amountLimit - extractedThisTick : Integer.MAX_VALUE;
            int maxExtract = Math.min(getMaxInsertable(stackInSlot), remainingBudget);
            if (maxExtract <= 0)
                continue;

            ItemStack extracted = source.extractItem(slot, maxExtract, false);
            if (extracted.isEmpty())
                continue;

            extractedThisTick += extracted.getCount();

            ItemStack remainder = insertAll(extracted);
            if (!remainder.isEmpty()) {
                // 理论不会发生（已按可放入量取出），保险起见退回源容器
                source.insertItem(slot, remainder, false);
            }
            transferred |= extracted.getCount() > remainder.getCount();
            setChanged();
        }

        // 与容器直接交换成功：全程没有东西掉进世界里
        if (transferred && level instanceof ServerLevel serverLevel) {
            YunxianAdvancements.awardNear(serverLevel, worldPosition, YunxianAdvancements.MACHINE_CLEANER_SWAP);
        }
    }

    /**
     * "吹出个数"配置作为容器吸取的每次数量上限：
     * 数量未显示或设为 0（anyAmount）时返回 -1 = 不限。
     */
    private int getEjectAmountCap() {
        if (filtering == null)
            return -1;
        if (!filtering.isCountVisible())
            return -1;
        if (filtering.anyAmount())
            return -1;
        return filtering.getAmount();
    }

    /** 模拟计算 stack 最多能有多少被放入自身库存 */
    private int getMaxInsertable(ItemStack stack) {
        ItemStack working = stack.copy();
        int inserted = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack remainder = inventory.insertItem(slot, working, true);
            inserted += working.getCount() - remainder.getCount();
            working = remainder;
            if (working.isEmpty())
                break;
        }
        return inserted;
    }

    /** 依次把整个 stack 塞入自身库存，返回塞不下的剩余部分 */
    private ItemStack insertAll(ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    /**
     * 吹出逻辑（方案 A，与智能溜槽一致）：
     * 数量是"筛选条件"，两种模式：
     * - 严格模式（!filtering.upTo）：只有槽内堆叠数量 == 配置值才吹出整组；
     * - 小于等于模式（filtering.upTo）：只有槽内堆叠数量 ≤ 配置值才吹出整组；
     * - 数量为 0（anyAmount）或未显示数量时：不过滤，任何堆叠都吹出整组。
     */
    private void ejectItems() {
        if (inventory == null)
            return;

        // 从第一个"非空且符合过滤"的槽取；不符合过滤的槽跳过
        int slotToEject = -1;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stackInSlot = inventory.getStackInSlot(slot);
            if (stackInSlot.isEmpty())
                continue;
            if (!canEject(stackInSlot))
                continue;
            slotToEject = slot;
            break;
        }
        if (slotToEject == -1)
            return; // 没有可吹出的物品

        float speed = Math.abs(getSpeed());
        if (speed <= 0)
            return; // 无转速不喷射

        // 发射间隔（tick）：转速越快间隔越短；下限 2 tick，避免过密
        int interval = Math.max(2, (int) Math.round(100f / speed));
        if (ejectCooldown > 0) {
            ejectCooldown--;
            return;
        }
        ejectCooldown = interval;

        // ---- 数量判定（与 SmartChuteBlockEntity.getExtractionAmount/getExtractionMode 同构） ----
        int stackCount = inventory.getStackInSlot(slotToEject).getCount();
        boolean countVisible = filtering != null && filtering.isCountVisible();
        boolean anyAmount = countVisible && filtering.anyAmount();
        int amount = countVisible && !anyAmount ? filtering.getAmount() : 64;
        boolean exactly = countVisible && !anyAmount && !filtering.upTo;

        if (exactly) {
            if (stackCount != amount)
                return;   // 严格模式：堆叠数量必须正好等于配置值
        } else {
            if (stackCount > amount)
                return;   // 小于等于模式：堆叠数量必须 ≤ 配置值
        }

        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);

        // 吹出整组
        ItemStack stack = inventory.extractItem(slotToEject, stackCount, false);

        // 正前方是容器：直接输送
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, frontPos, facing.getOpposite());
        if (target != null) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack, false);
            if (!remainder.isEmpty()) {
                // 送不回去（容器满）：退回吸尘器
                inventory.insertItem(slotToEject, remainder, false);
            }
            // 与容器直接交换成功：全程没有东西掉进世界里
            if (stack.getCount() > remainder.getCount() && level instanceof ServerLevel serverLevel) {
                YunxianAdvancements.awardNear(serverLevel, worldPosition,
                        YunxianAdvancements.MACHINE_CLEANER_SWAP);
            }
            setChanged();
            return;
        }

        // 前方不是容器：生成掉落物（模仿智能钻头，无任何初速度）
        Vec3 spawnPos = VecHelper.offsetRandomly(
                VecHelper.getCenterOf(frontPos),
                level.random,
                .125f
        );
        ItemEntity itemEntity = new ItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, stack);
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(Vec3.ZERO);   // 显式归零，无任何初速
        level.addFreshEntity(itemEntity);

        setChanged();
    }

    /**
     * 尝试把一个掉落物吸入容器。
     * 依次尝试放入每个槽位；放不下的部分留在掉落物中（不吞物品）。
     * 物品仍会瞬间进库存；同时记录一条"吸入事件"，让客户端播放幻影飞入动画。
     */
    private void suckItem(ItemEntity itemEntity) {
        // 过滤拦截：不匹配的物品不吸入
        if (!canSuck(itemEntity.getItem()))
            return;

        ItemStack remaining = itemEntity.getItem().copy();

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
            if (remaining.isEmpty())
                break;
        }

        int inserted = itemEntity.getItem().getCount() - remaining.getCount();
        if (inserted <= 0)
            return;

        // 吸走前先备份一份 + 记录起始位置（用于幻影动画）
        ItemStack phantomStack = itemEntity.getItem().copy();
        Vec3 origin = itemEntity.position();

        itemEntity.getItem().shrink(inserted);
        if (itemEntity.getItem().isEmpty()) {
            itemEntity.discard();
        }

        // 服务端：把吸入事件随下一次 sendData 同步给客户端
        if (level != null && !level.isClientSide) {
            phantomStack.setCount(inserted);
            pendingPhantoms.add(new SuckPhantom(phantomStack, origin));
            if (pendingPhantoms.size() > 8)
                pendingPhantoms.remove(0);
            sendData();
        }

        setChanged();
    }

    /** GUI 风向按钮调用：切换一次风向（正转↔反转） */
    public void toggleDirection() {
        if (filtering != null)
            filtering.toggleDirection();
    }

    /** 该命中点是否落在侧面过滤/方向配置栏位（值框）上 */
    public boolean isHitOnConfigSlot(Vec3 hit) {
        return filtering != null && filtering.testHit(hit);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 先输出 KineticBlockEntity 自带的 Kinetics + Stress Impact（机械动力同款面板）
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 气流方向：吹气 / 吸气（按配置的模式显示，不依赖是否有动力）
        boolean pulling = isPulling();
        CreateLang.builder()
                .add(Component.translatable(pulling
                                ? "create_crystal_industry.mechanical_cleaner.direction.reversed"
                                : "create_crystal_industry.mechanical_cleaner.direction.normal")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }
}