package com.minecart.yunxian.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamily;
import com.minecart.yunxian.budding.BuddingFamily.WorldGen;
import com.minecart.yunxian.compat.jei.BuddingInfo.Row;
import com.minecart.yunxian.config.ModConfig;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 「生成条件」小节的取数：<b>运行时读本模组自己发的那几个世界生成 JSON</b>，而不是把条件写死在文案里。
 * <p>
 * 读的是 {@code data/create_crystal_industry/worldgen/placed_feature/<feature>.json}（高度范围、每区块概率）
 * 与 {@code data/create_crystal_industry/neoforge/biome_modifier/<biomeModifier>.json}（在哪些生物群系里加它），
 * 文件名都来自 {@link BuddingFamily#worldGen()}——所以改了 JSON、重新构建后页面就跟着变，
 * 不存在「文档与现实漂移」。读取方式见 {@link InfoJson}（客户端读不到 {@code data/}，只能读自己的 mod 文件）。
 * <p>
 * 方块类里没有对应 feature 的母岩（荧石走原版荧石团的替换、玫瑰石英只有配方、福鲁伊克斯靠扩散）
 * 显示「不自然生成」+ 可选的 {@code origin.<id>} 手写补充行。
 * <p>
 * <b>绝不把异常抛出去</b>：读不到、解析失败都退回「不自然生成」或已解析出的那部分——
 * 这是给人看的信息页，不该因为某个数据包写了奇怪的 JSON 就把 JEI 的配方加载整个搞崩。
 */
final class GenerationInfoReader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LANG = BuddingInfoText.LANG;

    /** 认不出的 placement 修饰器最多列几个（正常只有 Create 的 config_filter 一个） */
    private static final int MAX_UNKNOWN_SHOWN = 3;

    private GenerationInfoReader() {
    }

    /**
     * 某自带家族的「生成条件」小节。
     * <p>
     * 注意 {@code worldGen} 与 {@code generateInWorld} 是两件事：荧石母岩开着世界生成，
     * 却没有自己的 feature（它替换的是原版荧石团），所以它的条件是 {@code origin.glowstone} 那句手写说明。
     */
    static List<Row> rows(BuddingFamily spec) {
        List<Row> rows = new ArrayList<>(4);
        WorldGen worldGen = spec.worldGen();

        if (worldGen != null) {
            Placement placement = readPlacement(worldGen.feature());

            Component biomes = readBiomes(worldGen);
            if (biomes != null) {
                rows.add(Row.line(Component.translatable(LANG + "generation.biome", biomes)));
            }

            Component extent = describeExtent(placement);
            if (extent != null) {
                rows.add(Row.line(extent));
            }

            if (!placement.unknownTypes.isEmpty()) {
                rows.add(Row.note(Component.translatable(LANG + "generation.other", joinUnknown(placement))));
            }

            if (spec.generateInWorld()) {
                rows.add(Row.line(Component.translatable(LANG + "generation.config",
                        Component.literal("generate_" + spec.id()).withStyle(ChatFormatting.DARK_AQUA),
                        Component.translatable(LANG + (ModConfig.Common.enabled(spec.id())
                                ? "config.on" : "config.off")))));
            }
        } else {
            rows.add(Row.note(Component.translatable(LANG + "generation.none")));
        }

        // 手写的来源补充行（荧石、玫瑰石英、福鲁伊克斯）：有语言键才加，没写就是纯「不自然生成」
        String origin = LANG + "origin." + spec.id();
        if (I18n.exists(origin)) {
            rows.add(Row.note(Component.translatable(origin)));
        }
        return rows;
    }

    // ==================== placed_feature ====================

    /** 一条 placed_feature 里我们认得出来的部分；认不出的修饰器收进 {@link #unknownTypes} 如实列出 */
    private static final class Placement {
        int rarity;
        int count;
        @Nullable Component minHeight;
        @Nullable Component maxHeight;
        final List<Component> unknownTypes = new ArrayList<>();
    }

    private static Placement readPlacement(String feature) {
        Placement placement = new Placement();
        // worldgen/placed_feature/<name>.json：与 BuddingFamily#worldGen().feature() 是同一份数据
        JsonObject json = InfoJson.read(featureId(feature).withPrefix("worldgen/placed_feature/"));
        if (json == null) {
            return placement;
        }

        JsonArray modifiers = json.getAsJsonArray("placement");
        if (modifiers == null) {
            return placement;
        }

        try {
            for (JsonElement element : modifiers) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject modifier = element.getAsJsonObject();
                String type = asString(modifier.get("type"));
                if (type == null) {
                    continue;
                }
                switch (type) {
                    case "minecraft:rarity_filter" -> placement.rarity = asInt(modifier.get("chance"));
                    case "minecraft:count" -> placement.count = countOf(modifier.get("count"));
                    case "minecraft:height_range" -> readHeight(modifier.getAsJsonObject("height"), placement);
                    // 位置与群系过滤不用单独成行：群系来自 biome_modifier，位置就是「每区块随机取点」
                    case "minecraft:in_square", "minecraft:biome" -> {
                    }
                    // 荧石、可燃冰那两条按地表/海床高度放置的 feature 会走到这里；不写高度比写错好
                    case "minecraft:heightmap" -> {
                    }
                    // create:config_filter：Create 自己的世界生成开关，写出来让玩家知道还有一层门槛
                    case "create:config_filter" ->
                            placement.unknownTypes.add(Component.translatable(LANG + "generation.filter"));
                    default -> placement.unknownTypes.add(Component.literal(type));
                }
            }
        } catch (RuntimeException e) {
            // 世界生成 JSON 由数据包作者维护：结构不对时只丢这一页的细节，绝不让异常冒到 JEI 的加载流程里
            LOGGER.warn("[JEI] 解析 placed_feature {} 失败，生成条件只显示已解析出的部分", feature, e);
        }
        return placement;
    }

    /** 「高度：Y -24 ~ Y 56 · 每区块 1/16」合成一行；两者都没有（比如按海床放置的）返回 null */
    @Nullable
    private static Component describeExtent(Placement placement) {
        List<Component> parts = new ArrayList<>(2);
        if (placement.minHeight != null && placement.maxHeight != null) {
            parts.add(Component.translatable(LANG + "generation.height", placement.minHeight, placement.maxHeight));
        }
        if (placement.rarity > 0) {
            parts.add(Component.translatable(LANG + "generation.rarity", placement.rarity));
        } else if (placement.count > 1) {
            parts.add(Component.translatable(LANG + "generation.count", placement.count));
        }

        if (parts.isEmpty()) {
            return null;
        }
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(Component.literal(" · "));
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    private static Component joinUnknown(Placement placement) {
        List<Component> types = placement.unknownTypes.subList(0,
                Math.min(MAX_UNKNOWN_SHOWN, placement.unknownTypes.size()));
        return BuddingInfoText.join(types);
    }

    private static void readHeight(@Nullable JsonObject height, Placement placement) {
        if (height == null) {
            return;
        }
        // 两种写法：常量 {"absolute": 16}，或 uniform 分布 {"type":"minecraft:uniform","min_inclusive":…,"max_inclusive":…}
        JsonObject min = height.has("min_inclusive") ? height.getAsJsonObject("min_inclusive") : height;
        JsonObject max = height.has("max_inclusive") ? height.getAsJsonObject("max_inclusive") : height;
        placement.minHeight = describeHeightBound(min);
        placement.maxHeight = describeHeightBound(max);
    }

    /** {@code {"absolute":-24}} → 「Y -24」；{@code above_bottom/below_top} → 相对基岩 / 相对顶部的高度 */
    @Nullable
    private static Component describeHeightBound(@Nullable JsonObject bound) {
        if (bound == null) {
            return null;
        }
        if (bound.has("absolute")) {
            return Component.literal("Y " + asInt(bound.get("absolute")));
        }
        if (bound.has("above_bottom")) {
            return Component.translatable(LANG + "height.above_bottom", asInt(bound.get("above_bottom")));
        }
        if (bound.has("below_top")) {
            return Component.translatable(LANG + "height.below_top", asInt(bound.get("below_top")));
        }
        // 认不出来的写法（梯形分布一类的提供器）：宁可不显示高度
        return null;
    }

    private static int countOf(@Nullable JsonElement element) {
        // count 可以是数字，也可以是 {"type":"minecraft:uniform",…} 这类提供器——后者我们不做数
        return element != null && element.isJsonPrimitive() ? asInt(element) : 0;
    }

    // ==================== biome_modifier ====================

    /** 读家族表里记着的那个生物群系修饰器，取它的 biomes 字段 */
    @Nullable
    private static Component readBiomes(WorldGen worldGen) {
        JsonObject json = InfoJson.read(ResourceLocation.fromNamespaceAndPath(
                Yunxian.MODID, "neoforge/biome_modifier/" + worldGen.biomeModifier()));
        if (json == null) {
            return null;
        }
        try {
            JsonElement biomes = json.get("biomes");
            if (biomes == null) {
                return null;
            }
            List<String> ids = gatherStrings(biomes);
            return ids.isEmpty() ? null : describeBiomes(ids);
        } catch (RuntimeException e) {
            LOGGER.warn("[JEI] 解析生物群系修饰器 {} 失败，跳过这一行", worldGen.biomeModifier(), e);
            return null;
        }
    }

    /**
     * 生物群系字段 → 一行文字。
     * <p>
     * 标签只展开<b>小</b>的：可燃冰那种两个群系的标签，列出名字比标签名好懂得多；
     * 而主世界标签（{@code #minecraft:is_overworld}）有六十多个群系，全列出来又长又没用，
     * 反而是标签名更好认。
     */
    private static Component describeBiomes(List<String> ids) {
        if (ids.size() == 1) {
            String id = ids.get(0);
            if (id.startsWith("#")) {
                List<String> members = expandBiomeTag(id.substring(1));
                if (!members.isEmpty() && members.size() <= BuddingInfoText.MAX_SHOWN) {
                    return summarize(members);
                }
            }
            return Component.literal(id);
        }
        return summarize(ids);
    }

    /** 展开 {@code #minecraft:is_overworld} 这类标签；群系注册表在客户端有同步，取不到就返回空（退回显示标签名） */
    private static List<String> expandBiomeTag(String tagId) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft == null ? null : minecraft.level;
        ResourceLocation location = ResourceLocation.tryParse(tagId);
        if (level == null || location == null) {
            return List.of();
        }

        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Optional<HolderSet.Named<Biome>> tag = registry.getTag(TagKey.create(Registries.BIOME, location));
        if (tag.isEmpty()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Holder<Biome> holder : tag.get()) {
            holder.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).ifPresent(names::add);
        }
        names.sort(null);
        return names;
    }

    /** 群系 id → 名字片段；一长串交给 {@link BuddingInfoText#summarize} 折成前几个 + 等 N 个 */
    private static Component summarize(List<String> ids) {
        List<Component> names = new ArrayList<>(ids.size());
        for (String id : ids) {
            names.add(Component.literal(id));
        }
        return BuddingInfoText.summarize(names);
    }

    // ==================== 读文件的通用件 ====================

    private static ResourceLocation featureId(String feature) {
        return ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, feature);
    }

    /** JSON 里的 features 字段可以是一个 id，也可以是一串 id */
    private static List<String> gatherStrings(JsonElement element) {
        List<String> ids = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            ids.add(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    ids.add(child.getAsString());
                }
            }
        }
        return ids;
    }

    @Nullable
    private static String asString(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /**
     * 数字读不出来时返回 0（0 在我们的文案里等于「没有这一项」），不让异常冒出去。
     * 走 {@code getAsDouble} 是为了兼容 {@code 16} 与 {@code 16.0} 两种写法（原版 JSON 里都出现过）。
     */
    private static int asInt(@Nullable JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return 0;
        }
        try {
            return (int) Math.round(element.getAsDouble());
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return 0;
        }
    }
}
