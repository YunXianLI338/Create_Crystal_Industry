package com.minecart.yunxian.block.budding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.EchoConvertingBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.FlammableIceBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingFamily;
import com.minecart.yunxian.budding.BuddingFamily.BlockConversion;
import com.minecart.yunxian.budding.BuddingFamily.EnergyRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.integration.ae2.AE2Budding;
import com.minecart.yunxian.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所有母岩的生长引擎：行为完全由构造时传入的 {@link BuddingFamily} 决定，
 * 不再靠子类覆写。各家族的全部差异（光照门槛、含水要求、充能要求、方块转化、
 * 芽/簇方块、方块实体）都写在 {@code budding/BuddingFamilies} 那一张表里。
 * <p>
 * 唯一保留的子类是 {@link EchoConvertingBuddingBlock}：它的 {@code CAN_SUMMON} 状态
 * 必须在构造器里注册，而 {@code BlockBehaviour} 的构造器会先调用
 * {@code createBlockStateDefinition}（此时子类字段尚未赋值），无法从 family 读取。
 */
public class GenericBuddingBlock extends BuddingAmethystBlock implements EntityBlock {
    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.budding");

    private static final Direction[] DIRECTIONS = Direction.values();

    protected final BuddingFamily family;
    protected final Block smallBud;
    protected final Block mediumBud;
    protected final Block largeBud;
    protected final Block cluster;

    /** 转化规则在首次随机刻（注册表已冻结）后解析并缓存，避免每 tick 查注册表 */
    @Nullable
    private List<PreparedConversion> preparedConversions;

    public GenericBuddingBlock(BuddingFamily family, Properties properties, Block smallBud, Block mediumBud,
                               Block largeBud, Block cluster) {
        super(properties);
        this.family = family;
        this.smallBud = smallBud;
        this.mediumBud = mediumBud;
        this.largeBud = largeBud;
        this.cluster = cluster;
    }

    public BuddingFamily family() {
        return family;
    }

    // 纯展示用 BE：不 tick、不存数据，仅支撑护目镜信息。
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (family.appearance().blockEntity()) {
            case SHARED_GROWTH -> new BuddingGrowthBlockEntity(pos, state);
            case ECHO_DISPLAY -> new EchoConvertingBuddingBlockEntity(pos, state);
            case ICE_DISPLAY -> new FlammableIceBuddingBlockEntity(pos, state);
            case AE2_GRID -> newFluixBlockEntity(pos, state);
        };
    }

    /**
     * 福鲁伊克斯母岩的 BE 由 AE2 联动模块注册（{@code ModBlockEntities.FLUIX_BUDDING}）。
     * AE2 缺席时该类型为 null，而那时连方块本身都不会注册，理论上不会走到这里。
     */
    private static BlockEntity newFluixBlockEntity(BlockPos pos, BlockState state) {
        Supplier<BlockEntityType<?>> type = ModBlockEntities.FLUIX_BUDDING;
        return type == null ? null : type.get().create(pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return family.growth().buddingSignal() > 0;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return family.growth().buddingSignal();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (family.growth().rule()) {
            case STANDARD -> growStandard(level, pos, random);
            case SUBMERGED -> growSubmerged(level, pos, random);
        }

        runConversions(level, pos, random);
    }

    // ==================== 生长 ====================

    /**
     * 生长概率基数 n：每次随机刻有 1/n 的概率推进一级，档位（极慢/慢/正常/快）来自配置文件。
     */
    private int growthChance() {
        return ModConfig.Common.growthChance(family.id());
    }

    /**
     * 通用生长：母岩自身不在液体中，且 1/{@link #growthChance()} 判定通过时，
     * 随机选一个面推进相邻晶簇一级（空位长新芽）。
     * <p>
     * 注意随机数消耗顺序：液体检查在 {@code nextInt(growthChance)} 之前，
     * 调换会改变生长速率。快档（n=1）时 {@code nextInt(1)} 恒为 0，但仍消耗一次随机数——
     * 不为快档特判，各档的随机序列才一致。
     */
    private void growStandard(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getFluidState(pos).isEmpty() || random.nextInt(growthChance()) != 0) {
            return;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);

        if (!canGrowAtLight(level, neighborPos)) {
            return;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        Block nextBlock = nextStage(neighborState, side, false);
        if (nextBlock == null) {
            return;
        }

        if (!payGrowthEnergy(level, pos)) {
            return;
        }

        level.setBlockAndUpdate(neighborPos, nextBlock.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, side)
                .setValue(AmethystClusterBlock.WATERLOGGED,
                        neighborState.getFluidState().getType() == Fluids.WATER));
    }

    /**
     * 水下生长（可燃冰）：长新芽与芽体进阶都要求目标格含水；
     * 长新芽还额外要求那是水源方块（waterlogged 的芽/簇其流体状态即水，不受影响）。
     */
    private void growSubmerged(ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(growthChance()) != 0) {
            return;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getFluidState().getType() != Fluids.WATER) {
            return;
        }

        Block nextBlock = nextStage(neighborState, side, true);
        if (nextBlock == null) {
            return;
        }

        if (!payGrowthEnergy(level, pos)) {
            return;
        }

        level.setBlockAndUpdate(neighborPos, nextBlock.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, side)
                .setValue(AmethystClusterBlock.WATERLOGGED, true));
    }

    /**
     * 相邻格决定的下一个阶段；无需变化时返回 null。
     *
     * @param submerged 水下生长：空位必须同时是水源才能长新芽
     */
    @Nullable
    private Block nextStage(BlockState neighborState, Direction side, boolean submerged) {
        if (submerged ? canGrowNewBud(neighborState) : canGrowAt(neighborState)) {
            return smallBud;
        }
        if (neighborState.is(smallBud) && sameFacing(neighborState, side)) {
            return mediumBud;
        }
        if (neighborState.is(mediumBud) && sameFacing(neighborState, side)) {
            return largeBud;
        }
        if (neighborState.is(largeBud) && sameFacing(neighborState, side)) {
            return cluster;
        }
        return null;
    }

    /** 生长位是否满足该家族的光照要求（默认无要求，回响母岩要求亮度 0） */
    protected boolean canGrowAtLight(ServerLevel level, BlockPos neighborPos) {
        return family.growth().light().allows(level, neighborPos);
    }

    /**
     * 生长能量钩子：真正放置下一阶段前调用，返回 false 表示本次放弃生长。
     * 只有 {@link EnergyRequirement#AE2_GRID} 的家族会走进 AE2 联动类。
     */
    protected boolean payGrowthEnergy(ServerLevel level, BlockPos pos) {
        if (family.growth().energy() == EnergyRequirement.FREE) {
            return true;
        }
        return AE2Budding.tryConsumeGrowthEnergy(level, pos);
    }

    private static boolean canGrowAt(BlockState state) {
        return BuddingFamily.isFree(state);
    }

    /** 长新芽的条件：空位且为水源方块 */
    private static boolean canGrowNewBud(BlockState state) {
        return BuddingFamily.isFree(state)
                && state.getFluidState().getType() == Fluids.WATER
                && state.getFluidState().isSource();
    }

    private static boolean sameFacing(BlockState state, Direction side) {
        return state.getValue(AmethystClusterBlock.FACING) == side;
    }

    // ==================== 随机刻副作用（转化/传播） ====================

    private void runConversions(ServerLevel level, BlockPos pos, RandomSource random) {
        List<PreparedConversion> conversions = preparedConversions();
        for (int i = 0; i < conversions.size(); i++) {
            PreparedConversion conversion = conversions.get(i);
            // 每条规则各消耗一次随机数，即使最终没有可替换的目标
            if (random.nextInt(conversion.chance()) != 0) {
                continue;
            }
            applyConversion(level, pos, random, conversion);
        }
    }

    private void applyConversion(ServerLevel level, BlockPos pos, RandomSource random, PreparedConversion conversion) {
        int radius = conversion.radius();
        BlockPos targetPos = pos.offset(
                random.nextInt(2 * radius + 1) - radius,
                random.nextInt(2 * radius + 1) - radius,
                random.nextInt(2 * radius + 1) - radius);
        if (targetPos.equals(pos)) {
            return;
        }

        BlockState targetState = level.getBlockState(targetPos);
        for (int i = 0; i < conversion.targets().size(); i++) {
            PreparedTarget target = conversion.targets().get(i);
            if (!target.matches().test(targetState)) {
                continue;
            }
            if (conversion.energyGated() && !payGrowthEnergy(level, pos)) {
                return;
            }
            level.setBlockAndUpdate(targetPos, target.output());
            return;
        }
    }

    private List<PreparedConversion> preparedConversions() {
        if (preparedConversions == null) {
            preparedConversions = prepareConversions();
        }
        return preparedConversions;
    }

    private List<PreparedConversion> prepareConversions() {
        List<BlockConversion> conversions = family.growth().conversions();
        List<PreparedConversion> prepared = new ArrayList<>(conversions.size());

        for (int i = 0; i < conversions.size(); i++) {
            BlockConversion conversion = conversions.get(i);
            List<Replacement> replacements = conversion.replacements();
            List<PreparedTarget> targets = new ArrayList<>(replacements.size());

            for (int j = 0; j < replacements.size(); j++) {
                Replacement replacement = replacements.get(j);
                Predicate<BlockState> matches = matcher(replacement);
                // output 为 null 表示“替换为本母岩自身”
                BlockState output = replacement.output() == null
                        ? defaultBlockState()
                        : resolveState(replacement.output(), "输出方块");
                if (output == null) {
                    continue; // 解析失败：该条替换禁用
                }
                targets.add(new PreparedTarget(matches, output));
            }

            prepared.add(new PreparedConversion(conversion.chance(), conversion.radius(),
                    conversion.energyGated(), targets));
        }
        return prepared;
    }

    private Predicate<BlockState> matcher(Replacement replacement) {
        if (replacement.input() != null) {
            Block input = resolveBlock(replacement.input(), "输入方块");
            // 解析失败（方块不存在）时永不匹配，绝不把空气当匹配目标
            return input == null ? state -> false : state -> state.is(input);
        }
        if (replacement.inputTag() != null) {
            TagKey<Block> tag = replacement.inputTag();
            return state -> state.is(tag);
        }
        LOGGER.error("[Budding] 母岩 {} 的转化规则既没有输入方块也没有输入标签，已禁用", family.id());
        return state -> false;
    }

    @Nullable
    private BlockState resolveState(Supplier<Block> supplier, String description) {
        Block block = resolveBlock(supplier, description);
        return block == null ? null : block.defaultBlockState();
    }

    /**
     * 解析目标方块。首次随机刻时注册表已冻结，查到的才是真实方块；
     * 解析失败时记一次错误并禁用该规则。
     */
    @Nullable
    private Block resolveBlock(Supplier<Block> supplier, String description) {
        Block block = supplier.get();
        if (block == null || block == Blocks.AIR) {
            LOGGER.error("[Budding] 母岩 {} 未能解析{}——资源位置写错或该方块不存在，相关规则已禁用",
                    family.id(), description);
            return null;
        }
        return block;
    }

    /** 一条已解析的转化规则 */
    private record PreparedConversion(int chance, int radius, boolean energyGated, List<PreparedTarget> targets) {
    }

    /** 一条已解析的替换规则：匹配即写入 output（“替换为本母岩自身”已在解析时展开） */
    private record PreparedTarget(Predicate<BlockState> matches, BlockState output) {
    }
}
