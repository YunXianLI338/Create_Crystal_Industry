package com.minecart.yunxian;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.effect.ElectrifiedAura;
import com.minecart.yunxian.effect.ElectrifiedZap;
import com.minecart.yunxian.fluid.FluidInteractions;
import com.minecart.yunxian.effect.FrozenEffect;
import com.minecart.yunxian.effect.ShockWard;
import com.minecart.yunxian.effect.SlurryShock;
import com.minecart.yunxian.effect.FlameBreath;
import com.minecart.yunxian.attachment.EchoAttachments;
import com.minecart.yunxian.behaviour.SmartDrillMovementBehaviour;
import com.minecart.yunxian.battery.CrystalBatteryInteractions;
import com.minecart.yunxian.blockentity.CleanerDropAbsorption;
import com.minecart.yunxian.budding.BuddingConversions;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.BuddingOverrides;
import com.minecart.yunxian.client.ModRenderers;
import com.minecart.yunxian.compat.YunxianJeiPlugin;
import com.minecart.yunxian.datagen.YunxianDataGen;
import com.minecart.yunxian.registry.*;
import com.minecart.yunxian.util.NightVisionWearHelper;
import com.minecart.yunxian.util.WindKnockbackGuard;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

@Mod(Yunxian.MODID)
public class Yunxian {
    public static final String MODID = "create_crystal_industry";

    public Yunxian(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, com.minecart.yunxian.config.ModConfig.Common.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, com.minecart.yunxian.config.ModConfig.Client.SPEC); // ← 新增

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModFluids.register(modEventBus);
        ModEffects.register(modEventBus);
        ModParticles.register(modEventBus);
        // 母岩家族由中央定义表注册：必须在这里触发一次类初始化，
        // 否则方块会晚于注册表事件才入表，启动后整批母岩缺失
        BuddingFamilies.bootstrap();
        EchoAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        // 脚本（KubeJS）注册的方块要进创造栏：KubeJS 的方块默认不进任何标签页，这里补上
        modEventBus.addListener(ModCreativeTabs::addScriptedEntries);
        // 可燃冰装饰套件也追加一份进机械动力的「建筑方块」页（见 ModCreativeTabs#addDecoToCreateTabs）
        modEventBus.addListener(ModCreativeTabs::addDecoToCreateTabs);
        // 脚本注册的芽/簇的掉落规则（精准采集掉本体、否则掉配置物品）：KubeJS 的掉落 API 表达不了，运行时接管
        NeoForge.EVENT_BUS.addListener(ScriptedBlockDrops::onBlockDrops);
        // 脚本给母岩设的开采等级：方块标签在注册期就固定了，改只能改"这一步判定"（见 ScriptedMiningLevels）
        NeoForge.EVENT_BUS.addListener(ScriptedMiningLevels::onHarvestCheck);
        // 玩家亲手挖掉一颗完整晶簇 → 「它真的会长」（按方块判，见 YunxianAdvancements.isCluster）
        NeoForge.EVENT_BUS.addListener(YunxianAdvancements::onBlockBroken);
        // 风场内生成的掉落物直接进吸尘器库存，不生成实体：轴线风场沿轴逐格找，
        // 喷嘴场查 NozzleFieldIndex 登记表（见 CleanerDropAbsorption 的类注释）
        NeoForge.EVENT_BUS.addListener(CleanerDropAbsorption::onEntityJoinLevel);
        // 配方（重新）加载完了：母岩按配方缓存的侵染规则表要重建。这个事件由服务器在数据包加载
        // 结束后发出（含开机与 /reload），晚于 RecipeManager 自己 apply，所以读到的一定是新配方
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class,
                event -> BuddingConversions.onRecipesReloaded());
        // 「感电」：生物待在弧光石系列方块或电流浆附近就获得（见 ElectrifiedAura 的类注释）
        NeoForge.EVENT_BUS.addListener(ElectrifiedAura::onEntityTick);
        // 泡在电流浆里持续挨雷劈（见 SlurryShock 的类注释）
        NeoForge.EVENT_BUS.addListener(SlurryShock::onEntityTick);
        // 「感电」的实际效果：带电的生物挨打时放电，自己再吃一发雷击并电到旁边带电的那个（见 ElectrifiedZap）
        NeoForge.EVENT_BUS.addListener(ElectrifiedZap::onDamagePost);
        // 全套 shock_immune 盔甲免疫闪电伤害：电流浆的电击与真实落雷共用 lightning_bolt 伤害类型，
        // 所以一个拦截点就够（见 ShockWard 的类注释）
        NeoForge.EVENT_BUS.addListener(ShockWard::onIncomingDamage);
        // 「冰封」与「灼寒」免掉自己造成的冻伤：满冻外观（冰心、发抖）与冻伤在原版共用同一个阈值，
        // 想要前者就只能拦下后者，真正踩进细雪受伤不受影响（见 FrozenEffect 的类注释）
        NeoForge.EVENT_BUS.addListener(FrozenEffect::onIncomingDamage);
        // 带「火焰吐息」时按住潜行就喷火：潜行状态本来就同步到服务端，所以判定整条都在服务端，
        // 不需要客户端配合（见 FlameBreath 的类注释）
        NeoForge.EVENT_BUS.addListener(FlameBreath::onPlayerTick);
        // 风弹不许吹动穿「气流抵抗」盔甲（或 fan_immune 标签盔甲）的玩家：风弹的击退是
        // Explosion 直接往 deltaMovement 里加的速度，NeoForge 在那句之前留了爆炸击退事件
        // （见 WindKnockbackGuard 的类注释）
        NeoForge.EVENT_BUS.addListener(WindKnockbackGuard::onExplosionKnockback);
        // 潜行右键换晶体：只能挂在物品层（原版潜行时会跳过方块的 useItemOn），且手持的是任意晶体方块，
        // 所以走 UseItemOnBlockEvent 这个对任何物品都生效的钩子，见该类注释
        NeoForge.EVENT_BUS.addListener(CrystalBatteryInteractions::onUseItemOnBlock);
        modEventBus.addListener(Yunxian::commonSetup);
        ModRecipes.register(modEventBus);
        // ModRenderers 整个类都是客户端专用的：它的类级字段是 ModelResourceLocation / PartialModel，
        // 引用的 EchoSpyglassHeadLayer 还继承客户端的 RenderLayer。专用服务端一旦加载到这个类就会
        // NoClassDefFoundError: net/minecraft/client/renderer/entity/layers/RenderLayer。
        // 拦截必须放在这里——放进 ModRenderers.register 内部判断是没用的，那时类已经被加载了。
        if (FMLEnvironment.dist.isClient()) {
            ModRenderers.register(modEventBus);
        }
        // 配置一改，JEI 的两页立刻跟着变（母岩侵染那几条是运行时就生效的，页面不该等到退出世界重进）。
        // YunxianJeiPlugin 引用 mezz.jei 的类：没装 JEI 的客户端加载到它就是 NoClassDefFoundError，
        // 所以"装没装 JEI"必须在这一层判掉——与上面 ModRenderers 同一个道理，放进处理函数里是没用的。
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("jei")) {
            modEventBus.addListener(ModConfigEvent.Reloading.class, event -> {
                // 别的模组的配置重载也走这个事件，别跟着白忙
                if (event.getConfig().getSpec() == com.minecart.yunxian.config.ModConfig.Common.SPEC) {
                    YunxianJeiPlugin.refreshRecipes();
                }
            });
        }
        ModFeatures.register(modEventBus);
        ModArmInteractionPointTypes.register(modEventBus);
        ModDisplaySources.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
        // 数据生成（./gradlew runData）：只在 data 运行里触发，正常游戏不受影响
        modEventBus.addListener(YunxianDataGen::gatherData);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 脚本用 CustomBudding.modify 改过的母岩：方块注册完了，这时才查得出目标写没写错
            // （写错只会静默不生效，所以这里逐个核一遍、打警告）
            BuddingOverrides.verifyTargets();
            // 成就：母岩每长出一级都会回调一次，用来判定「被催生出来的」那些时刻
            BuddingGrowthEngine.setGrowthListener(YunxianAdvancements::onBuddingGrown);
            BlockStressValues.IMPACTS.register(ModBlocks.SMART_DRILL.get(), () -> 8.0);
            BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_ACCELERATOR.get(), () -> 16.0);
            // 显示链接器的数据源要等方块注册完才能挂上去（见 ModDisplaySources 的类注释）
            ModDisplaySources.associateBlocks();
            BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_CLEANER.get(), () -> 4.0);
            // 智能温控室也当蒸汽锅炉的热源：直接借 Create 给附属模组留的那个
            // BoilerHeater.BLAZE_BURNER —— 它读的正是 BlazeBurnerBlock.HEAT_LEVEL 这一位，
            // 而我们方块挂的就是同一个属性实例（见 SmartTemperatureChamberBlock），
            // 于是烧着（SEETHING）= 2 档满热、没燃料 = 不着火，一字不用自己写。
            // 挂在这里而不是方块注册期：SimpleRegistry 没有冻结检查，晚加是安全的，
            // 而且这个位置与其它"等注册表齐了再挂"的钩子（ModDisplaySources 等）一致
            BoilerHeater.REGISTRY.register(ModBlocks.SMART_TEMPERATURE_CHAMBER.get(), BoilerHeater.BLAZE_BURNER);
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.SMART_DRILL.get(),
                    new SmartDrillMovementBehaviour()
            );
            GogglesItem.addIsWearingPredicate(player ->
                    NightVisionWearHelper.isWearingGoggles(player));
            // 熔岩 + 下方黑曜石 + 旁边岩浆块 → 深板岩：必须等注册表绑定后再注册（见该类注释）
            FluidInteractions.register();
        });
    }
}