package com.minecart.yunxian.blockentity;

import java.util.List;

import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalAcceleratorBlockEntity extends KineticBlockEntity {

    /** 满速基准：达到该转速时催生效果达到上限 */
    public static final float FULL_SPEED = 256f;

    /** 电力催生器参考强度：每次催生对 6 个面各触发一次 randomTick */
    public static final float ELECTRIC_RATE = 6f;

    /**
     * 本机满速时的效果上限（单位：randomTick / tick，6 个面合计）= 与电力催生器同级。
     * 注意：历史注释曾写作「电力催生器的 1/10」，与代码不符（曾用 {@code ELECTRIC_RATE / 10f}），
     * 平衡性以代码为准；实际倍率还会再除以配置里的催生间隔。
     */
    public static final float MAX_EFFECT_RATE = ELECTRIC_RATE;

    /** 工作面数量：全部 6 个面 */
    public static final int WORKING_FACES = 6;   // private → public

    public MechanicalAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_ACCELERATOR.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        float speed = getSpeed();
        boolean powered = speed != 0;

        BlockState state = getBlockState();
        if (state.getValue(MechanicalAcceleratorBlock.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(MechanicalAcceleratorBlock.POWERED, powered), 3);
        }

        if (!powered || !(level instanceof ServerLevel serverLevel))
            return;

        // 满速时每面的触发概率：效果上限 ÷ 6 个工作面 ÷ 配置的催生间隔
        float maxPerFaceProb = MAX_EFFECT_RATE / WORKING_FACES / ModConfig.Common.acceleratorIntervalTicks();

        // 随转速线性增长，超过满速也封顶
        float perFaceProb = maxPerFaceProb * (Math.abs(speed) / FULL_SPEED);
        perFaceProb = Math.min(perFaceProb, maxPerFaceProb);

        // 六个面全部施加催生效果（含传动杆所在面）
        for (Direction dir : Direction.values()) {
            if (level.random.nextFloat() < perFaceProb) {
                BlockPos target = worldPosition.relative(dir);
                level.getBlockState(target).randomTick(serverLevel, target, serverLevel.random);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 先输出 KineticBlockEntity 自带的 Kinetics + Stress Impact（Create 原生面板）
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        float speed = Math.abs(getSpeed());
        boolean running = speed != 0;

        // 每 tick 期望施加的随机刻总数（6 个面求和后与 tick() 内 perFaceProb 公式等价）
        int interval = ModConfig.Common.acceleratorIntervalTicks();
        float expectedPerTick = running
                ? Math.min(MAX_EFFECT_RATE * (speed / FULL_SPEED), MAX_EFFECT_RATE) / interval
                : 0f;
        float per20 = Math.round(expectedPerTick * 20f * 10f) / 10f;

        // 状态：工作中 / 已停止
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.status_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.translatable(running
                                ? "create_crystal_industry.goggles.status.working"
                                : "create_crystal_industry.goggles.status.idle")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip, 1);

        // 工作速度：平均每 20 tick 施加的随机刻数量（期望值，保留 1 位小数）
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.work_speed_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(CreateLang.number(per20)
                        .style(ChatFormatting.WHITE))
                .add(Component.literal(" "))
                .add(Component.translatable("create_crystal_industry.goggles.work_speed_unit")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }
}