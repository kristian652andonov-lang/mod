package com.aigirlfriend.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Safe sending: only to connections that negotiated this mod's channel (never crashes on odd clients). */
public final class Net {
    private Net() {}

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendNear(ServerLevel level, double x, double y, double z, double radius, CustomPacketPayload payload) {
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= r2) send(p, payload);
        }
    }
}
