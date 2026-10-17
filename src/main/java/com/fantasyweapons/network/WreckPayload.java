package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → players who see the chunk: the sections the world's destruction just rewrote in it, whole ({@code sectionYs}
 * their section y, {@code sections} their blocks packed as a chunk packet packs them). The client puts them straight
 * into its copy of the chunk with the sky's light let in where the land opened, all at once, instead of taking the
 * change a block at a time and working the light out itself; the true light follows once the server has lit the chunk.
 */
public record WreckPayload(int chunkX, int chunkZ, int[] sectionYs, byte[][] sections) implements CustomPacketPayload {
    public static final Type<WreckPayload> TYPE = new Type<>(FantasyWeapons.id("wreck"));
    /** Bytes of sections per packet at most, so a packet stays well under the size limit. */
    public static final int MAX_BYTES = 600_000;

    public static final StreamCodec<ByteBuf, WreckPayload> CODEC = StreamCodec.of(WreckPayload::write, WreckPayload::read);

    private static void write(ByteBuf buf, WreckPayload p) {
        FriendlyByteBuf out = new FriendlyByteBuf(buf);
        out.writeVarInt(p.chunkX);
        out.writeVarInt(p.chunkZ);
        out.writeVarInt(p.sectionYs.length);
        for (int i = 0; i < p.sectionYs.length; i++) {
            out.writeVarInt(p.sectionYs[i]);
            out.writeByteArray(p.sections[i]);
        }
    }

    private static WreckPayload read(ByteBuf buf) {
        FriendlyByteBuf in = new FriendlyByteBuf(buf);
        int x = in.readVarInt(), z = in.readVarInt(), n = Math.min(64, in.readVarInt());
        int[] ys = new int[n];
        byte[][] data = new byte[n][];
        for (int i = 0; i < n; i++) {
            ys[i] = in.readVarInt();
            data[i] = in.readByteArray(MAX_BYTES);
        }
        return new WreckPayload(x, z, ys, data);
    }

    @Override
    public Type<WreckPayload> type() {
        return TYPE;
    }
}
