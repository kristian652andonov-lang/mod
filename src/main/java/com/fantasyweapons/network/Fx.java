package com.fantasyweapons.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side helpers to dispatch {@link FxPayload}s. Effects are purely visual, so they go only to players close
 * enough to see them.
 */
public final class Fx {
    public static final double VIEW_RADIUS = 96;

    private Fx() {
    }

    /** Everyone near the payload position. */
    public static void near(ServerLevel level, FxPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, payload.pos().x, payload.pos().y, payload.pos().z, VIEW_RADIUS, payload);
    }

    /** Everyone tracking the entity, plus the entity itself if it is a player. */
    public static void tracking(Entity entity, FxPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void only(ServerPlayer player, FxPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
