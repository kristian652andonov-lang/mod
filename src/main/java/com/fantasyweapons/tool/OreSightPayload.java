package com.fantasyweapons.tool;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the player who used a pickaxe's Ore Sight: where it was used ({@code center}, a packed block position), every
 * legendary ore it found ({@code positions}, packed, with each one's {@link com.fantasyweapons.material.Material} ordinal
 * in {@code materials}) and how long they show ({@code ticks}).
 */
public record OreSightPayload(long center, long[] positions, byte[] materials, int ticks) implements CustomPacketPayload {
    public static final Type<OreSightPayload> TYPE = new Type<>(FantasyWeapons.id("ore_sight"));
    /** The most ores sent (far more than a 32-block sphere holds). */
    public static final int MOST = 8192;

    public static final StreamCodec<ByteBuf, OreSightPayload> CODEC = StreamCodec.of(OreSightPayload::write, OreSightPayload::read);

    private static void write(ByteBuf buf, OreSightPayload p) {
        FriendlyByteBuf out = new FriendlyByteBuf(buf);
        out.writeLong(p.center);
        int n = Math.min(MOST, p.positions.length);
        out.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            out.writeLong(p.positions[i]);
            out.writeByte(p.materials[i]);
        }
        out.writeVarInt(p.ticks);
    }

    private static OreSightPayload read(ByteBuf buf) {
        FriendlyByteBuf in = new FriendlyByteBuf(buf);
        long center = in.readLong();
        int n = Math.min(MOST, in.readVarInt());
        long[] positions = new long[n];
        byte[] materials = new byte[n];
        for (int i = 0; i < n; i++) {
            positions[i] = in.readLong();
            materials[i] = in.readByte();
        }
        return new OreSightPayload(center, positions, materials, in.readVarInt());
    }

    @Override
    public Type<OreSightPayload> type() {
        return TYPE;
    }
}
