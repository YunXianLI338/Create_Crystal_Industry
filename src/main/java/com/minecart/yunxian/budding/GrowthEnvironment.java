package com.minecart.yunxian.budding;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * 生长环境要求：母岩在哪些维度、哪些群系里正常生长，出了这些地方又还剩多大概率生长。
 * <p>
 * 一条要求 = 「生长维度列表」+「生长群系条件」+「环境之外的生长概率」：
 * <ul>
 *   <li>维度列表<b>为空</b>：维度不限；</li>
 *   <li>群系条件<b>为空</b>：群系不限；</li>
 *   <li>两边都写：<b>取交集</b>——维度在名单里、群系条件也满足，才算"在自己的地盘上"。</li>
 * </ul>
 * 不在自己的地盘上时，每次判定通过后再掷一次，只有 {@link #outsideGrowthChance} 的概率继续生长
 * （0 = 出了地盘再也长不动）。
 * <p>
 * 群系条件用字符串写（见 {@link #of(double, List, String...)}）：支持肯定、否定（{@code !} 前缀）、
 * 具体群系、群系标签，以及内置的气候关键字（{@code cold} / {@code warm} / {@code hot}，见 {@link Climate}）。
 * <p>
 * 石英母岩与荧石母岩写「只在下界、其它地方一半被打回」；脚本与附属模组可以列任意多条条件。
 * 本类刻意保持「纯数据」：不读注册表，也不碰任何方块状态；群系判定用调用方递进来的 {@link Level}。
 */
public record GrowthEnvironment(
        /** 生长维度；空 = 维度不限 */
        List<ResourceKey<Level>> dimensions,
        /** 生长群系条件（肯定与否定混排）；空 = 群系不限 */
        List<BiomeCondition> biomeConditions,
        /** 在自己的地盘<b>之外</b>还能生长的概率：0–1 的小数（0 = 完全不长，1 = 相当于不限制） */
        double outsideGrowthChance) {

    /**
     * 不限制生长环境：哪里都一样长，也永远不会消耗那一次随机数
     * （两个名单都空着，环境外的概率写 1 表示「不限制」）。
     */
    public static final GrowthEnvironment ANY = new GrowthEnvironment(List.of(), List.of(), 1);

    /**
     * 只在给定维度与群系里正常生长，别处按 {@code outsideGrowthChance} 的概率继续生长。
     * <p>
     * 群系条目（{@code biomeEntries}）的写法：
     * <ul>
     *   <li>{@code minecraft:lush_caves} —— 具体群系；</li>
     *   <li>{@code #minecraft:is_taiga} —— 群系标签（一个标签顶一整片群系）；</li>
     *   <li>{@code cold} / {@code warm} / {@code hot} —— 内置气候关键字（下界全域算炎热、末地全域算寒冷），
     *       见 {@link Climate}；</li>
     *   <li>上面三种前面加 {@code !} —— <b>否定</b>：命中的群系直接出局，
     *       如 {@code !cold}、{@code !hot}、{@code !#minecraft:is_taiga}。</li>
     * </ul>
     * 肯定与否定混排时：命中任一否定项 → 这一边不满足；否则至少命中一条肯定项才算满足；
     * <b>只写否定项</b>时，肯定那一侧视为「全部群系」（即"只要不是这些地方"）。
     *
     * @param outsideGrowthChance 0–1 的小数（0 = 出了这些地方完全不长，1 = 相当于不限制）
     * @throws IllegalArgumentException 群系 id、标签 id 格式不对，或关键字不认识
     *         （脚本会在 KubeJS 日志里直接看到）
     */
    public static GrowthEnvironment of(double outsideGrowthChance, List<ResourceKey<Level>> dimensions,
                                       String... biomeEntries) {
        List<BiomeCondition> conditions = new ArrayList<>(biomeEntries.length);
        for (String entry : biomeEntries) {
            conditions.add(parseBiomeCondition(entry));
        }
        return new GrowthEnvironment(dimensions, conditions, outsideGrowthChance);
    }

    public GrowthEnvironment {
        dimensions = List.copyOf(dimensions);
        biomeConditions = List.copyOf(biomeConditions);
        if (!(outsideGrowthChance >= 0 && outsideGrowthChance <= 1)) {
            throw new IllegalArgumentException("生长环境之外的生长概率必须是 0 到 1 之间的小数"
                    + "（0 = 完全不长，1 = 不限制），收到 " + outsideGrowthChance);
        }
    }

    // ==================== 条目解析 ====================

    /** {@code !} 前缀 = 否定；{@code #} 前缀 = 标签；没有冒号 = 内置气候关键字；其余 = 具体群系 id */
    private static BiomeCondition parseBiomeCondition(String entry) {
        boolean exclude = entry.startsWith("!");
        String body = exclude ? entry.substring(1) : entry;
        if (body.isEmpty()) {
            throw new IllegalArgumentException("群系条件只剩一个 ! 了（否定写成 !minecraft:snowy_plains、"
                    + "!#minecraft:is_taiga 或 !cold）");
        }
        if (body.startsWith("#")) {
            return BiomeCondition.ofTag(TagKey.create(Registries.BIOME, location(body.substring(1))), exclude);
        }
        if (!body.contains(":")) {
            return BiomeCondition.ofClimate(climate(body), exclude);
        }
        return BiomeCondition.ofBiome(ResourceKey.create(Registries.BIOME, location(body)), exclude);
    }

    /** 内置气候关键字；写不认识的名字当场抛，并列出全部可用名字 */
    private static Climate climate(String name) {
        for (Climate climate : Climate.values()) {
            if (climate.name().equalsIgnoreCase(name)) {
                return climate;
            }
        }
        StringBuilder known = new StringBuilder();
        for (Climate climate : Climate.values()) {
            known.append(known.isEmpty() ? "" : " / ").append(climate.name());
        }
        throw new IllegalArgumentException("不认识的群系关键字：" + name + "（内置关键字只有 " + known
                + "；要指定具体群系就写 minecraft:snowy_plains 这样的 id，标签写 #minecraft:is_taiga）");
    }

    /** 字符串形式的维度 id → 键；写错了直接抛（脚本会在 KubeJS 日志里看到） */
    public static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, location(id));
    }

    private static ResourceLocation location(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("环境 id 写错了：" + id
                    + "（维度形如 minecraft:nether，群系形如 minecraft:lush_caves，群系标签写 #minecraft:is_nether）");
        }
        return location;
    }

    // ==================== 判定 ====================

    /** 写了生长维度 */
    public boolean hasDimensions() {
        return !dimensions.isEmpty();
    }

    /** 写了生长群系条件（肯定或否定都算） */
    public boolean hasBiomes() {
        return !biomeConditions.isEmpty();
    }

    /** 写了肯定的群系条件（决定「只在…」那一行要不要出） */
    public boolean hasIncludedBiomes() {
        return biomeConditions.stream().anyMatch(condition -> !condition.exclude());
    }

    /** 写了否定的群系条件（决定「…里生长会受抑制」那一行要不要出） */
    public boolean hasExcludedBiomes() {
        return biomeConditions.stream().anyMatch(BiomeCondition::exclude);
    }

    /**
     * 这个位置是否在自己的地盘上：维度名单与群系条件都满足才算
     * （没写的那一边直接跳过——没写群系条件时连群系查询都不会做）。
     */
    public boolean allows(Level level, BlockPos pos) {
        if (hasDimensions() && !dimensions.contains(level.dimension())) {
            return false;
        }
        return biomeAllows(level, pos);
    }

    private boolean biomeAllows(Level level, BlockPos pos) {
        if (biomeConditions.isEmpty()) {
            return true;
        }
        Holder<Biome> biome = level.getBiome(pos);
        boolean hasInclude = false;
        boolean matchedInclude = false;
        for (BiomeCondition condition : biomeConditions) {
            if (!condition.exclude()) {
                hasInclude = true;
            }
            if (condition.matches(biome)) {
                if (condition.exclude()) {
                    return false;   // 命中否定项：这一边直接出局
                }
                matchedInclude = true;
            }
        }
        return matchedInclude || !hasInclude;
    }

    /**
     * 这一下是否被压制：不在自己的地盘上，而且没掷中 {@link #outsideGrowthChance}。
     * <p>
     * 概率是 0（出了地盘就完全不长）或 1（等于不限制）时结果是确定的，
     * 连随机数都不会消耗——只在真需要掷（0 &lt; 概率 &lt; 1）时消耗一次。
     */
    public boolean suppresses(Level level, BlockPos pos, RandomSource random) {
        if (outsideGrowthChance >= 1 || allows(level, pos)) {
            return false;
        }
        if (outsideGrowthChance <= 0) {
            return true;
        }
        return random.nextDouble() >= outsideGrowthChance;
    }

    /** 是否真的构成限制（写了名单、且环境外的概率不是 1；没写的母岩连随机数都不会多消耗） */
    public boolean restricts() {
        return (hasDimensions() || hasBiomes()) && outsideGrowthChance < 1;
    }

    // ==================== 嵌套类型 ====================

    /**
     * 一条群系条件：具体群系 / 群系标签 / 内置气候关键字三选一，外加肯定还是否定。
     * <p>
     * 与 {@code BuddingFamily.Replacement} 同一套写法：三个目标字段只有一个非空，
     * 用 {@link #ofBiome} / {@link #ofTag} / {@link #ofClimate} 建，别直接 new。
     */
    public record BiomeCondition(@Nullable ResourceKey<Biome> biome, @Nullable TagKey<Biome> tag,
                                 @Nullable Climate climate, boolean exclude) {

        public static BiomeCondition ofBiome(ResourceKey<Biome> biome, boolean exclude) {
            return new BiomeCondition(biome, null, null, exclude);
        }

        public static BiomeCondition ofTag(TagKey<Biome> tag, boolean exclude) {
            return new BiomeCondition(null, tag, null, exclude);
        }

        public static BiomeCondition ofClimate(Climate climate, boolean exclude) {
            return new BiomeCondition(null, null, climate, exclude);
        }

        public BiomeCondition {
            if ((biome == null ? 0 : 1) + (tag == null ? 0 : 1) + (climate == null ? 0 : 1) != 1) {
                throw new IllegalArgumentException("一条群系条件只能指定具体群系 / 标签 / 气候关键字之一");
            }
        }

        /** 当前群系是否命中这一条（肯定与否定都一样判，方向由 {@link #exclude()} 决定） */
        public boolean matches(Holder<Biome> biome) {
            if (climate != null) {
                return climate.matches(biome);
            }
            if (tag != null) {
                return biome.is(tag);
            }
            return biome.unwrapKey().map(key -> key.equals(this.biome)).orElse(false);
        }
    }

    /**
     * 内置气候关键字：三个关键字把群系分成三档，判据是「末地 / 下界标签 + 群系基础温度」——
     * 前两个保证<b>下界全域算炎热、末地全域算寒冷</b>（末地的温度其实只有 0.5，光看温度会落进温暖档），
     * 其余群系按温度分档。
     * <p>
     * 与其它群系条件一样可以取反：{@code !cold} / {@code !warm} / {@code !hot}。
     */
    public enum Climate {
        /**
         * 寒冷：<b>末地全域</b>，或基础温度 &lt; 0.3。
         * <p>
         * 除了会积雪的雪原、冰刺之地、雪针叶林、冰洋、雪坡、冻峰（温度都 ≤ 0.05），
         * 也把针叶林（0.25）、山地与风袭丘陵（0.2）这类「凉」群系算了进来。
         */
        COLD,
        /** 温暖：既不算寒冷也不算炎热的群系（平原、森林、丛林、沼泽、海洋、蘑菇岛、裸岩峰…） */
        WARM,
        /**
         * 炎热：<b>下界全域</b>，或基础温度 ≥ 1.2。
         * <p>
         * 覆盖沙漠、恶地（2.0）与热带草原（1.2）；裸岩峰（1.0）、丛林（0.95）、蘑菇岛（0.9）留在温暖档。
         */
        HOT;

        /** 基础温度低于此值算寒冷（把 0.25 的针叶林、0.2 的山地也算进来） */
        private static final float COLD_BELOW = 0.3F;
        /** 基础温度不低于此值算炎热（1.0 的裸岩峰、0.95 的丛林留在温暖档） */
        private static final float HOT_FROM = 1.2F;

        /**
         * 这个群系属于哪一档：先看末地 / 下界标签（末地全域寒冷、下界全域炎热），再按基础温度分档。
         * <p>
         * 用群系标签而不是位置所在的维度，所以用这两套群系的模组维度同样算数。
         */
        public static Climate of(Holder<Biome> biome) {
            if (biome.is(BiomeTags.IS_END)) {
                return COLD;
            }
            if (biome.is(BiomeTags.IS_NETHER)) {
                return HOT;
            }
            float temperature = biome.value().getBaseTemperature();
            if (temperature < COLD_BELOW) {
                return COLD;
            }
            return temperature >= HOT_FROM ? HOT : WARM;
        }

        /** 当前群系是否属于这一档 */
        public boolean matches(Holder<Biome> biome) {
            return of(biome) == this;
        }
    }
}
