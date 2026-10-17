package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks for a fresh status snapshot. */
public record StatusRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<StatusRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "status_request"));
    public static final StreamCodec<FriendlyByteBuf, StatusRequestPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, StatusRequestPayload::entityId, StatusRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
