package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Accept or decline her current request. */
public record RequestResponsePayload(int entityId, boolean accept) implements CustomPacketPayload {
    public static final Type<RequestResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "request_response"));
    public static final StreamCodec<FriendlyByteBuf, RequestResponsePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, RequestResponsePayload::entityId, ByteBufCodecs.BOOL, RequestResponsePayload::accept, RequestResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
