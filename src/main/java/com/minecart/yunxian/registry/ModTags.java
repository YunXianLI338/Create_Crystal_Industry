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

    /**
     * 通用芽标签 c:buds（方块 / 物品两份）：小 / 中 / 大三档芽。
     * <p>
     * NeoForge 把「母岩 / 芽 / 晶簇」分成三类通用标签，所以芽与晶簇<strong>不</strong>进
     * {@link #BUDDING_BLOCKS}——别的模组按类别筛方块（"所有芽"或"所有晶簇"）时读的是这两个。
     */
    public static final TagKey<Block> BUDS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "buds"));

    public static final TagKey<Item> BUDS_ITEM = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "buds"));

    /** 通用晶簇标签 c:clusters（方块 / 物品两份）：四个阶段里的终态 */
    public static final TagKey<Block> CLUSTERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "clusters"));

    public static final TagKey<Item> CLUSTERS_ITEM = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "clusters"));

    /**
     * 通用桶标签 c:buckets（<b>物品</b>标签，NeoForge 通用标签的一支）。
     * <p>
     * 别的模组按「这是不是一个桶」筛物品时读的就是它（Create 自己的蜂蜜桶、巧克力桶也在里面）。
     * 可燃冰沙桶虽然装的是方块、不是流体（见 {@code ModItems#FLAMMABLE_ICE_SLURRY_BUCKET}），
     * 但它终归是「用桶舀起来、倒出去」的一件容器，语义上归这一档。
     */
    public static final TagKey<Item> BUCKETS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "buckets"));

    /**
     * 机械动力的「可移动的空碰撞体」标签 {@code create:movable_empty_collider}（<b>方块</b>标签）。
     * <p>
     * 装的是细雪、蜘蛛网、藤蔓、绊线这类「看着空心、却该被动态结构一起带走」的方块——
     * Create 的 {@code BlockMovementChecksImpl#isMovementNecessaryFallback} 拿它当判据：
     * 碰撞箱为空的方块默认<b>不必</b>参与移动，进了这个标签就反过来。
     * 可燃冰沙方块与细雪同族（无碰撞、无物品形态），自然也该进。
     */
    public static final TagKey<Block> MOVABLE_EMPTY_COLLIDER = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create", "movable_empty_collider"));

    /**
     * 机械动力的「气流穿透」标签 {@code create:fan_transparent}（<b>方块</b>标签）。
     * <p>
     * 装的是铁栏杆、篝火、铜格栅这类「有碰撞箱、但风该照常穿过」的方块——
     * Create 的 {@code AirCurrent#getFlowLimit} 一开头就问这个标签，命中就整格放行，
     * 连形状都不量。进了这个标签的方块对气流等于不存在。
     * <p>
     * 泄压舱口要进来是因为它的安装板正好盖满一个横截面：气流沿它的朝向吹来时，
     * 形状深度判定会把气流挡在舱口那一格，舱口后面（风的下游）就整片没有风。
     * 舱口本来就该是"风从中间穿过去"的东西，不该当挡风板。
     */
    public static final TagKey<Block> FAN_TRANSPARENT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create", "fan_transparent"));

    /**
     * 机械动力的「可被动态结构控制器控制」标签 {@code create:contraption_controlled}（<b>物品</b>标签）。
     * <p>
     * 动态结构控制器上的过滤器槽位只认这个标签：方块实体建槽时用
     * {@code FilteringBehaviour#withPredicate(AllItemTags.CONTRAPTION_CONTROLLED::matches)} 当门禁，
     * 不在标签里的物品 {@code setFilter} 直接失败，右键只有一声拒绝音加「过滤器无效」的提示。
     * 槽位收下之后，物品会被写成控制器的过滤器（装配进结构数据里的 Filter），运动时由
     * {@code Contraption#setActorsActive} 拿它跟每个 actor 的 {@code canBeDisabledVia} 比对。
     * <p>
     * 智能钻头这边不用另写代码：{@code SmartDrillMovementBehaviour} 继承 {@code DrillMovementBehaviour}，
     * 没覆写 {@code canBeDisabledVia}，默认返回的就是方块自身的物品。差的只是这一条标签——
     * 没有它，控制器只能对整台结构一刀切；有了它，智能钻头才能像机械钻头那样被单独拎出来开关。
     */
    public static final TagKey<Item> CONTRAPTION_CONTROLLED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create", "contraption_controlled"));

    /**
     * 标记「免疫鼓风机 / 分散网风力」的盔甲（<b>物品</b>标签），数据包路径
     * {@code data/create_crystal_industry/tags/item/fan_immune.json}。
     * <p>
     * 判定是<b>任意一件</b>即免疫（见 {@code util/FanImmunityHelper}）；同一条判定还管风弹的击退
     * （见 {@code util/WindKnockbackGuard}）。
     * <p>
     * 与「气流抵抗」附魔（{@code ModEnchantments.AIRFLOW_RESISTANCE}）<b>完全等价</b>：两者任取其一即可，
     * 标签给不想刷附魔的存档用，附魔给愿意在附魔台上赌一把的玩家用。
     */
    public static final TagKey<Item> FAN_IMMUNE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "fan_immune"));

    /**
     * 可燃冰的「石材类型」标签（<b>物品</b>标签）：整套装饰方块里除台阶之外的每一件，
     * 由数据生成从 {@link ModBlocks#FLAMMABLE_ICE_DECO} 派生（见 {@code ModItemTagsProvider}），
     * 以后加变体不用动这里。数据包路径 {@code data/create_crystal_industry/tags/item/stone_types/flammable_ice.json}。
     * <p>
     * 可燃冰的切石配方一律拿它当原料，于是<b>任意一种形状都能切成任意另一种</b>。这是照 Create 的
     * {@code create:stone_types/<石材>} 那一套做的：那边同样是「基础石 + 各图案方块及其楼梯、墙」，
     * <b>唯独不收台阶</b>——一块石头能切出两个台阶，两个台阶又能回收成一个整块，
     * 让台阶参与互切等于开了一个低廉的万能转换口。
     * <p>
     * 与 Create 的一处差别：我们的基础石台阶/墙是模组自己的方块，所以也收进来了；
     * Create 那边基础石的台阶/墙属于原版、不归它管，才不在标签里。
     */
    public static final TagKey<Item> FLAMMABLE_ICE_STONE_TYPE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "stone_types/flammable_ice"));

    /**
     * 标记「全套穿上就免疫电击」的盔甲：原版锁链甲、原版下界合金甲，以及机械动力的下界合金潜水装备。
     * <p>
     * 与 {@link #FAN_IMMUNE} 的差别在<b>「任意一件」还是「四槽齐活」</b>：那个是任意一件即免疫，
     * 这个是<b>四个盔甲槽全部</b>带本标签才生效，判定见 {@code util/ShockImmunityHelper}。
     * <p>
     * 免疫的内容有两份，共用同一个伤害类型（{@code minecraft:lightning_bolt}），所以服务端只需一处拦截：
     * <ul>
     *   <li>电流浆的电击（{@code effect/SlurryShock}）与<b>原版真实落雷</b>——都在
     *       {@code effect/ShockWard} 里拦掉；</li>
     *   <li>弧光石系列 / 电流浆的「感电」状态——在 {@code effect/ElectrifiedAura} 里不再施加。</li>
     * </ul>
     */
    public static final TagKey<Item> SHOCK_IMMUNE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "shock_immune"));

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