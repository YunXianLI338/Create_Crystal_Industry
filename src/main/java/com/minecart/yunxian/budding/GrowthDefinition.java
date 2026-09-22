package com.minecart.yunxian.budding;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 一次生长判定所需的全部参数——<b>生长引擎的输入</b>（见 {@link BuddingGrowthEngine}）。
 * <p>
 * 本模组自带的母岩由家族定义合成它（{@code GenericBuddingBlock} 里合成并缓存，概率取配置文件里的档位）；
 * 附属模组与 KubeJS 脚本自己构造一个交给引擎即可。
 *
 * @param smallBud           空位长出的第一阶段方块
 * @param mediumBud          {@code smallBud} 的下一阶段
 * @param largeBud           {@code mediumBud} 的下一阶段
 * @param cluster            {@code largeBud} 的下一阶段（终态）
 * @param chance             概率基数 n：每次随机刻有 1/n 的概率推进一级（必须 ≥ 1）
 * @param maxLight           生长位允许的最大亮度；空 = 不限制（0–15）
 * @param minLight           生长位要求的最低亮度；空 = 不限制（0–15）。与 {@code maxLight} 一起构成一个闭区间
 * @param requiresWater      目标格必须是水源（可燃冰式）——为 true 时走水下生长路径
 * @param growthEnvironment 生长环境要求（维度 + 群系）：只在这些地方正常生长，出了地盘每次判定通过后再掷一次、
 *                          只有其中概率能继续长；{@link GrowthEnvironment#ANY}（默认）= 哪里都一样长
 */
public record GrowthDefinition(
        Block smallBud,
        Block mediumBud,
        Block largeBud,
        Block cluster,
        int chance,
        OptionalInt maxLight,
        OptionalInt minLight,
        boolean requiresWater,
        GrowthEnvironment growthEnvironment) {

    /**
     * 最常用的构造：四个阶段 + 概率，其余取默认（不限光照、不要求水源、不限维度）。
     * 给附属模组与 KubeJS 脚本用起来最省事。
     */
    public static GrowthDefinition of(Block smallBud, Block mediumBud, Block largeBud, Block cluster, int chance) {
        return new GrowthDefinition(smallBud, mediumBud, largeBud, cluster, chance,
                OptionalInt.empty(), OptionalInt.empty(), false, GrowthEnvironment.ANY);
    }

    /**
     * 用方块 id 构造——KubeJS 脚本里拿 {@link Block} 对象比较别扭，用这个最省事。
     * <p>
     * <b>注意调用时机</b>：解析是立即进行的，而 KubeJS 启动脚本执行时其它模组的方块**还没注册**
     * （模组方块在注册事件里才入表）。所以引用其它模组（含本模组）的方块时，要在方块自己的
     * {@code randomTick} 回调里构造并缓存，而不是写在脚本顶层：
     * <pre>{@code
     * let definition = null
     * event.create('my_budding').randomTick(ctx => {
     *   if (!definition) definition = GrowthDefinition.of('minecraft:small_amethyst_bud', ..., 20)
     *   BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
     * })
     * }</pre>
     *
     * @throws IllegalArgumentException 方块 id 不认识（脚本会在 KubeJS 日志里直接报出来）
     */
    public static GrowthDefinition of(String smallBud, String mediumBud, String largeBud, String cluster, int chance) {
        return of(smallBud, mediumBud, largeBud, cluster, chance, -1, -1, false);
    }

    /**
     * 同上，并可指定光照上限与含水要求（KubeJS 一键注册整族时用）。
     * 只限上限、不管下限；要下限用下面那个重载。
     *
     * @param maxLight 生长位允许的最大亮度（0–15）；负数 = 不限制
     */
    public static GrowthDefinition of(String smallBud, String mediumBud, String largeBud, String cluster,
                                      int chance, int maxLight, boolean requiresWater) {
        return of(smallBud, mediumBud, largeBud, cluster, chance, maxLight, -1, requiresWater);
    }

    /**
     * 同上，并同时给出光照的上下限（闭区间，两端都含；负数 = 那一端不限制）。
     *
     * @param maxLight 生长位允许的最大亮度（0–15）；负数 = 不限制
     * @param minLight 生长位要求的最低亮度（0–15）；负数 = 不限制。不能高于 {@code maxLight}
     */
    public static GrowthDefinition of(String smallBud, String mediumBud, String largeBud, String cluster,
                                      int chance, int maxLight, int minLight, boolean requiresWater) {
        return new GrowthDefinition(block(smallBud), block(mediumBud), block(largeBud), block(cluster),
                chance, lightBound(maxLight), lightBound(minLight), requiresWater, GrowthEnvironment.ANY);
    }

    // ==================== 生长环境（维度 + 群系） ====================

    /**
     * 指定生长维度（<b>可以列多个</b>）：只在列出的维度里按 {@link #chance()} 的概率生长，
     * 出了地盘每次判定通过后再掷一次、只有 {@link GrowthEnvironment#outsideGrowthChance()}
     * 的概率继续生长——默认是 0.5（一半）。
     * <p>
     * 本模组的石英母岩与荧石母岩用它把下界变成主场（下界的矿脉本该比别处高产）；
     * 不调用这个方法（或传空列表）就是维度不限。生长群系另见 {@link #growthBiomes(String...)}，
     * 两者都写时<b>取交集</b>。环境外的概率用 {@link #outsideGrowthChance(double)} 改。
     */
    public GrowthDefinition growthDimensions(List<ResourceKey<Level>> dimensions) {
        return withEnvironment(new GrowthEnvironment(dimensions, growthEnvironment.biomeConditions(),
                growthEnvironment.outsideGrowthChance()));
    }

    /**
     * 同上，维度用 id 写（KubeJS 脚本里拿不到 {@code Level.NETHER} 这种常量）。
     *
     * @throws IllegalArgumentException 维度 id 格式不对（脚本会在 KubeJS 日志里直接看到）
     */
    public GrowthDefinition growthDimensions(String... dimensions) {
        return growthDimensions(Arrays.stream(dimensions).map(GrowthEnvironment::dimension).toList());
    }

    /**
     * 指定生长<b>群系</b>（可以列多个）：
     * <ul>
     *   <li>{@code minecraft:lush_caves} —— 具体群系；{@code #minecraft:is_nether} —— 群系标签；</li>
     *   <li>{@code cold} / {@code warm} / {@code hot} —— 内置气候关键字：寒冷 / 温暖 / 炎热
     *       （下界全域算炎热、末地全域算寒冷，其余按群系基础温度分档，见 {@link GrowthEnvironment.Climate}）；</li>
     *   <li>前面加 {@code !} —— <b>否定</b>，如 {@code !cold}（只要不是寒冷群系就行）、{@code !hot}、
     *       {@code !minecraft:snowy_plains}、{@code !#minecraft:is_taiga}。</li>
     * </ul>
     * 命中任一否定项就出局；只写否定项时视为「只要不是这些地方」，详见
     * {@link GrowthEnvironment#of(double, List, String...)}。
     * 与 {@link #growthDimensions(String...)} 都写时<b>取交集</b>：维度、群系都满足才算在自己的地盘上。
     *
     * @throws IllegalArgumentException 群系 / 标签 id 写错，或关键字不认识
     *         （脚本会在 KubeJS 日志里直接看到）
     */
    public GrowthDefinition growthBiomes(String... biomes) {
        return withEnvironment(GrowthEnvironment.of(growthEnvironment.outsideGrowthChance(),
                growthEnvironment.dimensions(), biomes));
    }

    /**
     * 改自己的地盘<b>之外</b>的生长概率：每次判定通过后还有这么多概率继续生长
     * （{@code 0} = 出了地盘就再也长不动，{@code 0.5} = 一半，{@code 1} = 相当于不限制）。
     * <p>
     * 只有先写了 {@link #growthDimensions(String...)} 或 {@link #growthBiomes(String...)} 才有意义：
     * 没写环境的定义在哪里都按原概率生长。
     *
     * @param chance 0–1 的小数
     * @throws IllegalArgumentException 不在 0–1 之间
     */
    public GrowthDefinition outsideGrowthChance(double chance) {
        return withEnvironment(new GrowthEnvironment(growthEnvironment.dimensions(),
                growthEnvironment.biomeConditions(), chance));
    }

    private GrowthDefinition withEnvironment(GrowthEnvironment environment) {
        return new GrowthDefinition(smallBud, mediumBud, largeBud, cluster, chance,
                maxLight, minLight, requiresWater, environment);
    }

    /** 这个位置是否在自己的地盘上（维度与群系都命中；没写环境要求时恒为 true） */
    public boolean inGrowthEnvironment(Level level, BlockPos pos) {
        return growthEnvironment.allows(level, pos);
    }

    /** 光照门槛的写法：负数表示"这一端不限制" */
    private static OptionalInt lightBound(int bound) {
        return bound < 0 ? OptionalInt.empty() : OptionalInt.of(bound);
    }

    private static Block block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block block = location == null ? null : BuiltInRegistries.BLOCK.get(location);
        if (block == null || block == Blocks.AIR) {
            throw new IllegalArgumentException("阶段方块 id 不存在：" + id
                    + "（若它来自其它模组，请在 randomTick 回调里构造定义，那时方块才注册完）");
        }
        return block;
    }

    /** 四个阶段方块，按生长顺序；索引与 {@code BuddingFamilies.Stage} 的序号一致 */
    public List<Block> stages() {
        return List.of(smallBud, mediumBud, largeBud, cluster);
    }

    /**
     * 几何检查：概率基数必须 ≥ 1（否则 {@code RandomSource#nextInt} 会抛异常），
     * 光照区间不能自相矛盾（下限高于上限时一格都长不出来，属于写错了定义）。
     */
    public GrowthDefinition {
        if (chance < 1) {
            throw new IllegalArgumentException("生长概率基数必须 ≥ 1，收到 " + chance);
        }
        if (minLight.isPresent() && maxLight.isPresent() && minLight.getAsInt() > maxLight.getAsInt()) {
            throw new IllegalArgumentException("生长位的最低亮度（" + minLight.getAsInt()
                    + "）不能高于最高亮度（" + maxLight.getAsInt() + "），否则永远长不出来");
        }
    }

    /** 该亮度的生长位是否允许生长：上下限都满足才允许（不限制的那一端直接跳过） */
    boolean lightAllows(int brightness) {
        if (maxLight.isPresent() && brightness > maxLight.getAsInt()) {
            return false;
        }
        return minLight.isEmpty() || brightness >= minLight.getAsInt();
    }
}
