package com.minecart.yunxian.client.fog;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModBlocks;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.FogShape;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 视野陷进可燃冰沙里时的迷雾：颜色取那块方块贴图的平均色，距离与形状照抄原版细雪。
 * <p>
 * <b>为什么要自己写。</b>原版的细雪迷雾挂在 {@code Camera#getFluidInCamera()} 上：
 * 它拿相机近平面上的五个采样点（正前、四角）逐格查方块，命中
 * {@code blockstate.is(Blocks.POWDER_SNOW)} 就返回 {@code FogType.POWDER_SNOW}，
 * 之后 {@code FogRenderer} 照这个类型给药水色（0.623, 0.734, 0.785）与 0~2 格的距离。
 * 那一步判的是<b>方块本身</b>，不是「继承自 {@code PowderSnowBlock}」——可燃冰沙虽然整个
 * 继承细雪（见 {@link com.minecart.yunxian.block.FlammableIceSlurryBlock}），但方块注册的是自己的
 * {@code create_crystal_industry:flammable_ice_slurry}，原版那条判断永远不成立。
 * 而且那边还有一条更硬的：它的 {@code entityInside} 刻意不调 {@code setIsInPowderSnow(true)}
 * （细雪冻人的入口，可燃冰沙不能借），所以连"玩家在细雪里"这条旁路也断着。
 * <p>
 * 于是这里把原版那一套照抄一遍，只换两处：<b>判定</b>换成认可燃冰沙，<b>颜色</b>换成贴图平均色。
 * 判定沿用原版近平面五点采样：站在方块边缘、只把眼睛探进去半格时，单查相机所在格会漏掉。
 */
@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class FlammableIceSlurryFog {

    /**
     * 迷雾取色的贴图，与 {@code blockstates/flammable_ice_slurry.json} 里那个模型引的是同一张。
     */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/flammable_ice_slurry/block");

    /**
     * 原版近平面采样点离眼睛多远（{@code Camera#getNearPlane} 里那个 0.05）。
     */
    private static final float NEAR_PLANE_DEPTH = 0.05F;

    /**
     * 原版细雪迷雾的距离：{@code FogRenderer#setupFog} 里 POWDER_SNOW 那支给的就是 0 到 2 格，
     * 旁观者是 -8 起步、到视距的一半。
     */
    private static final float FOG_START = 0.0F;

    private static final float FOG_END = 2.0F;

    private static final float SPECTATOR_FOG_START = -8.0F;

    /**
     * 拿不到贴图时的退路：当前这张 16×16 贴图的平均色（0.588, 0.743, 1.0）。
     * <p>
     * 正常情况下永远走不到——贴图就在模组自己的资源里。留这一手是怕资源包把图换掉、
     * 或者贴图还没进图集时把雾染成黑的。
     */
    private static final float[] FALLBACK_COLOR = { 0.588F, 0.743F, 1.0F };

    /**
     * 上一次算平均色用的那张精灵图。图集重载后精灵图会换新的，据此重算。
     */
    private static TextureAtlasSprite cachedSprite;

    private static float[] cachedColor;

    private FlammableIceSlurryFog() {
    }

    /**
     * 在迷雾着色前把颜色换成贴图平均色。
     * <p>
     * 这个事件在 {@code FogRenderer#setupColor} 的末尾、也就是原版自己算完天空色/下雨/雷暴之后发，
     * 所以覆盖上去不会被原版那套再冲掉；随后 {@code RenderSystem.clearColor} 用的就是这里的值。
     */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isCameraInSlurry(event.getCamera())) {
            return;
        }

        float[] color = fogColor();
        event.setRed(color[0]);
        event.setGreen(color[1]);
        event.setBlue(color[2]);
    }

    /**
     * 把迷雾的距离与形状压到原版细雪那一档。
     * <p>
     * <b>必须取消事件</b>：{@code ClientHooks#onFogRender} 只在事件被取消时才把改过的
     * 起点/终点/形状写回着色器（{@code RenderSystem.setShaderFogStart/End/Shape}），
     * 光调 setter 是没人读的。
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!isCameraInSlurry(event.getCamera())) {
            return;
        }

        Entity entity = event.getCamera().getEntity();
        if (entity != null && entity.isSpectator()) {
            // 旁观者按原版放远：从 -8 开始，到视距的一半（这里的事件里拿不到视距，只能问渲染器）
            event.setNearPlaneDistance(SPECTATOR_FOG_START);
            event.setFarPlaneDistance(Minecraft.getInstance().gameRenderer.getRenderDistance() * 0.5F);
        } else {
            event.setNearPlaneDistance(FOG_START);
            event.setFarPlaneDistance(FOG_END);
        }
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    /**
     * 相机是否已经探进可燃冰沙里。
     * <p>
     * 采样点照抄 {@code Camera#getFluidInCamera()}：正前方 0.05 格，加上近平面四角。
     * <b>水与岩浆的雾优先</b>——相机泡在水里时原版给的是水色，这里不该抢。
     */
    private static boolean isCameraInSlurry(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || !camera.isInitialized()) {
            return false;
        }
        if (camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }

        Vec3 eye = camera.getPosition();
        Camera.NearPlane nearPlane = camera.getNearPlane();
        Vec3 forward = new Vec3(camera.getLookVector()).scale(NEAR_PLANE_DEPTH);

        return inSlurry(level, eye, forward)
                || inSlurry(level, eye, nearPlane.getTopLeft())
                || inSlurry(level, eye, nearPlane.getTopRight())
                || inSlurry(level, eye, nearPlane.getBottomLeft())
                || inSlurry(level, eye, nearPlane.getBottomRight());
    }

    private static boolean inSlurry(Level level, Vec3 eye, Vec3 offset) {
        BlockPos pos = BlockPos.containing(eye.add(offset));
        return level.getBlockState(pos).is(ModBlocks.FLAMMABLE_ICE_SLURRY.get());
    }

    /**
     * 贴图平均色，第一次用到时算一次就缓存；精灵图换了（资源包重载）再重算。
     */
    private static float[] fogColor() {
        TextureAtlasSprite sprite =
                Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(TEXTURE);
        if (sprite == null) {
            return FALLBACK_COLOR;
        }
        if (sprite == cachedSprite && cachedColor != null) {
            return cachedColor;
        }

        cachedSprite = sprite;
        cachedColor = averageColor(sprite);
        return cachedColor;
    }

    /**
     * 逐像素求平均，按 alpha 加权；全透明的像素直接跳过，免得贴图的留白把颜色冲淡。
     */
    private static float[] averageColor(TextureAtlasSprite sprite) {
        NativeImage image = sprite.contents().getOriginalImage();
        if (image.format() != NativeImage.Format.RGBA || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return FALLBACK_COLOR;
        }

        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long weight = 0L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // NativeImage 里存的是 ABGR：低字节是红
                int pixel = image.getPixelRGBA(x, y);
                int alpha = pixel >>> 24;
                if (alpha == 0) {
                    continue;
                }
                red += (long) (pixel & 0xFF) * alpha;
                green += (long) ((pixel >>> 8) & 0xFF) * alpha;
                blue += (long) ((pixel >>> 16) & 0xFF) * alpha;
                weight += alpha;
            }
        }

        if (weight == 0L) {
            return FALLBACK_COLOR;
        }
        return new float[] {
                (float) red / weight / 255.0F,
                (float) green / weight / 255.0F,
                (float) blue / weight / 255.0F
        };
    }
}
