package com.minecart.yunxian.client.echo;

import com.minecart.yunxian.item.EchoSpyglassItem;
import com.minecart.yunxian.client.ModRenderTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EchoHighlightRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 线宽（屏幕像素），所有平台生效 */
    private static final double EDGE_WIDTH_PIXELS = 5.0;

    /** 垂直 FOV 默认 70° 的换算常数 */
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(35.0));

    /** 自检开关：true = 轮廓画成纯红色并打印合并日志；确认后改回 false */
    private static final boolean DEBUG_MERGE = false;

    // ===== 轮廓合并缓存 =====
    private static int cachedVersion = -1;
    private static Set<EdgeKey> cachedEdges = Set.of();

    /**
     * 画线框专用的缓冲源，和游戏主缓冲源分开。
     * <p>
     * <b>绝对不要改用 {@code mc.renderBuffers().bufferSource()}。</b> Iris 会注入
     * {@code RenderBuffers.bufferSource()} 的入口，把它换成自己的 {@code FullyBufferedMultiBufferSource}：
     * 那个实现只把顶点按 RenderType 攒成 BufferSegment，等它自己挑时机再统一重放。
     * 而我们的线框"能透过方块看见"完全靠冲刷那一刻全局深度测试是关着的
     * （{@code NO_DEPTH_TEST} 这个 shard 在原版里是空操作，真正起作用的是我们自己那句
     * {@code disableDepthTest()}），顶点一旦被挪到别处重放，就会落进别人的深度状态——
     * 表现就是线框被方块挡住、和方块面 z-fighting 闪个不停，也就是玩家报的那个现象。
     * <p>
     * 自己 new 一个不经过 RenderBuffers 的缓冲源，Iris 就接管不到：{@code endBatch} 会当场
     * {@code setupRenderState()} → 立即绘制 → {@code clearRenderState()}，状态窗口不被破坏。
     * <p>
     * 容量照搬原版关卡缓冲源（768 KiB），不够时 ByteBufferBuilder 会自己扩容；
     * 这块原生内存随缓冲源长期持有，和为它多开的一块缓冲区，量很小。
     */
    private static final ByteBufferBuilder ECHO_BUFFER = new ByteBufferBuilder(786432);
    private static final MultiBufferSource.BufferSource ECHO_BUFFERS = MultiBufferSource.immediate(ECHO_BUFFER);

    private EchoHighlightRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoHighlightRenderer::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!CameraSync.isFirstPerson()) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
                || !player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof EchoSpyglassItem)) {
            return;
        }

        List<BlockPos> ores = EchoHighlightClient.positionsFor(mc.level.dimension());
        if (ores.isEmpty()) {
            return;
        }

        Set<EdgeKey> edges = mergedEdges(ores);
        if (edges.isEmpty()) {
            return;
        }

        float time = mc.level.getGameTime()
                + mc.getTimer().getGameTimeDeltaPartialTick(false);
        float alpha = 0.775F + 0.225F * Mth.sin(time * (float) Math.PI / 10.0F);

        int viewportHeight = Math.max(1, mc.getWindow().getHeight());
        double pixelToWorldPerDistance = 2.0 * TAN_HALF_FOV / (double) viewportHeight;

        Vec3 camPos = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // ColorModulator 不在 RenderType 的控制范围内，谁都能改：position_color.fsh 的最终颜色是
        // 顶点色 * ColorModulator，所以别的模组（Goety / Goety Twilight 的法术特效就会）调
        // RenderSystem.setShaderColor 之后不还原，线框的颜色连同 alpha 会被一起乘掉——
        // 表现是线框变淡、看不见、还随对方的动画一帧一帧地闪。这里自己钉成白色，
        // 不去赌"前面那段渲染会替我们还原"。注意 getShaderColor() 返回的是内部数组本身，
        // 必须先把四个分量抄出来，不能存引用（照 BuddingInfoCategory 的写法）。
        float[] incomingShaderColor = RenderSystem.getShaderColor();
        float prevR = incomingShaderColor[0];
        float prevG = incomingShaderColor[1];
        float prevB = incomingShaderColor[2];
        float prevA = incomingShaderColor[3];
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            // 显式绑回主帧缓冲。这个阶段画的时候不保证绑的是主缓冲：「极致」画质下粒子是画进
            // particles 中间缓冲的，别的模组也可能在同一个阶段留下一张自己的缓冲。
            // 一旦画进那种缓冲，线框要么被当成最远的一层盖掉，要么根本不会被合成出来。
            mc.getMainRenderTarget().bindWrite(false);

            // 用专用缓冲源，别借 mc.renderBuffers().bufferSource()（Iris 会把它换成延迟批处理的实现，
            // 顶点被挪到别处重放，我们的深度状态窗口就失效了——详见 ECHO_BUFFERS 的说明）
            VertexConsumer consumer = ECHO_BUFFERS.getBuffer(ModRenderTypes.ECHO_ORE_OVERLAY_QUADS);
            PoseStack.Pose pose = poseStack.last();

            for (EdgeKey edge : edges) {
                Vec3 a = edge.a().toVec3();
                Vec3 b = edge.b().toVec3();

                double minX = Math.min(a.x, b.x), minY = Math.min(a.y, b.y), minZ = Math.min(a.z, b.z);
                double maxX = Math.max(a.x, b.x), maxY = Math.max(a.y, b.y), maxZ = Math.max(a.z, b.z);
                if (event.getFrustum() != null
                        && !event.getFrustum().isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.5))) {
                    continue;
                }
                addThickEdge(consumer, pose, a, b, camPos, pixelToWorldPerDistance, alpha);
            }

            ECHO_BUFFERS.endBatch(ModRenderTypes.ECHO_ORE_OVERLAY_QUADS);
        } finally {
            RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        poseStack.popPose();
    }

    // ==================== 纯外轮廓合并 ====================

    private static Set<EdgeKey> mergedEdges(List<BlockPos> positions) {
        if (cachedVersion == EchoHighlightClient.version()) {
            return cachedEdges;
        }
        cachedVersion = EchoHighlightClient.version();

        Set<BlockPos> filled = new HashSet<>(positions);
        // 每条单位棱 → 依附它的所有暴露面的支撑平面
        Map<EdgeKey, Set<Plane>> edgePlanes = new HashMap<>();

        for (BlockPos pos : positions) {
            for (Direction dir : Direction.values()) {
                if (filled.contains(pos.relative(dir))) {
                    continue;   // 内部面
                }
                Plane plane = Plane.of(pos, dir);   // 该暴露面的支撑平面
                for (EdgeKey edge : faceEdges(pos, dir)) {
                    edgePlanes.computeIfAbsent(edge, k -> new HashSet<>()).add(plane);
                }
            }
        }

        // 关键规则：棱上所有暴露面共面 → 平面接缝，隐藏；
        //          存在不同平面（转角/凹凸角）→ 外轮廓，保留。
        Set<EdgeKey> edges = new HashSet<>();
        for (Map.Entry<EdgeKey, Set<Plane>> entry : edgePlanes.entrySet()) {
            if (entry.getValue().size() >= 2) {
                edges.add(entry.getKey());
            }
        }

        if (DEBUG_MERGE) {
            LOGGER.info("[EchoSpyglass] 轮廓合并: {} 个方块 -> {} 条单位棱",
                    positions.size(), edges.size());
        }

        cachedEdges = edges;
        return edges;
    }

    private static final int[][][] FACE_CORNERS = new int[6][4][3];

    static {
        FACE_CORNERS[Direction.DOWN.ordinal()]  = new int[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
        FACE_CORNERS[Direction.UP.ordinal()]    = new int[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        FACE_CORNERS[Direction.NORTH.ordinal()] = new int[][]{{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
        FACE_CORNERS[Direction.SOUTH.ordinal()] = new int[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
        FACE_CORNERS[Direction.WEST.ordinal()]  = new int[][]{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
        FACE_CORNERS[Direction.EAST.ordinal()]  = new int[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
    }

    private static List<EdgeKey> faceEdges(BlockPos pos, Direction dir) {
        int[][] corners = FACE_CORNERS[dir.ordinal()];
        EdgeKey[] result = new EdgeKey[4];
        for (int i = 0; i < 4; i++) {
            int[] c0 = corners[i];
            int[] c1 = corners[(i + 1) % 4];
            result[i] = new EdgeKey(
                    new Corner(pos.getX() + c0[0], pos.getY() + c0[1], pos.getZ() + c0[2]),
                    new Corner(pos.getX() + c1[0], pos.getY() + c1[1], pos.getZ() + c1[2]));
        }
        return List.of(result);
    }

    // ==================== 厚棱四边形绘制 ====================

    private static void addThickEdge(VertexConsumer consumer, PoseStack.Pose pose,
                                     Vec3 a, Vec3 b, Vec3 camPos,
                                     double pixelToWorldPerDistance, float alpha) {
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1e-6) {
            return;
        }
        dir = dir.scale(1.0 / len);

        Vec3 mid = a.add(b).scale(0.5);
        Vec3 toCamera = camPos.subtract(mid);
        double dist = toCamera.length();
        if (dist < 1e-6) {
            return;
        }
        Vec3 viewDir = toCamera.scale(1.0 / dist);

        Vec3 side = dir.cross(viewDir);
        double sideLen = side.length();
        if (sideLen < 1e-6) {
            Vec3 axis = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            side = dir.cross(axis);
            sideLen = side.length();
            side = sideLen < 1e-6 ? new Vec3(1, 0, 0) : side.scale(1.0 / sideLen);
        } else {
            side = side.scale(1.0 / sideLen);
        }

        double worldPerPixel = dist * pixelToWorldPerDistance;
        double halfWidth = (EDGE_WIDTH_PIXELS / 2.0) * worldPerPixel;

        Vec3 off = side.scale(halfWidth);
        Vec3 p0 = a.add(off);
        Vec3 p1 = a.subtract(off);
        Vec3 p2 = b.subtract(off);
        Vec3 p3 = b.add(off);

        if (DEBUG_MERGE) {
            consumer.addVertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).setColor(255, 0, 0, 255);
            consumer.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).setColor(255, 0, 0, 255);
            consumer.addVertex(pose, (float) p2.x, (float) p2.y, (float) p2.z).setColor(255, 0, 0, 255);
            consumer.addVertex(pose, (float) p3.x, (float) p3.y, (float) p3.z).setColor(255, 0, 0, 255);
        } else {
            int colorAlpha = (int) (alpha * 255.0F);
            consumer.addVertex(pose, (float) p0.x, (float) p0.y, (float) p0.z).setColor(0, 220, 200, colorAlpha);
            consumer.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z).setColor(0, 220, 200, colorAlpha);
            consumer.addVertex(pose, (float) p2.x, (float) p2.y, (float) p2.z).setColor(0, 220, 200, colorAlpha);
            consumer.addVertex(pose, (float) p3.x, (float) p3.y, (float) p3.z).setColor(0, 220, 200, colorAlpha);
        }
    }

    // ==================== 键类型 ====================

    /** 方块角点（整数坐标） */
    private record Corner(int x, int y, int z) {
        Vec3 toVec3() {
            return new Vec3(x, y, z);
        }
    }

    /** 一条单位棱：端点规范化，保证共享棱去重 */
    private record EdgeKey(Corner a, Corner b) {
        EdgeKey {
            if (compare(a, b) > 0) {
                Corner t = a;
                a = b;
                b = t;
            }
        }

        private static int compare(Corner p, Corner q) {
            if (p.x != q.x) {
                return Integer.compare(p.x, q.x);
            }
            if (p.y != q.y) {
                return Integer.compare(p.y, q.y);
            }
            return Integer.compare(p.z, q.z);
        }
    }

    /** 轴对齐平面：一个轴 + 该轴上的坐标，用于判断两暴露面是否共面 */
    private record Plane(Direction.Axis axis, int coord) {
        static Plane of(BlockPos pos, Direction dir) {
            Direction.Axis axis = dir.getAxis();
            int base = axis.choose(pos.getX(), pos.getY(), pos.getZ());
            int coord = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? base + 1 : base;
            return new Plane(axis, coord);
        }
    }
}