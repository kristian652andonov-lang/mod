package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → players who see the chunk: the blocks the world's destruction just changed in it, as runs down its columns
 * ({@code column (x | z << 4), first y, length, block state id} four at a time). The client writes them straight into
 * its copy of the chunk with the sky light let in, instead of taking them one block update at a time and working the
 * light out itself, which for a hole hundreds of blocks deep would stall it; the true light follows once the server has
 * lit the chunk.
 */
public record WreckPayload(int chunkX, int chunkZ, int[] runs) implements CustomPacketPayload {
    public static final Type<WreckPayload> TYPE = new Type<>(FantasyWeapons.id("wreck"));
    /** Runs per packet at most (each a few bytes), so a packet stays well under the size limit. */
    public static final int MAX_RUNS = 24_000;

    public static final StreamCodec<ByteBuf, WreckPayload> CODEC = StreamCodec.of(WreckPayload::write, WreckPayload::read);

    private static void write(ByteBuf buf, WreckPayload p) {
        FriendlyByteBuf out = new FriendlyByteBuf(buf);
        out.writeVarInt(p.chunkX);
        out.writeVarInt(p.chunkZ);
        out.writeVarIntArray(p.runs);
    }

    private static WreckPayload read(ByteBuf buf) {
        FriendlyByteBuf in = new FriendlyByteBuf(buf);
        int x = in.readVarInt(), z = in.readVarInt();
        return new WreckPayload(x, z, in.readVarIntArray(MAX_RUNS * 4));
    }

    @Override
    public Type<WreckPayload> type() {
        return TYPE;
    }
}
