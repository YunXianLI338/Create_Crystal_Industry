package com.minecart.yunxian.advancement;

import java.util.function.Predicate;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.GrowthDefinition;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 成就的授予入口。
 * <p>
 * 成就分两种触发方式：
 * <ul>
 *   <li><b>纯数据包</b>（拿到物品、放下方块、原版 {@code avoid_vibration}）——JSON 自己就能判定，
 *       本类插不上手；</li>
 *   <li><b>模组自己的时刻</b>（催生成功、母岩侵蚀出矿石、唤醒监守者……）——JSON 里写
 *       {@code minecraft:impossible} 触发器，由这里在代码里授予。</li>
 * </ul>
 * 之所以用 {@code impossible} 而不是注册自定义触发器：这些时刻不带任何条件数据，
 * 注册一整套 {@code CriterionTrigger} 只为发一个空判据并不划算，而 {@code award} 的效果完全一样。
 * <p>
 * 机器类事件（催生器、钻头、吸尘器、母岩转化）大多发生在没有玩家引用的方块实体里，
 * 所以用 {@link #awardNear} 按距离发给附近的玩家——这些机器不需要有主人。
 */
public final class YunxianAdvancements {

    /** {@code minecraft:impossible} 触发器的判据名（JSON 里 criteria 的键，必须一致） */
    public static final String CRITERION = "impossible";

    /** 附近玩家的判定半径：够得着"我就在这台机器旁边"，又不至于发给另一个基地的人 */
    private static final double NEARBY_RADIUS = 24.0;

    // ==================== 成就 id（只列代码授予的那些；纯 JSON 触发的写在这里没有意义） ====================

    public static final String BUDDING_MOTHERLODE = "budding/motherlode";

    public static final String ACCEL_RUNNING = "accel/running";
    public static final String ACCEL_FIRST_GROWTH = "accel/first_growth";
    public static final String ACCEL_WITNESS_CLUSTER = "accel/witness_cluster";
    public static final String ACCEL_VANILLA_AMETHYST = "accel/vanilla_amethyst";
    public static final String ACCEL_FULL_SPEED = "accel/full_speed";
    public static final String ACCEL_GENERAL_PURPOSE = "accel/general_purpose";

    public static final String MACHINE_SILK_TOUCH = "machine/silk_touch";
    public static final String MACHINE_CLEANER_SWAP = "machine/cleaner_swap";

    public static final String GEAR_NIGHT_VISION = "gear/night_vision";
    public static final String GEAR_REVEAL = "gear/reveal";

    public static final String DEEP_SCULK_SPREAD = "deep/sculk_spread";
    public static final String DEEP_WARDEN = "deep/warden";

    // ==================== 授予 ====================

    /**
     * 把成就直接授予某个玩家。返回本次是否真的发了（已经完成过、或该成就文件不存在时返回 false）。
     * <p>
     * 成就文件不存在不是异常情况：整合包可能把这条成就覆盖掉，那时静默跳过即可，
     * 绝不能让一台正在运转的机器因为找不到成就而崩掉。
     */
    public static boolean award(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        AdvancementHolder holder = server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, path));
        if (holder == null) {
            return false;
        }
        // 先查完成状态再发：award 本身不便宜，而这些调用点里有的每 tick 都会走到
        if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
            return false;
        }

        player.getAdvancements().award(holder, CRITERION);
        return true;
    }

    /**
     * 发给 {@code pos} 附近的玩家。返回值是"有没有发出去过"，调用方通常拿它当一次性门闩：
     * 一旦为 true 就不再重复扫描（见两个催生器的邻面检查）。
     */
    public static boolean awardNear(ServerLevel level, BlockPos pos, String path) {
        double radiusSqr = NEARBY_RADIUS * NEARBY_RADIUS;
        boolean awarded = false;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) > radiusSqr) {
                continue;
            }
            awarded |= award(player, path);
        }
        return awarded;
    }

    // ==================== 催生器：区分"催出来的"与"自然长的" ====================

    /**
     * 催生器重放的那次随机刻是不是正在发生。
     * <p>
     * 服务端主线程单线程执行，随机刻也不会反过来触发另一台催生器，所以一个计数器就够；
     * 用计数而不是布尔是为了万一将来出现嵌套也不至于提前清零。
     */
    private static int accelerationDepth;

    private static boolean accelerated() {
        return accelerationDepth > 0;
    }

    /**
     * 代催生器对邻格施加一次随机刻，并标记"这次是被催出来的"。
     * <p>
     * 催生器只是重放 {@code randomTick}，被催的方块自己并不知道是谁在催——
     * 生长监听器全靠这个标记才能做出「拔苗助长」「亲眼看着它长满」这两个成就。
     */
    public static void acceleratedRandomTick(ServerLevel level, BlockPos pos) {
        accelerationDepth++;
        try {
            level.getBlockState(pos).randomTick(level, pos, level.random);
        } finally {
            accelerationDepth--;
        }
    }

    private static final Predicate<BlockState> VANILLA_AMETHYST =
            state -> state.is(Blocks.BUDDING_AMETHYST);

    /** 随机刻驱动的远不止晶簇：作物与树苗是玩家最容易撞见的代表 */
    private static final Predicate<BlockState> CROPS_AND_SAPLINGS =
            state -> state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS);

    /** 邻面有原版紫水晶母岩就发「老本行」——催生器对它一样有效，天然晶洞也能被催 */
    public static boolean awardForVanillaAmethyst(ServerLevel level, BlockPos pos) {
        return awardForNeighbor(level, pos, VANILLA_AMETHYST, ACCEL_VANILLA_AMETHYST);
    }

    /** 邻面有作物/树苗就发「通用加速器」 */
    public static boolean awardForCrops(ServerLevel level, BlockPos pos) {
        return awardForNeighbor(level, pos, CROPS_AND_SAPLINGS, ACCEL_GENERAL_PURPOSE);
    }

    private static boolean awardForNeighbor(ServerLevel level, BlockPos pos, Predicate<BlockState> matches,
                                            String path) {
        for (Direction direction : Direction.values()) {
            if (matches.test(level.getBlockState(pos.relative(direction)))) {
                return awardNear(level, pos, path);
            }
        }
        return false;
    }

    // ==================== 生长监听器（挂在 BuddingGrowthEngine 上） ====================

    /**
     * 母岩长出了新的一级。
     * <p>
     * 自然生长的母岩也能走到这里（监听器只看得到"长了"，看不到是谁催的），
     * 所以先过一道 {@link #accelerated()}：这两个成就讲的是催生器，不能靠蹲在晶洞里等出来。
     * <p>
     * 长出晶簇时两个成就一起发：天然母岩周围本就可能已经有长到一半的芽，
     * 第一次被催就可能直接补上最后一级——那时"亲眼看着它长满"不该比它的前置成就先到手。
     * <p>
     * 注意本方法在随机刻里被调用——它必须便宜，且绝不能抛异常。
     */
    public static void onBuddingGrown(ServerLevel level, BlockPos buddingPos, BlockPos grownPos,
                                      Block grown, GrowthDefinition definition) {
        if (!accelerated()) {
            return;
        }
        awardNear(level, buddingPos, ACCEL_FIRST_GROWTH);
        if (grown == definition.cluster()) {
            awardNear(level, buddingPos, ACCEL_WITNESS_CLUSTER);
        }
    }

    private YunxianAdvancements() {
    }
}
