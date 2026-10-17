package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Change her outfit / appearance variant. */
public record OutfitPayload(int entityId, int outfit) implements CustomPacketPayload {
    public static final Type<OutfitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "outfit"));
    public static final StreamCodec<FriendlyByteBuf, OutfitPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, OutfitPayload::entityId, ByteBufCodecs.VAR_INT, OutfitPayload::outfit, OutfitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
