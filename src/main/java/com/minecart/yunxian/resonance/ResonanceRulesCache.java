package com.minecart.yunxian.resonance;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端这边存的一份「服务端眼里的共振网络」快照。
 * <p>
 * 为什么需要它：共振过滤器的判定<b>在客户端也要跑</b>。仓储发报机的界面要把库存按类别分组，
 * 分组用的是 {@code FilterItemStack.test}，而它读的是 Create 的 {@code CLIENT_LINKS}。
 * 那张表只由<b>被 tick 到的</b>方块实体续期（{@code LogisticallyLinkedBehaviour#initialize}
 * 在客户端直接 return，只有 {@code lazyTick → keepAlive} 会往里写），所以玩家站在离基地
 * 很远的地方（区块没加载）时它是空的，过滤器一律判「读不到」，界面上就表现为
 * 「类别里什么都没有、其余全堆进未分类」。
 * <p>
 * 于是服务端在响应仓储请求时顺手把解析结果推过来（包见
 * {@link com.minecart.yunxian.network.ResonanceRulesPayload}，发送端见
 * {@link ResonanceRulesSync}），这里存着，客户端那条解析路径在本地读不到时用它兜底。
 * <p>
 * <b>只有客户端会读写这张表</b>：写入在收包处理里，读取在
 * {@code ResonanceFilterItemStack#resolveAllUncached} 的客户端分支。
 * 单机里客户端与服务端是同一个 JVM，所以服务端那条路径必须显式地不碰它，
 * 否则「服务端替客户端解析」的结论会被客户端自己的旧缓存污染。
 */
public final class ResonanceRulesCache {

    /**
     * @param rules      台面上放着的物品（原样，未包成 FilterItemStack —— 包的过程要放在
     *                   判定现场做，才能跟着 {@code FilterItemStack#of} 的分发规则走）
     * @param tableCount 服务端此刻够得着的台子数，0 表示服务端也读不到
     */
    public record Snapshot(List<ItemStack> rules, int tableCount) {
    }

    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private ResonanceRulesCache() {
    }

    public static void put(UUID network, List<ItemStack> rules, int tableCount) {
        SNAPSHOTS.put(network, new Snapshot(List.copyOf(rules), tableCount));
    }

    public static Snapshot get(UUID network) {
        return SNAPSHOTS.get(network);
    }

    /** 退出世界/换服时清掉，免得把上一个存档的网络快照带过去。 */
    public static void clear() {
        SNAPSHOTS.clear();
    }
}
