package com.minecart.yunxian.client;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.blockentity.MechanicalAcceleratorBlockEntity;
import com.minecart.yunxian.client.battery.CrystalBatteryModel;
import com.minecart.yunxian.client.deco.FlammableIceLayeredModel;
import com.minecart.yunxian.client.deco.FlammableIcePillarModel;
import com.minecart.yunxian.client.deco.SmartTemperatureChamberModel;
import com.minecart.yunxian.client.nightvision.model.NightVisionGogglesModel;
import com.minecart.yunxian.client.tooltip.GenericTooltipModifier;
import com.minecart.yunxian.client.echo.CameraSync;
import com.minecart.yunxian.client.echo.EchoHighlightRenderer;
import com.minecart.yunxian.client.echo.EchoSpyglassFrameRenderer;
import com.minecart.yunxian.client.echo.EchoSpyglassModel;
import com.minecart.yunxian.client.echo.EchoSpyglassHeadLayer;
import com.minecart.yunxian.client.echo.EchoSpyglassScopeOverlay;
import com.minecart.yunxian.client.echo.EchoSpyglassUseRenderer;
import com.minecart.yunxian.client.mechanical.MechanicalAcceleratorRenderer;
import com.minecart.yunxian.client.mechanical.MechanicalCleanerRenderer;
import com.minecart.yunxian.client.mechanical.PressureReliefHatchRenderer;
import com.minecart.yunxian.client.mechanical.ResonanceTableRenderer;
import com.minecart.yunxian.client.mechanical.SmartPressureReliefHatchRenderer;
import com.minecart.yunxian.client.mechanical.SmartDrillRenderer;
import com.minecart.yunxian.client.sundae.FlammableSundaeFrameRenderer;
import net.minecraft.client.resources.language.I18n;

import com.minecart.yunxian.integration.curios.CuriosClientIntegration;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

public class ModRenderers {

    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_3D =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/night_vision_goggles/night_vision_goggles"), "standalone");
    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_3D_ON =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/night_vision_goggles/night_vision_goggles_on"), "standalone");
    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_ITEM =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "night_vision_goggles"), "inventory");
    private static final ModelResourceLocation ECHO_SPYGLASS_ITEM =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_spyglass"), "inventory");

    /** 连接材质模型只往 Create 的替换器里登记一次（见 {@link #registerConnectedModelSwaps()}） */
    private static boolean connectedModelsRegistered;

    /**
     * 只允许由客户端调用（入口见 {@link Yunxian} 里那个 {@code FMLEnvironment.dist.isClient()} 判断）——
     * 这个类本身是客户端专用的，服务端够不到它就不该有任何入口。
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModRenderers::onClientSetup);
        modEventBus.addListener(ModRenderers::onAddLayers);
        modEventBus.addListener(ModRenderers::onRegisterAdditional);
        modEventBus.addListener(ModRenderers::onModifyBakingResult);
        EchoHighlightRenderer.register();
        EchoSpyglassScopeOverlay.register();
        CameraSync.register();
        EchoSpyglassUseRenderer.register();
        modEventBus.addListener(ModRenderers::onRegisterRenderers);
        EchoSpyglassFrameRenderer.register();
        // 可燃冰圣代挂进展示框时改用平面贴图（置物台/传送带同用 FIXED 视角，只能在渲染路径上分）
        FlammableSundaeFrameRenderer.register();
        ResonanceFilterHolderTracker.register();
        ResonanceNetworkOutlineRenderer.register();
    }

    /**
     * 连接材质模型（Create 的 {@code MODEL_SWAPPER}）必须在「本会话第一次烘焙」之前登记。
     * <p>
     * Create 自己在方块注册那一步（{@code CreateRegistrate#connectedTextures}）就把工厂挂进去了，
     * 而 {@code CustomBlockModels} 只在第一次 {@code ModifyBakingResult} 时把注册表快照一份
     * （{@code funcsLoaded} 置真之后再也不读新登记）——晚一步不是「下一帧生效」，而是整局都不生效，
     * F3+T 也救不回来。所以这里挪到 {@link ModelEvent.RegisterAdditional}：它必定排在
     * {@code ModifyBakingResult} 之前，且此时方块 id 已经取得到。
     * <p>
     * 幂等：一次会话只登记一次。{@code RegisterAdditional} 每次资源重载都会发，
     * 重复登记会让同一个模型被多包一层 {@code CTModel}，UV 被重映射两遍。
     */
    private static void registerConnectedModelSwaps() {
        if (connectedModelsRegistered) {
            return;
        }
        connectedModelsRegistered = true;
        // 可燃冰柱：连接材质（柱身 4x4 图集、端面 8x8 图集），仿 Create 的装饰柱那一档
        FlammableIcePillarModel.register();
        // 层叠可燃冰块：连接材质（侧面 2x2 图集、端面与柱子共用）
        FlammableIceLayeredModel.register();
    }

    private static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        // 连接材质模型：必须赶在本轮烘焙的 ModifyBakingResult 之前登记（原因见方法注释）
        registerConnectedModelSwaps();

        // 关键修复：强制渲染器类在此刻加载（触发 PartialModel.of），并把其模型位置
        // 登记进本次烘焙，保证冷启动首次烘焙就包含这些 partial（替代手动 F3+T）。
        registerPartial(event, SmartDrillRenderer.HEAD);
        registerPartial(event, SmartDrillRenderer.SHAFT);
        registerPartial(event, MechanicalAcceleratorRenderer.SHAFT);
        registerPartial(event, MechanicalCleanerRenderer.SHAFT);
        registerPartial(event, MechanicalCleanerRenderer.PROPELLER);

        event.register(NIGHT_VISION_GOGGLES_3D);
        event.register(NIGHT_VISION_GOGGLES_3D_ON);
        event.register(EchoSpyglassFrameRenderer.FLAT_MODEL);
        event.register(FlammableSundaeFrameRenderer.FLAT_MODEL);
    }

    private static void registerPartial(ModelEvent.RegisterAdditional event, PartialModel partial) {
        if (partial != null) {
            // NeoForge 强制要求 sideload 模型的变体必须是 "standalone"
            event.register(new ModelResourceLocation(partial.modelLocation(), "standalone"));
        }
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel itemModel = event.getModels().get(NIGHT_VISION_GOGGLES_ITEM);
        BakedModel goggles3d = event.getModels().get(NIGHT_VISION_GOGGLES_3D);
        BakedModel goggles3dOn = event.getModels().get(NIGHT_VISION_GOGGLES_3D_ON);
        if (itemModel != null && goggles3d != null && goggles3dOn != null) {
            event.getModels().put(NIGHT_VISION_GOGGLES_ITEM,
                    new NightVisionGogglesModel(itemModel, goggles3d, goggles3dOn));
        }

        // 回响望远镜：只补一个「这是平面物品」的标志，机械动力才会在置物台/传送带上把它放平
        BakedModel spyglass = event.getModels().get(ECHO_SPYGLASS_ITEM);
        if (spyglass != null) {
            event.getModels().put(ECHO_SPYGLASS_ITEM, new EchoSpyglassModel(spyglass));
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.SMART_DRILL.get())
                    .factory(OrientedRotatingVisual.of(SmartDrillRenderer.HEAD))
                    .skipVanillaRender(be -> false)
                    .apply();

            // 动力催生器：传动杆要贴在应力输入那一面，也就是 FACING 的背面。
            // OrientedRotatingVisual.of 只会把模型的 +Z 转到 FACING，而这里用的 Create 半截传动杆
            // 本身就是朝 +Z 伸出的，所以必须自己给工厂、把它转到 FACING.getOpposite()。
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.MECHANICAL_ACCELERATOR.get())
                    .factory((context, be, partialTick) -> new OrientedRotatingVisual<MechanicalAcceleratorBlockEntity>(
                            context, be, partialTick, Direction.SOUTH,
                            be.getBlockState().getValue(MechanicalAcceleratorBlock.FACING).getOpposite(),
                            Models.partial(MechanicalAcceleratorRenderer.SHAFT)))
                    .skipVanillaRender(be -> true)
                    .apply();

            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.MECHANICAL_CLEANER.get())
                    .factory(OrientedRotatingVisual.of(MechanicalCleanerRenderer.SHAFT))
                    .factory(OrientedRotatingVisual.of(MechanicalCleanerRenderer.PROPELLER))
                    .skipVanillaRender(be -> false)
                    .apply();

            ItemProperties.register(
                    ModItems.ECHO_SPYGLASS.get(),
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "using"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                    ? 1.0F : 0.0F);

            // 水晶电池：连接材质 / 侧面剔除模型（半成品，暂不并入下面那次统一登记）。
            // 渲染层（窗那几片薄面要镂空）写在模型文件的 render_type 里，不走已过时的
            // ItemBlockRenderTypes.setRenderLayer
            CrystalBatteryModel.register();
            // 智能温控室：连接材质。侧面是横排三格（不是方格阵），所以没走 Create 的 CTModel，
            // 自绘了 UV 换算，见那个类的注释
            SmartTemperatureChamberModel.register();
            // 可燃冰柱 / 层叠可燃冰：正常早在 onRegisterAdditional 里登记过了，
            // 这里只是兜底（万一那一趟没跑到，至少还赶得上随后的资源重载）
            registerConnectedModelSwaps();
            // ===== 物品提示（机械动力风格，统一走 GenericTooltipModifier）=====
            // 夜视仪护目镜：简介含当前按键名，动态求值
            GenericTooltipModifier.register(ModItems.NIGHT_VISION_GOGGLES.get(),
                    () -> I18n.get("item.create_crystal_industry.night_vision_goggles.tooltip.summary",
                            ModKeybinds.TOGGLE_NIGHT_VISION.getTranslatedKeyMessage().getString()));

            // 回响望远镜：两行静态简介（Shift 展开按此顺序显示）
            GenericTooltipModifier.register(ModItems.ECHO_SPYGLASS.get(),
                    "item.create_crystal_industry.echo_spyglass.tooltip.summary",
                    "item.create_crystal_industry.echo_spyglass.tooltip.note");

            // 可燃冰：单行静态简介
            GenericTooltipModifier.register(ModItems.FLAMMABLE_ICE.get(),
                    "item.create_crystal_industry.flammable_ice.tooltip.summary");
            // 可燃冰圣代：单行静态简介（方块物品，名字取方块那套，简介仍是 item. 前缀）
            GenericTooltipModifier.register(ModBlocks.FLAMMABLE_SUNDAE.get().asItem(),
                    "item.create_crystal_industry.flammable_sundae.tooltip.summary");

            // 动力催生器：机械动力自家应力元件的物品提示（“应力影响：||| 16.0x RPM”，
            // 戴工程师护目镜时显示数值，不戴时显示“高/中/低”的进度条）。
            // 直接挂 Create 的 KineticStats，不另写一套排版：它在 Create.java 的
            // tooltipModifierFactory 里只经 CreateRegistrate 发给 Create 自己的物品，
            // 我们这套 DeferredRegister 注册的方块物品必须手动补一次，否则悬停时没有这几行
            // （方块实体那侧的护目镜面板走 addToGoggleTooltip，与这条物品提示互不相干）。
            Item acceleratorItem = ModBlocks.MECHANICAL_ACCELERATOR.get().asItem();
            TooltipModifier.REGISTRY.register(acceleratorItem,
                    new KineticStats(ModBlocks.MECHANICAL_ACCELERATOR.get()));
            // ★ 软依赖门控：客户端 + Curios 已加载才注册首饰栏渲染器
            if (ModList.get().isLoaded("curios")) {
                CuriosClientIntegration.registerRenderers();
            }
        });
    }

    // ========== 恢复原样：不加也不移除任何头部渲染层 ==========
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = (PlayerRenderer) event.getSkin(skin);
            renderer.addLayer(new EchoSpyglassHeadLayer(renderer));
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.MECHANICAL_CLEANER.get(),
                MechanicalCleanerRenderer::new);
        // 泄压舱口 / 智能泄压舱口：方块本体走普通模型，这里只补侧面过滤槽里那件过滤物品
        event.registerBlockEntityRenderer(
                ModBlockEntities.PRESSURE_RELIEF_HATCH.get(),
                PressureReliefHatchRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.SMART_PRESSURE_RELIEF_HATCH.get(),
                SmartPressureReliefHatchRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.RESONANCE_TABLE.get(),
                ResonanceTableRenderer::new);
    }
    static final ModelResourceLocation ECHO_SPYGLASS_FLAT =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "item/echo_spyglass_flat"),
                    "standalone");
}