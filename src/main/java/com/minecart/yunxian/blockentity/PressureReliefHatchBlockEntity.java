package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.behaviour.PressureReliefHatchValueBoxTransform;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 泄压舱口：被风打到就把"附着容器"里的东西吐出来。
 * <p>
 * <b>风从哪来。</b>本格自己不是风源，只能去找别人的风场——沿六个轴向逐格外走，
 * 找到一台 {@link IAirCurrentSource}，看它的风场盒子（{@code airCurrent.bounds}）
 * 是否与本格相交。用"相交"而不是"包含本格中心"，是因为 Create 的气流遇到挡风方块时
 * 只推进到<span>块内一点点</span>（{@code getFlowLimit} 返回 i + 形状深度 + 1/32），
 * 盒子只切进本格一条缝，用点判断会漏掉"正对吹过来"这一整类。
 * <p>
 * <b>放不放。</b>把风向（{@code pushing ? direction : direction.getOpposite()}）与舱口的
 * {@code FACING}（背离依附面那一侧）比一比：
 * <ul>
 *   <li>垂直于朝向——风从侧面扫过网罩，放；</li>
 *   <li>沿朝向朝外——相当于把舱口里的东西往外抽，放；</li>
 *   <li>沿朝向朝里（吹入）——气压把舱口压住，不放。</li>
 * </ul>
 * <p>
 * <b>分散网（喷嘴）。</b>喷嘴自己不是风源，它把背后那台机器的气流铺成<b>以喷嘴为中心的立方体风场</b>
 * （边长 = 背后那台风源的气流长度、半边长是它的一半，见 {@code NozzleBlockEntity.calcRange} 与
 * {@code lazyTick} 里的 {@code inflate(range / 2f)}）。所以补了一条独立的
 * 找人路径：以本格为心在附近找喷嘴，顺着喷嘴朝向把它背后那台风源点出来，再看场盖不盖得住本格
 * （{@link #reachThrough}）。风向与转速仍取那台风源的 {@code AirCurrent}——场是绕它的轴线铺开的，
 * 吹/吸、转速快慢都跟它走。
 * <p>
 * 这条路是<b>必需</b>的，因为 Create 的 {@code AirCurrent} 完全不认喷嘴（全量搜过：只有
 * {@code NozzleBlockEntity} 自己读风源，气流不会因为喷嘴而加长或变成锥形），沿轴扫描只会
 * 看到一台离轴的风机、什么也看不到。
 * <p>
 * 顺带一提：泄压舱口自己的碰撞箱在垂直于朝向的方向上是"有洞的"（网罩那圈中间是空的），
 * Create 的 `findMaxDepth` 判定有洞就整格放行，所以侧风是真的穿过本格，不是吹到脸上。
 * <p>
 * <b>吐多少、吐哪样。</b>一批只吐<b>一种</b>物品，数量最多 {@link #VENT_AMOUNT} 个（<b>16</b>，
 * 对齐原版溜槽），再受这一样物品自己的堆叠数压一头（16 堆叠的东西一次最多 16 个）。
 * 容器里混装时按槽位顺序挑第一种能吐的，这一批就只吐它；下一批才轮到别的，与溜槽一致。
 * 取货走 Create 的 {@code ItemHelper.extract}（溜槽 / 智能溜槽用的同一个函数），
 * 它只会把同种同组件、且加起来不超过一组的堆叠并进手里，所以不会出现"一次吐 n 个、
 * 而这 n 个不是一种物品"。
 * 侧面那个槽<b>只做过滤</b>，没有数量控件 —— 要调"每次吐几个"、并且能把上限抬到
 * {@link #SMART_VENT_AMOUNT}（64）的是子类
 * {@code SmartPressureReliefHatchBlockEntity}（智能泄压舱口），它把数量滑块加回来，
 * 并通过覆写 {@link #batchSize(int)} / {@link #requiresFullBatch()} 接上这里的吐货逻辑。
 * 节奏跟着风源转速走（间隔 = max(2, 100/转速) tick，与动力吸尘器吹出同一套公式）；
 * 风源不是动能方块（比如喷嘴后面那台机器缺动力、或者干脆是另一台吸尘器）时退回
 * {@link #FALLBACK_INTERVAL}。
 */
public class PressureReliefHatchBlockEntity extends SmartBlockEntity {

    /** 风场扫描间隔（tick）。风起风落最多迟这么久，一秒足够 */
    private static final int SCAN_INTERVAL = 20;

    /** 风源给不出转速时的兜底节奏（tick / 批） */
    private static final int FALLBACK_INTERVAL = 10;

    /**
     * 离轴找喷嘴时的搜索半径：<b>20</b> 格，与 Create 鼓风机的最大推送/抽吸距离同档
     * （{@code AllConfigs.server().kinetics.fanPushDistance} / {@code fanPullDistance} 默认都是 20）。
     * <p>
     * 这里搜的是"喷嘴可能坐在哪"，不是场本身：喷嘴那颗场的<b>边长</b>是背后风源的
     * {@code getMaxDistance()}，往各方向只铺出一半（见 {@link #probeNozzle}），默认满速时半边长 10 格。
     * 取 20 是留了余量（风源配置调大时半边长也跟着大），扫出来的每一格都还要过
     * {@link #probeNozzle} 的半边长判定，多搜不会放宽结果。
     * <p>
     * 20 格全扫一次是 41³ ≈ 6.9 万格，看着吓人，但这条重扫路径<b>只在两种时候走</b>：
     * 舱口还没记住风源，或者记住的那股风变了（风源被拆、停机、转向、喷嘴没了）。
     * 平常每次 {@link #SCAN_INTERVAL} 的复查只顺着记下来的锚点查两三格（见 {@link #revalidate}），
     * 所以稳态开销是 O(1)，与这个常量无关。
     */
    private static final int NOZZLE_SCAN_RADIUS = 20;

    /**
     * 离轴找喷嘴的间隔（tick）：<b>100</b>，比常规的 {@link #SCAN_INTERVAL} 长五倍。
     * <p>
     * 单独给它一条慢节奏，是因为它和轴扫描根本不是一个量级——轴扫描最多走 6×20 格且找到就停，
     * 这一条在没喷嘴时要老老实实走完 41³ ≈ 6.9 万格。摊到 5 秒一次，长期开销就只剩
     * 每秒 1.4 万次状态查询；而"把喷嘴摆到舱口旁边"本来就是个手工动作，五秒内生效完全够。
     */
    private static final int NOZZLE_SCAN_INTERVAL = 100;

    /**
     * 本类一批的上限：<b>16 个</b>，对齐原版溜槽（{@code ChuteBlockEntity.getExtractionAmount()} 也是 16）。
     * 它同时是混装时取"最小堆叠数"的初值，所以 64 堆叠的物品在这里也只会一次吐 16 个。
     */
    protected static final int VENT_AMOUNT = 16;

    /**
     * 智能舱口（带数量滑块）才够得着的天花板：<b>64 个</b>，对齐智能溜槽
     * （{@code SmartChuteBlockEntity.getExtractionAmount()} 在滑块未调时兜底 64）。
     * 基类不带滑块，碰不到这个数。
     */
    protected static final int SMART_VENT_AMOUNT = 64;

    /** 侧面 4 个槽共用同一个过滤器，只有过滤通过的物品才会被吐出来；子类要用它读数量 */
    protected FilteringBehaviour filtering;

    /**
     * 当前"打得到本格"的风场，null = 没风；每 {@link #SCAN_INTERVAL} tick 复查一次。
     * 用 {@link #findOpeningAirCurrent()} / {@link #revalidate} 拿，别直接用（那两条路会先做各种否决判定）。
     * <p>
     * 它同时是"锚点"的一半：{@code anchor} 记下这股风是穿过哪个喷嘴铺开的，{@code anchorSpan}
     * 记下当时本格到那个喷嘴的距离。有了这两样，下次复查只需要看那么一两格，
     * 不用把 {@link #NOZZLE_SCAN_RADIUS} 见方的范围再扫一遍。
     */
    private AirCurrent activeCurrent;

    /** 风向是穿过喷嘴（分散网）铺开的时，记下喷嘴所在格；直线吹过来的是 null */
    @Nullable
    private BlockPos anchor;

    /** 本格中心到 {@link #anchor} 的距离；直线风或还没找到风时是 {@link Float#NaN} */
    private float anchorSpan = Float.NaN;

    /**
     * {@link #anchor} 那格喷嘴的 {@code FACING}，也就是"从风源指向喷嘴"的方向。
     * 复查时要靠它把风源那格反推回来（{@code anchor.relative(anchorDirection)}），
     * 每次重新读方块状态也行，但记下来更省事、也免得朝向被扳走时白算一次。
     */
    private Direction anchorDirection = Direction.NORTH;

    private int scanCooldown;
    private int ventCooldown;
    private int nozzleScanCooldown;

    public PressureReliefHatchBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PRESSURE_RELIEF_HATCH.get(), pos, state);
    }

    /** 子类注册自己的方块实体类型用（{@code BlockEntityType.Builder} 认的是那个两参构造器） */
    protected PressureReliefHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new PressureReliefHatchValueBoxTransform())
                .withCallback(stack -> sendData());
        // 数量滑块只有子类（智能泄压舱口）要：本类一批固定，滑块只会让人以为能调
        if (showsAmountSlider())
            filtering.showCount();
        filtering.setLabel(Component.translatable(filterLabelKey()));
        behaviours.add(filtering);
    }

    /** 侧面槽上是否带"数量"滑块。泄压舱口不带；智能泄压舱口覆写成 true */
    protected boolean showsAmountSlider() {
        return false;
    }

    /**
     * 是否被红石锁住（锁住就不吐货）。泄压舱口没有红石锁，恒 false；
     * 智能泄压舱口覆写成读 {@code POWERED}。
     */
    protected boolean isLocked() {
        return false;
    }

    /** 侧面过滤槽的标题翻译键 */
    protected String filterLabelKey() {
        return "create_crystal_industry.pressure_relief_hatch.filter";
    }

    @Override
    public void tick() {
        super.tick();

        // ticker 两端都挂（客户端要靠它触发 SmartBlockEntity 的 initialize），
        // 但找风场与吐货是纯服务端的事
        if (level == null || level.isClientSide)
            return;

        // 红石锁：不吐货。顺手把风场缓存清掉，免得解锁后拿着一个过期风场先用一拍
        if (isLocked()) {
            forgetCurrent();
            return;
        }

        if (scanCooldown-- <= 0) {
            scanCooldown = SCAN_INTERVAL;
            // 顺着上次记下来的锚点复查（只查两三格）才是常态；这条空手才值得重扫
            if (revalidate() == null)
                findOpeningAirCurrent();
        }

        // 离轴找喷嘴单独走一条慢节奏：它要在 41³ 格里翻，比轴扫描贵三个数量级。
        // 只在"本格压根没有风"时才算数——有锚点的话轴扫描已经能把直线风续上，
        // 而锚点本身由每 SCAN_INTERVAL 一次的 revalidate 盯着，不需要再翻一遍邻居。
        // 放在扫描块外面递减，是为了让这条节奏不被 scanCooldown 的相位带着跳。
        if (nozzleScanCooldown > 0)
            nozzleScanCooldown--;
        if (activeCurrent == null && nozzleScanCooldown <= 0) {
            nozzleScanCooldown = NOZZLE_SCAN_INTERVAL;
            nozzleCoverageFrom(NOZZLE_SCAN_RADIUS);
        }

        AirCurrent current = activeCurrent;
        if (current == null)
            return;

        // 记住的风场可以一直用着不复查（每 SCAN_INTERVAL 才验一次），但"吐货"这一下必须
        // 用当帧的风场状态把关：风源被红石锁住时气流会当场塌掉，这里不挡住就会多吐一批。
        if (!canStillVent(current)) {
            forgetCurrent();
            return;
        }

        if (ventCooldown > 0) {
            ventCooldown--;
            return;
        }
        ventCooldown = ventInterval(current);
        vent();
    }

    // ==================== 找风场 ====================

    /**
     * 沿六个轴向找一台"风场打到本格、而且风向能把舱口吹开"的机器（判定见 {@link #opens}）。
     * 找不到返回 null。
     * <p>
     * 逐格外走而不是直接查邻居：鼓风机可以隔着十几格打到这儿，风场是细长的一条，
     * 只有沿轴线找才找得到（风场不会拐弯）。走满一个"推送/抽吸距离"仍没找到就停——
     * 更远的风不可能够到本格。
     * <p>
     * 注意是"第一台<b>合格</b>的"而不是"第一台"：同时有两股风打到本格时（一股吹入、
     * 一股侧扫），谁先被扫到不该决定舱口开不开，只要有一股合格就该开。
     * <p>
     * 六个轴都空手而归时不再往下找——离轴喷嘴由 {@link #nozzleCoverageFrom} 按
     * {@link #NOZZLE_SCAN_INTERVAL} 的慢节奏单独负责，不塞进这条每 20 tick 一次的路径里。
     */
    private void findOpeningAirCurrent() {
        AABB self = new AABB(worldPosition);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int range = scanRange();

        for (Direction direction : Direction.values()) {
            cursor.set(worldPosition);
            for (int step = 1; step <= range; step++) {
                cursor.move(direction);
                if (!level.isLoaded(cursor))
                    break;

                // 喷嘴要先于风源判定：它自己不是风源，但它把风铺成一个方场
                if (isNozzle(cursor)) {
                    if (nozzleAhead(cursor.immutable(), direction))
                        return;

                    // 够不着时：喷嘴只挡得住"风要从它身上穿过去"这条轴。喷嘴朝向与扫描轴
                    // 不同轴，说明正扫的是它的侧脸，风是横向流过去的、并不穿过它，继续往后找。
                    //（这正是"一台鼓风机挂一排喷嘴铺成一片"的摆法：每个喷嘴对同轴的舱口都是
                    // 侧脸，背后那条直线上的鼓风机仍然打得到，不能在这儿把扫描掐断。）
                    BlockState state = level.getBlockState(cursor);
                    if (state.hasProperty(NozzleBlock.FACING)
                            && state.getValue(NozzleBlock.FACING).getAxis().equals(direction.getAxis()))
                        break;
                    continue;
                }

                if (!(level.getBlockEntity(cursor) instanceof IAirCurrentSource source))
                    continue;
                AirCurrent current = source.getAirCurrent();
                if (current == null || source.isSourceRemoved())
                    continue;
                if (current.bounds == null || !current.bounds.intersects(self))
                    continue;
                if (opens(current)) {
                    remember(current, null);
                    return;
                }
            }
        }
    }

    /**
     * 轴外找喷嘴：以本格为心、在 {@code radius} 见方的范围内逐格试"假设这一格里坐着喷嘴"。
     * <p>
     * 和轴扫描是<b>反过来</b>的：那边是"从本格往外走，碰到什么算什么"；这边是"假设某格是喷嘴，
     * 问它铺开的场盖不盖得住本格"。之所以两个方向都得有——喷嘴必须挂在风源正前方
     * （{@code NozzleBlock.canSurvive} 卡死），而那台风源完全可以整个待在舱口六个轴的外面。
     * <p>
     * 逐格走，<b>步长就是 1</b>：场的边长是 {@code getMaxDistance()} 这个连续量，隔格采样会漏掉
     * 恰好站在边界外侧一格的位置。贵是贵，但只有"本格还没记住任何风"或"记的那股风没了"时才会
     * 走到这儿，平常每次复查都停在 {@link #revalidate} 的两三格上。
     * <p>
     * 每格要试六个方向，而不是"由偏移量猜一个方向"：偏移是 (1,1,1) 这种斜向时，
     * {@code Direction.getNearest} 只会吐出其中一条轴，反推出来的"风源那格"就指错了。
     * 六个方向挨个试才与喷嘴的实际摆法对得上（它只可能与风源共面相邻）。
     */
    private void nozzleCoverageFrom(int radius) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0)
                        continue;
                    candidate.setWithOffset(worldPosition, dx, dy, dz);
                    if (!isNozzle(candidate))
                        continue;

                    // 六个方向挨个试，而不是"由偏移量猜一个方向"：偏移是 (1,1,1) 这种斜向时，
                    // Direction.getNearest 只会吐出其中一条轴，反推出来的"风源那格"就指错了。
                    for (Direction awayFromFan : Direction.values()) {
                        if (reachThrough(candidate, awayFromFan) != null)
                            return;
                    }
                }
            }
        }
    }

    /** 这一格是喷嘴（分散网）吗——只当粗筛，真正要读的是它背后那台风源 */
    private boolean isNozzle(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof NozzleBlock;
    }

    /**
     * 按锚点复查：只查那么两三格，够用就沿用上次那股风，不动全局缓存。
     * <p>
     * 这是稳态下唯一会跑的那条路。之所以能这么省——舱口一旦被吹到，吹它的那台机器就在一个
     * <b>具体的坐标</b>上，风往哪边吹、够不够长都可以当场问它，不需要把周围再翻一遍。
     * <p>
     * 复查的东西依次是：录下来的那支 {@link AirCurrent} 还是本格记着的那支（风源重建过就换对象了，
     * 见 {@code AirCurrent.rebuild}）、还在吹、风速不为零、风向仍能吹开本格、风场仍压着本格；
     * 穿过喷嘴来的还要多确认喷嘴没被拆、朝向没被扳过、本格仍在那颗场的边长内。
     *
     * @return 仍然有效的那支风场；锚点失效则返回 null（调用方这才去做全量重扫）
     */
    @Nullable
    private AirCurrent revalidate() {
        AirCurrent current = activeCurrent;
        if (current == null || !isVentCurrent(current))
            return null;

        BlockPos anchorPos = anchor;
        if (anchorPos == null)
            return current;

        float span = anchorSpan;
        if (Float.isNaN(span))
            return null;
        // 记的距离比检索半径还远，只可能是记错了（换过配置、换过维度），别当真
        if (span > NOZZLE_SCAN_RADIUS)
            return null;
        if (!isNozzle(anchorPos) || !opens(current))
            return null;

        // 风源重建时会换一支新的 AirCurrent 对象，所以这里必须重新问一次而不是直接沿用引用
        if (!(level.getBlockEntity(anchorPos.relative(anchorDirection)) instanceof IAirCurrentSource source))
            return null;
        if (source.getAirCurrent() != current || source.isSourceRemoved())
            return null;

        // 场会随转速伸缩，所以每次复查都拿"现在"的边长重量一遍，不能只看记下来的那次。
        // 比的是半边长：Create 把 getMaxDistance() 当立方体场的边长（inflate(range / 2f)），
        // 场往各方向只铺出一半。
        float fieldLength = Math.max(0, source.getMaxDistance());
        if (fieldLength < 0.25f || span > fieldLength * 0.5F)
            return null;

        return remember(current, anchorPos, anchorDirection, span);
    }

    /**
     * 正前方这一格是喷嘴时，它铺开的场够不够得到本格。
     * <p>
     * 只有<b>喷嘴正对着本格</b>才算数：{@code FACING} 是喷嘴背离风源的那一面，所以它必须与
     * 扫描方向相反。朝向对不上的话，本格落在的是它的侧脸或背面——那不是场盖住本格，
     * 只是风从旁边路过。
     * <p>
     * 具体的风源查证与场判定都在 {@link #reachThrough} 里（离轴那条路走的是同一个函数）。
     *
     * @param nozzlePos 喷嘴所在格
     * @param direction 扫描方向（从本格指向喷嘴，正好等于"由风源指向喷嘴"）
     * @return 记下了锚点就返回 true
     */
    private boolean nozzleAhead(BlockPos nozzlePos, Direction direction) {
        BlockState state = level.getBlockState(nozzlePos);
        if (!state.hasProperty(NozzleBlock.FACING))
            return false;
        if (state.getValue(NozzleBlock.FACING) != direction.getOpposite())
            return false;
        return reachThrough(nozzlePos, direction) != null;
    }

    /**
     * 试一格"喷嘴铺开的场"——假设这一格里坐着喷嘴、它背对风源那一侧（{@code awayFromFan}
     * 的反方向）的那格是风源，问这颗场盖不盖得住本舱口。
     * <p>
     * <b>场的形状是立方体，不是球。</b>边长 = 背后那台风源的 {@code getMaxDistance()}
     * （{@code NozzleBlockEntity.calcRange()} 返回的就是它），<b>半边长是它的一半</b>；
     * 判定用<b>各轴差值的最大值</b>与半边长比大小。这一口径与 Create 挑实体的写法逐字一致
     * （{@code NozzleBlockEntity.lazyTick} 用 {@code new AABB(center, center).inflate(range / 2f)}），
     * 吸入时撒粒子也是 {@code offsetRandomly(center, random, range / 2)}——场从喷嘴往各方向
     * 只铺出 range/2 格，拿 range 当半边长会让舱口在网罩外面老远就假装有风。
     * <p>
     * 顺带一提，喷嘴推向实体的那段用的是欧氏距离（{@code distance > range} 就撒手），
     * 所以官方在立方体角上其实不推实体。本类不跟这一条：舱口是"感应器"，
     * 场覆盖到就该有反应，而这里的"覆盖"已经按半边长量过。
     * <p>
     * 半边长随风源自己的 {@code getMaxDistance()} 变化（与 {@code NozzleBlockEntity.calcRange()}
     * 逐字一致），所以转速越低场越小，跟看到的喷嘴动画对得上。
     * <p>
     * <b>"放不放"照旧按风场轴线判</b>（{@code opens}），这里只解决"在不在场里"。
     * 侧面舱口不会因此被挡：喷嘴场是立方体，侧向的偏移让它的 {@code FACING} 与风场轴
     * 必然垂直，正好落进 {@code opens} 的"垂直=放行"那一支。
     *
     * @param nozzlePos   待试的喷嘴格
     * @param awayFromFan 由风源指向喷嘴的方向（= 喷嘴的 {@code FACING}）。风源就在
     *                    {@code nozzlePos.relative(awayFromFan)} 的反方向那一格
     * @return 够得着就 {@link #remember} 并返回那支风场，否则 null
     */
    @Nullable
    private AirCurrent reachThrough(BlockPos nozzlePos, Direction awayFromFan) {
        AirCurrent current = probeNozzle(nozzlePos, awayFromFan);
        if (current == null)
            return null;
        float span = cubeDistance(VecHelper.getCenterOf(worldPosition), VecHelper.getCenterOf(nozzlePos));
        return remember(current, nozzlePos, awayFromFan, span);
    }

    /**
     * 只做判定、不记锚点：这个喷嘴背后是风源吗、风够不够长罩到本格、以及"放不放"。
     *
     * @return 通过就返回那支风场，否则 null
     */
    @Nullable
    private AirCurrent probeNozzle(BlockPos nozzlePos, Direction awayFromFan) {
        // 六个朝向里只有一个背后是风源，其余五个走到这儿就回头，属正常试错
        if (!(level.getBlockEntity(nozzlePos.relative(awayFromFan.getOpposite())) instanceof IAirCurrentSource source))
            return null;

        AirCurrent current = source.getAirCurrent();
        if (current == null || source.isSourceRemoved())
            return null;

        // 场长取的是<b>转速量</b> {@code source.getMaxDistance()}，与 {@code NozzleBlockEntity.calcRange()}
        // 逐字一致（满速 20 格、32 转速 10.5 格）。Create 把它当立方体场的<b>边长</b>
        // （{@code NozzleBlockEntity.lazyTick} 挑实体用 {@code inflate(range / 2f)}），
        // 所以场往各方向只铺出它的一半，这里比的也是半边长。
        //
        // 这里<b>不能</b>改用 {@code current.maxDistance}：那是被 {@code AirCurrent.getFlowLimit}
        // 按沿途方块截短过的长度，而喷嘴自己那颗方块的碰撞箱就会把风扇气流压到 {@code 1/32}
        // （实测 direction=south、maxDistance=0.03125）。于是"场明明在、读出来却近乎 0"，
        // 侧面舱口会被误判成没风。Create 自己也用 0.25 当塌场门槛（{@code AirCurrent.java:213}），
        // 但那说的是"轴线上被挡住多远"，不是喷嘴铺开的场有多大。
        float fieldLength = Math.max(0, source.getMaxDistance());
        if (fieldLength < 0.25f)
            return null;

        // 场的形状是立方体：比各轴差值的最大值，而不是欧氏距离
        float span = cubeDistance(VecHelper.getCenterOf(worldPosition), VecHelper.getCenterOf(nozzlePos));
        if (span > fieldLength * 0.5F)
            return null;

        // "放不放"照旧按风场轴线判（见 {@link #opens}）——这里只解决"在不在场里"。
        // 侧面舱口不会因此被挡：喷嘴场是立方体，侧向的偏移让它的 {@code FACING} 与风场轴
        // 必然垂直，正好落进"垂直=放行"那一支。
        if (!opens(current))
            return null;

        return current;
    }

    /** 两个点之间的"立方体距离"——各轴差值的最大值，即在这颗场里比的是最远的那条轴 */
    private static float cubeDistance(Vec3 from, Vec3 to) {
        return (float) Math.max(Math.abs(from.x - to.x),
                Math.max(Math.abs(from.y - to.y), Math.abs(from.z - to.z)));
    }

    /** 接上一股直线吹来的风场：没有锚点 */
    private AirCurrent remember(AirCurrent current, @Nullable BlockPos anchorPos) {
        return remember(current, anchorPos, Direction.NORTH, anchorPos == null
                ? Float.NaN
                : cubeDistance(VecHelper.getCenterOf(worldPosition), VecHelper.getCenterOf(anchorPos)));
    }

    /**
     * 接上一股穿过喷嘴铺开的风场，并记下喷嘴、喷嘴朝向、以及当时算出的距离。
     * <p>
     * {@code anchorDirection} 存的是"从风源指向喷嘴"的方向，也就是喷嘴的 {@code FACING}——
     * 复查时要靠它反推风源那格（{@code anchor.relative(anchorDirection)}），所以必须是
     * {@code reachThrough} 实际用的那个方向，不能事后由坐标差去猜。
     */
    private AirCurrent remember(AirCurrent current, BlockPos nozzlePos, Direction awayFromFan, float span) {
        activeCurrent = current;
        anchor = nozzlePos;
        anchorDirection = awayFromFan;
        anchorSpan = span;
        return current;
    }

    /** 忘掉记着的那股风（红石锁住时用） */
    private void forgetCurrent() {
        activeCurrent = null;
        anchor = null;
        anchorSpan = Float.NaN;
    }

    /** 这支风场还是本格记着的那支吗，而且还在吹 */
    private boolean isVentCurrent(AirCurrent current) {
        return activeCurrent == current && current.source != null && current.source.getSpeed() != 0;
    }

    /**
     * 这一 tick 还该不该吐货。{@link #isVentCurrent} 只看<b>风源转速</b>，而转速归零与
     * {@code AirCurrent} 塌掉是两步：{@code rebuild()} 在 {@code source.getSpeed() == 0} 时把
     * {@code bounds} 收成零点、{@code segments} 清空，转速却是当帧就读到的。本方法把"风场真的没了"
     * 也一并挡住，于是吸尘器被红石锁住（或满仓停转）时，本格在下一个 {@link #SCAN_INTERVAL}
     * 复查之前就<b>立刻</b>闭嘴，不会再多吐这一批。
     * <p>
     * 风源那只 {@code AirCurrent} 是方块实体构造时就 new 出来的（只 rebuild、不换对象），
     * 所以 {@code source.getAirCurrent() == current} 这条恒等判定可以当"这支风场还挂在风源上"用。
     */
    private boolean canStillVent(AirCurrent current) {
        if (!isVentCurrent(current))
            return false;
        if (current.bounds == null || current.bounds.getXsize() <= 0 || current.bounds.getYsize() <= 0
                || current.bounds.getZsize() <= 0)
            return false;
        return current.source.getAirCurrent() == current;
    }

    /** 扫描半径：取配置里鼓风机推送/抽吸距离的上限（默认 20） */
    private static int scanRange() {
        return Math.max(AllConfigs.server().kinetics.fanPushDistance.get(),
                AllConfigs.server().kinetics.fanPullDistance.get());
    }

    /**
     * 这股风能不能把舱口吹开。风向取的是空气<span>实际流动</span>的方向：
     * {@code AirCurrent.direction} 记的是风源"出风的那一面"，抽风时气流是反过来的，
     * 所以要用 {@code pushing} 翻一下。
     * <p>
     * 过喷嘴（分散网）的风也走这一套：场是绕着风源轴线铺开的，轴线没变，
     * 所以"沿轴往外抽就放、往里吹就压住"的判定照用。
     * <p>
     * 塌掉的风场直接判"不通过"。{@code AirCurrent.direction} 是可空的——{@code rebuild()} 只在
     * 风源有转速时才给它赋值，停机（或方块刚放上去还没 tick 过）时它是 null。同一支风场的
     * {@code maxDistance} 也会同时归零，但那两条判定不该互相依赖，这里自己挡住。
     * <p>
     * <b>它只判"风相对于本格的来向"，判不了"本格在不在场里"</b>——后者是纯几何，沿轴那条路
     * 靠 {@code bounds} 相交，喷嘴那条路靠 {@link #cubeDistance} 比边长。两件事分开做，
     * 所以喷嘴场里的侧向舱口能靠"垂直分支"放行，而不会被风场轴线误判成"吹在脸上"。
     */
    private boolean opens(AirCurrent current) {
        Direction origin = current.direction;
        if (origin == null)
            return false;

        Direction actualDirection = current.pushing
                ? origin
                : origin.getOpposite();
        Direction facing = getBlockState().getValue(AmethystClusterBlock.FACING);

        // 同一个轴上：只有"往外抽"才放，"往里吹"压住不放
        if (actualDirection.getAxis() == facing.getAxis())
            return actualDirection == facing;
        // 垂直：风从侧面扫过网罩
        return true;
    }

    /** 吐出间隔：与动力吸尘器吹出同款（间隔 = max(2, 100/转速) tick，转速越快越密） */
    private static int ventInterval(AirCurrent current) {
        if (current.source instanceof KineticBlockEntity kinetic) {
            float speed = Math.abs(kinetic.getSpeed());
            if (speed > 0)
                return Math.max(2, Math.round(100f / speed));
        }
        return FALLBACK_INTERVAL;
    }

    // ==================== 吐货 ====================

    /**
     * 从依附容器里取走<b>一样</b>物品，在舱口所在方块的正中心丢出去。
     * <p>
     * <b>一批只有一种物品</b>：混装容器里按槽位顺序找第一种过滤能过的物品，试成功就吐它、
     * 这一批到此为止，下一批再轮到别的（与溜槽 / 智能溜槽一致）。
     * <p>
     * 取货用 Create 的 {@link ItemHelper#extract}，模式跟溜槽同一套：
     * <ul>
     *   <li>"≤"（{@link #requiresFullBatch()} 为 false）：能取多少吐多少；</li>
     *   <li>"="（智能舱口的整批档）：这一样物品凑不满一批就整批不吐，换下一种物品再试
     *       （{@code EXACTLY} 模式凑不够直接返回空，于是外层循环继续往后找）。</li>
     * </ul>
     * 批量 = {@link #batchSize(int)} 传<b>这一样物品的堆叠数</b>进去，所以 16 堆叠的东西
     * 一批最多 16 个；{@code ItemHelper} 自己也只并"同种同组件、加起来不超一组"的堆叠，
     * 不会凑出超堆叠。
     */
    private void vent() {
        IItemHandler source = getAttachedContainer();
        if (source == null)
            return;

        // 试过的物品类型：同种同组件只试一次，避免同一批反复取同一样
        List<ItemStack> tried = new ArrayList<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack candidate = source.getStackInSlot(slot);
            if (candidate.isEmpty() || !canVent(candidate))
                continue;
            if (tried.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, candidate)))
                continue;
            tried.add(candidate.copy());

            int budget = batchSize(candidate.getMaxStackSize());
            if (budget <= 0)
                continue;

            ItemStack extracted = ItemHelper.extract(source,
                    stack -> ItemStack.isSameItemSameComponents(stack, candidate),
                    requiresFullBatch() ? ItemHelper.ExtractionCountMode.EXACTLY
                            : ItemHelper.ExtractionCountMode.UPTO,
                    budget, false);
            if (!extracted.isEmpty()) {
                dropItem(extracted);
                return;
            }
        }
    }

    /**
     * 这一批最多取几个。本类不带数量滑块，所以是固定档：{@code min(VENT_AMOUNT, groupSize)} ——
     * 也就是"最多 16 个，且不超过这一样物品的一组"。16 堆叠的东西还是 16，
     * 64 堆叠的东西也只吐 16（对齐原版溜槽那一档）。
     * 智能泄压舱口覆写它，改为"滑块上的数再被一组压一头"。
     *
     * @param groupSize 本批那一样物品的堆叠上限（{@code ItemStack#getMaxStackSize()}）
     */
    protected int batchSize(int groupSize) {
        return Math.min(VENT_AMOUNT, groupSize);
    }

    /** 是否要求"凑够一整批才吐"。只有带数量滑块的智能舱口会用到 "=" 那一档 */
    protected boolean requiresFullBatch() {
        return false;
    }

    /** 过滤器为空 = 全放行；否则只放过滤器认的物品 */
    private boolean canVent(ItemStack stack) {
        if (filtering == null)
            return true;
        if (filtering.getFilter().isEmpty())
            return true;
        return filtering.test(stack);
    }

    /** 依附方块（{@code FACING} 反方向那一格）暴露给舱口的物品容器；没有则 null */
    private IItemHandler getAttachedContainer() {
        if (level == null)
            return null;
        Direction facing = getBlockState().getValue(AmethystClusterBlock.FACING);
        BlockPos attached = worldPosition.relative(facing.getOpposite());
        // 访问方向是"依附方块朝向舱口的那个面"，与吸尘器取正前方容器同一个口径
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attached, facing);
    }

    /**
     * 掉落物生成：本格中心附近、无初速度、正常拾取延迟——与智能钻头挖出来的掉落物一字不差
     * （连那个 ±0.125 格的小抖动都照抄，免得一次吐多个时叠在一起）。
     * <p>
     * 唯一的加料是 {@link #freeDropSpot}：网罩那块碰撞箱是实心的，格心（以及抖动向下时的落点）
     * 会落在它里面，物品一生成就被碰撞箱卡住、被风一推就乱抖，所以生成前先沿朝向把落点推到
     * 碰撞箱外（仍留在本格内）。
     */
    private void dropItem(ItemStack stack) {
        Vec3 dropPosition = freeDropSpot(VecHelper.offsetRandomly(
                VecHelper.getCenterOf(worldPosition),
                level.random,
                .125f
        ));
        ItemEntity itemEntity = new ItemEntity(level, dropPosition.x, dropPosition.y, dropPosition.z, stack);
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(itemEntity);
    }

    /**
     * 把落点从碰撞箱里挪出来：从候选点开始沿舱口朝向（网罩开口那一侧）逐像素外推，
     * 直到物品的身位与方块碰撞箱不再相交。
     * <p>
     * 贴地时格心本来就在网罩顶面之上，一步都不用挪；贴墙、贴天花板时才真的会挪动，
     * 而且挪的方向就是舱口开口的方向，看起来就是"从网罩里掉出来"。推满一格仍没找到空位
     * （落点要留在本格内）就老实退回候选点——宁可卡住也不把掉落物甩到别的方块里去。
     */
    private Vec3 freeDropSpot(Vec3 candidate) {
        VoxelShape collision = getBlockState().getCollisionShape(level, worldPosition);
        if (collision.isEmpty())
            return candidate;

        Vec3 origin = Vec3.atLowerCornerOf(worldPosition);
        Vec3 local = candidate.subtract(origin);
        if (!touchesCollision(collision, local))
            return candidate;

        Direction facing = getBlockState().getValue(AmethystClusterBlock.FACING);
        Vec3 step = Vec3.atLowerCornerOf(facing.getNormal()).scale(1 / 16f);
        for (int i = 1; i <= 16; i++) {
            Vec3 moved = local.add(step.scale(i));
            // 落点必须留在本格内
            if (moved.x < 0 || moved.x > 1 || moved.y < 0 || moved.y > 1 || moved.z < 0 || moved.z > 1)
                break;
            if (!touchesCollision(collision, moved))
                return moved.add(origin);
        }
        return candidate;
    }

    /** 物品（0.25 格见方、落点在底面中心）在给定落点处会不会扎进碰撞箱 */
    private static boolean touchesCollision(VoxelShape collision, Vec3 local) {
        EntityDimensions size = EntityType.ITEM.getDimensions();
        double half = size.width() / 2d;
        AABB item = new AABB(
                local.x - half, local.y, local.z - half,
                local.x + half, local.y + size.height(), local.z + half);
        for (AABB box : collision.toAabbs()) {
            if (box.intersects(item))
                return true;
        }
        return false;
    }
}
