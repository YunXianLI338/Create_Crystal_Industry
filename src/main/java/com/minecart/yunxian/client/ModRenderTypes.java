package com.minecart.yunxian.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.OptionalDouble;

public final class ModRenderTypes extends RenderType {

    // 矿石线框（四边形粗线）。
    // 千万别给它挂 ITEM_ENTITY_TARGET：那是「极致」画质专用的 itemEntity 中间缓冲，
    // 只能由 transparency 后处理链合成，而合成时按每像素深度决定层叠顺序——我们的四边形
    // 只写颜色不写深度，深度停在「最远」，于是被不透明场景整层盖住：对着地形看不见、相机一动就闪。
    // 输出状态留默认即可（默认的 MAIN_TARGET 是个空实现，想「切回主缓冲」只能在 draw 时自己 bindWrite）。
    public static final RenderType ECHO_ORE_OVERLAY_QUADS = create(
            "echo_ore_overlay_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    // 幽匿全屏覆盖层：方块图集纹理 + POSITION_TEX（透明度由全局 shaderColor 控制）
    public static final RenderType SCULK_OVERLAY = create(
            "sculk_overlay",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_TEX_SHADER)
                    // ← 修复：纹理绑定走 setTextureState，不再用 setTexturingState
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                           int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                           Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}