package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.Yunxian;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/** 在物品栏/热键栏里用 16x16 平面贴图代替被隐藏的 3D 模型 */
public final class EchoSpyglassGuiDecorator implements IItemDecorator {

    private static final ResourceLocation FLAT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/item/echo_spyglass.png");

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack,
                          int xOffset, int yOffset) {
        graphics.pose().pushPose();
        // 关键修复:把平面贴图抬到与"物品模型"相同的深度。
        // GuiGraphics.renderItem 画模型时会在 base 之上再 translate z=150
        // (槽位 base=100 → 250,浮动物品 base=232 → 382);
        // 而装饰器回调时 pose 上只有 base,blit 的 blitOffset 又是 0,
        // 浮动物品时画在 232 < 槽位模型 250,深度测试一开就被其他图标挡住。
        // 这里补上 150,让贴图落到模型本该在的那一层。
        graphics.pose().translate(0.0F, 0.0F, 150.0F);
        graphics.blit(FLAT_TEXTURE, xOffset, yOffset, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
        return true;
    }
}