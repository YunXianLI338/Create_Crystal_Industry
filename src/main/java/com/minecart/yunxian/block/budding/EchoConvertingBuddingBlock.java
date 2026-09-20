package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.budding.BuddingFamily;
import com.minecart.yunxian.blockentity.budding.EchoConvertingBuddingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 回响母岩。生长、光照门槛（要求目标格亮度为 0）与幽匿转化都来自
 * {@link BuddingFamily}，本类只负责两件与「方块状态」绑定的事：
 * <ul>
 *   <li>{@code CAN_SUMMON} 状态：必须在构造器里注册，而 {@code createBlockStateDefinition}
 *       由 {@code BlockBehaviour} 的构造器调用（早于子类字段赋值），因此无法从 family 读取，
 *       只能留在子类里无条件添加；</li>
 *   <li>玩家破坏 {@code can_summon=true} 的母岩时召唤监守者。</li>
 * </ul>
 */
public class EchoConvertingBuddingBlock extends GenericBuddingBlock implements EntityBlock {

    /**
     * 召唤监守者的检测半径（格）。与幽匿尖啸体一致：以母岩为中心 ±48 格内已有监守者则不重复召唤。
     */
    private static final double WARDEN_CHECK_RADIUS = 48.0;

    public EchoConvertingBuddingBlock(BuddingFamily family, Properties properties,
                                      Block smallBud, Block mediumBud, Block largeBud, Block cluster) {
        super(family, properties, smallBud, mediumBud, largeBud, cluster);
        // 默认 false：玩家放置的母岩不会召唤监守者；自然生成的由世界生成置为 true
        this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.CAN_SUMMON, false));
    }

    // 纯展示用 BE：不参与生长/召唤逻辑，仅支撑护目镜信息显示
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EchoConvertingBuddingBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.CAN_SUMMON);
        super.createBlockStateDefinition(builder);
    }

    /** 护目镜显示用的生长状态 */
    public enum GrowthStatus {
        /** 存在一个"空位或可进阶芽"且该格亮度为 0 */
        GROWABLE,
        /** 存在生长位，但全部被光照挡住（亮度 ≥ 阈值） */
        NEEDS_DARKNESS,
        /** 6 个面都没有任何生长位（已被晶簇/其它方块占满） */
        NO_SPACE
    }

    /**
     * 供护目镜信息（客户端安全）判断"母岩当前为何不可生长"。
     * 与父类 randomTick 的判定一致：逐面检查是否有"空位（长新芽）或朝向匹配的芽（进阶）"，
     * 再看该目标格是否满足光照要求。晶簇是终态、不可替换，因此全部长满后归入 NO_SPACE，
     * 而非误报光照不足。
     */
    public GrowthStatus getGrowthStatus(Level level, BlockPos pos) {
        boolean hasAnyValidSpot = false;

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);

            // 该面是否为有效生长位
            boolean newBudSpot = BuddingFamily.isFree(neighborState);
            boolean advancingBud = (neighborState.is(smallBud) || neighborState.is(mediumBud)
                    || neighborState.is(largeBud))
                    && neighborState.getValue(AmethystClusterBlock.FACING) == side;
            if (!newBudSpot && !advancingBud)
                continue;

            hasAnyValidSpot = true;

            // 光照要求：该格亮度低于阈值（即 0）→ 当前即可生长。
            // 走父类的判定，光照门槛将来若改动，这里只会跟着一起变
            if (canGrowAtLight(level, neighborPos))
                return GrowthStatus.GROWABLE;
        }

        return hasAnyValidSpot ? GrowthStatus.NEEDS_DARKNESS : GrowthStatus.NO_SPACE;
    }

    /**
     * 自然生成的母岩（can_summon=true）被玩家破坏时，在周围召唤一只监守者。
     * 玩家放置的母岩 can_summon=false，不会召唤。
     * 注意：NeoForge 里返回 false 表示「不破坏方块」，所以必须返回 super 的结果。
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        boolean result = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);

        if (state.getValue(BlockStateProperties.CAN_SUMMON) && level instanceof ServerLevel serverLevel
                && trySummonWarden(serverLevel, pos) && player instanceof ServerPlayer serverPlayer) {
            // 挖掉自然生成的回响母岩 = 把监守者叫醒。只有真的召出来了才算数
            YunxianAdvancements.award(serverPlayer, YunxianAdvancements.DEEP_WARDEN);
        }
        return result;
    }

    /**
     * @return 是否真的召唤出了监守者（附近已有监守者、或实体创建失败时为 false——
     *         此时没有"唤醒"可言，成就不该发）
     */
    private boolean trySummonWarden(ServerLevel level, BlockPos pos) {
        // 与幽匿尖啸体（SculkShriekerBlock）完全一致的检测方式：
        // 以母岩为中心 ±48 格内已有监守者则不重复召唤。
        AABB checkArea = new AABB(pos).inflate(WARDEN_CHECK_RADIUS);
        if (!level.getEntitiesOfClass(Warden.class, checkArea, EntitySelector.NO_SPECTATORS).isEmpty()) {
            return false;
        }

        BlockPos spawnPos = findWardenSpawnPos(level, pos);
        Warden warden = EntityType.WARDEN.create(level);
        if (warden == null) {
            return false;
        }
        warden.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        warden.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.TRIGGERED, null);
        level.addFreshEntityWithPassengers(warden);
        return true;
    }

    /**
     * 在母岩周围（±3 水平、±1 垂直）找一个空气格，要求下方是完整方块。
     */
    private static BlockPos findWardenSpawnPos(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 10; i++) {
            int x = pos.getX() + level.getRandom().nextInt(7) - 3;
            int y = pos.getY() + level.getRandom().nextInt(3) - 1;
            int z = pos.getZ() + level.getRandom().nextInt(7) - 3;
            mutablePos.set(x, y, z);
            BlockPos below = mutablePos.below();
            if (level.getBlockState(mutablePos).isAir()
                    && level.getBlockState(below).isCollisionShapeFullBlock(level, below)) {
                return mutablePos.immutable();
            }
        }
        return pos.immutable(); // 兜底：直接生成在母岩位置
    }
}
