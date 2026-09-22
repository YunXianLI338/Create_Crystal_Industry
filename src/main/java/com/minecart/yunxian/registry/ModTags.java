package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Block> ECHO_REVEALS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_reveals"));

    /** 回响母岩会转化为幽匿的方块（见 BuddingFamilies 的 echo 家族） */
    public static final TagKey<Block> ECHO_CONVERTIBLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_convertible"));

    /**
     * 通用母岩标签 c:budding_blocks（方块 / 物品两份）。
     * 智能钻头的精准采集与 AE2 晶体催生器都认这个标签，新增母岩必须登记。
     */
    public static final TagKey<Block> BUDDING_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "budding_blocks"));

    public static final TagKey<Item> BUDDING_BLOCKS_ITEM = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "budding_blocks"));

    // 新增：标记"免疫鼓风机/喷头风力"的盔甲
    public static final TagKey<Item> FAN_IMMUNE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "fan_immune"));

    /**
     * 水晶电池的晶体方块容量档位（方块标签，见 {@code battery/CrystalTier}）。
     * <p>
     * 一个方块只要出现在其中任意一个标签里就是晶体方块，能塞进电池；出现在哪个标签里就按哪一档算容量。
     * 四个标签都进不去的方块右键电池不生效。<b>容量数值写死在 CrystalTier 里，标签只决定归属。</b>
     * <p>
     * 标签带 {@code /} 的分段路径：数据包路径是
     * {@code data/create_crystal_industry/tags/block/battery_crystal/&lt;档位&gt;.json}。
     */
    public static final TagKey<Block> BATTERY_CRYSTAL_LOW = batteryCrystal("low_capacity");

    public static final TagKey<Block> BATTERY_CRYSTAL_MEDIUM = batteryCrystal("medium_capacity");

    public static final TagKey<Block> BATTERY_CRYSTAL_HIGH = batteryCrystal("high_capacity");

    public static final TagKey<Block> BATTERY_CRYSTAL_EXTREME = batteryCrystal("extreme_capacity");

    /**
     * 上面四个档位标签的物品版，内容由数据生成从方块标签<b>原样复制</b>过来（{@code ItemTagsProvider#copy}），
     * 所以两个列表不会各写一份、也不会脱节。写配方、写 KubeJS 过滤时用物品标签，方块侧的判定仍看方块标签。
     */
    public static final TagKey<Item> BATTERY_CRYSTAL_LOW_ITEM = batteryCrystalItem("low_capacity");

    public static final TagKey<Item> BATTERY_CRYSTAL_MEDIUM_ITEM = batteryCrystalItem("medium_capacity");

    public static final TagKey<Item> BATTERY_CRYSTAL_HIGH_ITEM = batteryCrystalItem("high_capacity");

    public static final TagKey<Item> BATTERY_CRYSTAL_EXTREME_ITEM = batteryCrystalItem("extreme_capacity");

    /**
     * 全部「能当晶体用」的方块汇总标签 = 上面四个档位标签的并集，供整合包 / 脚本按类别筛选
     * （配方批量替换、JEI 过滤、KubeJS 里 {@code tagItem(...)} 之类）。
     * <p>
     * <b>它只是汇总，不是判据</b>：判定某个方块能不能当晶体、算哪一档，看的一律是上面那四个档位标签，
     * 本标签只是把四个并起来方便引用。所以往里面加方块没用——要加晶体，请加到某个<b>档位</b>标签里，
     * 这个汇总标签会自动跟着变。数据包路径为
     * {@code data/create_crystal_industry/tags/block/battery_crystal.json}。
     */
    public static final TagKey<Block> BATTERY_CRYSTAL = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "battery_crystal"));

    /** 上面那个汇总标签的物品版，同样由数据生成复制过来 */
    public static final TagKey<Item> BATTERY_CRYSTAL_ITEM = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "battery_crystal"));

    private static TagKey<Block> batteryCrystal(String tier) {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "battery_crystal/" + tier));
    }

    private static TagKey<Item> batteryCrystalItem(String tier) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "battery_crystal/" + tier));
    }

    private ModTags() {
    }
}