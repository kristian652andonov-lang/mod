package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sit on the targeted block (hasPos) or toggle sitting where you stand. */
public record SitPayload(boolean hasPos, net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
    public static final Type<SitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "sit"));
    public static final StreamCodec<FriendlyByteBuf, SitPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, SitPayload::hasPos, net.minecraft.core.BlockPos.STREAM_CODEC, SitPayload::pos, SitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
