package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public final class EchoSpyglassScopeOverlay {

    private static final ResourceLocation SCOPE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/misc/echo_spyglass_scope.png");

    /** 镜片从 1/2 长到全尺寸的用时(tick),20 = 1 秒 */
    private static final int GROW_TICKS = 4;

    /**
     * 绘制用的 z。原版 {@code Gui.renderSpyglassOverlay} 就用 -90，别改成 0。
     * <p>
     * 注意方向：z 越负越<b>远</b>，HUD 元素默认的 z = 0 比它更近，所以镜筒是垫在聊天栏 /
     * 快捷栏<b>下面</b>的（原版把南瓜模糊、粉雪覆盖层也放在这一层、用同一个 z，同样是让 HUD 盖在它们之上）。
     * 而 {@code RenderType.guiOverlay()} 的深度测试是 {@code NO_DEPTH_TEST}——它在 1.21.1 里是
     * <b>空操作 shard</b>，不会去动深度状态——所以光靠"谁后画"定不下来层级，必须给一个固定的 z。
     * <p>
     * 同一图层里：Pre 是夜视仪贴图（它也取这个 z），Post 是我们自己；同一深度下后画者在上，
     * 于是镜筒稳定压在夜视贴图之上。
     */
    private static final int Z = -90;

    private EchoSpyglassScopeOverlay() {
    }

    /** 客户端初始化时调用一次(放在 ModRenderers.register 里) */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoSpyglassScopeOverlay::onRenderGuiLayer);
    }

    /**
     * 只在原版画望远镜镜筒的那一层（{@code camera_overlays}）上画。
     * <p>
     * 这么挂是为了拿到**固定的位置**：这一层是 HUD 序列里的第一个，原版自己的
     * {@code renderSpyglassOverlay} 就在这里；挂在这里，镜筒贴图与聊天栏、快捷栏等元素的
     * 先后关系就和原版望远镜完全一致。
     * <p>
     * 原先挂在 {@code RenderGuiEvent.Post} 上，位置相对原生图层没有定义，而聊天栏的背景 / 文字 /
     * 我们的覆盖层各属不同的 RenderType、真正的绘制发生在按类型分批冲刷时——谁压谁是不确定的，
     * 玩家看到的就是镜筒贴图和聊天栏互相穿插。
     */
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            return;
        }
        if (!CameraSync.isFirstPerson()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (!player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof EchoSpyglassItem)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // ===== 平滑动画进度 =====
        // 关键:getTicksUsingItem() 是整数,只按 20Hz 跳变;
        // 加上帧间插值 partialTicks 后,progress 在 60/120 帧下每帧连续变化,消除跳帧。
        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float ticksUsing = player.getTicksUsingItem() + partialTicks;
        float progress = Mth.clamp(ticksUsing / (float) GROW_TICKS, 0.0F, 1.0F);

        // ease-out 缓动:先快后慢(起始速度快,接近全尺寸时减速)
        float remaining = 1.0F - progress;
        float ease = 1.0F - remaining * remaining * remaining;

        // 从 1/2 放大到 1.0
        float START = 0.3F;                          // 起始大小:0.3 = 屏幕短边的 30%
        float scale = START + (1.0F - START) * ease; // 从 START 平滑放大到 1.0

        int size = Math.max(0, Mth.floor(Math.min(width, height) * scale));
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        int right = x + size;
        int bottom = y + size;

        // 照抄原版 renderSpyglassOverlay 的写法:blend 只在这个 blit 前后开合,
        // 不留着开启状态给后面的 HUD 元素(原来一直开着没关)。
        RenderSystem.enableBlend();
        if (size > 0) {
            graphics.blit(SCOPE_TEXTURE, x, y, Z, 0.0F, 0.0F, size, size, size, size);
        }
        RenderSystem.disableBlend();

        // 镜片外涂黑:黑框跟着一起从小变大
        graphics.fill(RenderType.guiOverlay(), 0, bottom, width, height, Z, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), 0, 0, width, y, Z, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), 0, 0, x, height, Z, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), right, 0, width, height, Z, 0xFF000000);
    }
}
