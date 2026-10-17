package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Something she says, shown as a speech bubble (typing = "..." indicator while the AI thinks). */
public record SpeechPayload(int entityId, String text, int emotion, boolean typing, int duration) implements CustomPacketPayload {
    public static final Type<SpeechPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "speech"));
    public static final StreamCodec<FriendlyByteBuf, SpeechPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SpeechPayload::entityId, ByteBufCodecs.stringUtf8(1024), SpeechPayload::text, ByteBufCodecs.VAR_INT, SpeechPayload::emotion, ByteBufCodecs.BOOL, SpeechPayload::typing, ByteBufCodecs.VAR_INT, SpeechPayload::duration, SpeechPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
