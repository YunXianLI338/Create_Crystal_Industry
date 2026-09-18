package com.minecart.yunxian.block.budding;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.EchoConvertingBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.FlammableIceBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingFamily;
import com.minecart.yunxian.budding.BuddingFamily.BlockConversion;
import com.minecart.yunxian.budding.BuddingFamily.EnergyRequirement;
import com.minecart.yunxian.budding.BuddingFamily.GrowthRule;
import com.minecart.yunxian.budding.BuddingFamily.LightRequirement;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.GrowthDefinition;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.integration.ae2.AE2Budding;
import com.minecart.yunxian.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 母岩方块：把家族定义合成一个 {@link GrowthDefinition}，
 * 生长本身交给公开的 {@link BuddingGrowthEngine}（附属模组与 KubeJS 脚本用的是同一个入口）。
 * <p>
 * 各家族的全部差异（光照门槛、含水要求、充能要求、方块转化、芽/簇方块、方块实体）
 * 都写在 {@code budding/BuddingFamilies} 那一张表里，本类不含任何家族特例；
 * 随机刻副作用（转化/传播）与生长能量留在本类，因为它们是家族特有的。
 * <p>
 * 唯一保留的子类是 {@link EchoConvertingBuddingBlock}：它的 {@code CAN_SUMMON} 状态
 * 必须在构造器里注册，而 {@code BlockBehaviour} 的构造器会先调用
 * {@code createBlockStateDefinition}（此时子类字段尚未赋值），无法从 family 读取。
 */
public class GenericBuddingBlock extends BuddingAmethystBlock implements EntityBlock {
    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.budding");

    protected final BuddingFamily family;
    protected final Block smallBud;
    protected final Block mediumBud;
    protected final Block largeBud;
    protected final Block cluster;

    /** 生长能量钩子：免费家族为 null（避免每次随机刻现建 lambda） */
    @Nullable
    private final BuddingGrowthEngine.GrowthGate energyGate;

    /** 转化规则在首次随机刻（注册表已冻结）后解析并缓存，避免每 tick 查注册表 */
    @Nullable
    private List<PreparedConversion> preparedConversions;

    // 生长定义缓存：配置里的生长档位变了才重建
    @Nullable
    private GrowthDefinition cachedDefinition;
    private int cachedChance;

    public GenericBuddingBlock(BuddingFamily family, Properties properties, Block smallBud, Block mediumBud,
                               Block largeBud, Block cluster) {
        super(properties);
        this.family = family;
        this.smallBud = smallBud;
        this.mediumBud = mediumBud;
        this.largeBud = largeBud;
        this.cluster = cluster;
        this.energyGate = family.growth().energy() == EnergyRequirement.FREE ? null : this::payGrowthEnergy;
    }

    public BuddingFamily family() {
        return family;
    }

    /**
     * 四个生长阶段的方块，顺序：小芽 → 中芽 → 大芽 → 晶簇（与 {@link GrowthDefinition#stages()} 一致）。
     * <p>
     * 供外部读取本方块会往哪四个方块长——附属模组用本类建自己的母岩时，
     * JEI 的母岩信息页就是靠它渲染出整套芽与晶簇的（我们拿不到对方私有的四个字段）。
     */
    public List<Block> stages() {
        return List.of(smallBud, mediumBud, largeBud, cluster);
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
        BuddingGrowthEngine.tryGrow(level, pos, random, definition(), energyGate);
        runConversions(level, pos, random);
    }

    // ==================== 生长定义 ====================

    /**
     * 本方块当前生效的生长定义：家族定义 + 配置文件里的生长档位。
     * <p>
     * 按解析出的概率缓存（配置重载会改变它）：随机刻是热路径，不能每 tick 重建一个定义。
     */
    private GrowthDefinition definition() {
        int chance = ModConfig.Common.growthChance(family.id());

        GrowthDefinition cached = cachedDefinition;
        if (cached != null && cachedChance == chance) {
            return cached;
        }

        GrowthDefinition built = new GrowthDefinition(smallBud, mediumBud, largeBud, cluster, chance,
                familyMaxLight(), family.growth().rule() == GrowthRule.SUBMERGED);
        cachedChance = chance;
        cachedDefinition = built;
        return built;
    }

    /** 家族的光照要求换算成"允许的最大亮度"：{@code below(t)} 即亮度 ≤ t-1，不限光时为空 */
    private OptionalInt familyMaxLight() {
        LightRequirement light = family.growth().light();
        return light.kind() == LightRequirement.Kind.BELOW
                ? OptionalInt.of(light.threshold() - 1)
                : OptionalInt.empty();
    }

    /**
     * 生长位是否满足光照要求（默认无要求，回响母岩要求亮度 0）。
     * <p>
     * 客户端也会调它（回响母岩的护目镜状态），所以判定只依赖客户端也拿得到的东西：家族定义。
     */
    protected boolean canGrowAtLight(Level level, BlockPos neighborPos) {
        return BuddingGrowthEngine.lightAllows(level, neighborPos, definition());
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
