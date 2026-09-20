package com.minecart.yunxian.network;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.attachment.EchoAttachments;
import com.minecart.yunxian.util.NightVisionWearHelper;
import com.minecart.yunxian.Yunxian;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NightVisionTogglePayload() implements CustomPacketPayload {

    public static final Type<NightVisionTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "night_vision_toggle"));

    public static final StreamCodec<FriendlyByteBuf, NightVisionTogglePayload> STREAM_CODEC =
            StreamCodec.unit(new NightVisionTogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NightVisionTogglePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // 服务端校验：头盔槽 或 首饰栏 都算穿戴（防作弊 + 支持首饰栏）
            if (!NightVisionWearHelper.isWearingGoggles(player)) {
                return;
            }
            boolean enabled = !player.getData(EchoAttachments.NIGHT_VISION);
            player.setData(EchoAttachments.NIGHT_VISION, enabled);
            if (enabled) {
                YunxianAdvancements.award(player, YunxianAdvancements.GEAR_NIGHT_VISION);
            }
        });
    }
}