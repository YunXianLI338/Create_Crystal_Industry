package com.minecart.yunxian.util;

import java.util.List;
import java.util.Locale;

import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.budding.GenericBuddingBlock;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.blockentity.MechanicalAcceleratorBlockEntity;
import com.minecart.yunxian.budding.BuddingFamily.GrowthSpeed;
import com.minecart.yunxian.budding.GrowthDefinition;
import com.minecart.yunxian.budding.GrowthEnvironment;
import com.minecart.yunxian.client.budding.EnvironmentDisplay;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.integration.ae2.AE2Budding;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 母岩“当前生长速度”的共享计算/渲染辅助。
 * <p>
 * 浮窗里的数字只有<b>当前环境算出来的倍率</b>（玩家自己摆出来的催生器给的），
 * 方块固有的参数一律只说定性话（「缓慢」「必须足够暗」）：「平均多少秒一级」这类换算
 * 本类故意不提供，具体数值由玩家自己在世界里体会。
 */
public final class BuddingGrowthHelper {

    private BuddingGrowthHelper() {}

    /**
     * 一块母岩每秒钟收到的“自然”随机刻数。
     * 每个 16×16×16 区块段每 tick 随机抽 randomTickSpeed 次，故单格 = rts/4096 次/tick。
     * randomTickSpeed 客户端可读（与服务器同步的 gamerule）。
     */
    static double naturalRandomTicksPerSecond(Level level) {
        int rts = 3;
        if (level != null)
            rts = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        double perSecond = rts * 20.0 / 4096.0;
        // randomTickSpeed = 0 时自然基底不存在；退回默认基准，倍数仍可表达
        return perSecond > 0 ? perSecond : 3.0 * 20.0 / 4096.0;
    }

    /**
     * 周围催生器对该母岩的随机刻施加速率（次/秒）。
     * 遍历 6 个邻格：电力催生器运行中每 tick 施加 1 次；动力催生器按单面概率；
     * AE2 的晶体催生器（可选联动）按它在 AE2 配置里的间隔施加。
     * 多个催生器可叠加（同一母岩最多贴 6 台）。
     */
    static double acceleratorRandomTicksPerSecond(Level level, BlockPos pos) {
        // 两次催生之间的 tick 数（两种催生器共用的配置；客户端读本地配置，仅影响显示）
        int interval = ModConfig.Common.acceleratorIntervalTicks();
        double perSecond = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            Block block = state.getBlock();

            if (block instanceof AcceleratorBlock) {
                // POWERED 由服务端 setBlock(flag 3) 同步，客户端可直接反映运行状态
                if (state.getValue(AcceleratorBlock.POWERED))
                    perSecond += 20.0 / interval; // 每 interval tick 对每个面施加 1 次 randomTick
            } else if (block instanceof MechanicalAcceleratorBlock) {
                BlockEntity be = level.getBlockEntity(neighbor);
                if (be instanceof MechanicalAcceleratorBlockEntity mech) {
                    float speed = Math.abs(mech.getSpeed()); // 客户端已同步
                    if (speed != 0) {
                        // 与 MechanicalAcceleratorBlockEntity.tick() 中 perFaceProb 完全一致
                        float maxPerFace = MechanicalAcceleratorBlockEntity.MAX_EFFECT_RATE
                                / MechanicalAcceleratorBlockEntity.WORKING_FACES / interval;
                        float perFaceProb = Math.min(
                                maxPerFace * (speed / MechanicalAcceleratorBlockEntity.FULL_SPEED),
                                maxPerFace);
                        perSecond += perFaceProb * 20.0;
                    }
                }
            } else if (ModBlocks.AE2_LOADED && AE2Budding.isActiveGrowthAccelerator(level, neighbor)) {
                // AE2 联动：它只加速 ae2:growth_acceleratable 里的方块（含 #c:budding_blocks），
                // 本模组的母岩都在其中；速率由它自己的配置决定，默认 10 tick 施加一次。
                // 前置的 AE2_LOADED 判断必须保留：它保证无 AE2 时永远不会触碰 AE2Budding
                // （否则 JVM 会在执行到 invokestatic 时去加载缺失的 appeng 类型而崩溃）。
                perSecond += AE2Budding.growthAcceleratorRandomTicksPerSecond();
            }
        }
        return perSecond;
    }

    /** 向护目镜浮窗追加“当前生长速度”行 */
    public static void appendGrowthTooltip(Level level, BlockPos pos, List<Component> tooltip) {
        if (level == null)
            return;

        double natural = naturalRandomTicksPerSecond(level);
        double accel = acceleratorRandomTicksPerSecond(level, pos);

        // 无催生器：自然生长（= ×1 基准）
        if (accel <= 0) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.growth_speed.natural")
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);
            return;
        }

        // 有催生器：倍数 = 催生器贡献 / 自然基底（自然占比极小，按需求忽略）
        double multiplier = accel / natural;
        String text = multiplier >= 10
                ? String.format(Locale.ROOT, "%.0f", multiplier)
                : String.format(Locale.ROOT, "%.1f", multiplier);

        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.growth_speed.label")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(text)
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip, 1);
    }

    /**
     * 脚本（KubeJS）注册的母岩额外显示它的生长要求（速度 / 光照 / 含水）；
     * 生长环境（维度 / 群系）是两种母岩共用的，在 {@link #appendGrowthEnvironment} 里加。
     * <p>
     * 客户端也能读到：这些参数是脚本随方块实例传进来的（{@code ScriptedBuddingBlock} 持有定义），
     * 不需要服务端同步。自带家族不显示这几行（它们的档位在配置文件里，护目镜显示的倍率即为所需）。
     * <p>
     * 一律只给<b>定性说法</b>（「缓慢」「必须足够暗」）：具体数值（1/n、亮度阈值）不写进浮窗，
     * 免得把方块变成一张参数表。
     */
    public static void appendScriptedInfo(BlockState state, List<Component> tooltip) {
        if (!(state.getBlock() instanceof ScriptedBuddingBlock scripted)) {
            return;
        }
        GrowthDefinition definition = scripted.growthDefinition();

        // 概率基数归到最接近的档位，只说「缓慢 / 很快」
        addLine(tooltip, Component.translatable(SCRIPTED_SPEED_KEY,
                Component.translatable(SCRIPTED_SPEED_KEY + "." + GrowthSpeed.nearest(definition.chance()).langSuffix())));

        if (definition.minLight().isPresent()) {
            addLine(tooltip, "create_crystal_industry.goggles.scripted.min_light");
        }
        if (definition.maxLight().isPresent()) {
            addLine(tooltip, "create_crystal_industry.goggles.scripted.max_light");
        }
        if (definition.requiresWater()) {
            addLine(tooltip, "create_crystal_industry.goggles.scripted.requires_water");
        }
    }

    /**
     * 母岩的「生长环境」行（生长维度一行、生长群系一行）：<b>自带家族与脚本母岩共用</b>
     * ——家族母岩读家族表（{@code GenericBuddingBlock#family()}），脚本母岩读它自己的定义，
     * 所以石英母岩、荧石母岩这种自带家族也能显示（原先只有脚本母岩有这一行）。
     * <p>
     * 没写环境（{@link GrowthEnvironment#restricts()} 为假）的母岩一行都不加：
     * 绝大多数母岩本来就不挑地方，写了反而是噪音。地盘外还剩多少概率生长同样只给定性说法。
     * <p>
     * 只在客户端调（维度与群系名走 {@link EnvironmentDisplay}，它读语言文件）。
     * <p>
     * 目前只有共用的 {@code BuddingGrowthBlockEntity} 调它；回响 / 可燃冰 / 福鲁伊克斯那三个专用 BE
     * 的家族都还没有环境要求，将来给它们加了要求，记得在那几个 {@code addToGoggleTooltip} 里也调一次。
     */
    public static void appendGrowthEnvironment(BlockState state, List<Component> tooltip) {
        GrowthEnvironment environment = growthEnvironmentOf(state);
        if (environment == null || !environment.restricts()) {
            return;
        }
        if (environment.hasDimensions()) {
            addLine(tooltip, Component.translatable("create_crystal_industry.goggles.growth_dimensions",
                    EnvironmentDisplay.dimensions(environment)));
        }
        if (environment.hasIncludedBiomes()) {
            addLine(tooltip, Component.translatable("create_crystal_industry.goggles.growth_biomes",
                    EnvironmentDisplay.biomes(environment)));
        }
        if (environment.hasExcludedBiomes()) {
            addLine(tooltip, Component.translatable("create_crystal_industry.goggles.growth_biomes_excluded",
                    EnvironmentDisplay.excludedBiomes(environment)));
        }
    }

    /** 家族母岩读家族表，脚本母岩读它的定义；别的母岩（原版紫水晶等）没有要求 */
    @Nullable
    private static GrowthEnvironment growthEnvironmentOf(BlockState state) {
        if (state.getBlock() instanceof GenericBuddingBlock generic) {
            return generic.family().growth().growthEnvironment();
        }
        if (state.getBlock() instanceof ScriptedBuddingBlock scripted) {
            return scripted.growthDefinition().growthEnvironment();
        }
        return null;
    }

    /** 「生长速度: <定性词>」那一行：后接 {@code .<档位后缀>} 就是四个定性词 */
    private static final String SCRIPTED_SPEED_KEY = "create_crystal_industry.goggles.scripted.speed";

    private static void addLine(List<Component> tooltip, String key) {
        addLine(tooltip, Component.translatable(key));
    }

    private static void addLine(List<Component> tooltip, MutableComponent text) {
        CreateLang.builder().add(text.withStyle(ChatFormatting.GRAY)).forGoggles(tooltip, 1);
    }
}