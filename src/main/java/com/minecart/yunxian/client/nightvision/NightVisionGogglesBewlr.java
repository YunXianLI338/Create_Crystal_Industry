package com.minecart.yunxian.client.nightvision;

import com.minecart.yunxian.client.nightvision.model.GlowingLensItemModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 开启态护目镜的自定义物品渲染器：让四个镜筒前方的镜片面无视环境光照。
 * <p>
 * 做法是照抄原版 {@link ItemRenderer#renderModelLists}
 * （逐个方向 {@code setSeed(42L)} 取 quad），只是把每个方向的 quad 按
 * {@link #LENS_TINT_INDEX} 分成两批，用<b>同一个</b>渲染类型、<b>同一个</b> VertexConsumer
 * 各画一遍：
 * <ul>
 *   <li>普通面：传调用方给的环境光 {@code combinedLight}；</li>
 *   <li>镜片面：传 {@link LightTexture#FULL_BRIGHT}。</li>
 * </ul>
 * 与原版唯一的差别就是这四个面的 lightmap 值，渲染类型、贴合、混合方式全都一样，
 * 所以外观不会出现透明、变暗或 z-fighting。
 * <p>
 * 镜片面靠模型 JSON 里的 {@code "tintindex": 1} 标记识别——该物品没注册
 * {@code ItemColor}，{@code ItemColors} 对它返回 -1（纯白），标记不会改变贴图颜色。
 */
public final class NightVisionGogglesBewlr extends BlockEntityWithoutLevelRenderer {

    public static final NightVisionGogglesBewlr INSTANCE = new NightVisionGogglesBewlr();

    /** 模型 JSON 里镜片面的 tintindex */
    public static final int LENS_TINT_INDEX = 1;

    private NightVisionGogglesBewlr() {
        // 这里不用任何原版模型资源（全用自己的 baked 模型），与机械动力的自定义渲染器一样传 null
        super(null, null);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        BakedModel model = GlowingLensItemModel.PENDING_SOURCE.get();
        GlowingLensItemModel.PENDING_SOURCE.remove();
        if (model == null) {
            return;
        }

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        // 与本物品原本走的渲染类型完全一致（非 BlockItem ⇒ 半透明裁剪方块贴图）
        VertexConsumer buffer = ItemRenderer.getFoilBufferDirect(
                bufferSource, Sheets.translucentCullBlockSheet(), true, stack.hasFoil());

        RandomSource random = RandomSource.create();
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            renderQuads(itemRenderer, poseStack, buffer, model.getQuads(null, direction, random),
                    stack, combinedLight, combinedOverlay);
        }
        random.setSeed(42L);
        renderQuads(itemRenderer, poseStack, buffer, model.getQuads(null, null, random),
                stack, combinedLight, combinedOverlay);
    }

    /** 画一个方向上的 quad：先画普通面（环境光），再画镜片面（满亮） */
    private void renderQuads(ItemRenderer itemRenderer, PoseStack poseStack, VertexConsumer buffer,
                             List<BakedQuad> quads, ItemStack stack,
                             int combinedLight, int combinedOverlay) {
        if (quads.isEmpty()) {
            return;
        }
        List<BakedQuad> base = new ArrayList<>(quads.size());
        List<BakedQuad> lens = new ArrayList<>(1);
        for (BakedQuad quad : quads) {
            if (quad.getTintIndex() == LENS_TINT_INDEX) {
                lens.add(quad);
            } else {
                base.add(quad);
            }
        }
        if (!base.isEmpty()) {
            itemRenderer.renderQuadList(poseStack, buffer, base, stack, combinedLight, combinedOverlay);
        }
        if (!lens.isEmpty()) {
            itemRenderer.renderQuadList(poseStack, buffer, lens, stack, LightTexture.FULL_BRIGHT, combinedOverlay);
        }
    }
}
