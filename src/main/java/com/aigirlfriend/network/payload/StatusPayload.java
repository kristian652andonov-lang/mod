package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.aigirlfriend.network.StatusSnapshot;

/** Status snapshot for the owner's HUD / status screen. */
public record StatusPayload(int entityId, StatusSnapshot status) implements CustomPacketPayload {
    public static final Type<StatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "status"));
    public static final StreamCodec<FriendlyByteBuf, StatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StatusPayload::entityId, StatusSnapshot.STREAM_CODEC, StatusPayload::status, StatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
