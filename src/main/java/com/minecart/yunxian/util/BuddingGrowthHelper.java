package com.minecart.yunxian.util;

import java.util.List;
import java.util.Locale;

import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.blockentity.MechanicalAcceleratorBlockEntity;
import com.minecart.yunxian.budding.GrowthDefinition;
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

/** 母岩“当前生长速度”的共享计算/渲染辅助 */
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

    /**
     * 按当前的 randomTickSpeed，一块母岩平均需要多少秒推进一级。
     * <p>
     * 每次随机刻有 1/chance 的概率推进一级，而随机刻平均每 1/自然速率 秒才落到这一格一次，
     * 因此数学期望 = chance ÷ 自然速率（默认设置下 1/5 档约为 341 秒，与原版紫水晶母岩同速）。
     * JEI 的母岩信息页要显示这个直观数字；{@code level} 为 null（还没进世界）时沿用默认基准。
     */
    public static double averageSecondsPerStage(@Nullable Level level, int chance) {
        return chance / naturalRandomTicksPerSecond(level);
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
     * 脚本（KubeJS）注册的母岩额外显示它的生长参数（概率 / 光照 / 含水）。
     * <p>
     * 客户端也能读到：这些参数是脚本随方块实例传进来的（{@code ScriptedBuddingBlock} 持有定义），
     * 不需要服务端同步。自带家族不显示这几行（它们的档位在配置文件里，护目镜显示的倍率即为所需）。
     */
    public static void appendScriptedInfo(BlockState state, List<Component> tooltip) {
        if (!(state.getBlock() instanceof ScriptedBuddingBlock scripted)) {
            return;
        }
        GrowthDefinition definition = scripted.growthDefinition();

        addLine(tooltip, "create_crystal_industry.goggles.scripted.chance", definition.chance());

        if (definition.minLight().isPresent()) {
            addLine(tooltip, "create_crystal_industry.goggles.scripted.min_light",
                    definition.minLight().getAsInt());
        }
        if (definition.maxLight().isPresent()) {
            addLine(tooltip, "create_crystal_industry.goggles.scripted.max_light",
                    definition.maxLight().getAsInt());
        }
        if (definition.requiresWater()) {
            addLine(tooltip, "create_crystal_industry.goggles.scripted.requires_water");
        }
    }

    private static void addLine(List<Component> tooltip, String key, int arg) {
        addLine(tooltip, Component.translatable(key, arg));
    }

    private static void addLine(List<Component> tooltip, String key) {
        addLine(tooltip, Component.translatable(key));
    }

    private static void addLine(List<Component> tooltip, MutableComponent text) {
        CreateLang.builder().add(text.withStyle(ChatFormatting.GRAY)).forGoggles(tooltip, 1);
    }
}