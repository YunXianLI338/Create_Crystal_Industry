package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.echo.EchoHighlightClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.core.registries.Registries;   // 新增 import

import java.util.ArrayList;
import java.util.List;

public record EchoRevealPayload(ResourceKey<Level> dimension, List<BlockPos> ores)
        implements CustomPacketPayload {

    public static final Type<EchoRevealPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_reveal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRevealPayload> STREAM_CODEC =
            StreamCodec.of(EchoRevealPayload::encode, EchoRevealPayload::decode);

    public static EchoRevealPayload clear(Level level) {
        return new EchoRevealPayload(level.dimension(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buf, EchoRevealPayload payload) {
        buf.writeResourceKey(payload.dimension);
        buf.writeVarInt(payload.ores.size());
        for (BlockPos pos : payload.ores) {
            buf.writeBlockPos(pos);
        }
    }

    private static EchoRevealPayload decode(RegistryFriendlyByteBuf buf) {
        // 1.21.1 必须显式给出注册表键；无参重载是 1.21.2+ 的 API
        ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);

        int count = buf.readVarInt();
        List<BlockPos> ores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ores.add(buf.readBlockPos());
        }
        return new EchoRevealPayload(dimension, ores);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 客户端收包，切回主线程更新缓存
    public static void handle(EchoRevealPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EchoHighlightClient.replace(payload.dimension, payload.ores));
    }
}