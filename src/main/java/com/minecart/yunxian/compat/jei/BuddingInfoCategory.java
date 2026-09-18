package com.minecart.yunxian.compat.jei;

import java.util.List;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.compat.jei.BuddingInfo.Row;
import com.minecart.yunxian.compat.jei.BuddingInfo.Style;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * JEI 的「母岩信息」页：左边 3D 渲染母岩与其晶簇，右边逐行写生长条件 / 生长速度 / 生成条件。
 * <p>
 * 三条刻意的取舍：
 * <ul>
 *   <li><b>左侧是自己渲染的方块模型</b>（{@code BlockRenderDispatcher}），不是 JEI 的物品槽——
 *       芽与晶簇的物品模型是平面贴图，只有渲染方块状态才看得出它们的立体形状；
 *       晶簇还按它在世界里的样子<b>长在母岩顶面上</b>（而不是并排摆两块）。为了让玩家仍能
 *       对着母岩按 R/U 翻到这一页，同样的物品以"不可见材料"登记进了 JEI（见 {@link #setRecipe}）。</li>
 *   <li><b>底板交给 JEI</b>：JEI 会先给每个配方铺一块自己的九宫格底板
 *       （{@code jei:single_recipe_background}，浅灰）再调 {@link #draw}，所以本页只画内容——
 *       页面看起来才和别的配方一致，资源包换掉那块底板时本页也跟着变。</li>
 *   <li><b>行数与换行在绘制时兜底</b>：文案是构建期定好的（见 {@link BuddingInfoCollector}），
 *       但只要有换行就可能超出固定高度，所以这里画不下就直接停笔，宁可少一行也不画出框。</li>
 * </ul>
 */
public class BuddingInfoCategory extends AbstractRecipeCategory<BuddingInfo> {

    public static final RecipeType<BuddingInfo> TYPE =
            RecipeType.create(Yunxian.MODID, "budding_info", BuddingInfo.class);

    // ==================== 版面 ====================

    private static final int WIDTH = 280;
    private static final int HEIGHT = 180;

    /** 左右两栏之间的分栏线（x 与上下留白） */
    private static final int DIVIDER_X = 62;
    private static final int DIVIDER_MARGIN = 8;

    /** 左侧整组渲染（母岩 + 长在它顶面上的晶簇）的中心 */
    private static final int RENDER_CENTER_X = 31;
    private static final int RENDER_CENTER_Y = 90;
    /**
     * 方块渲染的缩放。等轴测下一个方块投影成约 1.41 宽 × 1.57 高（单位：模型单位 × 缩放），
     * 而"母岩 + 顶上一格晶簇"是两格高：约 1.41 × (2×0.87 + 0.71) = 1.41 × 2.44。
     * 取 36 时整组约 51 × 88 像素，正好落在左侧 62 像素宽的栏里（别调大到超过它）。
     */
    private static final float RENDER_SCALE = 36.0F;
    /** 整组渲染的外接框，用于悬停判定（就是上面算出的 51 × 88，取整） */
    private static final int RENDER_BOX_W = 52;
    private static final int RENDER_BOX_H = 88;
    private static final int RENDER_X = RENDER_CENTER_X - RENDER_BOX_W / 2;
    private static final int RENDER_Y = RENDER_CENTER_Y - RENDER_BOX_H / 2;
    /**
     * 一格高在屏幕上的投影（{@code cos30°×缩放}）与"本格前角比中心再低多少"（{@code sin30°×√2÷2×缩放}）；
     * 两者正是上面那个外接框的组成：{@code RENDER_BOX_H = 2×SIDE + 2×FRONT}。
     * 阴影要贴到方块最底下那个角上，就得用这两个数把方块底面算出来。
     */
    private static final float SIDE_PIXELS = RENDER_SCALE * 0.866F;
    private static final float FRONT_PIXELS = RENDER_SCALE * 0.354F;

    /**
     * Create 的 JEI 贴图：{@code AllGuiTextures} 里所有 {@code jei/widgets} 开头的条目都落在这个文件上
     * （它把枚举构造器的字符串拼成 {@code create:textures/gui/<路径>.png}）。
     * 本页只用它两张图：底下的阴影与产物的箭头，坐标都是照 {@code AllGuiTextures} 的构造器实参取的。
     */
    private static final ResourceLocation CREATE_JEI_WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/jei/widgets.png");
    /** 阴影：{@code AllGuiTextures.JEI_SHADOW} —— u=0、v=56，52×11 的灰椭圆 */
    private static final int SHADOW_U = 0;
    private static final int SHADOW_V = 56;
    private static final int SHADOW_WIDTH = 52;
    private static final int SHADOW_HEIGHT = 11;

    /**
     * 晶簇产物栏位（16×16 物品格的左上角）：摆在渲染出来的晶簇的<b>上方偏左</b>，
     * 栏位右侧画一支 Create 同款的箭头指着它（见 {@link #drawProductArrow}）。JEI 会在格子外面补一圈边框。
     */
    private static final int PRODUCT_SLOT_X = 5;
    private static final int PRODUCT_SLOT_Y = 30;
    /** 物品格边长，用来算栏位边框与中心 */
    private static final int SLOT_SIZE = 16;

    /** Create 的 JEI 下箭头：{@code AllGuiTextures.JEI_DOWN_ARROW} —— u=0、v=21，18×14，原样贴、不旋转 */
    private static final int ARROW_U = 0;
    private static final int ARROW_V = 21;
    private static final int ARROW_W = 18;
    private static final int ARROW_H = 14;
    /** 竖直方向：以栏位中心为基准再往下挪这么多像素 */
    private static final int ARROW_DROP = 5;
    /** 箭头摆在栏位右侧，与栏位边框之间留一点空隙 */
    private static final int ARROW_X = PRODUCT_SLOT_X + SLOT_SIZE + 3;
    private static final int ARROW_Y = PRODUCT_SLOT_Y + SLOT_SIZE / 2 - ARROW_H / 2 + ARROW_DROP;

    private static final int TEXT_X = 68;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X - 6;
    private static final int TEXT_TOP = 10;
    private static final int LINE_HEIGHT = 10;
    /** 小节之间多留一点空隙，让三节读起来是分开的 */
    private static final int SECTION_GAP = 4;
    /**
     * 每次绘制最多写几行（含小节标题与折行后的续行）。版面高度就按它定的：
     * {@code (HEIGHT - 4 - TEXT_TOP) / LINE_HEIGHT} 约 16 行，而最长的一条
     * （粗锌母岩：转化 + Create 的 config_filter 说明）按 {@link #TEXT_WIDTH} 折行后约 15 行。
     * 文案再长就该精简 {@link BuddingInfoCollector} 里的行，而不是把版面撑得更大。
     */
    private static final int MAX_LINES = 16;

    /**
     * 晶簇渲染的提亮系数：晶簇是 {@code cross} 模型，模型里 {@code shade=false} 表示它不参与方向性明暗，
     * 页面上的明暗完全由贴图决定——深色晶体（回响、福鲁伊克斯）在浅灰底上就显得发闷。
     * 只影响这一页的观感，不动方块在世界里的任何属性。
     */
    private static final float CLUSTER_BRIGHTNESS = 1.3F;

    /** 方块渲染的 z：与物品渲染同级，保证盖在自绘背景之上、又在 JEI 的浮层之下 */
    private static final float Z_LEVEL = 150.0F;

    // ==================== 配色 ====================
    // 底色是 JEI 自己那块九宫格底板（{@code jei:single_recipe_background}，主色 #C6C6C6 的浅灰），
    // 所以文字用深色、不加阴影；资源包把底板换深了的话，改这三个常量即可。

    private static final int HEADER_TEXT = 0xFF1F1F1F;
    private static final int LINE_TEXT = 0xFF3F3F3F;
    private static final int NOTE_TEXT = 0xFF636363;

    /**
     * 分栏线：半透明黑（alpha 0x66 ≈ 40%）。GUI 的 fill 是带混合的，所以这里压出来的
     * 是一条柔和的深灰线，而不是一块死黑——换任何底色都不刺眼。
     */
    private static final int DIVIDER = 0x66000000;

    public BuddingInfoCategory(IGuiHelper guiHelper) {
        super(TYPE,
                Component.translatable(BuddingInfoText.LANG + "title"),
                guiHelper.createDrawableItemStack(new ItemStack(Blocks.BUDDING_AMETHYST)),
                WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BuddingInfo recipe, IFocusGroup focuses) {
        // 唯一一个可见栏位：晶簇的产物，摆在左上角、正上方有支箭头指着它。
        // 可见栏位天然参与检索与收藏书签，所以产物按下 R/U 也能翻到这一页。
        ItemStack product = recipe.product();
        if (!product.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, PRODUCT_SLOT_X, PRODUCT_SLOT_Y).addItemStack(product);
        }

        // 其余（母岩与各级芽/晶簇）不显示：左侧的方块是我们自己渲染的。它们只服务于 JEI 的检索，
        // 所以走"不可见材料"（JEI 文档：重要的检索材料，但不在版面上显示）。
        //
        // 同一批物品必须两种角色都挂。JEI 按 R/U 时的查找角色是（FocusInputHandler）：
        //   R（配方）→ OUTPUT                       U（用途）→ INPUT 或 CATALYST
        // 信息页两头都不沾，只挂 OUTPUT 时按 U 就翻不到——自带母岩当初能翻到，只是因为它们
        // 另外注册成了催化剂；KubeJS 脚本注册的与别的模组的没有催化剂，于是只剩 R 能用。
        // 挂上 INPUT 之后，"用途"那一侧也能查到这一页，且不必把它们都塞进催化剂列表。
        List<ItemStack> items = recipe.lookupItems();
        if (items.isEmpty()) {
            return;
        }
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(items);
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(items);
    }

    @Override
    public void draw(BuddingInfo recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        drawDivider(guiGraphics);
        if (!recipe.product().isEmpty()) {
            drawProductArrow(guiGraphics);
        }
        // 阴影像地面一样垫在方块下面，所以要先画：方块的正面会压住它的上缘
        drawShadow(guiGraphics, pivotY(recipe));
        drawBlocks(guiGraphics, recipe, pivotY(recipe));
        drawLines(guiGraphics, recipe.rows());
    }

    /**
     * 在母岩底下垫一块 Create 的 JEI 阴影——就是 {@code AllGuiTextures.JEI_SHADOW} 用的那张贴图里的那一块
     * （{@code assets/create/textures/gui/jei/widgets.png}，u=0、v=56，52×11 的实心灰椭圆），
     * 用的是同一个 {@code blit} 调用，所以本页与 Create 自己的 JEI 页面观感一致。
     * <p>
     * 贴图只引用、不复制进本模组（Create 是本模组的必需前置，路径一定在）。
     */
    private static void drawShadow(GuiGraphics guiGraphics, float pivotY) {
        int x = RENDER_CENTER_X - SHADOW_WIDTH / 2;
        // 阴影贴着方块最底下的那个角：它在屏幕上的高度 = 重心高度 + 半格投影 + 前角下探
        int bottom = Math.round(RENDER_CENTER_Y + SIDE_PIXELS * pivotY + FRONT_PIXELS);
        guiGraphics.blit(CREATE_JEI_WIDGETS, x, bottom - SHADOW_HEIGHT + 1,
                SHADOW_U, SHADOW_V, SHADOW_WIDTH, SHADOW_HEIGHT);
    }

    /** 整组（母岩 + 顶上的晶簇）的重心在块空间里的高度：有晶簇时两格高，重心落在第二格的中心 */
    private static float pivotY(BuddingInfo recipe) {
        return recipe.cluster() == null ? 0.5F : 1.0F;
    }

    /**
     * 产物栏位右边那支箭头：原样贴 Create 的 {@code JEI_DOWN_ARROW}
     * （{@code jei/widgets.png} 的 u=0、v=21，18×14），不旋转也不缩放，就是那几行像素。
     */
    private static void drawProductArrow(GuiGraphics guiGraphics) {
        guiGraphics.blit(CREATE_JEI_WIDGETS, ARROW_X, ARROW_Y, ARROW_U, ARROW_V, ARROW_W, ARROW_H);
    }

    /**
     * 左右两栏之间的一条淡线。
     * <p>
     * 这是本页唯一自绘的装饰：底色是 JEI 的，双方都不画分栏时左栏的方块与右栏的文字容易糊成一片。
     * 颜色取 JEI 自己边框（#999999）与底色（#C6C6C6）之间的灰，既不抢眼也不至于看不见。
     */
    private static void drawDivider(GuiGraphics guiGraphics) {
        guiGraphics.fill(DIVIDER_X, DIVIDER_MARGIN, DIVIDER_X + 1, HEIGHT - DIVIDER_MARGIN, DIVIDER);
    }

    /** 悬停渲染出来的方块时给出它的名字（外来的母岩方块往往没有物品，靠这里也认得出来） */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, BuddingInfo recipe, IRecipeSlotsView recipeSlotsView,
                           double mouseX, double mouseY) {
        // 箭头下移后会压到悬停区的一角，那一块不算渲染出来的方块
        if (isInside(mouseX, mouseY, ARROW_X, ARROW_Y, ARROW_W, ARROW_H)
                || !isInside(mouseX, mouseY, RENDER_X, RENDER_Y, RENDER_BOX_W, RENDER_BOX_H)) {
            return;
        }
        tooltip.add(recipe.budding().getBlock().getName().withStyle(ChatFormatting.WHITE));
        if (recipe.cluster() != null) {
            tooltip.add(recipe.cluster().getBlock().getName().withStyle(ChatFormatting.WHITE));
        }
    }

    // ==================== 绘制 ====================

    private static boolean isInside(double mouseX, double mouseY, int boxX, int boxY, int boxW, int boxH) {
        return mouseX >= boxX && mouseX < boxX + boxW && mouseY >= boxY && mouseY < boxY + boxH;
    }

    /**
     * 把母岩与它的晶簇按「物品栏里那种等轴测视角」画进左栏，晶簇摆在母岩<b>顶面</b>上——
     * 也就是它在世界里的样子，而不是两个方块平铺着并排。
     */
    private static void drawBlocks(GuiGraphics guiGraphics, BuddingInfo recipe, float pivotY) {
        BlockState cluster = recipe.cluster();

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        MultiBufferSource.BufferSource buffer = guiGraphics.bufferSource();
        PoseStack pose = guiGraphics.pose();

        compositePose(guiGraphics, pivotY);
        // 光照给满亮，免得页面上出现一块黑方块
        dispatcher.renderSingleBlock(recipe.budding(), pose, buffer,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // 先把母岩画出去：下面给晶簇加的提亮是"flush 时"才生效的颜色调制，
        // 顶点写进批次与批次画出去是两件事，同一次 flush 会把两笔一起画亮。
        guiGraphics.flush();

        if (cluster == null) {
            return;
        }

        // 晶簇的 cross 模型不参与方向性明暗（模型里 shade=false），深色晶体（回响、福鲁伊克斯）
        // 在浅灰底上会显得发闷，所以给它一个略大于 1 的颜色调制。
        // 注意 getShaderColor 返回的是内部数组本身，必须在改动前先把四个分量抄出来。
        float[] current = RenderSystem.getShaderColor();
        float r = current[0];
        float g = current[1];
        float b = current[2];
        float a = current[3];
        RenderSystem.setShaderColor(CLUSTER_BRIGHTNESS, CLUSTER_BRIGHTNESS, CLUSTER_BRIGHTNESS, a);

        compositePose(guiGraphics, pivotY);
        // 原版晶簇模型是"从本格底面往上"的 cross，所以放进正上方那一格就是长在顶面上的样子
        pose.translate(0.0F, 1.0F, 0.0F);
        dispatcher.renderSingleBlock(attachUp(cluster), pose, buffer,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        guiGraphics.flush();

        RenderSystem.setShaderColor(r, g, b, a);
    }

    /**
     * 推入整组方块的等轴测变换（调用方负责 {@code popPose}）。
     * <p>
     * 方块模型以 [0,1]³ 为单位：先平移到左栏中心、按物品栏的 30°/225° 摆正（与 {@code block/block}
     * 的 display.gui 变换一致）、缩放到 {@link #RENDER_SCALE}，再把模型原点挪到整组重心上。
     */
    private static void compositePose(GuiGraphics guiGraphics, float pivotY) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(RENDER_CENTER_X, RENDER_CENTER_Y, Z_LEVEL);
        pose.scale(RENDER_SCALE, -RENDER_SCALE, RENDER_SCALE);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(225.0F));
        pose.translate(-0.5F, -pivotY, -0.5F);
    }

    /**
     * 把晶簇摆成「朝上」的样子。
     * <p>
     * {@code FACING} 的默认值是 {@code NORTH}（它列在 {@code BlockStateProperties.FACING} 的第一个），
     * 照默认状态渲染出来是一片斜躺着的晶簇；朝上才是它长在方块顶面上的姿态。
     */
    private static BlockState attachUp(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.setValue(BlockStateProperties.FACING, Direction.UP)
                : state;
    }

    /** 逐行写正文；每行按 {@link #TEXT_WIDTH} 折行，写到 {@link #MAX_LINES} 就停笔（宁可少一行，也不画出框） */
    private static void drawLines(GuiGraphics guiGraphics, List<Row> rows) {
        Font font = Minecraft.getInstance().font;
        int y = TEXT_TOP;
        int drawn = 0;

        for (Row row : rows) {
            if (row.style() == Style.HEADER && y > TEXT_TOP) {
                y += SECTION_GAP;
            }
            // 小节标题加粗，正文与说明保持常规字重
            Component text = row.style() == Style.HEADER
                    ? row.text().copy().withStyle(ChatFormatting.BOLD)
                    : row.text();
            for (FormattedCharSequence line : font.split(text, TEXT_WIDTH)) {
                if (drawn++ >= MAX_LINES) {
                    return;
                }
                // 不画阴影：浅灰底上带阴影的字反而发糊，纯深色更清楚
                guiGraphics.drawString(font, line, TEXT_X, y, color(row.style()), false);
                y += LINE_HEIGHT;
            }
        }
    }

    private static int color(Style style) {
        return switch (style) {
            case HEADER -> HEADER_TEXT;
            case LINE -> LINE_TEXT;
            case NOTE -> NOTE_TEXT;
        };
    }
}
