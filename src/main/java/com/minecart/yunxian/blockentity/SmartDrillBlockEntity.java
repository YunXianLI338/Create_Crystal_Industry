package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.behaviour.SmartDrillFilterBehaviour;
import com.minecart.yunxian.behaviour.SmartDrillValueBoxTransform;
import com.minecart.yunxian.block.SmartDrillBlock;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.registry.ScriptedBlockDrops;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SmartDrillBlockEntity extends DrillBlockEntity {
    private SmartDrillFilterBehaviour filtering;

    /**
     * 服务端推送的"锁定"状态。
     * 客户端世界状态可能因"无更新的方块变化"而滞后，停转必须信任服务器。
     */
    private boolean syncedBlocked;

    /** c:budding_blocks 方块标签（data/c/tags/block/...，如存在） */
    private static final TagKey<Block> BUDDING_BLOCKS_BLOCK_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:budding_blocks"));
    /** c:budding_blocks 物品标签（data/c/tags/item/...，当前实际生效的那份） */
    private static final TagKey<Item> BUDDING_BLOCKS_ITEM_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:budding_blocks"));

    public SmartDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_DRILL.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        filtering = new SmartDrillFilterBehaviour(
                this,
                new SmartDrillValueBoxTransform()
        ).withCallback(stack -> destroyNextTick())
                .withModeCallback(mode -> destroyNextTick());
        filtering.setLabel(Component.translatable("create_crystal_industry.smart_drill.filter"));
        behaviours.add(filtering);
    }

    @Override
    protected BlockPos getBreakingPos() {
        return getBlockPos().relative(getBlockState().getValue(SmartDrillBlock.FACING));
    }

    @Override
    public boolean canBreak(BlockState stateToBreak, float blockHardness) {
        if (!super.canBreak(stateToBreak, blockHardness)) {
            return false;
        }
        return filtering == null
                || filtering.getFilter().isEmpty()
                || filtering.test(BlockHelper.getRequiredItem(stateToBreak));
    }

    /**
     * 命中 c:budding_blocks（物品标签或方块标签任一）→ 精准模式直接掉落方块自身。
     * 注意：钻头破坏的是方块状态，若只存在物品标签，必须通过方块对应的物品去匹配。
     */
    private static boolean isBuddingBlock(BlockState state) {
        if (state.is(BUDDING_BLOCKS_BLOCK_TAG)) {
            return true;
        }
        Item item = state.getBlock().asItem();
        return item != Items.AIR && new ItemStack(item).is(BUDDING_BLOCKS_ITEM_TAG);
    }

    @Override
    public void onBlockBroken(BlockState stateToBreak) {
        if (level != null) {
            // 采到的若是完整晶簇：普通挖掉给产物、精准采集给本体，两种都算"采下第一颗晶簇"
            YunxianAdvancements.onClusterHarvested(level,
                    breakingPos != null ? breakingPos : getBreakingPos(), stateToBreak);
        }
        if (filtering == null || filtering.getMode() == DrillMode.NORMAL) {
            // 脚本（KubeJS）注册的方块按我们自己的掉落规则来，而不是原版掉落表
            // （Create 的挖掘辅助自己算掉落，NeoForge 的 BlockDropsEvent 在那条路上不触发）
            if (level != null && ScriptedBlockDrops.isScripted(stateToBreak.getBlock())) {
                BlockPos target = breakingPos != null ? breakingPos : getBreakingPos();
                level.destroyBlock(target, false);
                for (ItemStack stack : ScriptedBlockDrops.dropsFor(stateToBreak, ItemStack.EMPTY)) {
                    if (!stack.isEmpty()) {
                        dropItem(target, stack);
                    }
                }
                return;
            }
            super.onBlockBroken(stateToBreak);
            return;
        }
        if (level == null) {
            return;
        }

        BlockPos target = breakingPos != null ? breakingPos : getBreakingPos();

        // 命中 c:budding_blocks 的方块不遵循原版掉落表：精准模式下先真正破坏方块，再手动掉落方块自身
        if (isBuddingBlock(stateToBreak)) {
            level.destroyBlock(target, false);                            // 真正移除方块（不产生掉落）
            dropItem(target, new ItemStack(stateToBreak.getBlock()));     // 补发方块自身掉落物
            return;
        }

        ItemStack silkTouchTool = new ItemStack(Items.NETHERITE_PICKAXE);
        Registry<Enchantment> enchantmentRegistry =
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        silkTouchTool.enchant(enchantmentRegistry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);

        BlockHelper.destroyBlockAs(level, target, null, silkTouchTool, 1f, stack -> {
            if (stack.isEmpty()) {
                return;
            }
            dropItem(target, stack);
        });

        // 精准采集模式下"完整采下方块本身"——智能钻头的招牌能力之一
        if (level instanceof ServerLevel serverLevel) {
            YunxianAdvancements.awardNear(serverLevel, target, YunxianAdvancements.MACHINE_SILK_TOUCH);
        }
    }

    /** 在目标位置生成一个无初速、正常拾取延迟的掉落物 */
    private void dropItem(BlockPos pos, ItemStack stack) {
        Vec3 dropPosition = VecHelper.offsetRandomly(
                VecHelper.getCenterOf(pos),
                level.random,
                .125f
        );
        ItemEntity itemEntity = new ItemEntity(
                level,
                dropPosition.x,
                dropPosition.y,
                dropPosition.z,
                stack
        );
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(itemEntity);
    }

    public enum DrillMode implements INamedIconOptions {
        NORMAL(AllIcons.I_TOOL_DEPLOY, "create_crystal_industry.smart_drill.mode.normal"),
        PRECISE(AllIcons.I_RESPECT_NBT, "create_crystal_industry.smart_drill.mode.precise");

        private final AllIcons icon;
        private final String translationKey;

        DrillMode(AllIcons icon, String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    /** 普通模式挖掘速度倍率 */
    private static final float NORMAL_SPEED_MULTIPLIER = 2.0f;
    /** 精准模式挖掘速度倍率 */
    private static final float PRECISE_SPEED_MULTIPLIER = 1.0f;

    /** 原版钻头破坏方块后重新判定目标的间隔（即 lazyTickRate 的默认值） */
    private static final int VANILLA_REARM_INTERVAL = 10;

    /** 已写入 lazyTickRate 的重新判定间隔，-1 表示尚未写入过 */
    private int appliedRearmInterval = -1;

    @Override
    protected float getBreakSpeed() {
        return super.getBreakSpeed() * getSpeedMultiplier();
    }

    private float getSpeedMultiplier() {
        return filtering == null || filtering.getMode() == DrillMode.NORMAL
                ? NORMAL_SPEED_MULTIPLIER
                : PRECISE_SPEED_MULTIPLIER;
    }

    /**
     * 原版钻头破坏方块后把 ticksUntilNextProgress 置为 -1，要等下一次 lazyTick（默认每 10 tick）
     * 才重新判定目标。这段空转与转速无关，挖掘越快占比越大。
     * 刷石机这类"方块被流体持续补料、钻头反复采集同一个方块"的结构里，它就是纯粹的节流：
     * 方块越软，2 倍速被吃掉得越多，甚至完全抵消（只剩破坏动画快一倍，产量却一样）。
     * 因此让重新判定间隔也按倍率缩短，使采集循环（空转 + 挖掘）整体一起减半。
     */
    private void updateRearmInterval() {
        int interval = Math.max(1, (int) (VANILLA_REARM_INTERVAL / getSpeedMultiplier()));
        if (interval == appliedRearmInterval)
            return;
        appliedRearmInterval = interval;
        setLazyTickRate(interval);
    }

    /**
     * 判断钻头当前目标是否处于"无法挖掘"的停转状态。
     * 空气/液体视为待机目标，不停转；其余按 canBreak 判断（含过滤拦截、基岩等）。
     */
    public boolean isBreakingBlocked() {
        if (level == null || filtering == null)
            return false;

        BlockPos targetPos = getBreakingPos();
        BlockState target = level.getBlockState(targetPos);
        if (target.isAir() || target.liquid())
            return false;

        return !canBreak(target, target.getDestroySpeed(level, targetPos));
    }

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(SmartDrillBlock.POWERED);
    }

    /**
     * 参考 ClutchBlockEntity 的脱开机制：
     * 无法挖掘时把转速汇报为 0，客户端冻结旋转动画，服务端跳过挖掘逻辑。
     * 客户端额外信任服务端推送的 syncedBlocked（防止无更新方块变化导致视觉不停止）。
     */
    @Override
    public float getSpeed() {
        if (isRedstoneLocked() || isBreakingBlocked() || syncedBlocked)
            return 0;
        return super.getSpeed();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        updateRearmInterval();

        boolean blocked = isBreakingBlocked() || isRedstoneLocked();

        // 清掉被拦截时的残留破坏裂纹
        if (blocked && destroyProgress != 0) {
            destroyProgress = 0;
            level.destroyBlockProgress(breakerId, breakingPos, -1);
        }

        // 状态变化时推送给客户端，让视觉立刻跟上（即使方块变化没通知客户端）
        if (blocked != syncedBlocked) {
            syncedBlocked = blocked;
            setChanged();
            sendData();
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("SyncedBlocked", syncedBlocked);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        syncedBlocked = compound.getBoolean("SyncedBlocked");
    }

    /**
     * 真实网络转速：不受过滤/红石停转影响，供传动杆渲染使用。
     */
    public float getTrueSpeed() {
        return super.getSpeed();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 保留父类（DrillBlockEntity → KineticBlockEntity）的机械动力同款信息
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 当前采集模式：普通采集 / 精准采集（整行灰色，与动力吸尘器方向提示一致）
        // 前缀独立成 key，避免污染 DrillMode 在滚动选择器里的显示文本
        DrillMode mode = filtering != null ? filtering.getMode() : DrillMode.NORMAL;

        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.smart_drill.mode_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.translatable(mode.getTranslationKey())
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }
}