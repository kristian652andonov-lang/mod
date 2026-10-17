package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Player wants out of the current interaction. */
public record CancelInteractionPayload(int unused) implements CustomPacketPayload {
    public static final Type<CancelInteractionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "cancel_interaction"));
    public static final StreamCodec<FriendlyByteBuf, CancelInteractionPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, CancelInteractionPayload::unused, CancelInteractionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
