package com.minecart.yunxian.item;

import com.minecart.yunxian.registry.ModTags;
import com.minecart.yunxian.config.ModConfig;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public final class EchoScanner {

    private EchoScanner() {
    }

    /**
     * 扫描球体范围内的命中方块。
     * <p>
     * 走「区块 → section → 逐格」而不是逐方块暴力扫：先让
     * {@link LevelChunkSection#maybeHas} 拿这一段的调色板过一遍，整段没有可能出现命中方块的
     * （纯石头、纯空气）一次比较就跳过，4096 格一格都不读。区块查表也从每方块一次降成每区块一次。
     * <p>
     * section 按「离玩家最近有多远」升序扫，结果只保留最近的
     * {@link ModConfig.Common#MAX_RESULTS} 个。触到上限时截断的是远处，而不是先扫到的那一侧；
     * 站在矿脉中间满视野都是矿时，看到的会是身边一圈，而不是一个方方正正的半边球。
     */
    public static List<BlockPos> findOres(ServerLevel level, BlockPos center, int radius, ItemStack filterStack) {

        // 过滤模式：
        // 0 = 无过滤物品，走 echo_reveals 标签
        // 1 = Create 过滤器（普通 Filter / Create 6 数据驱动的属性过滤器）
        // 2 = 普通物品（方块物品），按方块精确匹配
        int mode;
        FilterItemStack createFilter = null;

        if (filterStack == null || filterStack.isEmpty()) {
            mode = 0;
        } else {
            FilterItemStack parsed = FilterItemStack.of(filterStack);
            if (!parsed.isEmpty()) {
                mode = 1;
                createFilter = parsed;
            } else {
                mode = 2;
            }
        }

        // 从配置读取结果上限（只读一次，不在循环里重复调 get()）
        int maxResults = ModConfig.Common.MAX_RESULTS.get();
        Matcher matcher = new Matcher(level, mode, createFilter, filterStack);
        Predicate<BlockState> matcherPredicate = matcher::test;

        final int centerX = center.getX();
        final int centerY = center.getY();
        final int centerZ = center.getZ();
        int radiusSq = radius * radius;

        // 只走球体覆盖到的区块与 section（y 上球体通常只跨 3~4 段，不必过整列 24 段）
        int sectionX0 = SectionPos.blockToSectionCoord(centerX - radius);
        int sectionX1 = SectionPos.blockToSectionCoord(centerX + radius);
        int sectionZ0 = SectionPos.blockToSectionCoord(centerZ - radius);
        int sectionZ1 = SectionPos.blockToSectionCoord(centerZ + radius);
        int sectionY0 = Math.max(level.getMinSection(), SectionPos.blockToSectionCoord(centerY - radius));
        int sectionY1 = Math.min(level.getMaxSection() - 1, SectionPos.blockToSectionCoord(centerY + radius));

        // 先把球体覆盖到的 section 列出来并算好「这一段离玩家最近有多远」，据此升序排。
        // 高位存距离、低位存下标，排 long 就等于按距离排
        int capacity = (sectionX1 - sectionX0 + 1) * (sectionZ1 - sectionZ0 + 1) * (sectionY1 - sectionY0 + 1);
        int[] sectionXs = new int[capacity];
        int[] sectionYs = new int[capacity];
        int[] sectionZs = new int[capacity];
        LevelChunk[] chunks = new LevelChunk[capacity];
        long[] ordered = new long[capacity];
        int sectionCount = 0;

        for (int sectionX = sectionX0; sectionX <= sectionX1; sectionX++) {
            for (int sectionZ = sectionZ0; sectionZ <= sectionZ1; sectionZ++) {
                // 一次区块查表管整块，顶替原先逐方块的 isLoaded。
                // 用 getChunkNow 而不是 getChunk(..., FULL, false)：后者在区块 holder 已存在、
                // 但区块状态还没到 FULL 时会走 managedBlock，把主线程堵在区块生成上；
                // getChunkNow 只查已经就绪的区块，拿不到就返回 null（换个扫描周期自然会补上）
                LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
                if (chunk == null) {
                    continue;
                }
                int baseX = SectionPos.sectionToBlockCoord(sectionX);
                int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
                int dx = axisDistance(centerX, baseX);
                int dz = axisDistance(centerZ, baseZ);

                for (int sectionY = sectionY0; sectionY <= sectionY1; sectionY++) {
                    int dy = axisDistance(centerY, SectionPos.sectionToBlockCoord(sectionY));
                    sectionXs[sectionCount] = sectionX;
                    sectionYs[sectionCount] = sectionY;
                    sectionZs[sectionCount] = sectionZ;
                    chunks[sectionCount] = chunk;
                    ordered[sectionCount] = ((long) (dx * dx + dy * dy + dz * dz) << 32) | sectionCount;
                    sectionCount++;
                }
            }
        }
        if (sectionCount == 0) {
            return List.of();
        }
        ordered = Arrays.copyOf(ordered, sectionCount);
        Arrays.sort(ordered);

        // 大顶堆，堆顶是当前保留的结果里离玩家最远的那个；满了就挤掉堆顶
        Comparator<BlockPos> farthestFirst = (a, b) -> Integer.compare(
                distanceSq(b, centerX, centerY, centerZ),
                distanceSq(a, centerX, centerY, centerZ));
        PriorityQueue<BlockPos> nearest = new PriorityQueue<>(farthestFirst);

        for (long entry : ordered) {
            // 这一段（以及排在它后面的每一段，距离只增不减）里最近的方块都比手上最远的还远，
            // 后面不可能再翻盘了，收工
            if (nearest.size() >= maxResults
                    && (int) (entry >>> 32) > distanceSq(nearest.peek(), centerX, centerY, centerZ)) {
                break;
            }

            int index = (int) entry;
            int sectionX = sectionXs[index];
            int sectionY = sectionYs[index];
            int sectionZ = sectionZs[index];
            LevelChunk chunk = chunks[index];
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (!section.maybeHas(matcherPredicate)) {
                continue;
            }
            int baseX = SectionPos.sectionToBlockCoord(sectionX);
            int baseY = SectionPos.sectionToBlockCoord(sectionY);
            int baseZ = SectionPos.sectionToBlockCoord(sectionZ);

            for (int y = 0; y < LevelChunkSection.SECTION_HEIGHT; y++) {
                int worldY = baseY + y;
                int dy = worldY - centerY;
                int dySq = dy * dy;
                if (dySq > radiusSq) {
                    continue;
                }
                for (int z = 0; z < LevelChunkSection.SECTION_WIDTH; z++) {
                    int worldZ = baseZ + z;
                    int dz = worldZ - centerZ;
                    int dyzSq = dySq + dz * dz;
                    if (dyzSq > radiusSq) {
                        continue;
                    }
                    // 这一层里 x 的合法区间能直接解出来，不必逐格试再丢
                    int maxDx = Mth.floor(Math.sqrt((double) (radiusSq - dyzSq)));
                    int from = Math.max(0, centerX - maxDx - baseX);
                    int to = Math.min(LevelChunkSection.SECTION_WIDTH - 1, centerX + maxDx - baseX);

                    for (int x = from; x <= to; x++) {
                        if (!matcher.test(section.getBlockState(x, y, z))) {
                            continue;
                        }
                        nearest.add(new BlockPos(baseX + x, worldY, worldZ));
                        if (nearest.size() > maxResults) {
                            nearest.poll();
                        }
                    }
                }
            }
        }
        return new ArrayList<>(nearest);
    }

    /** 玩家坐标到 [base, base+15] 这一段方块的最短距离（0 = 玩家就在这一段里） */
    private static int axisDistance(int center, int base) {
        if (center < base) {
            return base - center;
        }
        if (center > base + LevelChunkSection.SECTION_WIDTH - 1) {
            return center - (base + LevelChunkSection.SECTION_WIDTH - 1);
        }
        return 0;
    }

    private static int distanceSq(BlockPos pos, int cx, int cy, int cz) {
        int dx = pos.getX() - cx;
        int dy = pos.getY() - cy;
        int dz = pos.getZ() - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 方块是否命中过滤条件，按方块种类记忆化。
     * <p>
     * 三种模式都只看 {@link Block}——标签挂在方块上，另外两种也一律用
     * {@code state.getBlock().asItem()}，与 {@link BlockState} 的具体状态、与坐标都无关。
     * 所以一次扫描里同一种方块只需要真的判定一次：调色板预筛会先把这一段出现过的种类填进缓存，
     * 后面逐格的判定基本全是缓存命中，顺带干掉了 mode 1 原先每方块一次 {@code new ItemStack}。
     */
    private static final class Matcher {

        private final ServerLevel level;
        private final int mode;
        private final FilterItemStack createFilter;
        private final ItemStack filterStack;
        private final Map<Block, Boolean> cache = new HashMap<>();

        Matcher(ServerLevel level, int mode, FilterItemStack createFilter, ItemStack filterStack) {
            this.level = level;
            this.mode = mode;
            this.createFilter = createFilter;
            this.filterStack = filterStack;
        }

        boolean test(BlockState state) {
            Block block = state.getBlock();
            Boolean cached = cache.get(block);
            if (cached != null) {
                return cached;
            }
            boolean result = switch (mode) {
                case 0 -> state.is(ModTags.ECHO_REVEALS);
                // Create 的 FilterItemStack 只有物品/流体两个重载，方块必须转成物品再测
                case 1 -> createFilter.test(level, new ItemStack(block.asItem()));
                default -> block.asItem() == filterStack.getItem();
            };
            cache.put(block, result);
            return result;
        }
    }
}
