package com.minecart.yunxian.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecart.yunxian.registry.ScriptedBlockDrops;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * 「晶簇被采下时掉什么」——信息页左上角那个栏位显示的东西，也是让产物能在 JEI 里反查到这一页的依据。
 * <p>
 * 取数与方块本身一致：<b>现读掉落表</b>，不写死。
 * <ul>
 *   <li>本模组自己的晶簇读它自己的掉落表（{@code data/create_crystal_industry/loot_table/blocks/<方块>.json}，
 *       经 {@link InfoJson} 从本模组的 mod 文件里读）；</li>
 *   <li>KubeJS 脚本注册的晶簇没有掉落表 JSON（它们的掉落规则是运行时接管的，见
 *       {@link ScriptedBlockDrops}），所以走那边问；</li>
 *   <li>原版紫水晶簇写死（它的掉落表在 vanilla 数据包里，客户端读不到）；</li>
 *   <li>别的模组的晶簇读不到它们的掉落表，于是不显示栏位。</li>
 * </ul>
 * <p>
 * 掉落表只做「够显示」的解读：在掉落池里找<b>第一个不是晶簇本体</b>的物品条目，数量取该条目的
 * {@code set_count}，没有就用池子的 {@code rolls}。精准采集那一支（掉本体）本来就该跳过，
 * 这样一过滤正好剩下普通破坏的产物；读不出来就返回空，页面不显示栏位。
 */
final class ClusterProductReader {

    private ClusterProductReader() {
    }

    /** 晶簇的产物；{@code cluster} 为 null（外来方块认不出晶簇）或读不出掉落时返回 {@link ItemStack#EMPTY} */
    static ItemStack productOf(@Nullable Block cluster) {
        if (cluster == null) {
            return ItemStack.EMPTY;
        }

        // KubeJS 脚本注册的晶簇：掉落规则在运行时表里，而且"什么都不掉"也是合法配置
        if (ScriptedBlockDrops.isScripted(cluster)) {
            List<ItemStack> drops = ScriptedBlockDrops.dropsFor(cluster.defaultBlockState(), ItemStack.EMPTY);
            return drops == null || drops.isEmpty() ? ItemStack.EMPTY : drops.get(0).copy();
        }

        // 原版紫水晶簇：它的掉落表在 vanilla 数据包里，而客户端读不到别的数据包（见 InfoJson 的类注释），
        // 所以这里写死。原版内容不会变，这与 BuddingFamilies 里把原版同速写成 1/5 是同一个理由。
        if (cluster == Blocks.AMETHYST_CLUSTER) {
            return new ItemStack(Items.AMETHYST_SHARD, 4);
        }

        return fromLootTable(cluster);
    }

    private static ItemStack fromLootTable(Block cluster) {
        // 方块默认的掉落表键就是 <命名空间>:blocks/<方块名>（见 BlockBehaviour 的构造器），
        // 所以这里拿到的键直接对应 data/<命名空间>/loot_table/blocks/<方块名>.json
        if (cluster.getLootTable() == BuiltInLootTables.EMPTY) {
            return ItemStack.EMPTY;   // 没有掉落表：比如压根不掉落的东西
        }
        JsonObject json = InfoJson.read(cluster.getLootTable().location().withPrefix("loot_table/"));
        if (json == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation self = BuiltInRegistries.BLOCK.getKey(cluster);
        JsonArray pools = json.getAsJsonArray("pools");
        if (pools == null) {
            return ItemStack.EMPTY;
        }

        for (JsonElement poolElement : pools) {
            if (!poolElement.isJsonObject()) {
                continue;
            }
            JsonObject pool = poolElement.getAsJsonObject();
            int rolls = asInt(pool.get("rolls"), 1);
            for (JsonElement entry : arrayOrEmpty(pool.get("entries"))) {
                ItemStack product = stackOf(entry, self, rolls);
                if (!product.isEmpty()) {
                    return product;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 在一个掉落条目里找产物。
     * <p>
     * {@code minecraft:alternatives} / {@code group} / {@code sequence} 这些容器条目要把
     * {@code children} 展开递归——vanilla 的紫水晶簇就是两层 alternatives：精准采集那支掉本体（跳过），
     * 另一支才是晶簇碎片。
     */
    private static ItemStack stackOf(@Nullable JsonElement entry, ResourceLocation self, int rolls) {
        if (entry == null || !entry.isJsonObject()) {
            return ItemStack.EMPTY;
        }
        JsonObject object = entry.getAsJsonObject();
        String type = asString(object.get("type"));
        if (type == null) {
            return ItemStack.EMPTY;
        }

        if (type.equals("minecraft:item")) {
            ResourceLocation name = asLocation(object.get("name"));
            // 掉本体（精准采集那一支）：这不是"产物"，继续看别的条目
            if (name == null || name.equals(self)) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.get(name);
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, countOf(object, rolls));
        }

        // 容器条目：展开 children 逐个找
        for (JsonElement child : arrayOrEmpty(object.get("children"))) {
            ItemStack product = stackOf(child, self, rolls);
            if (!product.isEmpty()) {
                return product;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 条目自己的 {@code set_count} 优先，其次用掉落池的 {@code rolls}（两者都是"基准数量"，不含时运加成） */
    private static int countOf(JsonObject entry, int rolls) {
        for (JsonElement function : arrayOrEmpty(entry.get("functions"))) {
            if (!function.isJsonObject()) {
                continue;
            }
            JsonObject object = function.getAsJsonObject();
            if (!"minecraft:set_count".equals(asString(object.get("function")))) {
                continue;
            }
            int count = asInt(object.get("count"), 0);
            if (count > 0) {
                return count;
            }
        }
        return Math.max(1, rolls);
    }

    private static JsonArray arrayOrEmpty(@Nullable JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    @Nullable
    private static String asString(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    @Nullable
    private static ResourceLocation asLocation(@Nullable JsonElement element) {
        String id = asString(element);
        return id == null ? null : ResourceLocation.tryParse(id);
    }

    /**
     * 数字读不出来就返回兜底值。
     * <p>
     * 走 {@code getAsDouble} 而不是 {@code getAsInt}：掉落表里 {@code 4} 与 {@code 4.0} 两种写法都常见
     * （原版写 {@code "rolls": 1.0}、{@code "count": 4.0}），而 {@code getAsInt} 读 {@code "4.0"} 会抛异常。
     * 数量都是整数，四舍五入即可；范围写法（{@code {"min":…,"max":…}}）读不出来，退回兜底值。
     */
    private static int asInt(@Nullable JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return (int) Math.round(element.getAsDouble());
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return fallback;
        }
    }
}
