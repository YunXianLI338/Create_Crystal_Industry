package com.minecart.yunxian.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 脚本（KubeJS）注册的芽/簇的掉落规则：<b>精准采集掉本体，否则掉配置的物品</b>
 * （默认什么都不掉）——与本模组自带芽/簇的行为一致。
 * <p>
 * 为什么不用战利品表：KubeJS 的 {@code BlockBuilder#generateLootTable()} 虽然能覆写，
 * 但在 2101 里**没有任何调用者**（只被标了 {@code @Deprecated(forRemoval)}），覆写了也不生效；
 * 而它的 {@code drops}/{@code BlockDrops} API 只能表达「无条件掉这些物品」，表达不了精准采集。
 * 所以这里改在运行时接管：监听 NeoForge 的 {@link BlockDropsEvent}（它同时给出方块、工具与掉落物）。
 * <p>
 * 规则由 {@code integration.kubejs.CustomBudding} 在注册方块时登记（按方块 id，
 * 因为脚本执行时方块对象还没建出来），掉落物 id 也延后到真正掉落时才解析。
 */
public final class ScriptedBlockDrops {

    private ScriptedBlockDrops() {
    }

    /** 一条规则：普通破坏时掉落的物品（空 = 什么都不掉）与数量 */
    private record Rule(Optional<ResourceLocation> item, int count) {
    }

    private static final Map<ResourceLocation, Rule> RULES = new ConcurrentHashMap<>();

    /**
     * 登记一条规则。
     *
     * @param blockId   方块 id
     * @param dropItem  普通破坏时掉落的物品 id；null = 什么都不掉（精准采集始终掉方块本体）
     * @param dropCount 掉落数量（小于 1 时按 1 处理）
     */
    public static void register(ResourceLocation blockId, @Nullable ResourceLocation dropItem, int dropCount) {
        RULES.put(blockId, new Rule(Optional.ofNullable(dropItem), Math.max(1, dropCount)));
    }

    /** 事件入口（在 {@code Yunxian} 的构造器里挂到游戏事件总线上） */
    public static void onBlockDrops(BlockDropsEvent event) {
        List<ItemStack> drops = dropsFor(event.getState(), event.getTool());
        if (drops == null) {
            return; // 不是脚本方块（或没登记规则）：交给别的模组与原版逻辑
        }

        // 拦掉默认掉落，再按规则补上
        event.setCanceled(true);
        for (ItemStack drop : drops) {
            Block.popResource(event.getLevel(), event.getPos(), drop);
        }
    }

    /** 该方块是否由脚本登记了掉落规则（智能钻头等机器用它决定要不要绕过原版掉落表） */
    public static boolean isScripted(Block block) {
        return !RULES.isEmpty() && RULES.containsKey(BuiltInRegistries.BLOCK.getKey(block));
    }

    /**
     * 按规则算出掉落物：<b>精准采集 → 方块本体</b>；否则晶簇掉配置的物品、芽与母岩什么都不掉。
     *
     * @return 掉落列表；返回 {@code null} 表示这个方块不归我们管
     */
    @Nullable
    public static List<ItemStack> dropsFor(BlockState state, ItemStack tool) {
        Rule rule = RULES.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (rule == null) {
            return null;
        }
        if (hasSilkTouch(tool)) {
            return List.of(new ItemStack(state.getBlock()));
        }
        ItemStack configured = itemStack(rule.item().orElse(null), rule.count());
        return configured.isEmpty() ? List.of() : List.of(configured);
    }

    /** 精准采集：直接比对附魔的 ResourceKey，不需要附魔注册表（它在 1.21 是数据包注册表） */
    private static boolean hasSilkTouch(ItemStack tool) {
        if (tool.isEmpty()) {
            return false;
        }
        for (Holder<Enchantment> enchantment : tool.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack itemStack(@Nullable ResourceLocation itemId, int count) {
        Item item = itemId == null ? null : BuiltInRegistries.ITEM.get(itemId);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
