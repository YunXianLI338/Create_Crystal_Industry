package com.minecart.yunxian.resonance;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 远距离查看共振类别用的两个钩子，全部挂在 NeoForge 的游戏总线上（在 {@code Yunxian} 里注册）。
 * <p>
 * 机械动力的仓储界面（{@code StockKeeperRequestMenu}）和幻翼物流的便携发报机都是
 * {@code player.openMenu(...)} 打开的，所以 {@link PlayerContainerEvent.Open} 两条路径都能收到。
 * 菜单里的 {@code contentHolder} 就是发报机本身 —— 便携发报机那边是 phantom 造的假方块实体，
 * 但同样是 {@link StockTickerBlockEntity} 的子类，类别表也照样在它的同步标签里。
 */
public final class ResonanceRulesEvents {

    /** 界面开着时补刷的间隔（单位：tick）。 */
    private static final int REFRESH_INTERVAL = 20;

    private ResonanceRulesEvents() {
    }

    /** 界面刚打开：此时客户端还没收到打开界面的包，先于它的第一次库存请求。 */
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!(event.getContainer() instanceof StockKeeperRequestMenu menu))
            return;
        StockTickerBlockEntity ticker = menu.contentHolder;
        if (ticker != null)
            ResonanceRulesSync.send(player, ticker);
    }

    /** 界面开着时定期补刷：网络里的台子/台面物品变了就重发，没变一个包都不发。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % REFRESH_INTERVAL != 0)
            return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof StockKeeperRequestMenu menu && menu.contentHolder != null)
                ResonanceRulesSync.sendIfChanged(player, menu.contentHolder);
        }
    }

    /** 下线就把上次发出的快照丢掉。 */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ResonanceRulesSync.forget(event.getEntity().getUUID());
    }
}
