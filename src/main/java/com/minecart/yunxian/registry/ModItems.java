package com.minecart.yunxian.registry;

import com.minecart.yunxian.item.FlammableIceItem;
import com.minecart.yunxian.item.FlammableIceSlurryBottleItem;
import com.minecart.yunxian.item.FlammableIceSlurryBucketItem;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.NightVisionGogglesItem;
import com.minecart.yunxian.item.ResonanceFilterItem;
import com.minecart.yunxian.item.EchoSpyglassItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Yunxian.MODID);

    /**
     * 可燃冰本体：燃料（见 {@code FlammableIceItem#getBurnTime}）与各种配方的原料。
     * <p>
     * <b>刻意不是食物</b>：早先给它挂过「吃下去冻 10 秒」的属性，结果是拿着它对冷却器右键
     * 不走加热、反而被当成进食。成因在原版 {@code Minecraft#startUseItem}：方块交互没吃掉这次
     * 右键时就回落到 {@code GameMode#useItem} → {@code Item#use}，而冷却器拒绝这一下
     * （典型情形是已经喂饱了）时正好不消费，于是食物属性接管。
     * <p>
     * 它是一族的燃料，能被吃就等于喂不进冷却器，所以<b>本体不挂食物属性</b>。
     * 全族只有两件能入口：{@link #FLAMMABLE_ICE_SLURRY_BOTTLE} 与可燃冰圣代——
     * 那两件本来就是要喝的东西。
     */
    public static final DeferredItem<FlammableIceItem> FLAMMABLE_ICE =
            ITEMS.register("flammable_ice", () -> new FlammableIceItem(new Item.Properties()));

    /** 喝一口「灼寒」持续多久：10 秒，与可燃冰圣代给的那份一致 */
    public static final int ICE_SCORCHING_COLD_TICKS = 200;

    /**
     * 弧光石：弧光石母岩的晶簇掉落物，也是冲压出电流浆的原料。
     * 它<b>只有物品</b>，没有对应的装饰方块（与可燃冰不同）。
     */
    public static final DeferredItem<Item> ARCLIGHT =
            ITEMS.register("arclight", () -> new Item(new Item.Properties()));

    /**
     * 电流浆桶：电流浆的容器物品（灌装、倒出、进储罐）。
     * <p>
     * 刻意用原版的 {@code BucketItem} 本体、<b>不要</b>子类：NeoForge 只给
     * 「{@code item.getClass() == BucketItem.class}」的物品挂流体能力
     * （{@code CapabilityHooks} 里就是 == 判断），换成子类的话 Create 的注液器就灌不进它、
     * 手持它也倒不出东西——这是静默失效，没有报错。
     */
    public static final DeferredItem<BucketItem> CURRENT_SLURRY_BUCKET =
            ITEMS.register("current_slurry_bucket", () -> new BucketItem(ModFluids.CURRENT_SLURRY.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    /**
     * 可燃冰沙桶：倒出来是<b>方块</b>（可燃冰沙没有流体形态，和细雪一样），
     * 空桶右键那个方块又能舀回来。
     * <p>
     * <b>与上面电流浆桶的处理刻意相反</b>：那个必须用 {@code BucketItem} 本体，这个必须用子类——
     * 理由见 {@link FlammableIceSlurryBucketItem} 的类注释。少了配套的那一步
     * （{@code ModCapabilities} 里手动注册流体能力），子类同样会静默地灌不进去。
     */
    public static final DeferredItem<FlammableIceSlurryBucketItem> FLAMMABLE_ICE_SLURRY_BUCKET =
            ITEMS.register("flammable_ice_slurry_bucket",
                    () -> new FlammableIceSlurryBucketItem(ModFluids.FLAMMABLE_ICE_SLURRY.get(),
                            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    /**
     * 可燃冰沙瓶：玻璃瓶注 250 mB 可燃冰沙得来（{@code create:filling}），也能倒回去
     * （{@code create:emptying}）。能喝，喝下去只给 {@value #ICE_SCORCHING_COLD_TICKS} tick 的「灼寒」
     * （冻着，同时烧着），并留下空瓶；<b>不给「冰霜行者」</b>——踩水成冰是圣代那份的独有好处。
     * <p>
     * 它同时也是件燃料（见燃料数据映射），{@code craftRemainder(玻璃瓶)} 就是为这个准备的：
     * Create 的烈焰人（以及照着它做的两种冷却器）在烧掉燃料时会把"剩下的容器"还给玩家，
     * 于是烧一瓶还你一个空瓶。
     * <p>
     * 正途则是拿去加工：机械手蘸岩浆膏一压就成可燃冰圣代（{@code create:deploying}）。
     * 加工这一侧刻意<b>不</b>还瓶——机械手只交出圣代，见 {@code BeltDeployerCallbacksMixin}。
     */
    public static final DeferredItem<FlammableIceSlurryBottleItem> FLAMMABLE_ICE_SLURRY_BOTTLE =
            ITEMS.register("flammable_ice_slurry_bottle",
                    () -> new FlammableIceSlurryBottleItem(new Item.Properties()
                            .stacksTo(16)
                            .craftRemainder(Items.GLASS_BOTTLE)
                            .food(new FoodProperties.Builder()
                                    .nutrition(1)
                                    .saturationModifier(0.1F)
                                    .alwaysEdible()
                                    .usingConvertsTo(Items.GLASS_BOTTLE)
                                    .effect(() -> new MobEffectInstance(ModEffects.SCORCHING_COLD, ICE_SCORCHING_COLD_TICKS), 1.0F)
                                    .build())));

    public static final DeferredItem<EchoSpyglassItem> ECHO_SPYGLASS =
            ITEMS.register("echo_spyglass",
                    () -> new EchoSpyglassItem(new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.RARE)
                            .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)));
    /**
     * 共振过滤器：放进漏斗/溜槽/工作盆的过滤槽后，过滤规则实时取自它绑定的共振台。
     * <p>
     * <b>刻意保持默认可堆叠</b>（和 Create 的过滤器物品一致）。别改成 {@code stacksTo(1)}：
     * {@code FilteringBehaviour.getMaxStackSize(ItemStack)} 直接取过滤器物品的堆叠上限，
     * 上限为 1 会让漏斗/溜槽的「提取数量」值设定面板整个消失（{@code isCountVisible()} 要求 > 1）。
     */
    public static final DeferredItem<ResonanceFilterItem> RESONANCE_FILTER =
            ITEMS.register("resonance_filter",
                    () -> new ResonanceFilterItem(new Item.Properties()
                            .rarity(Rarity.RARE)));

    public static final DeferredItem<NightVisionGogglesItem> NIGHT_VISION_GOGGLES =
            ITEMS.register("night_vision_goggles",
                    () -> new NightVisionGogglesItem(new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}