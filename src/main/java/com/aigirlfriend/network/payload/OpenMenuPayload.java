package com.aigirlfriend.network.payload;

import com.aigirlfriend.AIGirlfriend;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.aigirlfriend.network.StatusSnapshot;

/** Opens the interaction wheel with the options that are valid right now. */
public record OpenMenuPayload(int entityId, int[] actions, StatusSnapshot status) implements CustomPacketPayload {
    public static final Type<OpenMenuPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "open_menu"));
    public static final StreamCodec<FriendlyByteBuf, OpenMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVarIntArray(p.actions);
                StatusSnapshot.STREAM_CODEC.encode(buf, p.status);
            },
            buf -> new OpenMenuPayload(buf.readVarInt(), buf.readVarIntArray(64), StatusSnapshot.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
