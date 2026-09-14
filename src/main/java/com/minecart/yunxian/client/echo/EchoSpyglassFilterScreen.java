package com.minecart.yunxian.client.echo;

import com.google.common.collect.ImmutableList;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.menu.EchoSpyglassFilterMenu;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Inventory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class EchoSpyglassFilterScreen extends AbstractSimiContainerScreen<EchoSpyglassFilterMenu> {

    // 纹理引用
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/gui/echo_spyglass_filter.png");

    private static final ResourceLocation CREATE_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");

    // ========== 平面贴图参数 ==========
    private static final ResourceLocation ECHO_OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/item/echo_spyglass.png");
    // 贴图渲染的目标宽度（像素），高度按原图宽高比自动缩放
    private static final int TEXTURE_SIZE = 50;
    // 贴图中心点相对 GUI 左上角的位置（像素）
    private static final int TEXTURE_X = 210;
    private static final int TEXTURE_Y = 60;

    // JEI 规避区域：贴图实际占用宽高（像素），可适当留边
    private static final int TEXTURE_AREA_WIDTH = 70;
    private static final int TEXTURE_AREA_HEIGHT = 70;

    // 贴图实际像素尺寸缓存：-1 = 尚未读取，0 = 读取失败
    private int texWidth = -1;
    private int texHeight = -1;

    // 尺寸常量
    private static final int MAIN_HEIGHT = 88;
    private static final int INVENTORY_HEIGHT = 108;
    private static final int TOTAL_HEIGHT = MAIN_HEIGHT + INVENTORY_HEIGHT;

    // 主界面上移
    private static final int MOVE_UP = 6;

    // 幽灵槽位置
    private static final int FILTER_SLOT_X = 79;
    private static final int FILTER_SLOT_Y = 22;
    private static final int SLOT_SIZE = 18;

    // 按钮位置
    private static final int BUTTON_X = 149;
    private static final int BUTTON_Y = 57;
    private static final int BUTTON_SIZE = 18;

    public EchoSpyglassFilterScreen(EchoSpyglassFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 182;
        this.imageHeight = TOTAL_HEIGHT;
    }

    @Override
    protected void init() {
        // AbstractSimiContainerScreen 要求：setWindowSize 必须在 super.init() 之前调用
        setWindowSize(182, TOTAL_HEIGHT);
        super.init();

        // ---- 标题位置 ----
        this.titleLabelY = -2;
        int filterCenterX = FILTER_SLOT_X + SLOT_SIZE / 2;
        int titleWidth = this.font.width(this.title);
        this.titleLabelX = filterCenterX - titleWidth / 2;

        // ---- "物品栏"标签 ----
        this.inventoryLabelX = 8;
        this.inventoryLabelY = MAIN_HEIGHT + 6;

        // ---- 确认按钮 ----
        IconButton confirmButton = new IconButton(
                this.leftPos + BUTTON_X,
                this.topPos + BUTTON_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(confirmButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 1. 绘制主界面纹理（上方），Y坐标上移 MOVE_UP 像素
        int mainY = this.topPos - MOVE_UP;
        graphics.blit(TEXTURE, this.leftPos, mainY, 0, 0, this.imageWidth, MAIN_HEIGHT);

        // 2. 绘制机械动力的背包纹理（下方），保持原位置
        int inventoryY = this.topPos + MAIN_HEIGHT;
        graphics.blit(CREATE_INVENTORY_TEXTURE, this.leftPos, inventoryY, 0, 0, 176, INVENTORY_HEIGHT);

        // 3. 在背景之上绘制回响望远镜平面贴图
        this.renderEchoSpyglassTexture(graphics);
    }

    /**
     * 渲染平面贴图。
     * 位置/大小由 TEXTURE_X, TEXTURE_Y, TEXTURE_SIZE 控制。
     */
    private void renderEchoSpyglassTexture(GuiGraphics graphics) {
        this.ensureTextureSize();
        if (this.texWidth <= 0 || this.texHeight <= 0) {
            return; // 贴图缺失或读取失败：不绘制，避免紫黑块
        }

        // 保持宽高比：宽度固定 TEXTURE_SIZE，高度等比缩放
        int w = TEXTURE_SIZE;
        int h = Math.max(1, (int) Math.round((float) this.texHeight * TEXTURE_SIZE / this.texWidth));

        // 以 (TEXTURE_X, TEXTURE_Y) 为中心
        int x = this.leftPos + TEXTURE_X - w / 2;
        int y = this.topPos + TEXTURE_Y - h / 2;

        // 用 PoseStack 把 1:1 像素绘制缩放到目标 w×h
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale((float) w / this.texWidth, (float) h / this.texHeight, 1.0F);
        graphics.blit(ECHO_OUTLINE_TEXTURE, 0, 0, 0, 0, this.texWidth, this.texHeight, this.texWidth, this.texHeight);
        pose.popPose();
    }

    /**
     * 让 JEI 的物品栏避开右侧渲染的回响望远镜贴图区域。
     */
    @Override
    public List<Rect2i> getExtraAreas() {
        int x = this.leftPos + TEXTURE_X - TEXTURE_AREA_WIDTH / 2;
        int y = this.topPos + TEXTURE_Y - TEXTURE_AREA_HEIGHT / 2;
        return ImmutableList.of(new Rect2i(x, y, TEXTURE_AREA_WIDTH, TEXTURE_AREA_HEIGHT));
    }

    /**
     * 从资源里读取贴图的真实像素尺寸，只读一次并缓存。
     * 1.21.1 的 AbstractTexture 没有 getWidth/getHeight，所以需要解码一次 PNG 拿尺寸。
     */
    private void ensureTextureSize() {
        if (this.texWidth != -1) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> opt = mc.getResourceManager().getResource(ECHO_OUTLINE_TEXTURE);
        if (opt.isPresent()) {
            try (InputStream in = opt.get().open();
                 NativeImage img = NativeImage.read(in)) {
                this.texWidth = img.getWidth();
                this.texHeight = img.getHeight();
                return;
            } catch (IOException e) {
                // 读取失败，落到下面置 0
            }
        }
        this.texWidth = 0;
        this.texHeight = 0;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 绘制标题
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        // 绘制物品栏标签
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}