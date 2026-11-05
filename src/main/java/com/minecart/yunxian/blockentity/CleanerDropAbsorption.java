package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Iterator;
import java.util.Map;

/**
 * 掉落物"直接入库存"：物品实体即将进入世界时，若它落在某台吸入中的吸尘器的吸取覆盖里，
 * 就跳掉实体这一步，直接把物品收进那台吸尘器的库存（{@link MechanicalCleanerBlockEntity#tryAbsorbDrop}）。
 * <p>
 * 为什么值得做：覆盖里的掉落物本来就在同一个 tick 被收走，实体只活了一瞬间，却要走完一整套实体流程
 * （登记 section、同步给附近客户端、每 tick 参与碰撞与吸取扫描）。母岩/晶簇这类产线一次破坏就掉一份，
 * 省下来的都是纯开销。
 * <p>
 * 为什么钩在 {@link EntityJoinLevelEvent} 而不是掉落事件：掉落物的来源太多
 * （原版掉落表走 {@code BlockDropsEvent}、智能钻头自己 popResource、脚本方块走
 * {@link com.minecart.yunxian.registry.ScriptedBlockDrops}、别的模组的机器各写各的），
 * 但终点都是"一个 ItemEntity 进入世界"，钩在这里一次覆盖全部，且不用改动任何一条掉落路径。
 * <p>
 * <b>怎么找到那台吸尘器</b>：两条路，都只负责"把候选机器找出来"，收不收一律由
 * {@link MechanicalCleanerBlockEntity#tryAbsorbDrop} 判定——它内部同时认轴线风场和喷嘴场
 * （{@code getNozzleCoverage}）：
 * <ol>
 *   <li>{@link #absorbByAxis}：沿六个轴逐格查方块状态，最多 6 × 20 次调色板查询。
 *       没装分散网时风场就是一条沿朝向的直线，沿轴外扫必定碰到那台机器方块。</li>
 *   <li>{@link #absorbByNozzle}：喷嘴（分散网）场里的点不在任何过吸尘器的轴线上，
 *       改问 {@link NozzleFieldIndex}——一张由吸尘器自己在 tick 里维护的
 *       "哪台机器前方挂着喷嘴、正在吸入"的小表。查表是 O(登记机器数)，而这张表通常
 *       <b>是空的</b>（没装分散网就一台都不会登记），空表一次 {@code isEmpty()} 就返回。</li>
 * </ol>
 * 早先第二条路用的是"以落点为中心扫半径 20 的立方体找喷嘴"（41³ = 68,921 格）。问题出在触发率上：
 * 这个事件对<b>世界里每一个</b>物品实体入场都会跑，而第一条路对绝大多数落点都是落空的——矿洞里挖掉的
 * 方块、怪物掉落、任何离吸尘器 20 格以外的物品。等于每次入场都付 6.9 万格，扫完多半还是"附近根本
 * 没有喷嘴"。换成登记表后，"实体压根不生成"这个收益一点没丢，每次入场的代价降到一次空表判断。
 * <p>
 * 已知不覆盖（保持原样：掉落物照常生成实体，随后由吸尘器收走，只是没省下这个实体）：
 * <ul>
 *   <li><b>无动力的吸尘器</b>——它没有风场，靠 {@link MechanicalCleanerBlockEntity#collectItemsPassive}
 *       只捡正前方紧贴的一格（{@link MechanicalCleanerBlockEntity#PASSIVE_SUCK_RANGE}）。
 *       那条路不收在这里，因为接管的前提是"这一 tick 本来就会被收走"，而无动力时
 *       {@code getSpeed()} 恒为 0，连按 tick 收实体都要等到有动力才发生。</li>
 * </ul>
 */
public final class CleanerDropAbsorption {

    /** 事件在每次实体入场时都会跑一遍，方向数组先存好，省掉 values() 的克隆 */
    private static final Direction[] DIRECTIONS = Direction.values();

    private CleanerDropAbsorption() {
    }

    /** 事件入口（在 {@code Yunxian} 的构造器里挂到游戏事件总线上） */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只处理服务端新生成的物品：客户端不持有库存，从存档读回来的实体本来就已经在吸尘器的账上了
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) {
            return;
        }
        if (!ModConfig.Common.CLEANER_DIRECT_ABSORB.get()) {
            return;
        }

        // 包裹走的是 Create 的 PackageEntity（LivingEntity，不是 ItemEntity），同样接管：
        // 吸尘器按 tick 扫的那条路也能收到它，这里只是让它和前两类物品一样"压根不生成实体"
        if (event.getEntity() instanceof PackageEntity packageEntity) {
            ItemStack box = packageEntity.getBox();
            if (!box.isEmpty() && absorbIntoSuction(level, packageEntity.position(), box)) {
                packageEntity.discard();
                event.setCanceled(true);
            }
            return;
        }

        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }

        if (absorbIntoSuction(level, itemEntity.position(), stack)) {
            // 取消入场之前先把实体标成"已移除"。实体没进过世界，但 {@code isAlive()} 仍然
            // 只看移除标志——Create 的漏斗在 {@code level.addFreshEntity} 之后会把刚建好的实体
            // 存进一个 WeakReference，下一 tick 拿 {@code isAlive()} 判"上一份还堵着出口吗"：
            // 不标移除，漏斗会一直以为出口被自己吐的那份挡着，再也不吐了。
            // 顺手也替别的"吐完看一眼实体还在不在"的机器兜住。
            itemEntity.discard();
            event.setCanceled(true);
        }
    }

    /**
     * 从掉落物所在位置找出能收下它的那台吸尘器：先沿六轴找（覆盖绝大多数落点），落空再问喷嘴场登记表。
     * <p>
     * 落点取整只取一次，两条路共用——{@code BlockPos.containing} 每次都要走一遍 Math.floor，而这里是
     * "每次物品入场"的热路径。
     */
    private static boolean absorbIntoSuction(ServerLevel level, Vec3 point, ItemStack stack) {
        BlockPos origin = BlockPos.containing(point);
        return absorbByAxis(level, point, origin, stack) || absorbByNozzle(level, point, origin, stack);
    }

    /**
     * 沿六个轴向逐格找吸尘器方块，找到第一台能收下的就收走。
     * <p>
     * 为什么"逐格找方块"这一条管得住轴线风场：没喷嘴时风场是一条以吸尘器为起点、沿朝向的
     * 直线（{@code airCurrent.bounds}），掉落物沿六轴任意方向外扫，最远 20 格
     * （{@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX}）内必定碰到那台吸尘器方块。
     * <p>
     * 为什么这里不管喷嘴场：喷嘴场里的点不在任何过吸尘器的轴线上，这一条扫不到那台机器，
     * 那部分交给 {@link #absorbByNozzle} 查登记表。换句话说，这里保持"廉价的、几乎必中的一种形状"，
     * 而把"昂贵的、几乎必不中的形状"换成一次查表，不让绝大多数普通掉落物为喷嘴付账。
     * <p>
     * 每个方向上遇到未加载的区块就停——风场不会跨过未加载的区块，方块实体也取不到。
     */
    private static boolean absorbByAxis(ServerLevel level, Vec3 point, BlockPos origin, ItemStack stack) {
        Block block = ModBlocks.MECHANICAL_CLEANER.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Direction direction : DIRECTIONS) {
            cursor.set(origin);
            for (int step = 1; step <= MechanicalCleanerBlockEntity.SUCK_RANGE_MAX; step++) {
                cursor.move(direction);
                if (!level.isLoaded(cursor)) {
                    break;
                }
                if (!level.getBlockState(cursor).is(block)) {
                    continue;
                }
                // 判定（轴线边界 / 喷嘴场 / 视线 / 过滤 / 容量）全在吸尘器那边，
                // 与按 tick 收实体那条路共用同一套口径
                if (level.getBlockEntity(cursor) instanceof MechanicalCleanerBlockEntity cleaner
                        && cleaner.tryAbsorbDrop(point, stack, cleaner.getMaxDistance())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 喷嘴（分散网）场里的落点：问 {@link NozzleFieldIndex} 有哪些机器前方挂着喷嘴，
     * 逐个按"落点到喷嘴的立方体距离不超过 {@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX} 格"
     * 粗筛（这里只是上界，真正的半边长由机器自己按转速算），命中再交给机器复判。
     * <p>
     * 为什么按喷嘴而不是按机器算距离：场的中心是喷嘴，机器的气流长度是那颗立方体场的
     * <b>边长</b>（往外铺的距离只有它的一半，见机器那边的 {@code nozzleFieldHalfExtent}），
     * 所以"落点到喷嘴的立方体距离 ≤ 上限"才是不漏的口径；按机器算会差一格。
     * <p>
     * 粗筛只用整数比较，真正的边界（视线、过滤、容量）留给 {@code tryAbsorbDrop}——这里多放进来一格
     * 没关系，那边会拦；少放就会漏收，所以半径取的是上限而不是当前真实半径。
     * <p>
     * 顺带把失效的登记项剔掉，两种：所在区块没加载了（里面的方块实体已经没了），
     * 或者区块还在但那台机器已经不在了（被拆掉、换成别的方块）。不在这里剔，这张表会随
     * "走过的地方越来越多"只长不缩——反查这一侧每几十 tick 才被调到一次，成本可以忽略。
     */
    private static boolean absorbByNozzle(ServerLevel level, Vec3 point, BlockPos origin, ItemStack stack) {
        Map<BlockPos, BlockPos> fields = NozzleFieldIndex.of(level);
        if (fields.isEmpty()) {
            return false;
        }
        int radius = MechanicalCleanerBlockEntity.SUCK_RANGE_MAX;
        Iterator<Map.Entry<BlockPos, BlockPos>> cursor = fields.entrySet().iterator();
        while (cursor.hasNext()) {
            Map.Entry<BlockPos, BlockPos> entry = cursor.next();
            BlockPos nozzlePos = entry.getValue();
            if (!level.isLoaded(nozzlePos)) {
                cursor.remove();
                continue;
            }
            if (cubeDistance(origin, nozzlePos) > radius) {
                continue;
            }
            if (!(level.getBlockEntity(entry.getKey()) instanceof MechanicalCleanerBlockEntity cleaner)) {
                // 机器已经没了（被拆掉、换成别的方块）：上面那句 isLoaded 只拦得住"区块没加载"，
                // 拦不住"区块还在但机器不在了"，所以这里顺手把登记项剔掉
                cursor.remove();
                continue;
            }
            if (cleaner.tryAbsorbDrop(point, stack, cleaner.getMaxDistance())) {
                return true;
            }
        }
        return false;
    }

    /** 立方体距离（各轴差值的最大值）：与机器那边判风场边界的口径一致 */
    private static int cubeDistance(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return Math.max(dx, Math.max(dy, dz));
    }
}
