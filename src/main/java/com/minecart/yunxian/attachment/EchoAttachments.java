package com.minecart.yunxian.attachment;

import com.minecart.yunxian.Yunxian;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class EchoAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Yunxian.MODID);

    /** 服务端记录的"该玩家当前是否第一人称"；默认 true */
    public static final Supplier<AttachmentType<Boolean>> FIRST_PERSON =
            ATTACHMENT_TYPES.register("camera_first_person",
                    () -> AttachmentType.builder(() -> Boolean.TRUE)
                            .serialize(Codec.BOOL)
                            .build());

    /** ★ 服务端权威的"该玩家夜视模式开关"；StreamCodec 同步给所有客户端 */
    public static final Supplier<AttachmentType<Boolean>> NIGHT_VISION =
            ATTACHMENT_TYPES.register("night_vision",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL)
                            .sync(ByteBufCodecs.BOOL)
                            .build());

    /**
     * 上一次真正发给客户端的扫描结果（只对服务端有意义，不落盘也不同步）。
     * <p>
     * 望远镜本来就是「举着站着看」的用法，连续几次扫描的结果往往逐字节相同；
     * 靠它判断「客户端手上那份是不是已经等于这次算出来的」，一样就整个跳过发包，
     * 省掉的是发包序列化 + 客户端重做一遍轮廓合并这条链路。
     * <p>
     * 维度要一起存：客户端缓存是按维度比对的，换了维度即使方块集合碰巧一样也必须重发。
     */
    public record RevealCache(ResourceKey<Level> dimension, Set<BlockPos> positions) {

        public static final RevealCache EMPTY = new RevealCache(Level.OVERWORLD, Set.of());

        /** 客户端此刻是否已经持有这份结果 */
        public boolean matches(ResourceKey<Level> dim, List<BlockPos> found) {
            return dimension.equals(dim) && positions.size() == found.size() && positions.containsAll(found);
        }
    }

    public static final Supplier<AttachmentType<RevealCache>> REVEAL_CACHE =
            ATTACHMENT_TYPES.register("reveal_cache",
                    () -> AttachmentType.builder(() -> RevealCache.EMPTY).build());

    private EchoAttachments() {
    }
}