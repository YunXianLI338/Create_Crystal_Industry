package com.minecart.yunxian.item;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModDataComponents;
import com.minecart.yunxian.resonance.ResonanceRulesCache;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 共振过滤器的判定逻辑：<b>本身不存过滤规则</b>，每次判定时现场去它所在的那个共振台网络里
 * 把每张台面上放着的物品都收集起来，逐个交给 Create 自己的 {@link FilterItemStack} 解释，
 * 最后<b>取并集</b>（任意一张台子接受就算接受）。
 * <p>
 * 为什么这样接得上：Create 的 {@code FilteringBehaviour.test(ItemStack)} 是
 * {@code filter.test(level, stack)}（拉模型），而 {@code filter} 就是本对象。
 * 所以台面上换个东西，所有接了本过滤器的漏斗/溜槽/工作盆<b>下一次判定</b>就变，
 * 不需要同步、不需要 tick、不需要监听容器变化。
 * <p>
 * 网络本身借用 Create 的 {@link LogisticallyLinkedBehaviour}：它就是一个
 * 「UUID → 一组方块实体」的全局注册表（用弱引用、靠 lazyTick 续期，区块卸载/方块被拆会自动掉出去），
 * 而且是<b>全局</b>的 —— 所以同一个网络里的台子天然可以跨维度，不需要我们自己做维度解析。
 * <p>
 * 这里是动态判定的，所以<b>绝对不能照抄 {@code ListFilterItemStack} 那种构造时快照的写法</b>
 * （它在构造器里就把 18 格读进 containedItems 了）。本类的解析发生在 {@link #resolveAll} 里、
 * 按游戏刻缓存。
 * <p>
 * 四种情况，界限划得很清楚：
 * <ol>
 *   <li><b>网络里有台子放着东西 → 取并集，空台面被忽略。</b>
 *       空台子不产出过滤器、也不影响别人 —— 否则一张空台子就能把整个网络放开，
 *       其余台子放什么都白搭。</li>
 *   <li><b>台子全都空着 → 全集</b>，和「过滤槽里没插过滤器」同一个意思
 *       （空的 {@code FilterItemStack} 的 test 恒为 true，物品和流体都是）。</li>
 *   <li><b>没接入网络、而且是被过滤槽直接问到的那一份 → 过滤物就是它自己</b>：
 *       白名单下只认共振过滤器，黑名单下其余全部放行；「匹配物品属性」打开时，
 *       只有同样没接入网络的才算（网络 id 存在数据组件里，会被一起比掉）。
 *       见 {@link #filtersItself()} —— 台面上那份没接入网络的过滤器不在此列。</li>
 *   <li><b>其余读不到 → 什么都不通过</b>（fail-closed）：网络里一台都没有、
 *       台子的区块没加载、被拆了、台面上那个共振过滤器接成了环。</li>
 * </ol>
 * 2 与 4 的分界是有意为之：台面空了是玩家自己动的手、看得见，跟空过滤槽一致；
 * 而"读不到"是环境造成的、或者是环，那时候放开就等于「区块一卸载漏斗突然放行全部物品」。
 * <p>
 * <b>台面上可以再放一个共振过滤器</b>：只要它绑的是<b>别的</b>网络，就会被照常解析 ——
 * 也就是「A 网络的台子读得到 B 网络在过滤什么」，还能一层层套下去（A→B→C）。
 * 绑的是本网络、或者没绑网络时它读不出东西，判定入口的重入护栏会把那种情况挡成"读不到"
 * （台面这条链上<b>不</b>走「过滤物为它自己」，理由见 {@link #filtersItself()}）。
 * 挡的是<b>环</b>（解析链上出现过这个网络没有），不是"台面上有共振过滤器"这个事实本身
 * —— 后者正是跨网络兼容要放开的东西，见 {@link #RESOLVING_NETWORKS}。
 * <p>
 * 注意并集的一个后果，这是有意为之、但要心里有数：<b>一张台子的区块没加载时，它就不在并集里</b>
 * （{@code getAllPresent} 只给得出有效的方块实体），于是它那一份过滤规则会暂时消失。
 * 只有全部台子都不可达时才退化成"什么都不通过"。
 * <p>
 * 客户端还有一层退路：{@code CLIENT_LINKS} 只由被 tick 到的方块实体续期，玩家离开基地
 * （超过模拟距离）后整张网络会空掉，于是远距离打开仓储界面时所有物品都会落进「未分类」。
 * 这种情况下改用服务端推来的快照，见 {@link #fromServerSnapshot}。
 */
public class ResonanceFilterItemStack extends FilterItemStack {

    /** 按游戏刻缓存上一刻的解析结果，避免每个物品都重建一遍网络里所有台子的过滤器 */
    private long cachedTick = Long.MIN_VALUE;
    private Level cachedLevel;
    private List<FilterItemStack> cached = List.of();

    /**
     * <b>环护栏</b>：本线程当前这条解析链上已经走过的网络 id。
     * <p>
     * 共振过滤器可以放在网络的台面上，于是「A 的台子读 B 网络、B 的台子读回 A 网络」这种
     * 互相指向是玩家摆得出来的，顺着它 {@code test} 会无限递归成栈溢出（直接崩游戏，不是卡一下）。
     * 这里按<b>网络</b>记账、而不是按"台面上是不是共振过滤器"记账：一次判定里同一个网络
     * 只允许进一次，再进来就判"读不到"。
     * <p>
     * 护栏放在 {@link #test}/{@link #fluid} 这两层、而不是 {@link #resolveAllUncached} 里：
     * 解析出来的是<b>一串</b>过滤器，它们是在解析<b>结束之后</b>才逐个 test 的，
     * 护栏必须罩住整个判定过程，否则内层同一个网络会重新穿进来。
     * <p>
     * 按线程存：判定可能同时发生在服务端主线程与客户端渲染线程（见 {@code FilterItemStackMixin}），
     * 两边各记各的账，互不干扰。
     */
    private static final ThreadLocal<Set<UUID>> RESOLVING_NETWORKS = ThreadLocal.withInitial(HashSet::new);

    /**
     * <b>深度护栏</b>：兜住没有环、但很长的链条（A→B→C→D→…，每个网络的台面上各放一个
     * 指向下一个网络的共振过滤器）。这种链不会死循环，但没理由一路解析到底，
     * 超了就按"读不到"处理。
     */
    private static final ThreadLocal<Integer> RESOLVE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_RESOLVE_DEPTH = 4;

    public ResonanceFilterItemStack(ItemStack filter) {
        super(filter);
    }

    // ==================== 判定入口 ====================

    @Override
    public boolean test(Level world, ItemStack stack, boolean matchNBT) {
        // 没接入网络：规则就是它自己，直判即可，不需要解析、也进不了环
        if (filtersItself())
            return blacklist() != FilterItem.testDirect(item(), stack, strict(matchNBT));

        return guarded(() -> {
            List<FilterItemStack> filters = resolveAll(world);
            // 读不到（台面这条链上没绑网络 / 台子全不可达 / 环）→ fail-closed
            if (filters.isEmpty())
                return false;
            // 台面全空的哨兵（见 resolveAllUncached）：没有规则可反，两种名单模式都放行
            if (!hasRules(filters))
                return true;

            boolean strict = strict(matchNBT);
            boolean matched = false;
            for (FilterItemStack filter : filters) {
                if (filter.test(world, stack, strict)) {
                    matched = true;
                    break;
                }
            }
            // 白名单：命中才放行；黑名单：命中反而不放行
            return blacklist() != matched;
        }, false);
    }

    /**
     * 流体那一半：它自己是一样物品，不可能命中任何流体，于是"命中"恒为 false，
     * 结果照旧由名单开关决定 —— 黑名单下「除它以外全通过」，流过它的流体全都放行。
     * 与「台面上放着一件普通物品」时是同一份规则，区别只在这条规则的来源。
     */
    @Override
    public boolean test(Level world, FluidStack stack, boolean matchNBT) {
        if (filtersItself())
            return blacklist();

        return guarded(() -> {
            List<FilterItemStack> filters = resolveAll(world);
            if (filters.isEmpty())
                return false;
            if (!hasRules(filters))
                return true;

            boolean strict = strict(matchNBT);
            boolean matched = false;
            for (FilterItemStack filter : filters) {
                if (filter.test(world, stack, strict)) {
                    matched = true;
                    break;
                }
            }
            return blacklist() != matched;
        }, false);
    }

    // ==================== 两个开关 ====================

    /** 名单模式：{@code true} = 黑名单（把并集结果取反），缺省 = 白名单 */
    private boolean blacklist() {
        return item().getOrDefault(ModDataComponents.RESONANCE_FILTER_BLACKLIST.get(), false);
    }

    /**
     * 匹配模式：{@code true} = 连数据组件一起比。
     * <p>
     * <b>只收紧、不放松</b>：缺省时原样沿用调用方传进来的 {@code matchNBT}，
     * 所以没动过这个开关的过滤器，判法与加开关之前逐字一致。
     */
    private boolean respectsData() {
        return item().getOrDefault(ModDataComponents.RESONANCE_FILTER_RESPECT_DATA.get(), false);
    }

    private boolean strict(boolean matchNBT) {
        return matchNBT || respectsData();
    }

    /**
     * 这次判定该不该走「过滤物就是它自己」：<b>没接入网络</b>，且<b>这是最外层的那一次判定</b>。
     * <p>
     * 为什么要卡"最外层"：同一个对象也会被当成<b>台面上的物品</b>去解析 —— 谁在网络里某张台子上
     * 放了一个没接入网络的共振过滤器，那边的解析就会把它包成一份本类对象来问。那种场合它没有
     * 网络可读，老规矩是"读不出东西、什么也不产出"，显示板
     * （{@code ResonanceTableBlockEntity#getNetworkFilterText}）按的也是这个口径。那里若也套上
     * 「只认自己」，一张台子上闲置的过滤器就会悄悄让整张网络开始放行所有共振过滤器，而显示板上
     * 什么都不写 —— 判定和显示就对不上了。
     * <p>
     * 插在过滤槽里则不同：那一份规则指的就是这个物品本身，机器问的是"这份过滤器认什么"。
     * 没有可读的网络时它认自己，是玩家直接看得见、也解释得清的一种确定行为。
     * {@link #RESOLVE_DEPTH} 正好能分开这两种场合：外层的网络解析会先加一，之后才去问解析出来的
     * 那些过滤器，于是深度大于零就说明这是别人链上的调用。
     */
    private boolean filtersItself() {
        return !ResonanceFilterItem.isTuned(item()) && RESOLVE_DEPTH.get() == 0;
    }

    /**
     * 解析结果里有没有「真规则」。全是空 {@code FilterItemStack} 说明台面一张都没放东西 ——
     * 那是 {@link #resolveAllUncached} 用哨兵表达的「全集」，不是一条会命中一切的规则，
     * 所以不参与黑名单取反（否则黑名单会把「台面空着」翻成「什么都不放行」）。
     */
    private static boolean hasRules(List<FilterItemStack> filters) {
        for (FilterItemStack filter : filters)
            if (!filter.isEmpty())
                return true;
        return false;
    }

    /**
     * 判定入口的统一护栏，{@link #RESOLVING_NETWORKS} 的记账就发生在这里。
     * <p>
     * 两道护栏任意一道触发（自己绑的网络已经在这条链上 = 环，或链条长得过了头），
     * 都直接返回 {@code blocked}，当作"读不到"。
     */
    private <T> T guarded(Supplier<T> body, T blocked) {
        if (RESOLVE_DEPTH.get() >= MAX_RESOLVE_DEPTH)
            return blocked;

        UUID network = item().get(ModDataComponents.RESONANCE_NETWORK.get());
        Set<UUID> chain = RESOLVING_NETWORKS.get();
        if (network != null && !chain.add(network))
            return blocked;

        RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() + 1);
        try {
            return body.get();
        } finally {
            RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() - 1);
            if (network != null)
                chain.remove(network);
        }
    }

    /**
     * 基类的 {@code fluid()} 有个 {@code fluidExtracted} 懒加载缓存标志，动态场景下那个缓存
     * 会把「第一次问到的流体」一直留着，所以必须绕开、直接委托给每次现场解析出来的对象。
     * <p>
     * 网络里可能有多个流体过滤器，这里取<b>第一个非空</b>的。调用方是流体阈值开关那类
     * "把过滤器当流体样本来读"的场景，并集在流体上没有明确含义，取第一个是最不意外的。
     */
    @Override
    public FluidStack fluid(Level level) {
        return guarded(() -> {
            for (FilterItemStack filter : resolveAll(level)) {
                FluidStack fluid = filter.fluid(level);
                if (!fluid.isEmpty())
                    return fluid;
            }
            return FluidStack.EMPTY;
        }, FluidStack.EMPTY);
    }

    // ==================== 解析 ====================

    /**
     * 把网络里所有共振台当下的过滤器收集起来。返回空表表示「读不到」，
     * 调用方一律判不通过。
     */
    private List<FilterItemStack> resolveAll(Level world) {
        if (world == null)
            return List.of();

        long tick = world.getGameTime();
        if (tick == cachedTick && world == cachedLevel)
            return cached;

        List<FilterItemStack> resolved = resolveAllUncached(world);
        cachedTick = tick;
        cachedLevel = world;
        cached = resolved;
        return resolved;
    }

    private List<FilterItemStack> resolveAllUncached(Level world) {
        // 注意：基类的 filterItemStack 字段是 private，只能走公开的 item() 取。
        UUID network = item().get(ModDataComponents.RESONANCE_NETWORK.get());
        if (network == null)
            return List.of();                   // 没绑网络（走到这里说明不是过滤槽直接问的那一份，见 filtersItself）

        List<FilterItemStack> filters = new ArrayList<>();
        int reachable = 0;   // 够得着的台子数
        // 客户端读的是另一张表（CLIENT_LINKS），两边各自由 lazyTick 里的 keepAlive 维护。
        // 这里不碰 getBlockEntity，也就不会为了一个过滤器去加载区块。
        Collection<LogisticallyLinkedBehaviour> links = LogisticallyLinkedBehaviour.getAllPresent(
                network, false, world.isClientSide);

        // 远距离时客户端那张表是空的：CLIENT_LINKS 只由<b>被 tick 到的</b>方块实体续期
        // （{@code LogisticallyLinkedBehaviour#initialize} 在客户端直接 return，只有
        // lazyTick → keepAlive 会往里写），台子的区块没加载就没人给它续。这时改用服务端
        // 在响应仓储请求时推过来的解析结果 —— 否则仓储界面的分组会把所有物品塞进「未分类」。
        if (links.isEmpty() && world.isClientSide)
            return fromServerSnapshot(network);

        for (LogisticallyLinkedBehaviour link : links) {

            // 再核一次 id：Create 的 LogisticallyLinkedBehaviour.remove() 只清服务端那张表
            // （CLIENT_LINKS 是私有的，够不到），所以客户端那张旧网络的条目要等 400 tick 才自然过期。
            // 不加这道校验的话，刚并到新网络的台子会在旧网络的过滤器里多存活 20 秒。
            if (!link.freqId.equals(network))
                continue;

            if (!(link.blockEntity instanceof ResonanceTableBlockEntity table))
                continue;

            reachable++;
            ItemStack onTable = table.getFilterSource();
            if (onTable.isEmpty())
                continue;   // 空台面本身不产出过滤器，也不能往 filters 里塞"接受一切"——
                            // 那样一张空台子就能把整个网络放开，别的台子放什么都白搭

            // 台面上放的是共振过滤器也<b>照样解析</b>：它绑的是别的网络时，
            // 这一句就是"读别人在过滤什么"——跨网络的兼容性全靠它。
            // 绑的是本网络、或者没绑网络时它自己读不出东西，由判定入口的
            // RESOLVING_NETWORKS 护栏判成"读不到"。这里刻意不按物品类型特判，
            // 一特判兼容性就没了，而环本来也不该由"物品类型"来识别。
            //
            // 完整镜像：台面上放列表过滤器就得到它的白/黑名单 + NBT 开关 + 18 格内容，
            // 放属性过滤器就得到它的属性筛选，放普通物品就退化成「只匹配这个物品类型」
            // （基类 FilterItemStack 走 FilterItem.testDirect → ItemHelper.sameItem）。
            //
            // 必须传副本：FilterItemStack.of 对 FilterItem 会调 trimFilterComponents，
            // 那个方法会 remove 掉 ENCHANTMENTS 与 ATTRIBUTE_MODIFIERS —— 而 getFilterSource()
            // 返回的是台面上那个物品栈本身，直接传进去等于把台上物品的附魔和属性修饰符抹掉。
            filters.add(FilterItemStack.of(onTable.copy()));
        }

        // 一台都够不着（没加载 / 被拆）→ 什么都不通过，fail-closed，见类注释
        if (reachable == 0)
            return List.of();

        // 有实货 → 就按并集来，空台面被忽略
        if (!filters.isEmpty())
            return filters;

        // 走到这里 = 够得着的台面<b>全是空的</b> → 全集（"过滤槽里什么都没插"），
        // 用空的 FilterItemStack，它的 test 恒为 true（物品和流体都是）。
        //
        // "环"和"读不到"都不会走到这里：那种台面照样产出过滤器，只是它判不通过，
        // 所以 filters 非空、结果就是"什么都不通过"。这是有意的 —— 一张读不出规则的台子
        // 不该把整个网络悄悄变成"全部放行"。
        return List.of(FilterItemStack.empty());
    }

    /**
     * 客户端在本地读不到网络时（台子的区块没加载）用的退路：照抄服务端算好的结果。
     * <p>
     * 快照的三种情况与活链接那条路一一对应，判定语义不能走样：
     * 服务端也一台都够不着（{@code tableCount == 0}）→ 空表，fail-closed；
     * 一台够得着但台面全空（{@code rules} 空）→ 空过滤器哨兵，等于"全部放行"；
     * 否则逐条包成过滤器。
     * <p>
     * 包的时候必须传副本：{@code FilterItemStack.of} 对 FilterItem 会
     * {@code trimFilterComponents}，抹掉附魔与属性修饰符。快照里的栈是给多个判定共用的，
     * 直接传等于把缓存里的数据改掉。
     */
    private static List<FilterItemStack> fromServerSnapshot(UUID network) {
        ResonanceRulesCache.Snapshot snapshot = ResonanceRulesCache.get(network);
        if (snapshot == null)
            return List.of();                            // 服务端还没推过 → 维持 fail-closed
        if (snapshot.tableCount() == 0)
            return List.of();                            // 服务端此刻也一台都够不着
        if (snapshot.rules().isEmpty())
            return List.of(FilterItemStack.empty());      // 台面全空 = 全集

        List<FilterItemStack> filters = new ArrayList<>(snapshot.rules().size());
        for (ItemStack onTable : snapshot.rules())
            filters.add(FilterItemStack.of(onTable.copy()));
        return filters;
    }
}
