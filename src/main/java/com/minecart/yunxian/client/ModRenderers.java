package com.minecart.yunxian.client;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.battery.CrystalBatteryModel;
import com.minecart.yunxian.client.nightvision.model.NightVisionGogglesModel;
import com.minecart.yunxian.client.tooltip.GenericTooltipModifier;
import com.minecart.yunxian.client.echo.CameraSync;
import com.minecart.yunxian.client.echo.EchoHighlightRenderer;
import com.minecart.yunxian.client.echo.EchoSpyglassFrameRenderer;
import com.minecart.yunxian.client.echo.EchoSpyglassHeadLayer;
import com.minecart.yunxian.client.echo.EchoSpyglassScopeOverlay;
import com.minecart.yunxian.client.echo.EchoSpyglassUseRenderer;
import com.minecart.yunxian.client.mechanical.MechanicalAcceleratorRenderer;
import com.minecart.yunxian.client.mechanical.MechanicalCleanerRenderer;
import com.minecart.yunxian.client.mechanical.SmartDrillRenderer;
import net.minecraft.client.resources.language.I18n;

import com.minecart.yunxian.integration.curios.CuriosClientIntegration;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

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

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModRenderers::onClientSetup);
        modEventBus.addListener(ModRenderers::onAddLayers);
        modEventBus.addListener(ModRenderers::onRegisterAdditional);
        modEventBus.addListener(ModRenderers::onModifyBakingResult);
        if (FMLEnvironment.dist.isClient()) {
            EchoHighlightRenderer.register();
        }
        EchoSpyglassScopeOverlay.register();
        CameraSync.register();
        EchoSpyglassUseRenderer.register();
        modEventBus.addListener(ModRenderers::onRegisterRenderers);
        EchoSpyglassFrameRenderer.register();
    }

    private static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        // 关键修复：强制渲染器类在此刻加载（触发 PartialModel.of），并把其模型位置
        // 登记进本次烘焙，保证冷启动首次烘焙就包含这些 partial（替代手动 F3+T）。
        registerPartial(event, SmartDrillRenderer.HEAD);
        registerPartial(event, SmartDrillRenderer.SHAFT);
        registerPartial(event, SmartDrillRenderer.HEAD_FULL);
        registerPartial(event, MechanicalAcceleratorRenderer.SHAFT);
        registerPartial(event, MechanicalCleanerRenderer.SHAFT);
        registerPartial(event, MechanicalCleanerRenderer.PROPELLER);

        event.register(NIGHT_VISION_GOGGLES_3D);
        event.register(NIGHT_VISION_GOGGLES_3D_ON);
        event.register(EchoSpyglassFrameRenderer.FLAT_MODEL);
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
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.SMART_DRILL.get())
                    .factory(OrientedRotatingVisual.of(SmartDrillRenderer.HEAD))
                    .skipVanillaRender(be -> false)
                    .apply();

            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.MECHANICAL_ACCELERATOR.get())
                    .factory(OrientedRotatingVisual.of(MechanicalAcceleratorRenderer.SHAFT))
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

            // 水晶电池：连接材质 / 侧面剔除模型。
            // 渲染层（窗那几片薄面要镂空）写在模型文件的 render_type 里，不走已过时的
            // ItemBlockRenderTypes.setRenderLayer
            CrystalBatteryModel.register();
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
    }
    static final ModelResourceLocation ECHO_SPYGLASS_FLAT =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "item/echo_spyglass_flat"),
                    "standalone");
}