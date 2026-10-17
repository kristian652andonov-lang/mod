package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A line the player says to her. */
public record TalkPayload(int entityId, String message) implements CustomPacketPayload {
    public static final Type<TalkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "talk"));
    public static final StreamCodec<FriendlyByteBuf, TalkPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TalkPayload::entityId, ByteBufCodecs.stringUtf8(256), TalkPayload::message, TalkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
