package com.minecart.yunxian.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;

import net.neoforged.neoforge.common.ModConfigSpec;

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