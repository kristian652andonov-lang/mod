package com.aigirlfriend.world;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.companion.Teleport;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Moving a companion to her owner: same level, across dimensions, or out of unloaded chunks (recall). */
public final class CompanionTransfer {
    public static final TicketType<ChunkPos> RECALL_TICKET = TicketType.create(AIGirlfriend.MOD_ID + ":recall", Comparator.comparingLong(ChunkPos::toLong), 300);

    private record PendingRecall(UUID companionId, long deadline) {}

    private static final Map<UUID, PendingRecall> PENDING = new HashMap<>();

    private CompanionTransfer() {}

    /** Brings her next to the player, changing dimension if needed. Returns the (possibly new) entity. */
    @Nullable
    public static GirlfriendEntity bringTo(GirlfriendEntity gf, ServerPlayer player) {
        gf.interactions().stop(InteractionManager.StopReason.INVALID);
        SeatManager.standUp(gf);
        if (gf.isSleeping()) gf.stopSleeping();
        if (gf.level() == player.level()) {
            if (!Teleport.nearPlayer(gf, player)) {
                Vec3 p = player.position();
                gf.moveTo(p.x, p.y, p.z, player.getYRot(), 0f);
            }
            return gf;
        }
        ServerLevel target = player.serverLevel();
        Vec3 pos = player.position();
        Entity moved = gf.changeDimension(new DimensionTransition(target, pos, Vec3.ZERO, player.getYRot(), 0f, DimensionTransition.DO_NOTHING));
        if (moved instanceof GirlfriendEntity ng) {
            Teleport.nearPlayer(ng, player);
            return ng;
        }
        return null;
    }

    /** Recall command: works even if her chunk is unloaded, by briefly loading it with a timed ticket. */
    public static void recall(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CompanionRegistry reg = CompanionRegistry.get(server);
        CompanionRegistry.Entry best = null;
        for (CompanionRegistry.Entry e : reg.byOwner(player.getUUID())) {
            if (e.status == CompanionRegistry.Status.ACTIVE && (best == null || e.updatedTick > best.updatedTick)) best = e;
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("aigirlfriend.message.none_owned"), false);
            return;
        }
        GirlfriendEntity loaded = ActiveCompanions.get(best.companionId);
        if (loaded != null) {
            if (bringTo(loaded, player) != null) player.displayClientMessage(Component.translatable("aigirlfriend.message.recalled", best.name), true);
            return;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(best.dimension));
        ServerLevel level = server.getLevel(dim);
        if (level == null) {
            player.displayClientMessage(Component.translatable("aigirlfriend.message.lost", best.name), false);
            return;
        }
        ChunkPos cp = new ChunkPos(best.lastPos);
        level.getChunkSource().addRegionTicket(RECALL_TICKET, cp, 2, cp);
        level.getChunk(cp.x, cp.z);
        PENDING.put(player.getUUID(), new PendingRecall(best.companionId, server.getTickCount() + 200));
        player.displayClientMessage(Component.translatable("aigirlfriend.message.recalling", best.name), true);
    }

    /** Called every server tick: finishes recalls once her chunk's entities have loaded. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, PendingRecall>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingRecall> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            GirlfriendEntity gf = ActiveCompanions.get(e.getValue().companionId());
            if (gf != null) {
                it.remove();
                GirlfriendEntity moved = bringTo(gf, player);
                if (moved != null) player.displayClientMessage(Component.translatable("aigirlfriend.message.recalled", moved.data().name), true);
            } else if (server.getTickCount() > e.getValue().deadline()) {
                it.remove();
                CompanionRegistry reg = CompanionRegistry.get(server);
                CompanionRegistry.Entry entry = reg.get(e.getValue().companionId());
                if (entry != null) reg.markStatus(entry.companionId, CompanionRegistry.Status.LOST);
                player.displayClientMessage(Component.translatable("aigirlfriend.message.lost", entry == null ? "?" : entry.name), false);
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }

}
