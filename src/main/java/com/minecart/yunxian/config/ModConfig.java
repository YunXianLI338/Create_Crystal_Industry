package com.minecart.yunxian.config;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamily.GrowthSpeed;
import com.minecart.yunxian.budding.BuddingRegistration;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModConfig {
    private ModConfig() {
    }

    /**
     * 配置界面文案的翻译键前缀（界面文字全部来自语言文件，类中不再出现提示文案）：
     *   标签：create_crystal_industry.configuration.<TOML 键名>
     *   提示：create_crystal_industry.configuration.<TOML 键名>.tooltip
     *
     * 每个配置值必须显式调用 .translation(键)，原因（均有源码依据）：
     *   - NeoForge 原生配置界面：显式键与它自身的默认拼法（modId + ".configuration." + 键名）完全相同，零影响；
     *   - Configured：NeoForgeValue#getTranslationKey() 返回的就是 ValueSpec#getTranslationKey()；
     *     未显式设置时为 null，getComment() 会拼出 "null.tooltip" 查不到翻译，
     *     回退成 Component.literal(英文 comment)——即此前配置界面显示英文的原因。
     */
    private static final String LANG_PREFIX = "create_crystal_industry.configuration.";

    public static final class Common {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // ===== 世界生成开关 =====
        // comment 仅保留英文：它会写入 .toml 文件供直接编辑者阅读，并作为翻译缺失时的兜底。
        private static ModConfigSpec.BooleanValue budding(String name, boolean defaultValue) {
            String path = "generate_" + name;
            return BUILDER
                    .comment("Whether the " + name + " budding block generates in the world.")
                    .translation(LANG_PREFIX + path)
                    .define(path, defaultValue);
        }

        /**
         * 各母岩家族的世界生成开关：由中央定义表派生（{@link BuddingFamilies#ALL} 里
         * {@code generateInWorld} 为 true 的家族各产出一个 generate_&lt;id&gt; 配置项）。
         */
        private static final Map<String, ModConfigSpec.BooleanValue> GENERATE_BUDDING = buildBuddingFlags();

        private static Map<String, ModConfigSpec.BooleanValue> buildBuddingFlags() {
            Map<String, ModConfigSpec.BooleanValue> flags = new LinkedHashMap<>();
            for (RegisteredFamily family : BuddingFamilies.ALL) {
                if (family.spec().generateInWorld()) {
                    flags.put(family.spec().id(), budding(family.spec().id(), true));
                }
            }
            return Map.copyOf(flags);
        }

        // ★ 新增：可燃冰母岩结构生成概率（1/N 每区块）★
        public static final ModConfigSpec.IntValue FLAMMABLE_ICE_CHANCE = BUILDER
                .comment(
                        "1-in-N chance per eligible deep-ocean chunk that a flammable ice budding structure generates.",
                        "Higher = rarer.")
                .translation(LANG_PREFIX + "flammableIceChance")
                .defineInRange("flammableIceChance", 256, 1, 10000);

        // ★ 新增：可燃冰结构周围灵魂沙（海面冒泡）配置 ★
        public static final ModConfigSpec.BooleanValue SOUL_SAND_GENERATE = BUILDER
                .comment(
                        "Whether soul sand (sea-surface bubble effect) generates around flammable ice structures.")
                .translation(LANG_PREFIX + "soulSandGenerate")
                .define("soulSandGenerate", true);

        public static final ModConfigSpec.IntValue SOUL_SAND_MIN = BUILDER
                .comment(
                        "Minimum number of soul sand blocks scattered around each flammable ice structure.")
                .translation(LANG_PREFIX + "soulSandMin")
                .defineInRange("soulSandMin", 3, 0, 64);

        public static final ModConfigSpec.IntValue SOUL_SAND_MAX = BUILDER
                .comment(
                        "Maximum number of soul sand blocks scattered around each flammable ice structure.")
                .translation(LANG_PREFIX + "soulSandMax")
                .defineInRange("soulSandMax", 6, 0, 64);

        public static final ModConfigSpec.IntValue SOUL_SAND_MARGIN = BUILDER
                .comment(
                        "Scatter range: how many blocks outward from the structure edge soul sand may spawn.")
                .translation(LANG_PREFIX + "soulSandMargin")
                .defineInRange("soulSandMargin", 5, 1, 32);

        public static final ModConfigSpec.IntValue SOUL_SAND_SINK = BUILDER
                .comment(
                        "How many blocks below the seafloor surface the soul sand is buried (1 = a one-block-deep pit).")
                .translation(LANG_PREFIX + "soulSandSink")
                .defineInRange("soulSandSink", 1, 1, 16);

        public static final ModConfigSpec.DoubleValue GLOWSTONE_BUDDING_CHANCE = BUILDER
                .comment(
                        "Chance (0.0–1.0) that a naturally generated glowstone cluster gets its lowest block replaced with glowstone budding.",
                        "0.0 = never, 1.0 = every cluster.")
                .translation(LANG_PREFIX + "glowstoneBuddingChance")
                .defineInRange("glowstoneBuddingChance", 0.5, 0.0, 1.0);

        // ★ 两个新字段：必须在 SPEC = BUILDER.build() 之前定义 ★
        public static final ModConfigSpec.BooleanValue GLOWSTONE_GENERATE_BUDS = BUILDER
                .comment(
                        "Whether glowstone budding also spawns a few glowstone buds/clusters on its side faces.")
                .translation(LANG_PREFIX + "glowstoneGenerateBuds")
                .define("glowstoneGenerateBuds", true);

        public static final ModConfigSpec.IntValue GLOWSTONE_BUD_COUNT = BUILDER
                .comment(
                        "Number of buds/clusters spawned around each naturally generated glowstone budding block.",
                        "0 = none.")
                .translation(LANG_PREFIX + "glowstoneBudCount")
                .defineInRange("glowstoneBudCount", 5, 0, 8);

        public static final ModConfigSpec.BooleanValue GLOWSTONE_BUDS_ON_GLOWSTONE = BUILDER
                .comment(
                        "Whether glowstone buds also generate on nearby glowstone blocks around the budding block.",
                        "If false, buds only appear on the budding block's own faces.")
                .translation(LANG_PREFIX + "glowstoneBudsOnGlowstone")
                .define("glowstoneBudsOnGlowstone", true);

        /** 查询某个母岩家族的世界生成开关；未配置该项的家族（如玫瑰石英、福鲁伊克斯）恒为 true */
        public static boolean enabled(String key) {
            ModConfigSpec.BooleanValue flag = GENERATE_BUDDING.get(key);
            return flag == null || flag.get();
        }

        // ===== 母岩生长速度（四档） =====
        private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.config");

        /** {@link #knownBuddingIds()} 的缓存：只在首次使用时构建一次 */
        private static Set<String> knownBuddingIds;

        /**
         * 全部已知的母岩 id：自带的 14 个家族 + 附属模组通过
         * {@link BuddingRegistration#declareKnownId} 声明的 id。
         * <p>
         * <b>延迟到首次使用</b>（首个随机刻解析生长档位时）才构建：附属模组是在自己构造器里声明的，
         * 而配置项本身在模组构造期就要建立，晚一点收集才收得全。
         * AE2 缺席时福鲁伊克斯母岩不存在，但配置里写了也不算错。
         */
        private static Set<String> knownBuddingIds() {
            Set<String> ids = knownBuddingIds;
            if (ids == null) {
                ids = Stream.concat(
                                BuddingFamilies.ALL.stream().map(family -> family.spec().id()),
                                BuddingRegistration.declaredIds().stream())
                        .collect(Collectors.toUnmodifiableSet());
                knownBuddingIds = ids;
            }
            return ids;
        }

        /**
         * 四档生长速度各一个配置项，由 {@link GrowthSpeed} 派生：
         * 键名 = {@code GrowthSpeed#configKey()}，值 = 母岩家族 id 列表。
         * 判定顺序见 {@link #speedFor(String)}。
         */
        private static final Map<GrowthSpeed, ModConfigSpec.ConfigValue<List<? extends String>>> GROWTH_SPEEDS =
                buildGrowthSpeeds();

        private static Map<GrowthSpeed, ModConfigSpec.ConfigValue<List<? extends String>>> buildGrowthSpeeds() {
            Map<GrowthSpeed, ModConfigSpec.ConfigValue<List<? extends String>>> speeds =
                    new EnumMap<>(GrowthSpeed.class);
            for (GrowthSpeed speed : GrowthSpeed.values()) {
                String path = speed.configKey();
                speeds.put(speed, BUILDER
                        .comment("Budding blocks that grow at " + speed.name() + " speed: a 1-in-"
                                        + speed.chance() + " chance to advance a stage per random tick.",
                                "Values are budding family ids (the part shared with the generate_<id> switches),",
                                "e.g. raw_iron, diamond, echo. Budding blocks listed nowhere are treated as NORMAL.")
                        .translation(LANG_PREFIX + path)
                        // 宽松校验：只挡非字符串。拼错的 id 交给解析阶段记警告，
                        // 若在这里剔除，空列表会被 NeoForge 整体回退成默认值，反而把错误藏了起来。
                        .defineListAllowEmpty(path, defaultIds(speed), (Supplier<String>) () -> "",
                                element -> element instanceof String));
            }
            return Map.copyOf(speeds);
        }

        /** 某档的默认成员：只有「正常」档默认把全部已注册的母岩写出来，其余三档默认空 */
        private static List<String> defaultIds(GrowthSpeed speed) {
            if (speed != GrowthSpeed.NORMAL) {
                return List.of();
            }
            return BuddingFamilies.ALL.stream()
                    .filter(RegisteredFamily::isRegistered)
                    .map(family -> family.spec().id())
                    .toList();
        }

        // 解析结果缓存。随机刻只在服务端主线程跑，这几个字段无需同步；Map 本身不可变。
        private static Map<String, GrowthSpeed> resolvedSpeeds;
        private static List<? extends String> cachedVerySlow;
        private static List<? extends String> cachedSlow;
        private static List<? extends String> cachedNormal;
        private static List<? extends String> cachedFast;

        /**
         * 某母岩家族每随机刻的生长概率基数 n（每次随机刻 1/n）。
         * <p>
         * 随机刻是热路径（催生器每 tick 就会给相邻母岩施加随机刻），所以这里只做一次档位查表，
         * 判定与缓存都在 {@link #speedFor(String)} 里。
         */
        public static int growthChance(String familyId) {
            return speedFor(familyId).chance();
        }

        /**
         * 某母岩家族当前所属的生长档位；没有被任何列表提到时按「正常」。
         * <p>
         * 生长引擎只用得上概率（{@link #growthChance}），这个方法给 JEI 的母岩信息页显示档位名用。
         * <p>
         * 判定顺序：先看三个非「正常」档（极慢 → 慢 → 快），命中即用；都没命中再看「正常」档；
         * 仍未命中按「正常」兜底，所以把「正常」列表删空也不会改变行为。
         * 一个母岩同时出现在两个非「正常」档里时取更慢的一档，并记一条警告。
         * <p>
         * 解析结果带缓存：四个源列表只要还是原来那批对象就直接复用。配置重载时 NeoForge 会清掉
         * {@code ConfigValue} 的缓存并重新解析出新的 List 实例，因此比较引用就足以判断过期。
         */
        public static GrowthSpeed speedFor(String familyId) {
            List<? extends String> verySlow = speedList(GrowthSpeed.VERY_SLOW);
            List<? extends String> slow = speedList(GrowthSpeed.SLOW);
            List<? extends String> normal = speedList(GrowthSpeed.NORMAL);
            List<? extends String> fast = speedList(GrowthSpeed.FAST);

            Map<String, GrowthSpeed> speeds = resolvedSpeeds;
            if (speeds == null || verySlow != cachedVerySlow || slow != cachedSlow
                    || normal != cachedNormal || fast != cachedFast) {
                speeds = resolveGrowthSpeeds();
                cachedVerySlow = verySlow;
                cachedSlow = slow;
                cachedNormal = normal;
                cachedFast = fast;
                resolvedSpeeds = speeds;
            }
            return speeds.getOrDefault(familyId, GrowthSpeed.NORMAL);
        }

        private static List<? extends String> speedList(GrowthSpeed speed) {
            return GROWTH_SPEEDS.get(speed).get();
        }

        /** id → 档位的解析结果；概率由 {@link GrowthSpeed#chance()} 现取，不再单独存一份 */
        private static Map<String, GrowthSpeed> resolveGrowthSpeeds() {
            Map<String, GrowthSpeed> assignment = new HashMap<>();
            // 先按枚举声明顺序处理三个非「正常」档（极慢 → 慢 → 快，更慢者先占位），
            // 「正常」档留到最后：它默认列出全部母岩，不该盖掉其它三档的显式分配。
            for (GrowthSpeed speed : GrowthSpeed.values()) {
                if (speed != GrowthSpeed.NORMAL) {
                    assign(assignment, speed);
                }
            }
            assign(assignment, GrowthSpeed.NORMAL);
            return Map.copyOf(assignment);
        }

        private static void assign(Map<String, GrowthSpeed> assignment, GrowthSpeed speed) {
            for (String id : speedList(speed)) {
                if (!knownBuddingIds().contains(id)) {
                    LOGGER.warn("[Config] {} 里的“{}”不是已知的母岩家族 id，已忽略", speed.configKey(), id);
                    continue;
                }
                GrowthSpeed previous = assignment.putIfAbsent(id, speed);
                if (previous != null && previous != GrowthSpeed.NORMAL && speed != GrowthSpeed.NORMAL) {
                    LOGGER.warn("[Config] 母岩“{}”同时列在 {} 与 {} 两档里，按更慢的一档处理",
                            id, previous.configKey(), speed.configKey());
                }
            }
        }

        // ===== 催生器 =====
        public static final ModConfigSpec.IntValue ACCELERATOR_INTERVAL_TICKS = BUILDER
                .comment(
                        "Ticks between two acceleration passes (1 = every tick, the default).",
                        "Both accelerators scale with it: larger = slower. The electric one pays its energy cost per pass.")
                .translation(LANG_PREFIX + "acceleratorIntervalTicks")
                .defineInRange("acceleratorIntervalTicks", 1, 1, 100);

        /**
         * 两次催生之间的 tick 数，电力与动力催生器共用（下限 1 防止除零）。
         * 护目镜的倍率也读它——客户端读的是本地配置，多人游戏里服务端改过该值时会与实际不符（仅影响显示）。
         */
        public static int acceleratorIntervalTicks() {
            return Math.max(1, ACCELERATOR_INTERVAL_TICKS.get());
        }

        // ===== 回响望远镜 =====
        public static final ModConfigSpec.IntValue SCAN_RADIUS = BUILDER
                .comment(
                        "Scan radius of the Echo Spyglass, in blocks.")
                .translation(LANG_PREFIX + "scanRadius")
                .defineInRange("scanRadius", 16, 4, 128);

        public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS = BUILDER
                .comment(
                        "Interval between two scans, in ticks.")
                .translation(LANG_PREFIX + "scanIntervalTicks")
                .defineInRange("scanIntervalTicks", 10, 1, 200);

        public static final ModConfigSpec.IntValue MAX_RESULTS = BUILDER
                .comment(
                        "Maximum number of blocks per scan.",
                        "Prevents huge network packets from overly broad filters (e.g. stone).")
                .translation(LANG_PREFIX + "maxResults")
                .defineInRange("maxResults", 4096, 64, 100000);

        public static final ModConfigSpec SPEC = BUILDER.build();
    }

    public static final class Client {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // ===== 回响护目镜 =====
        public static final ModConfigSpec.DoubleValue GOGGLES_OVERLAY_ALPHA = BUILDER
                .comment(
                        "Overall opacity of the night vision goggles screen overlay (0.0 = invisible, 1.0 = fully opaque).")
                .translation(LANG_PREFIX + "gogglesOverlayAlpha")
                .defineInRange("gogglesOverlayAlpha", 0.75, 0.0, 1.0);

        public static final ModConfigSpec SPEC = BUILDER.build();
    }
}