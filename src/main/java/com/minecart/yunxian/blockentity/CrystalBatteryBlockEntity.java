package com.minecart.yunxian.blockentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.minecart.yunxian.battery.CrystalCapacities;
import com.minecart.yunxian.block.CrystalBatteryBlock;
import com.minecart.yunxian.block.CrystalBatteryBlock.Shape;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 水晶电池：既能存电（FE），又是一座仿机械动力流体储罐的多方块容器。
 * <p>
 * <b>晶体挂在每一格自己身上</b>：刚做出来的电池容量为 0，必须手持带晶体标签的方块
 * （{@code create_crystal_industry:battery_crystal/<档位>_capacity}）右键把晶体塞进去，
 * 那一格才开始能存电。多方块结构里每一格都要各自塞晶体，
 * 整座电池的总容量 = 各格晶体容量之和，电在整座结构里共享（对外只暴露一个 FE 接口）。
 * <p>
 * 多方块骨架直接复用 Create 的 {@link ConnectivityHandler}：它就是储罐用的那套，
 * 只要实现 {@link IMultiBlockEntityContainer}（<b>不要</b>实现它的 {@code Fluid} 子接口，
 * 那个接口会额外引发流体合并逻辑）。方块状态里的 TOP/BOTTOM/SHAPE 也与储罐同构，
 * 所以外观模型可以直接照搬。
 * <p>
 * 电量与容量不做集中存放，各格记各格的，控制器只是把同一座结构里的格子加起来算总数。
 * 好处是结构拆分/合并完全不影响数据：拆掉一格只损失那一格自己的电量，合并时天然叠加，
 * 不需要在 ConnectivityHandler 那几处回调里小心翼翼地搬运数据。
 * <p>
 * <b>类上的 {@code @SuppressWarnings("unchecked")} 只服务于下面那个协变的
 * {@link #getControllerBE()}。</b>接口里它是 {@code <T> T getControllerBE()}，重写成具体返回类型
 * 属于 unchecked 重写；javac 把这条警告算在类头上，注释写在方法上一律无效
 * （Create 自己的 FluidTankBlockEntity 就是那么写的，所以它那边一直没能真正抑制掉）。
 */
@SuppressWarnings("unchecked")
public class CrystalBatteryBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, IMultiBlockEntityContainer {

    /** 客户端同步节流：每 8 tick 最多发一次（与储罐一致） */
    private static final int SYNC_RATE = 8;

    private final IEnergyStorage energyHandler;

    private BlockPos controller;
    private BlockPos lastKnownPos;
    private boolean updateConnectivity;
    private boolean window = true;
    private int width = 1;
    private int height = 1;

    /** 本格塞入的晶体方块；EMPTY = 还没塞，容量为 0，一格电也存不了 */
    private ItemStack crystal = ItemStack.EMPTY;
    /** 本格储存的电量（FE）；整座电池的总电量 = 各格之和 */
    private int energy;

    // 仅客户端：控制器同步过来的整座汇总值，供护目镜显示（详见 write / read）
    private int syncedEnergy;
    private int syncedCapacity;

    private int syncCooldown;
    private boolean queuedSync;

    public CrystalBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_BATTERY.get(), pos, state);
        energyHandler = new AggregateEnergyHandler(this);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 电池没有行为组件
    }

    // ==================== 晶体 ====================

    /** 本格塞入的晶体方块（未塞时为空栈） */
    public ItemStack getCrystal() {
        return crystal;
    }

    /**
     * 普通右键：给整座电池里所有还空着的格子各塞一颗晶体，手上有多少塞多少，一格一颗。
     * 被右键的那一格优先保证——万一手上的晶体不够填满整座，至少点中的那格一定装上，不会让人白点。
     *
     * @return 实际塞进去的数量
     */
    public int fillEmptySlots(ItemStack stack, Player player) {
        return fill(stack, player, false);
    }

    /**
     * 潜行右键：只装被点的那一格，不碰结构里的其它空位。
     * 想给特定某格换/补晶体、又不想影响整座时用这个。
     *
     * @return 实际塞进去的数量（0 或 1）
     */
    public int fillThisSlot(ItemStack stack, Player player) {
        return fill(stack, player, true);
    }

    /**
     * 装晶体的共同实现。调用方负责判断手里拿的是不是晶体方块
     * （客户端只是预测，真正的消耗在服务端发生）。
     *
     * @param onlyThisSlot true = 只装被点的那一格，false = 补满整座结构
     */
    private int fill(ItemStack stack, Player player, boolean onlyThisSlot) {
        if (stack.isEmpty() || !CrystalCapacities.isCrystal(stack)) {
            return 0;
        }
        int installed = installCrystalInto(this, stack, player);
        if (!onlyThisSlot) {
            for (CrystalBatteryBlockEntity part : resolveController().parts()) {
                if (stack.isEmpty()) {
                    break;
                }
                if (part != this) {
                    installed += installCrystalInto(part, stack, player);
                }
            }
        }
        if (installed > 0) {
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                        0.7F, 1.1F);
            }
            notifyEnergyChanged();
        }
        return installed;
    }

    /**
     * 往一格空电池里塞一颗，并从手上扣掉一颗。改到哪一格就由哪一格标脏并发包：
     * 各格的晶体是分散存的，也只由各格自己同步给客户端（护目镜要靠它统计组成）。
     * <p>
     * 扣数量走 {@link ItemStack#consume(int, Entity)}，创造模式下不扣——与"一次放一层"
     * 那边 {@code BlockItem.place} 的消耗口径一致，也让创造模式一次点击就能装满整座电池。
     */
    private static int installCrystalInto(CrystalBatteryBlockEntity part, ItemStack stack, Player player) {
        if (stack.isEmpty() || !part.crystal.isEmpty()) {
            return 0;
        }
        part.crystal = stack.copyWithCount(1);
        stack.consume(1, player);
        part.setChanged();
        part.sendData();
        return 1;
    }

    /** 整座电池里还空着的格子数；客户端用它预测右键能不能装上 */
    public int getEmptySlotCount() {
        int empty = 0;
        for (CrystalBatteryBlockEntity part : resolveController().parts()) {
            if (part.crystal.isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    /**
     * 整座电池的晶体组成：晶体方块 → 个数，顺序 = 结构扫描顺序（先出现的排前面）。
     * 每格至多一颗，所以个数也就是"装了几格"。整座都空时返回空表。
     * <p>
     * <b>键必须是 {@link Item}，不能是 {@code ItemStack}。</b>原版的 {@code ItemStack} 没有重写
     * {@code equals}/{@code hashCode}（只提供 {@link ItemStack#isSameItemSameComponents} 这类静态工具），
     * 拿它当键就是按引用比较——每格都会落成新的一项，护目镜于是显示成一长串「可燃冰块 ×1」。
     * 物品本体在注册表里是单例，用身份相等正好等价于"同一种物品"，不依赖任何 equality 实现。
     */
    public Map<Item, Integer> getCrystalComposition() {
        Map<Item, Integer> composition = new LinkedHashMap<>();
        for (CrystalBatteryBlockEntity part : resolveController().parts()) {
            if (part.crystal.isEmpty()) {
                continue;
            }
            composition.merge(part.crystal.getItem(), 1, Integer::sum);
        }
        return composition;
    }

    // ==================== 电量 ====================

    /** 本格的电容量：由塞入的晶体决定，没塞晶体 = 0 */
    public int getBlockCapacity() {
        return CrystalCapacities.capacityOf(crystal);
    }

    /** 整座电池已存电量（FE） */
    public int getTotalEnergy() {
        int total = 0;
        for (CrystalBatteryBlockEntity part : resolveController().parts()) {
            total += part.energy;
        }
        return total;
    }

    /** 整座电池的总容量（FE）= 各格晶体容量之和 */
    public int getTotalCapacity() {
        int total = 0;
        for (CrystalBatteryBlockEntity part : resolveController().parts()) {
            total += part.getBlockCapacity();
        }
        return total;
    }

    /** 充电进度（0~1），比较器与护目镜用 */
    public float getChargeFraction() {
        int capacity = getTotalCapacity();
        return capacity <= 0 ? 0F : (float) getTotalEnergy() / capacity;
    }

    /** 整座电池的格子数 */
    public int getTotalSize() {
        return width * width * height;
    }

    public IEnergyStorage getEnergyCapability(@Nullable Direction side) {
        return energyHandler;
    }

    /** 本格所属电池的控制器；结构没形成 / 控制器所在区块没加载时退回自己 */
    private CrystalBatteryBlockEntity resolveController() {
        if (isController()) {
            return this;
        }
        CrystalBatteryBlockEntity controllerBE = getControllerBE();
        return controllerBE != null ? controllerBE : this;
    }

    /**
     * 控制器视角：整座电池的全部格子，按 结构原点 + (x, y, z) 扫描。
     * <p>
     * 原点取 {@link #getController()}（控制器自己的坐标就是原点），而不是本格坐标——
     * 非控制器格子的坐标不是原点，直接当原点扫会扫错区域。
     */
    private List<CrystalBatteryBlockEntity> parts() {
        if (level == null) {
            return List.of(this);
        }
        BlockPos origin = getController();
        List<CrystalBatteryBlockEntity> parts = new ArrayList<>(width * width * height);
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    if (level.getBlockEntity(origin.offset(xOffset, yOffset, zOffset))
                            instanceof CrystalBatteryBlockEntity part) {
                        parts.add(part);
                    }
                }
            }
        }
        return parts;
    }

    /** 从整座电池取电（供能力接口调用，可由任意一格发起） */
    private int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }
        CrystalBatteryBlockEntity controller = resolveController();
        int remaining = maxReceive;
        int accepted = 0;
        for (CrystalBatteryBlockEntity part : controller.parts()) {
            if (remaining <= 0) {
                break;
            }
            int got = part.receiveIntoBlock(remaining, simulate);
            accepted += got;
            remaining -= got;
        }
        if (!simulate && accepted > 0) {
            controller.notifyEnergyChanged();
        }
        return accepted;
    }

    /** 向整座电池取电（供能力接口调用，可由任意一格发起） */
    private int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) {
            return 0;
        }
        CrystalBatteryBlockEntity controller = resolveController();
        int remaining = maxExtract;
        int taken = 0;
        for (CrystalBatteryBlockEntity part : controller.parts()) {
            if (remaining <= 0) {
                break;
            }
            int got = part.extractFromBlock(remaining, simulate);
            taken += got;
            remaining -= got;
        }
        if (!simulate && taken > 0) {
            controller.notifyEnergyChanged();
        }
        return taken;
    }

    /**
     * 电量存在每一格自己身上，所以<b>改到哪一格就由哪一格标脏</b>：
     * 一座高塔会横跨多个区块，只标控制器的脏的话，其余区块里的电量在存档时会被丢掉。
     * 客户端同步则由 {@link #notifyEnergyChanged} 统一走控制器。
     */
    private int receiveIntoBlock(int amount, boolean simulate) {
        int space = getBlockCapacity() - energy;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, space);
        if (!simulate && accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    private int extractFromBlock(int amount, boolean simulate) {
        int taken = Math.min(amount, energy);
        if (!simulate && taken > 0) {
            energy -= taken;
            setChanged();
        }
        return taken;
    }

    /** 电量或容量变了：由控制器把整座汇总值发给客户端（护目镜显示的是汇总值，不是单格） */
    private void notifyEnergyChanged() {
        resolveController().sendData();
    }

    // ==================== 多方块骨架（与 FluidTankBlockEntity 同构） ====================

    /** 由方块在放置时直接调用，其余时机走 tick 里的延迟重建 */
    public void updateConnectivity() {
        updateConnectivity = false;
        if (level == null || level.isClientSide) {
            return;
        }
        if (!isController()) {
            return;
        }
        ConnectivityHandler.formMulti(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync) {
                sendData();
            }
        }

        if (lastKnownPos == null) {
            lastKnownPos = getBlockPos();
        } else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
            onPositionChanged();
            return;
        }

        if (updateConnectivity) {
            updateConnectivity();
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        sendData();
        if (level != null && level.isClientSide) {
            invalidateRenderBoundingBox();
        }
    }

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    @Override
    public boolean isController() {
        return controller == null || worldPosition.getX() == controller.getX()
                && worldPosition.getY() == controller.getY()
                && worldPosition.getZ() == controller.getZ();
    }

    @Override
    @SuppressWarnings("unchecked")
    public CrystalBatteryBlockEntity getControllerBE() {
        if (isController() || !hasLevel()) {
            return this;
        }
        BlockEntity blockEntity = level.getBlockEntity(controller);
        return blockEntity instanceof CrystalBatteryBlockEntity battery ? battery : null;
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    public void setController(BlockPos controller) {
        if (level != null && level.isClientSide && !isVirtual()) {
            return;
        }
        if (controller.equals(this.controller)) {
            return;
        }
        this.controller = controller;
        setChanged();
        sendData();
    }

    /**
     * 脱离多方块结构。晶体和电量都挂在每一格自己身上，不随结构变化，
     * {@code keepCrystals} 在这里没有可做的事，保留参数只为与 ConnectivityHandler 的调用约定一致。
     */
    @Override
    public void removeController(boolean keepCrystals) {
        if (level == null || level.isClientSide) {
            return;
        }
        updateConnectivity = true;
        controller = null;
        width = 1;
        height = 1;

        BlockState state = getBlockState();
        if (CrystalBatteryBlock.isBattery(state)) {
            state = state.setValue(CrystalBatteryBlock.BOTTOM, true)
                    .setValue(CrystalBatteryBlock.TOP, true)
                    .setValue(CrystalBatteryBlock.SHAPE, window ? Shape.WINDOW : Shape.PLAIN);
            level.setBlock(worldPosition, state,
                    Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
        }
        setChanged();
        sendData();
    }

    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public void notifyMultiUpdated() {
        BlockState state = getBlockState();
        if (CrystalBatteryBlock.isBattery(state)) {
            state = state.setValue(CrystalBatteryBlock.BOTTOM,
                            getController().getY() == getBlockPos().getY())
                    .setValue(CrystalBatteryBlock.TOP,
                            getController().getY() + height - 1 == getBlockPos().getY());
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        if (isController()) {
            setWindows(window);
            sendData();
        }
        setChanged();
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    public void toggleWindows() {
        CrystalBatteryBlockEntity controller = getControllerBE();
        if (controller != null) {
            controller.setWindows(!controller.window);
        }
    }

    /** 把整座结构的每一格都改成有窗 / 无窗（窗形规则与储罐的 setWindows 逐字一致） */
    public void setWindows(boolean window) {
        this.window = window;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
                    BlockState blockState = level.getBlockState(pos);
                    if (!CrystalBatteryBlock.isBattery(blockState)) {
                        continue;
                    }

                    Shape shape = Shape.PLAIN;
                    if (window) {
                        // 1 格宽：每格都有窗
                        if (width == 1) {
                            shape = Shape.WINDOW;
                        }
                        // 2 格宽：每格一个角窗
                        if (width == 2) {
                            shape = xOffset == 0
                                    ? zOffset == 0 ? Shape.WINDOW_NW : Shape.WINDOW_SW
                                    : zOffset == 0 ? Shape.WINDOW_NE : Shape.WINDOW_SE;
                        }
                        // 3 格宽：只有中间那排有窗
                        if (width == 3 && Math.abs(Math.abs(xOffset) - Math.abs(zOffset)) == 1) {
                            shape = Shape.WINDOW;
                        }
                    }

                    level.setBlock(pos, blockState.setValue(CrystalBatteryBlock.SHAPE, shape),
                            Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
                    level.getChunkSource().getLightEngine().checkBlock(pos);
                }
            }
        }
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        return longAxis == Direction.Axis.Y ? ModConfig.Common.crystalBatteryMaxHeight()
                : ModConfig.Common.crystalBatteryMaxWidth();
    }

    @Override
    public int getMaxWidth() {
        return ModConfig.Common.crystalBatteryMaxWidth();
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setExtraData(@Nullable Object data) {
        if (data instanceof Boolean windows) {
            window = windows;
        }
    }

    @Override
    @Nullable
    public Object getExtraData() {
        return window;
    }

    @Override
    public Object modifyExtraData(Object data) {
        if (data instanceof Boolean windows) {
            return windows || window;
        }
        return data;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return isController()
                ? super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1)
                : super.createRenderBoundingBox();
    }

    /** 同步节流：短时间内的多次变更合并成一次发包 */
    @Override
    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        super.sendData();
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }

    // ==================== 存档 / 同步 ====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (updateConnectivity) {
            tag.putBoolean("Uninitialized", true);
        }
        if (lastKnownPos != null) {
            tag.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        }
        if (!isController()) {
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        }
        if (isController()) {
            tag.putBoolean("Window", window);
            tag.putInt("Size", width);
            tag.putInt("Height", height);
        }

        // 落盘写本格自己的值；客户端包只认控制器，直接发整座汇总值，
        // 省得客户端为了一次护目镜显示去把每个格子的同步包都收一遍
        tag.put("Crystal", crystal.saveOptional(registries));
        if (clientPacket && isController()) {
            tag.putInt("Energy", getTotalEnergy());
            tag.putInt("Capacity", getTotalCapacity());
        } else {
            tag.putInt("Energy", energy);
        }

        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        BlockPos controllerBefore = controller;
        int prevWidth = width;
        int prevHeight = height;

        updateConnectivity = tag.contains("Uninitialized");
        lastKnownPos = tag.contains("LastKnownPos") ? NBTHelper.readBlockPos(tag, "LastKnownPos") : null;
        controller = tag.contains("Controller") ? NBTHelper.readBlockPos(tag, "Controller") : null;

        if (isController()) {
            window = tag.getBoolean("Window");
            width = tag.getInt("Size");
            height = tag.getInt("Height");
        }

        crystal = ItemStack.parseOptional(registries, tag.getCompound("Crystal"));
        if (clientPacket && isController()) {
            syncedEnergy = tag.getInt("Energy");
            syncedCapacity = tag.getInt("Capacity");
        } else {
            energy = tag.getInt("Energy");
        }

        // 客户端拿到包才能知道结构有没有变大变小，缓存过的渲染包围盒要在这一步失效。
        // 这里还必须主动顶一次方块更新让这一段区块网格重建，原因见 FluidTankBlockEntity 同处：
        // CT 连接材质的数据（CTModel.CT_PROPERTY）是在**区块网格构建时**算的，判定走
        // FluidTankCTBehaviour.connectsTo → ConnectivityHandler.isConnected，读的是方块实体的
        // controller 字段；而方块实体的同步包本身不会让网格重建（原版认为 BE 数据只影响实体渲染）。
        // 结果是网格用旧的连接关系算出 CT 索引，一旦落到 fluid_tank_top_connected 里那几个**全透明**
        // 的格子上，cutout 渲染会把整面丢掉——表现为"顶盖消失、能看穿进去"，而方块状态其实是对的。
        if (clientPacket && (!Objects.equals(controllerBefore, controller)
                || prevWidth != width || prevHeight != height)) {
            if (hasLevel()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
            }
            invalidateRenderBoundingBox();
        }
    }

    // ==================== 护目镜 ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CrystalBatteryBlockEntity controller = resolveController();
        boolean client = level != null && level.isClientSide;
        // 客户端拿不到别的格子的实时电量，用控制器同步过来的汇总值；
        // 晶体组成不用特殊处理：每格的晶体都由各格自己同步，客户端照样能自己扫出来
        int stored = client ? controller.syncedEnergy : controller.getTotalEnergy();
        int capacity = client ? controller.syncedCapacity : controller.getTotalCapacity();

        // 三条带标签的信息（晶体 / 电量 / 结构）必须给同一个缩进值才能左端对齐成一列。
        // forGoggles 的缩进值每多一级就整体右移一点（实测一级约一个空格宽），所以只要其中
        // 一行写成 1 而别的行是 0，那一行看着就像被嵌套在上一行下面——三行统一用 0，
        // 只有真正从属的行（下面的晶体条目）才给 1。
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.battery.crystal_label")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip);
        addCrystalComposition(tooltip, controller);

        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.battery.energy_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(CreateLang.number(stored).style(ChatFormatting.GOLD))
                .text(ChatFormatting.GRAY, " / ")
                .add(CreateLang.number(capacity).style(ChatFormatting.DARK_GRAY))
                .add(Component.translatable("create_crystal_industry.goggles.battery.energy_unit")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip);

        // 结构用尺寸表示（宽×宽×高）而不是格子总数：单格就是 1×1×1，没有显示的必要
        int width = controller.getWidth();
        int height = controller.getHeight();
        if (width > 1 || height > 1) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.battery.size_label")
                            .withStyle(ChatFormatting.GRAY))
                    .add(Component.translatable("create_crystal_industry.goggles.battery.size_value",
                                    width, width, height)
                            .withStyle(ChatFormatting.WHITE))
                    .forGoggles(tooltip);
        }
        return true;
    }

    /**
     * 晶体组成：<b>每种晶体各占一行</b>，形如「可燃冰块 ×10」换行「玫瑰石英块 ×5」，
     * 而不是把每种晶体在同行里逗号隔开。排版与 Create 流体容器那段一致：标签单独一行，条目再缩进。
     */
    private void addCrystalComposition(List<Component> tooltip, CrystalBatteryBlockEntity controller) {
        Map<Item, Integer> composition = controller.getCrystalComposition();
        if (composition.isEmpty()) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.battery.crystal_empty")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
            return;
        }
        composition.forEach((crystal, count) -> CreateLang.builder()
                .add(new ItemStack(crystal).getHoverName().copy().withStyle(ChatFormatting.WHITE))
                .add(Component.translatable("create_crystal_industry.goggles.battery.crystal_count", count)
                        .withStyle(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1));
    }

    /**
     * 对外暴露的 FE 接口。故意做成"每次调用现解析控制器"，而不是缓存控制器算好的实例：
     * 多方块每一次结构变化（形成 / 拆分 / 换控制器）都不需要再让方块能力缓存失效，
     * 拿到的这个 handler 永远指向当前正确的整座电池。
     */
    private static final class AggregateEnergyHandler implements IEnergyStorage {

        private final CrystalBatteryBlockEntity battery;

        AggregateEnergyHandler(CrystalBatteryBlockEntity battery) {
            this.battery = battery;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return battery.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return battery.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return battery.getTotalEnergy();
        }

        @Override
        public int getMaxEnergyStored() {
            return battery.getTotalCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
