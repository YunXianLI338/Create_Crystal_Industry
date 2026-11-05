package com.minecart.yunxian.resonance;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.item.ResonanceFilterItem;
import com.minecart.yunxian.network.ResonanceRulesPayload;
import com.minecart.yunxian.registry.ModDataComponents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端侧的「把这个网络现在过滤什么告诉玩家」。
 * <p>
 * 什么时候发：玩家打开仓储界面时（{@code PlayerContainerEvent.Open}，见
 * {@link ResonanceRulesEvents}）。这个事件在 {@code ServerPlayer#openMenu} 里紧跟
 * {@code initMenu} 发出，此时客户端连界面都还没创建、更没发过库存请求，所以规则一定先于
 * 第一次分组抵达，不需要客户端再问一次。界面开着时每 20 tick 再补刷一次，内容没变就一个
 * 包都不发。
 * <p>
 * 发什么：仓储发报机的类别里每个<b>接了网络的</b>共振过滤器各一条，外加它们<b>嵌套</b>到的
 * 网络（台面上放着别的网络的共振过滤器时，客户端解析那条规则也要用），有环就靠 visited 刹住，
 * 层数上限与判定侧一致（{@code ResonanceFilterItemStack#MAX_RESOLVE_DEPTH}）。
 * <p>
 * 怎么读类别：发报机把类别表原样写进同步标签（{@code StockTickerBlockEntity#write} 无条件写
 * {@code "Categories"}），所以走公开的 {@link net.minecraft.world.level.block.entity.BlockEntity#getUpdateTag}
 * 就能拿到，不必去碰 protected 字段 —— phantom 的存储频道扩展卡也是这么读的。
 * <p>
 * 为什么在服务端解析：服务端的 {@code LogisticallyLinkedBehaviour.LINKS} 是被 tick 到的
 * 方块实体维护的，动力加载器保着的台子一直在里面，玩家跑多远都不影响；
 * 客户端那张 {@code CLIENT_LINKS} 只在区块被加载时才有人续期。
 */
public final class ResonanceRulesSync {

    /** 与判定侧的展开深度一致（{@code ResonanceFilterItemStack#MAX_RESOLVE_DEPTH}）。 */
    private static final int MAX_DEPTH = 4;

    /** 每个玩家上一次发出去的快照，用来在补刷时判断「内容真的变了吗」。 */
    private static final Map<UUID, List<ResonanceRulesPayload>> LAST_SENT = new HashMap<>();

    private ResonanceRulesSync() {
    }

    /** 玩家刚打开仓储界面：无条件发一遍。 */
    public static void send(ServerPlayer player, StockTickerBlockEntity ticker) {
        List<ResonanceRulesPayload> payloads = build(player.level(), ticker);
        if (payloads.isEmpty()) {
            LAST_SENT.remove(player.getUUID());
            return;
        }
        for (ResonanceRulesPayload payload : payloads)
            PacketDistributor.sendToPlayer(player, payload);
        LAST_SENT.put(player.getUUID(), payloads);
    }

    /** 界面开着时的补刷：和上次发的一样就跳过。 */
    public static void sendIfChanged(ServerPlayer player, StockTickerBlockEntity ticker) {
        List<ResonanceRulesPayload> payloads = build(player.level(), ticker);
        if (!payloads.isEmpty() && sameAsLastSent(player, payloads))
            return;
        if (payloads.isEmpty()) {
            LAST_SENT.remove(player.getUUID());
            return;
        }
        for (ResonanceRulesPayload payload : payloads)
            PacketDistributor.sendToPlayer(player, payload);
        LAST_SENT.put(player.getUUID(), payloads);
    }

    /** 玩家下线，把快照丢掉（同一个 UUID 下次进来时不该沿用旧内容）。 */
    public static void forget(UUID playerId) {
        LAST_SENT.remove(playerId);
    }

    // ------------------------------------------------------------------ 构建

    private static List<ResonanceRulesPayload> build(Level level, StockTickerBlockEntity ticker) {
        if (level == null)
            return List.of();

        Set<UUID> rootNetworks = new LinkedHashSet<>();
        for (ItemStack filter : readCategories(ticker, level.registryAccess())) {
            UUID network = networkOf(filter);
            if (network != null)
                rootNetworks.add(network);
        }
        if (rootNetworks.isEmpty())
            return List.of();

        List<ResonanceRulesPayload> payloads = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>(rootNetworks);
        Set<UUID> visited = new HashSet<>();
        int depth = 0;
        while (!queue.isEmpty() && depth <= MAX_DEPTH) {
            int layer = queue.size();
            for (int i = 0; i < layer; i++) {
                UUID network = queue.poll();
                if (!visited.add(network))
                    continue;

                List<ItemStack> rules = new ArrayList<>();
                int tableCount = 0;
                for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(network, false)) {
                    // 客户端那张表要等 400 tick 才清掉旧网络，服务端 remove() 是即时的；
                    // 这里照样核一遍 id，和判定侧保持同一套谨慎
                    if (!link.freqId.equals(network))
                        continue;
                    if (!(link.blockEntity instanceof ResonanceTableBlockEntity table))
                        continue;

                    tableCount++;
                    ItemStack onTable = table.getFilterSource();
                    if (onTable.isEmpty())
                        continue;
                    if (rules.size() >= ResonanceRulesPayload.MAX_RULES)
                        continue;
                    rules.add(onTable.copy());
                }

                // 一台都够不着也照发（tableCount = 0）：客户端据此把这一格判成"读不到"，
                // 而不是继续用上一份可能已经过时的快照
                payloads.add(new ResonanceRulesPayload(network, rules, tableCount));

                for (ItemStack rule : rules) {
                    UUID nested = networkOf(rule);
                    if (nested != null && !visited.contains(nested))
                        queue.add(nested);
                }
            }
            depth++;
        }
        return payloads;
    }

    /**
     * 类别的公开读法：发报机把整份类别表写进同步标签（{@code write} 里无条件写
     * {@code "Categories"}），{@code getUpdateTag} 就是「发给客户端的那一份」。
     */
    private static List<ItemStack> readCategories(StockTickerBlockEntity ticker, HolderLookup.Provider registries) {
        CompoundTag tag = ticker.getUpdateTag(registries);
        return NBTHelper.readItemList(tag.getList("Categories", Tag.TAG_COMPOUND), registries);
    }

    private static UUID networkOf(ItemStack stack) {
        if (!(stack.getItem() instanceof ResonanceFilterItem))
            return null;
        return stack.get(ModDataComponents.RESONANCE_NETWORK.get());
    }

    // ------------------------------------------------------------------ 比对

    private static boolean sameAsLastSent(ServerPlayer player, List<ResonanceRulesPayload> payloads) {
        List<ResonanceRulesPayload> last = LAST_SENT.get(player.getUUID());
        if (last == null || last.size() != payloads.size())
            return false;
        for (int i = 0; i < payloads.size(); i++) {
            ResonanceRulesPayload a = last.get(i);
            ResonanceRulesPayload b = payloads.get(i);
            if (!a.network().equals(b.network()) || a.tableCount() != b.tableCount())
                return false;
            if (a.rules().size() != b.rules().size())
                return false;
            for (int j = 0; j < a.rules().size(); j++) {
                if (!ItemStack.matches(a.rules().get(j), b.rules().get(j)))
                    return false;
            }
        }
        return true;
    }
}
