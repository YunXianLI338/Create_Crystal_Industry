package com.minecart.yunxian.battery;

import com.minecart.yunxian.block.CrystalBatteryBlock;
import com.minecart.yunxian.blockentity.CrystalBatteryBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;

/**
 * 水晶电池那些「挂在手持物品上、却不属于任何特定物品」的交互。
 * <p>
 * <b>为什么既不能写在方块里、也不能写在某个物品里：</b>
 * 原版 {@code ServerPlayerGameMode#useItemOn} 有一条潜行规则——潜行且手上有非空物品时，
 * <b>整段跳过方块的 {@code useItemOn}</b>，把控制权交给手持物品的 {@code useOn} 去放方块
 * （Create 扳手的潜行拆卸之所以一直有效，正是因为它挂在自己的{@code WrenchItem#useOn} 上）。
 * 而水晶电池的潜行交互，玩家手里拿的是<b>任意晶体方块</b>——可能来自本模组、机械动力，
 * 也可能是整合包用标签加进来的别的模组方块，我们没法给它们的物品逐个加代码。
 * <p>
 * 于是改用 {@link UseItemOnBlockEvent} 的 {@code ITEM_AFTER_BLOCK} 相位：它在
 * {@code ItemStack#useOn} 里触发、对<b>任何物品</b>都生效，而且可以取消——
 * 取消即拦下放置，正好把「潜行右键只换一格」接过来。
 * <p>
 * <b>注意</b>：这个相位在「没潜行、方块放行之后」的那次放置里也会触发，所以必须用
 * {@code player.isShiftKeyDown()} 把范围收窄到潜行，否则会与方块里的非潜行逻辑重复执行。
 */
public final class CrystalBatteryInteractions {

    private CrystalBatteryInteractions() {
    }

    /** 挂在 NeoForge 事件总线上（见 Yunxian 构造器） */
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK) {
            return;
        }
        // 非潜行那次也会经过这里（方块放行之后），交给方块的 useItemOn 处理，别重复
        Player player = event.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !CrystalCapacities.isCrystal(stack)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!CrystalBatteryBlock.isBattery(level.getBlockState(pos))
                || !(level.getBlockEntity(pos) instanceof CrystalBatteryBlockEntity battery)) {
            return;
        }

        // 客户端只负责摆臂、并拦下本地那次放置；判据与服务端一致
        if (level.isClientSide) {
            if (battery.canApplyCrystal(stack, true)) {
                event.cancelWithResult(ItemInteractionResult.SUCCESS);
            }
            return;
        }
        if (battery.applyCrystal(stack, player, true) > 0) {
            event.cancelWithResult(ItemInteractionResult.SUCCESS);
        }
    }
}
