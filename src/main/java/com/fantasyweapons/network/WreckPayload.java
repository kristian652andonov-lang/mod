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
    /**
     * Bytes of sections per packet at most: well under the 1 MiB a custom packet to a player may hold (even through
     * servers and proxies that do not split bigger ones), and small enough for one not to hold back the others.
     */
    public static final int MAX_BYTES = 256 * 1024;
    /** The most sections one packet may say it holds (a chunk of the tallest world the game allows has 254). */
    private static final int MOST_SECTIONS = 256;
    /** The most bytes one section may take (a section of 4096 different blocks takes about 8 KB). */
    private static final int MOST_SECTION_BYTES = 1 << 20;

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
        int x = in.readVarInt(), z = in.readVarInt(), n = in.readVarInt();
        // all of what was written is read back, whatever its size: a packet read short leaves bytes behind, and the
        // game drops the connection over them
        if (n < 0 || n > MOST_SECTIONS) throw new io.netty.handler.codec.DecoderException("A destruction packet with " + n + " sections");
        int[] ys = new int[n];
        byte[][] data = new byte[n][];
        for (int i = 0; i < n; i++) {
            ys[i] = in.readVarInt();
            data[i] = in.readByteArray(MOST_SECTION_BYTES);
        }
        return new WreckPayload(x, z, ys, data);
    }

    @Override
    public Type<WreckPayload> type() {
        return TYPE;
    }
}
