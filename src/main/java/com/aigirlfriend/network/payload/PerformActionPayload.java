package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A wheel option was chosen. Re-validated on the server. */
public record PerformActionPayload(int entityId, int action) implements CustomPacketPayload {
    public static final Type<PerformActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "perform_action"));
    public static final StreamCodec<FriendlyByteBuf, PerformActionPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, PerformActionPayload::entityId, ByteBufCodecs.VAR_INT, PerformActionPayload::action, PerformActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
