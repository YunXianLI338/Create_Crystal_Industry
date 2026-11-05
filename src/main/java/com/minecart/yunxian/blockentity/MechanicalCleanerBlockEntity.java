package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.behaviour.MechanicalCleanerFilterBehaviour;
import com.minecart.yunxian.menu.MechanicalCleanerMenu;
import com.minecart.yunxian.behaviour.MechanicalCleanerValueBoxTransform;
import com.minecart.yunxian.block.MechanicalCleanerBlock;
import com.minecart.yunxian.integration.deployerlib.NonItemPackageCompat;
import com.minecart.yunxian.integration.fluidlogistics.FluidPackageCompat;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CKinetics;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.Lang;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class MechanicalCleanerBlockEntity extends KineticBlockEntity
        implements MenuProvider, IAirCurrentSource {

    /** 容器容量：27 格 */
    public static final int INVENTORY_SIZE = 27;

    /**
     * 满仓提示的翻译键，<b>不含命名空间</b>（LangBuilder 会自己补 {@code Yunxian.MODID}）。
     * 资源包里对应的条目见 {@code assets/create_crystal_industry/lang/}。
     */
    private static final String FULL_HINT_KEY = "hint.full_mechanical_cleaner";

    /** 目标气流长度最小值（格） */
    public static final int SUCK_RANGE_MIN = 1;

    /** 目标气流长度最大值（格）：与鼓风机 256 转速下的最大距离一致（fanPushDistance 默认 20） */
    public static final int SUCK_RANGE_MAX = 20;

    /**
     * 无应力时的被动吸取距离（格）：固定一格，<b>不受 GUI 滑块影响</b>。
     * 滑块配置的是"风场要多长"（见 {@link #getMaxDistance()}），没有风就没有风场可言；
     * 无应力时它只是台摆在传送带尽头的普通吸尘口，只捡正前方紧贴的那一格。
     */
    public static final int PASSIVE_SUCK_RANGE = 1;

    /** 目标气流长度（格）：GUI 调节的数值，实际长度还会受转速上限约束 */
    private int suckRange = SUCK_RANGE_MIN;

    /** 吸尘器内部库存 */
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);

    /**
     * 库存已满：27 格全部塞满，再吸不进任何物品。
     * 判定只在服务端做（客户端不持有库存，库存不走客户端包），结果随客户端包同步下去，
     * 供 {@link #getSpeed()}（风力与扇叶渲染）和护目镜提示读取——两边读的是同一个值。
     */
    private boolean inventoryFull;

    /**
     * 侧面的过滤 / 方向配置槽（吹出数量与模式也由它承载，见 FilteringBehaviour 的 showCount 机制）。
     * 数量滑块只在该配置真正生效时显示：吹出模式恒显示；吸气模式仅在正前方有容器时显示，
     * 敞开吸气（无容器）时隐藏，只保留过滤功能（见 {@link #shouldShowCount()}）。
     */
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

    /** 吸气时从正前方容器直取的冷却：与吹出侧共用同一套"间隔 = max(2, 100/转速)"的节奏 */
    private int suckCooldown;

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
     * <p>
     * 转速为 0（无动力 / 红石锁 / 满仓停摆）时一律返回 0：这台机器此刻不工作，就没有任何风场可言。
     * <b>这一条不能省</b>——{@link IAirCurrentSource} 的默认量是
     * {@code Mth.lerp(distanceFactor, 3, max)}，距离因子为 0 时仍会留下 3 格；轴线风场那边
     * {@code AirCurrent.rebuild()} 自己会把 {@code maxDistance} 归零，可喷嘴场是拿<b>本方法</b>当立方体场的边长
     * （{@link #nozzleFieldHalfExtent()} 再取一半，见 {@link #getNozzleCoverage()}），不归零就会在满仓停摆后
     * 仍留一个边长 3 的立方体风场，掉落物接管（{@link #tryAbsorbDrop}）、泄压舱口复查都会把这份"残余风场"当真。
     */
    @Override
    public float getMaxDistance() {
        if (getSpeed() == 0)
            return 0;
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
        }).showCountWhen(this::shouldShowCount);   // 数量配置只在生效时显示（智能溜槽同款机制）
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

    /**
     * 值框"数量"滑块是否显示（两端都会调用：客户端画标签、服务端判 {@code acceptsValueSettings}）。
     * <p>
     * 数量配置只服务两处 —— 吹出模式的筛选条件（{@link #ejectItems()}）、
     * 以及吸气时从正前方容器直抽的取货批量（{@link #collectItemsFromContainer()}）：
     * "≤" 是每次上限，"=" 是从单格整取的一整批。
     * 吹出模式恒显示；吸气模式仅当正前方真有容器时才显示，
     * 敞开吸气（无容器）时该配置永远读不到，隐藏滑块以免误导玩家。
     */
    private boolean shouldShowCount() {
        return !isPulling() || getFrontContainer() != null;
    }

    /**
     * 吸尘器正前方（FACING 方向）区块面向自身的那个面所暴露的容器；没有则返回 {@code null}。
     * 吸气取数、吹出直送、以及数量滑块的显隐都用它，保证判定口径一致。
     * 能力在客户端同样注册（无 dist 门控），故两端结果一致。
     */
    private IItemHandler getFrontContainer() {
        if (level == null)
            return null;
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        return level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(facing), facing.getOpposite());
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
            // 先落满仓标志：rebuildAirBounds 会问 getSpeed()，它读的就是这个值
            inventoryFull = compound.getBoolean("InventoryFull");
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
        // 满仓标志不入档：它是库存的派生状态，读档后立刻重算，免得第一 tick 之前读到旧值
        inventoryFull = isInventoryFull();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("SuckRange", suckRange);
        if (clientPacket) {
            // 满仓标志：客户端靠它冻结扇叶、清空风场、给出护目镜提示
            compound.putBoolean("InventoryFull", inventoryFull);
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

    /**
     * 转速汇报：两种情况下汇报 0，表现都是"无风、扇叶冻结"。
     * <ul>
     *   <li>红石锁定：彻底停止；</li>
     *   <li>满仓停摆（见 {@link #isStalled()}）：吸不动了，也就不转了。</li>
     * </ul>
     * 两者都不影响动能网络本身与传动杆渲染（见 {@link #getTrueSpeed()}）。
     */
    @Override
    public float getSpeed() {
        if (isRedstoneLocked() || isStalled())
            return 0;
        return super.getSpeed();
    }

    /** 真实网络转速：不受红石锁与满仓停摆影响，供传动杆渲染使用 */
    public float getTrueSpeed() {
        return super.getSpeed();
    }

    // ==================== 满仓停摆 ====================

    /**
     * 库存是否已经塞满：27 格全部达到各自的最大堆叠数，此时再吸不进任何物品。
     * 与过滤无关——过滤决定的是"吸什么"，不是"有没有地方放"。
     */
    private boolean isInventoryFull() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize())
                return false;
        }
        return true;
    }

    /**
     * 是否因满仓而停摆：只在吸气模式下成立。
     * 吹出模式不能被自己锁死——它正靠把库存吹出去腾地方，满仓时照常工作。
     */
    public boolean isStalled() {
        return isPulling() && inventoryFull;
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

        // 满仓判定只在服务端做：客户端没有库存，结果靠下面的客户端包同步下去。
        // 翻转时顺手把气流标记为待重建——既让本侧的风场跟着收放，也借那次 sendData
        // 把标志发给客户端（扇叶冻结、风场清空、护目镜提示都靠它）。
        if (server) {
            boolean full = isInventoryFull();
            if (full != inventoryFull) {
                inventoryFull = full;
                updateAirFlow = true;
            }
        }

        // 周期性检查阻挡：每 fanBlockCheckRate(30) tick 重建一次气流
        if (server && airCurrentUpdateCooldown-- <= 0) {
            airCurrentUpdateCooldown = AllConfigs.server().kinetics.fanBlockCheckRate.get();
            updateAirFlow = true;
        }

        if (updateAirFlow) {
            updateAirFlow = false;
            rebuildAirBounds();
            if (server) {
                // 气流刚重建过，顺手对一次喷嘴场登记表的账（见 NozzleFieldIndex）
                syncNozzleFieldIndex();
            }
            sendData();
        }

        boolean hasPower = getTrueSpeed() != 0;   // 是否有动力输入（网络转速，不受红石锁与满仓影响）
        boolean locked = isRedstoneLocked();      // 红石锁：完全停止
        boolean stalled = isStalled();            // 满仓停摆：吸不动了，同样不转

        // 有动力、未锁定、未停摆：驱动气流
        if (hasPower && !locked && !stalled) {
            // 每 5 tick 刷新气流中的实体列表
            if (entitySearchCooldown-- <= 0) {
                entitySearchCooldown = 5;
                airCurrent.findEntities();
            }
            airCurrent.tick();
        }

        if (server) {
            if (!locked && !stalled) {   // 红石锁 / 满仓停摆：不吸不吹
                if (isPulling()) {
                    if (hasPower) {
                        // 有动力：气流范围 + 喷嘴覆盖范围 + 面前容器直吸 + 内部凹陷吸取
                        collectItemsInFlow();
                        collectItemsFromNozzle();
                        collectItemsFromContainer();
                        collectItemsInInterior();
                    } else {
                        // 无动力：不产生风，只捡正前方紧贴的一格（PASSIVE_SUCK_RANGE，不受 GUI 滑块影响）；
                        // 容器直吸不生效；凹陷内仍可吸
                        collectItemsPassive();
                        collectItemsInInterior();
                    }
                } else if (hasPower) {
                    // 吹出模式需要动力
                    ejectItems();
                }
            }

            // 含水：库存里的包裹一律拆开。放在收取之后——这样本 tick 刚吸进来的包裹当场就拆掉，
            // 不用等下一 tick。也不看锁定/停摆：起作用的是"水"，跟有没有风没关系
            if (isWaterlogged())
                unpackWaterloggedPackages();
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

        suckAllIn(flowBounds, null);
    }

    /**
     * 扫一片范围，把"能被吸走的东西"收进来。两类实体分开扫：
     * 常见的是 {@link ItemEntity}，另一类是 Create 的包裹 {@link PackageEntity}
     * ——它是 {@code LivingEntity} 而不是 ItemEntity，所以以前那些只扫 ItemEntity 的循环
     * 根本看不见包裹，包裹就永远吸不进来（见 {@link #suckPackage}）。
     *
     * @param extra 额外的逐实体条件（喷嘴那条要用：超出半边长、没有视线的都不吸），null = 全收
     */
    private void suckAllIn(AABB area, Predicate<Entity> extra) {
        if (level == null)
            return;
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (extra == null || extra.test(itemEntity))
                suckItem(itemEntity);
        }
        for (PackageEntity packageEntity : level.getEntitiesOfClass(PackageEntity.class, area)) {
            if (extra == null || extra.test(packageEntity))
                suckPackage(packageEntity);
        }
    }

    /**
     * 无动力时的被动吸取：不依赖 AirCurrent 的风力，只捡正前方紧贴的那<b>一格</b>
     * （{@link #PASSIVE_SUCK_RANGE}），把掉落物直接收入容器。
     * <p>
     * 距离恒为一格，<b>刻意不读 {@link #getSuckRange()}</b>：那个值是给风场用的长度配额
     * （见 {@link #getMaxDistance()}）。无应力时压根没有风场，读它就成了"没有风却能隔 20 格吸东西"，
     * 滑块也就变成了绕过应力的后门。要拉长距离就得通应力，这是这台机器的设计前提。
     * <p>
     * 与有动力时一致：过滤生效；完整方块（isSuffocating）阻挡。
     */
    private void collectItemsPassive() {
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);

        // 逐格扫描，遇到完整方块截断（与有动力时的阻挡口径一致）
        int effective = 0;
        for (int i = 1; i <= PASSIVE_SUCK_RANGE; i++) {
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

        suckAllIn(area, null);
    }

    /**
     * 收集掉进方块自身凹陷内的掉落物。
     * 由于凹陷是方块内部唯一的开放空间（其余实心），扫描整块 AABB 是安全的。
     * 只供吸入模式调用；吹出模式下凹陷内的物品应由风力吹出，不应被吸回。
     */
    private void collectItemsInInterior() {
        AABB interior = new AABB(worldPosition);
        suckAllIn(interior, null);
    }

    /**
     * 分散网（喷嘴）兼容：吸尘器正前方挂有喷嘴时，
     * 喷嘴把气流分散成以自身为中心的<b>立方体</b>风场：<b>边长</b> = 吸尘器气流长度
     * {@link #getMaxDistance()}（与 {@code NozzleBlockEntity.calcRange()} 同源），
     * <b>半边长是它的一半</b>。吸入模式下，该场内、且有视线的掉落物直接收入容器。
     * <p>
     * 尺寸照抄 Create：{@code NozzleBlockEntity.lazyTick} 挑实体用
     * {@code new AABB(center, center).inflate(range / 2f)}，吸入时撒粒子也是
     * {@code VecHelper.offsetRandomly(center, random, range / 2)}——<b>场从喷嘴往各方向只铺出 range/2 格</b>。
     * 把 range 直接当半边长会胖一倍（每轴 2×、体积 8×），那正是"网罩外面老远的东西也被吸走"的来源。
     * <p>
     * 形状用立方体而不是球：{@link #withinCube} 比各轴差值的最大值，与泄压舱口判
     * "我这个舱口在不在场里"的 {@code PressureReliefHatchBlockEntity.cubeDistance} 是同一口径，
     * 两处都用半边长，才不会出现"舱口认为有风、吸尘器认为收不到"的裂缝。
     */
    private void collectItemsFromNozzle() {
        AABB coverage = getNozzleCoverage();
        if (coverage == null)
            return;

        BlockPos nozzlePos = getNozzlePos();
        Vec3 center = VecHelper.getCenterOf(nozzlePos);
        float radius = nozzleFieldHalfExtent();

        // 顺手登记给"掉落物直接入库存"反查（见 CleanerDropAbsorption / NozzleFieldIndex）：
        // 这里本来就要算一遍覆盖，登记不额外多一次查询
        NozzleFieldIndex.add(level, worldPosition, nozzlePos);

        // 与喷嘴自身行为一致：超出半边长或无视线的不吸（掉落物与包裹同一套条件）
        suckAllIn(coverage, entity ->
                withinCube(entity.position(), center, radius)
                        && lineOfSightToNozzle(entity.position(), CollisionContext.of(entity), nozzlePos));
    }

    /**
     * 让 {@link NozzleFieldIndex} 与"这台机器现在到底有没有喷嘴场"保持一致。
     * <p>
     * 为什么还要在这里对账，而不是只靠 {@link #collectItemsFromNozzle} 里那句登记：那句话只在
     * "有动力 + 在吸入 + 未锁未停摆"时才会执行，所以它管得住"登记"，管不住"注销"——
     * 玩家拆掉分散网、把机器切成吹出模式、断掉动力，都不会再走到那句登记。
     * 气流本来就每 {@code fanBlockCheckRate}(30) tick 重建一次，在那里对一次账最省。
     * <p>
     * 判的是"有没有场"（在吸入 + 有动力 + 正前方有喷嘴），不判"这一 tick 收不收得下"：
     * 满仓停摆、红石锁、过滤不过都只是暂时收不下，下一 tick 可能就好了，
     * 让登记表跟着这些状态抖动没有意义——那些条件 {@code tryAbsorbDrop} 收的时候自己会判。
     * 所以这里问 {@link #hasNozzleField()} 而不是 {@link #getNozzleCoverage()}：后者的半边长是
     * {@link #getMaxDistance()} 的一半，转速归零时会变成 0，拿它判就会跟着停摆来回增删。
     */
    private void syncNozzleFieldIndex() {
        if (hasNozzleField()) {
            NozzleFieldIndex.add(level, worldPosition, getNozzlePos());
        } else {
            NozzleFieldIndex.remove(level, worldPosition);
        }
    }

    /**
     * 这台机器"结构上"有没有喷嘴场：有动力 + 在吸入 + 正前方那一格是分散网。
     * <p>
     * 刻意不看红石锁、满仓停摆与过滤——那些只是"这一 tick 收不下"，
     * 真正的收与不收由 {@link #tryAbsorbDrop} 里的 {@code getSpeed()} 等条件把关。
     */
    private boolean hasNozzleField() {
        if (getTrueSpeed() == 0 || !isPulling())
            return false;
        return level.getBlockEntity(getNozzlePos()) instanceof NozzleBlockEntity;
    }

    /**
     * 两个点之间的"立方体距离"——各轴差值的最大值，即在这颗立方体风场里比的是最远的那条轴。
     * 与 {@code NozzleBlockEntity} 的 {@code inflate(range / 2f)} 外接盒、以及泄压舱口的
     * {@code cubeDistance} 逐字一致——比的都是<b>半边长</b>，不是风场长度。
     */
    private static boolean withinCube(Vec3 from, Vec3 center, float radius) {
        return Math.max(Math.abs(from.x - center.x),
                Math.max(Math.abs(from.y - center.y), Math.abs(from.z - center.z))) <= radius;
    }

    /**
     * 分散网（喷嘴）场的半边长：Create 把风源的气流长度（{@link #getMaxDistance()}）
     * 当作那颗立方体场的<b>边长</b>（{@code NozzleBlockEntity.lazyTick} 挑实体用
     * {@code inflate(range / 2f)}，吸风时撒粒子用 {@code offsetRandomly(center, random, range / 2)}），
     * 所以场从喷嘴往各方向只铺出它的一半。
     * <p>
     * {@link #getNozzleCoverage()}、{@link #collectItemsFromNozzle()} 与
     * {@link #tryAbsorbDrop} 共用这一份口径，改一处就得三处一起改。
     */
    private float nozzleFieldHalfExtent() {
        return getMaxDistance() * 0.5F;
    }

    /** 正前方（FACING 方向）那一格：分散网挂在这里，没有喷嘴时也是"面前容器"那一格 */
    public BlockPos getNozzlePos() {
        return worldPosition.relative(getBlockState().getValue(MechanicalCleanerBlock.FACING));
    }

    /**
     * 分散网（喷嘴）覆盖的外接盒：正前方挂着喷嘴且气流长度 &gt; 0 时返回
     * "以喷嘴为中心、半边长 = {@link #nozzleFieldHalfExtent()} 的立方体"的外接 AABB，否则 {@code null}。
     * <p>
     * 只给"掉落物直接入库存"那条路做<b>粗筛</b>用（{@link #tryAbsorbDrop} 再自己补立方体距离与视线判定）；
     * 按 tick 收实体那条路（{@link #collectItemsFromNozzle}）直接用同一份盒。
     * <p>
     * 半边长随转速伸缩，归零（无动力 / 红石锁 / 满仓停摆）时返回 {@code null}——机器停了就没有风场，
     * 这一步由 {@link #getMaxDistance()} 负责。
     */
    public AABB getNozzleCoverage() {
        float halfExtent = nozzleFieldHalfExtent();
        if (airCurrent == null || halfExtent <= 0)
            return null;
        BlockPos nozzlePos = getNozzlePos();
        if (!(level.getBlockEntity(nozzlePos) instanceof NozzleBlockEntity))
            return null;
        Vec3 center = VecHelper.getCenterOf(nozzlePos);
        return new AABB(center, center).inflate(halfExtent);
    }

    /**
     * 与 NozzleBlockEntity.canSee 相同：从给定位置到喷嘴中心做碰撞射线，
     * 命中喷嘴自身才视为可见（风能真正覆盖到）。
     * <p>
     * 碰撞上下文由调用方给，而不是直接传 Entity：{@code ClipContext} 接 Entity 的那个构造器
     * 内部会走 {@code CollisionContext.of(entity)}，而它<b>不接受 null</b>
     * （{@code EntityCollisionContext} 构造器会立刻对 entity 解引用 → NPE 崩服务端）。
     * 所以有实体的那条路传 {@link CollisionContext#of(Entity)}，
     * 掉落物直接入库存那条路（{@link CleanerDropAbsorption}，物品实体还没进世界）传
     * {@link CollisionContext#empty()}。
     */
    boolean lineOfSightToNozzle(Vec3 from, CollisionContext collisionContext, BlockPos nozzlePos) {
        ClipContext context = new ClipContext(from, VecHelper.getCenterOf(nozzlePos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collisionContext);
        BlockPos hitPos = level.clip(context).getBlockPos();
        if (nozzlePos.equals(hitPos))
            return true;
        // 起点所在的那一格不算遮挡（前提是它挡不住整格）：物品经常就落在这种"半格"方块里
        // （泄压舱口把落点推到网罩碰撞箱外、仍留在本格内；栅栏、台阶同理），它的碰撞箱是
        // "装物品的容器"，不是隔在两物之间的墙。从格内往外看，射线先撞到的必然是它自己，
        // 按原判据会永远判成"看不见喷嘴"——那样掉落物接管与每 tick 收取会一致地漏掉这些物品。
        // 整格实心的方块不在此列：物品真埋进墙里，本来就不该隔着墙被吸走。
        BlockPos startPos = BlockPos.containing(from);
        return startPos.equals(hitPos) && !level.getBlockState(hitPos).isCollisionShapeFullBlock(level, hitPos);
    }

    /**
     * 吸入模式：识别正前方（FACING 方向）的容器，按过滤直接把其中物品吸进自身库存，
     * 不产生掉落物实体。与吹出模式"面前容器直送"对称。
     * 仅在"有动力"时由 tick 调用（被动模式不生效）。
     * <p>
     * 数量档照智能溜槽的结构来（{@code SmartChuteBlockEntity#getExtractionAmount} /
     * {@code #getExtractionMode} 先定"一次搬多少、按哪种口径搬"，再走同一条搬运流程）：
     * <ul>
     *   <li>"≤"（{@code filtering.upTo}）：一次最多搬 N 个，容器里有多少搬多少，可以跨格；</li>
     *   <li>"="：容器里凑够 N 个才搬，且只搬正好 N 个。散在几格里的同种物品可以合起来凑
     *       （16 + 16 取；32 + 1 只取那 32 个），零头不会被顺带吸空。</li>
     * </ul>
     * 整批的量始终卡在 N：Create 的 EXACTLY 逐格按"还差多少"截断，凑满就停手，
     * 所以 32 + 1 这种组合只会取走 32 个，不会把零头那一格的 1 个也顺带吞掉。
     * <p>
     * 数量未显示或设为"任意"（anyAmount）时按"一次最多 64 个"处理（与溜槽同口径）。
     * <p>
     * 节奏跟吹出侧同一套（{@link #transferInterval()}）：每次搬完按"间隔 = max(2, round(100 / 转速))"
     * 上锁，转速越快越密——否则容器直取是每 tick 一遍、固定 20 批/秒，转速高低看不出来。
     */
    private void collectItemsFromContainer() {
        // 正前方没有容器就直接返回（与数量滑块显隐同一口径，见 getFrontContainer）
        IItemHandler source = getFrontContainer();
        if (source == null)
            return;

        // 取货节奏与吹出侧同款：间隔 = max(2, round(100 / 转速)) tick。
        if (suckCooldown > 0) {
            suckCooldown--;
            return;
        }
        int interval = transferInterval();

        // 一次搬多少、按哪种口径搬：与智能溜槽同一套
        int amount = getExtractionAmount();
        boolean moved = getExtractionMode() == ItemHelper.ExtractionCountMode.EXACTLY
                ? collectBatchFromContainer(source, amount)
                : collectUpToFromContainer(source, amount);
        if (!moved)
            return;

        setChanged();
        // 与容器直接交换成功：全程没有东西掉进世界里
        if (level instanceof ServerLevel serverLevel)
            YunxianAdvancements.awardNear(serverLevel, worldPosition, YunxianAdvancements.MACHINE_CLEANER_SWAP);
        // 真的搬过东西才重新上弦：容器里没得吸时就照旧每 tick 看一眼，
        // 与吹出侧"没东西可吹就不走间隔"是同一手感
        suckCooldown = interval;
    }

    /**
     * "=" 档：在容器里凑够 {@code batch} 个才搬，且只搬正好这一批 —— 零头不跟着被吞。
     * <p>
     * 取货交给 Create 的 {@link ItemHelper#extract}（EXACTLY 口径），这样散在几格里的同种物品
     * 可以合起来凑一批：16 + 16 能取，32 + 1 则只取那 32 个，剩下 1 个原样留在容器里。
     * EXACTLY 在 {@code ItemHelper.java:200-201} 用 {@code min(还差多少, 该格数量)} 逐格截断，
     * 凑满 {@code batch} 立刻停手，所以总数不会超过一批。
     * <p>
     * 先干跑一遍再真取（与溜槽 {@code ChuteBlockEntity#handleInput} 同款）：凑不够就整批不动，
     * 免得取一半又退回去空转。自身库存放不下一整批时也先不动。
     *
     * @return 真的往自身库存搬进了一批物品就返回 {@code true}（调用方据此上搬运冷却）
     */
    private boolean collectBatchFromContainer(IItemHandler source, int batch) {
        // 干跑一遍：凑不够一整批就不动（散在几格里的零头也算数）
        ItemStack preview = ItemHelper.extract(source, this::canSuck, ItemHelper.ExtractionCountMode.EXACTLY, batch, true);
        if (preview.isEmpty())
            return false;
        // 自身库存放不下一整批就先不动
        if (getMaxInsertable(preview) < preview.getCount())
            return false;

        ItemStack extracted = ItemHelper.extract(source, this::canSuck, ItemHelper.ExtractionCountMode.EXACTLY, batch, false);
        if (extracted.isEmpty())
            return false;
        // 保险：真取的量多过一批时，多出来的原样还回容器（正常不会发生，防止任何实现多抽）
        if (extracted.getCount() > batch) {
            ItemStack excess = extracted.copyWithCount(extracted.getCount() - batch);
            extracted.setCount(batch);
            giveBackToContainer(source, excess);
        }
        deliverToInventory(extracted);
        return true;
    }

    /**
     * "≤" 档：一次最多搬 {@code limit} 个，容器里有多少搬多少（可跨格）。
     * 与溜槽 {@code ExtractionCountMode.UPTO} 同口径。
     *
     * @return 真的搬过东西就返回 {@code true}
     */
    private boolean collectUpToFromContainer(IItemHandler source, int limit) {
        boolean moved = false;
        int budget = limit;
        for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
            ItemStack stackInSlot = source.getStackInSlot(slot);
            if (stackInSlot.isEmpty() || !canSuck(stackInSlot))
                continue;

            // 本次可从该槽取出的数量 = min(槽内数量, 剩余额度, 自身库存可容纳量)
            int maxExtract = Math.min(Math.min(stackInSlot.getCount(), budget), getMaxInsertable(stackInSlot));
            if (maxExtract <= 0)
                continue;

            ItemStack extracted = source.extractItem(slot, maxExtract, false);
            if (extracted.isEmpty())
                continue;

            budget -= extracted.getCount();
            deliverToInventory(extracted);
            moved = true;
        }
        return moved;
    }

    /** 把从容器取出的一份物品塞进自身库存；塞不下的部分退回容器，绝不凭空消失 */
    private void deliverToInventory(ItemStack stack) {
        ItemStack remainder = insertAll(stack);
        if (!remainder.isEmpty())
            giveBackToContainer(getFrontContainer(), remainder);
    }

    /** 把物品还回容器；容器塞不下就掉在机器正前方，而不是直接丢掉 */
    private void giveBackToContainer(IItemHandler source, ItemStack stack) {
        ItemStack remainder = stack;
        if (source != null)
            remainder = ItemHandlerHelper.insertItemStacked(source, remainder, false);
        if (remainder.isEmpty() || level == null)
            return;
        Vec3 center = VecHelper.getCenterOf(worldPosition);
        ItemEntity dropped = new ItemEntity(level, center.x, center.y + .5, center.z, remainder);
        dropped.setDefaultPickUpDelay();
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);
    }

    /**
     * 搬运节奏（tick）：间隔 = max(2, round(100 / 转速))，转速越快越密。
     * <p>
     * 吹出侧原本就是这套算法，智能泄压舱口的 {@code ventInterval} 也是照抄它
     * （那边注释写的正是"与动力吸尘器吹出同款"）。容器直取以前没用上它，
     * 于是吸货速度跟转速无关，这里统一成同一个口径。
     * <p>
     * 无转速时返回下限 2：两个方向本来都只在有动力时才搬东西，走到这里只可能是
     * 应力过载那类 {@code getSpeed()} 归零的边缘情况，给个下限免得除零。
     */
    private int transferInterval() {
        float speed = Math.abs(getSpeed());
        if (speed <= 0)
            return 2;
        return Math.max(2, Math.round(100f / speed));
    }

    /**
     * 一次搬多少：数量可见且不是"任意数量"时就是面板上那个数，否则 64。
     * 与 {@code SmartChuteBlockEntity#getExtractionAmount} 同口径。
     */
    private int getExtractionAmount() {
        if (filtering == null || !filtering.isCountVisible() || filtering.anyAmount())
            return 64;
        return filtering.getAmount();
    }

    /**
     * 按哪种口径搬：数量可见、不是"任意数量"、且选的是"="时按"整批"，其余按"最多"。
     * 与 {@code SmartChuteBlockEntity#getExtractionMode} 同口径，吸气与吹出共用。
     */
    private ItemHelper.ExtractionCountMode getExtractionMode() {
        if (filtering == null || !filtering.isCountVisible() || filtering.anyAmount())
            return ItemHelper.ExtractionCountMode.UPTO;
        return filtering.upTo ? ItemHelper.ExtractionCountMode.UPTO : ItemHelper.ExtractionCountMode.EXACTLY;
    }

    // ==================== 含水解包 ====================

    /**
     * 含水时把库存里的包裹当场拆开：内容物倒进库存，包装消失。
     * <p>
     * 用的是 Create 自己那对小工具——{@link PackageItem#getContents(ItemStack)} 按 9 格读出内容物、
     * {@link ItemHelper#getNonEmptyStacks} 滤掉空格，与打包机拆包走的是同一套口径。
     * 包装本身按 Create 的惯例直接吃掉（打包机拆完也不还纸板）；<b>空包裹也一并销毁</b>——
     * 里面没东西可留，而且空包裹本来是自动化运不走的死物。
     * <p>
     * 例外是两类<b>装的不是物品</b>的包裹，它们照常吸得进来，只是含水也不拆，原样躺在槽里：
     * 一是「流体包裹」那种碰水不会自己拆开的铜质包裹（{@link FluidPackageCompat}），
     * 二是 deployer 库那套非物品库存的包裹，例如 repackaged 的 {@code copper_bottle} / {@code brass_battery}
     * （{@link NonItemPackageCompat}）。两者按物品读内容物都是空的，走"空包裹销毁"就等于把里面的流体/能量整包蒸发。
     * <p>
     * 有内容物时，只在"整个包裹都放得下"时才拆：放不下就整包留着，绝不拆到一半把塞不下的部分吞掉
     * （包裹的地址存在物品组件里，拆一半没法把剩下的重新包回去）。
     * <p>
     * 含水就有了这一条以后，含水吸尘器顺带成了拆包口：吸进来的包裹变成散件，
     * 散件是能被漏斗之类正常运走的——包裹本身因为
     * {@code PackageItem#canFitInsideContainerItems()} 为 false，是运不走的。
     */
    private void unpackWaterloggedPackages() {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack box = inventory.getStackInSlot(slot);
            if (!PackageItem.isPackage(box))
                continue;
            // 装的不是物品的包裹（「流体包裹」的铜质包裹、deployer 系的铜瓶/黄铜电池……）：
            // 按物品读内容物是空的，走下面那条"空包裹直接销毁"会把里面的流体或能量整包吞掉。
            // 吸照吸，只是不拆——原样留在槽里
            if (FluidPackageCompat.survivesWater(box) || NonItemPackageCompat.isNonItemPackage(box))
                continue;

            List<ItemStack> items = ItemHelper.getNonEmptyStacks(PackageItem.getContents(box));
            if (items.isEmpty()) {
                // 空包裹直接销毁：里面本来就没东西，纸板按上面那套规矩一起吃掉
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
                changed = true;
                continue;
            }
            if (!contentsFit(items, slot))
                continue;

            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            for (ItemStack item : items) {
                insertAll(item.copy());
            }
            changed = true;
        }
        if (changed) {
            setChanged();
            sendData();   // 一轮拆完只发一次，别每拆一格发一个包
        }
    }

    /**
     * 把包装占的那一格腾空之后，这些内容物能不能全部塞进库存。
     * 拿一份库存副本试插（副本走的是同一套 {@link ItemStackHandler#insertItem} 逻辑，
     * 所以合并、每格上限这些规矩与真插入完全一致）。
     */
    private boolean contentsFit(List<ItemStack> contents, int emptiedSlot) {
        ItemStackHandler probe = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (slot != emptiedSlot)
                probe.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        for (ItemStack content : contents) {
            ItemStack remaining = content.copy();
            for (int slot = 0; slot < probe.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = probe.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty())
                return false;
        }
        return true;
    }

    /** 方块是否含水（与气流洗涤那条 mixin 读的是同一个状态位） */
    private boolean isWaterlogged() {
        return getBlockState().getValue(BlockStateProperties.WATERLOGGED);
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

        if (Math.abs(getSpeed()) <= 0)
            return; // 无转速不喷射

        // 发射间隔（tick）：转速越快间隔越短；下限 2 tick，避免过密（与吸气侧同一套）
        int interval = transferInterval();
        if (ejectCooldown > 0) {
            ejectCooldown--;
            return;
        }
        ejectCooldown = interval;

        // ---- 数量判定（与 SmartChuteBlockEntity.getExtractionAmount/getExtractionMode 同构） ----
        int stackCount = inventory.getStackInSlot(slotToEject).getCount();
        int amount = getExtractionAmount();
        boolean exactly = getExtractionMode() == ItemHelper.ExtractionCountMode.EXACTLY;

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
        IItemHandler target = getFrontContainer();
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
     * 掉落物"直接入库存"：物品实体即将进入世界时由 {@link CleanerDropAbsorption} 调用，
     * 判定通过就把整份物品收进库存并返回 {@code true}（调用方会取消这次实体生成）。
     * <p>
     * 只在"这一 tick 本来就会被收走"时才接管：
     * <ol>
     *   <li>这台机器此刻真在吸：{@code getSpeed() != 0}、{@code getMaxDistance() > 0} 且
     *       {@link #isPulling()}。{@code getSpeed()} 那一条与 tick 里的
     *       {@code hasPower && !locked && !stalled} 等价——无动力、红石锁与满仓停摆都汇报 0。
     *       少了它，红石锁切换后风场边界还没重建的那几 tick 会把物品收进来，而那几 tick
     *       吸尘器其实是不吸的；满仓时同理，接管的判定一旦漏掉，就会绕开 {@link #isStalled()}
     *       把物品收进来。{@code getMaxDistance() == 0} 兜住的是"转速极低导致气流长度为 0"
     *       的边缘（此时按 tick 收实体那几条路也都直接返回）。</li>
     *   <li>物品确实落在 {@code radius} 这份覆盖里，与 tick 收实体用的两套范围逐条对上：
     *       点在 {@code airCurrent.bounds} 内（{@link #collectItemsInFlow} 的范围），
     *       或落进 {@link #getNozzleCoverage} 那个分散网立方体场内且有视线
     *       （{@link #collectItemsFromNozzle} 的范围）。</li>
     * </ol>
     * 无动力的吸尘器走不到这里（{@code getSpeed() == 0} 直接返回），所以
     * {@link #collectItemsPassive} 那条路保持原样：掉落物照常生成实体、下一 tick 再被收走。
     *
     * @param radius 调用方已经确认的那份气流长度，就是 {@link #getMaxDistance()}（喷嘴场的边长）。
     *               传进来只为省掉重复计算，判定语义以本方法为准；喷嘴场的半边长在这里取它的一半。
     *
     * <h4>不接管的情况（掉落物照常生成，随后由 {@link #suckItem} 按实际空间收走）</h4>
     * <ul>
     *   <li>该点所在的气流段没有加工催化剂（熔岩、营火等）：那一段气流会把物品洗涤、熔炼，
     *       接管就跳过了加工。留给实体流程，它被吸走时拿到的才是加工后的产物。</li>
     *   <li>过滤不通过、或库存放不下整份：放不下就整份不收——不吞物品。</li>
     * </ul>
     */
    public boolean tryAbsorbDrop(Vec3 point, ItemStack stack, float radius) {
        if (stack.isEmpty() || airCurrent == null || getSpeed() == 0 || radius <= 0 || !isPulling())
            return false;

        // 气流轴线范围 或 喷嘴分散网立方体场，命中任一即可
        // 视线用 CollisionContext.empty()：这条路上物品实体还没进世界，没有实体可传
        boolean inFlow = airCurrent.bounds.contains(point);
        if (!inFlow) {
            AABB coverage = getNozzleCoverage();
            inFlow = coverage != null && coverage.contains(point)
                    && withinCube(point, VecHelper.getCenterOf(getNozzlePos()), radius * 0.5F)
                    && lineOfSightToNozzle(point, CollisionContext.empty(), getNozzlePos());
        }
        if (!inFlow)
            return false;
        if (hasFlowProcessing(point))
            return false;

        if (!canSuck(stack))
            return false;
        if (getMaxInsertable(stack) < stack.getCount())
            return false;

        // getMaxInsertable 已确认整份放得下，这里的剩余量必然为空
        insertAll(stack.copy());
        setChanged();

        // 幻影动画照旧：接管的只是实体，客户端看到的仍是"物品飞进吸尘器"
        pendingPhantoms.add(new SuckPhantom(stack.copy(), point));
        if (pendingPhantoms.size() > 8)
            pendingPhantoms.remove(0);
        sendData();
        return true;
    }

    /** 该点所在的气流段是否有加工催化剂（熔岩、营火、含水吹气等），有则不能跳过实体 */
    private boolean hasFlowProcessing(Vec3 point) {
        float offset = (float) VecHelper.alignedDistanceToFace(point, worldPosition, getAirflowOriginSide());
        return airCurrent.getTypeAt(offset) != null;
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

    /**
     * 吸包裹。Create 的包裹掉在地上时<span>不是</span> {@code ItemEntity}：
     * {@code PackageItem#hasCustomEntity} 会把它换成 {@link PackageEntity}（一个 {@code LivingEntity}），
     * 所以上面那些只扫 {@code ItemEntity} 的循环根本看不见它——这就是"包裹吸不进来"的全部原因。
     * <p>
     * 处理方式与 {@link #suckItem} 一致，只是物品挂在实体身上而不是实体本身：过滤 → 塞库存 →
     * 把拿走的量从包裹里扣掉（扣空了就把实体收掉），并照旧记一条幻影事件播飞入动画。
     * <p>
     * <b>注意这里绕过了 Create 的一道闸。</b>{@code PackageItem#canFitInsideContainerItems()} 返回 false，
     * 意思是"包裹不该被漏斗这类自动化搬进普通容器"（正经通道是包裹港 / 蛙港）。本模组的库存是直接往
     * {@code ItemStackHandler} 里 insertItem，那条闸门管不到这儿——也就是说这个功能是<b>特意</b>做的，
     * 不是漏网。哪天不想要了，把 {@link #suckAllIn} 里扫包裹那个循环删掉即可。
     */
    private void suckPackage(PackageEntity packageEntity) {
        ItemStack box = packageEntity.getBox();
        if (box.isEmpty() || !canSuck(box))
            return;

        ItemStack remaining = insertAll(box.copy());
        int inserted = box.getCount() - remaining.getCount();
        if (inserted <= 0)
            return;

        // 吸走前先备份一份 + 记录起始位置（用于幻影动画）
        ItemStack phantomStack = box.copy();
        Vec3 origin = packageEntity.position();

        if (remaining.isEmpty())
            packageEntity.discard();
        else
            packageEntity.setBox(remaining);

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

    /**
     * 悬停信息：满仓停摆时给出提示。
     * 文案与排版都照着机械动力给"机械手"（部署器）写的那条来：金色标题 + 灰白正文。
     * <p>
     * 排版没直接调 {@code TooltipHelper.addHint}：它内部走 {@code CreateLang}，而 LangBuilder 会
     * 往 key 前面<b>再拼一个命名空间</b>（部署器传的是不带命名空间的 {@code "hint.full_deployer"}）。
     * 换成我们自己的 key 就会被拼成 {@code create.create_crystal_industry.hint...}，
     * 游戏里只会显示原始键名。这里改用 {@link Lang#builder(String)} 显式带上本模组的命名空间。
     * <p>
     * 只在"有动力、却是被自己的库存卡住"时提示：没动力时它本来就不转，
     * 这时再报一句"无法工作"只会误导玩家去找不存在的问题。
     */
    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 过载 / 转速不足由基类先行提示（与部署器同款写法）
        if (super.addToTooltip(tooltip, isPlayerSneaking))
            return true;
        if (getTrueSpeed() == 0)
            return false;
        if (!isStalled())
            return false;

        // 标题（金色）——LangBuilder 负责补上"create_crystal_industry."前缀与护目镜缩进
        Lang.builder(Yunxian.MODID)
                .translate(FULL_HINT_KEY + ".title")
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip);
        // 正文（灰白相间）："_…_"包住的部分是高亮，按行宽切好再逐行挂上去
        List<Component> lines = TooltipHelper.cutTextComponent(
                Component.translatable(Yunxian.MODID + "." + FULL_HINT_KEY),
                FontHelper.Palette.GRAY_AND_WHITE);
        for (Component line : lines)
            Lang.builder(Yunxian.MODID).add(line).forGoggles(tooltip);
        return true;
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
