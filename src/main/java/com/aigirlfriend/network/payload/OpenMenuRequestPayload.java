package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks to open the interaction wheel (entityId -1 = the companion you are looking at / nearest). */
public record OpenMenuRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<OpenMenuRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "open_menu_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenMenuRequestPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenMenuRequestPayload::entityId, OpenMenuRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
