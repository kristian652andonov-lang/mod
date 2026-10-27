package com.fantasyweapons.world.gen;

import com.fantasyweapons.FantasyWeapons;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Whether the overworld is taller or deeper than vanilla's, so the legendary ores there lie in bands fitted to its terrain
 * (see {@link FittedHeightPlacement}) and their descriptions give depths under the ground instead of heights. The server
 * works it out and tells each player as they join.
 */
public final class WorldShape {
    private static volatile boolean fittedOres;

    private WorldShape() {
    }

    /** Whether the ores of the world joined are fitted to its terrain (as the server told it, on a client). */
    public static boolean fittedOres() {
        return fittedOres;
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, new Payload(fitted(player.server)));
    }

    /** Whether the server's overworld generates its ores in fitted bands. */
    public static boolean fitted(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return !FittedHeightPlacement.vanillaHeight(overworld.getMinBuildHeight(), overworld.getHeight());
    }

    /** The world's shape, sent to a player as they join (server to client). */
    public record Payload(boolean fittedOres) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(FantasyWeapons.id("world_shape"));
        public static final StreamCodec<ByteBuf, Payload> CODEC = ByteBufCodecs.BOOL.map(Payload::new, Payload::fittedOres);

        public void handle() {
            WorldShape.fittedOres = fittedOres;
        }

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }
}
