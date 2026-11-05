package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.resonance.ResonanceRulesCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 → 客户端：某个共振网络此刻在过滤什么。
 * <p>
 * 只在客户端本地读不到网络时才用得上（远距离、共振台所在区块没加载，见
 * {@link ResonanceRulesCache}）。发送时机见 {@code ResonanceRulesSync} ——
 * 玩家打开仓储界面时立刻发一遍，界面开着时每 20 tick 补刷，总在第一次库存分组之前抵达。
 * <p>
 * 传的是台面上的<b>原始物品栈</b>而不是展开后的过滤规则：客户端要照常走
 * {@code FilterItemStack.of(...)}，台面上放的是列表过滤器 / 属性过滤器 / 别的网络的
 * 共振过滤器时才有正确的语义。台子数单独传，用来区分「一台都够不着」与「台面全空」。
 */
public record ResonanceRulesPayload(UUID network, List<ItemStack> rules, int tableCount)
        implements CustomPacketPayload {

    /** 一个网络推多少条上限：台面再多也不至于全塞进一个包，超出的部分客户端按"读不到"处理。 */
    public static final int MAX_RULES = 32;

    public static final Type<ResonanceRulesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "resonance_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResonanceRulesPayload> STREAM_CODEC =
            StreamCodec.of(ResonanceRulesPayload::encode, ResonanceRulesPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ResonanceRulesPayload payload) {
        buf.writeUUID(payload.network);
        buf.writeVarInt(payload.tableCount);
        int size = Math.min(payload.rules.size(), MAX_RULES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            // OPTIONAL：空栈也要能过去（台面被锁/刚被拿走那种瞬时状态）
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.rules.get(i));
        }
    }

    private static ResonanceRulesPayload decode(RegistryFriendlyByteBuf buf) {
        UUID network = buf.readUUID();
        int tableCount = buf.readVarInt();
        int size = buf.readVarInt();
        List<ItemStack> rules = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rules.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new ResonanceRulesPayload(network, rules, tableCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResonanceRulesPayload payload, IPayloadContext context) {
        // 切回主线程再写缓存：客户端那边的读发生在界面/判定的主线程上
        context.enqueueWork(() ->
                ResonanceRulesCache.put(payload.network(), payload.rules(), payload.tableCount()));
    }
}
