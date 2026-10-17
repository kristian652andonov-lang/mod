package com.aigirlfriend.network;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueService;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.ActionType;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.interaction.InteractionMenu;
import com.aigirlfriend.network.payload.*;
import com.aigirlfriend.seat.SeatManager;
import com.aigirlfriend.companion.RequestSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side handling of client requests. Nothing a client sends is trusted without re-checking. */
public final class ServerPayloadHandler {
    private static final Map<UUID, Long> LAST_TALK = new HashMap<>();

    private ServerPayloadHandler() {}

    public static void clear() {
        LAST_TALK.clear();
    }

    @Nullable
    private static GirlfriendEntity owned(ServerPlayer player, int entityId, double maxDist) {
        Entity e = player.level().getEntity(entityId);
        if (e instanceof GirlfriendEntity gf && gf.isAlive() && gf.isOwnedBy(player) && gf.distanceTo(player) <= maxDist) return gf;
        return null;
    }

    @Nullable
    public static GirlfriendEntity findNearestOwned(ServerPlayer player, double radius) {
        GirlfriendEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 look = player.getLookAngle();
        for (GirlfriendEntity gf : player.level().getEntitiesOfClass(GirlfriendEntity.class, player.getBoundingBox().inflate(radius), g -> g.isOwnedBy(player))) {
            Vec3 to = gf.getEyePosition().subtract(player.getEyePosition());
            double dist = to.length();
            double angle = 1 - look.dot(to.normalize());
            double score = dist * (0.3 + angle * 3);
            if (score < bestScore) {
                bestScore = score;
                best = gf;
            }
        }
        return best;
    }

    public static void openMenu(OpenMenuRequestPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        GirlfriendEntity gf = p.entityId() >= 0 ? owned(player, p.entityId(), 12) : findNearestOwned(player, 10);
        if (gf == null) {
            player.displayClientMessage(Component.translatable("aigirlfriend.fail.none_nearby"), true);
            return;
        }
        InteractionMenu.open(gf, player);
    }

    public static void performAction(PerformActionPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        GirlfriendEntity gf = owned(player, p.entityId(), 16);
        ActionType action = ActionType.byId(p.action());
        if (gf == null || action == null) return;
        InteractionMenu.perform(gf, player, action);
    }

    public static void talk(TalkPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        GirlfriendEntity gf = owned(player, p.entityId(), GFConfig.CHAT_LISTEN_RADIUS.get());
        if (gf == null) return;
        long now = player.level().getGameTime();
        Long last = LAST_TALK.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < 15) return;
        LAST_TALK.put(player.getUUID(), now);
        String msg = sanitize(p.message());
        if (msg.isEmpty()) return;
        if (GFConfig.SPEECH_IN_CHAT.get()) {
            // the talk screen is private, so echo the player's line to them: their chat log shows both sides
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("<" + player.getGameProfile().getName() + "> " + msg)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        DialogueService.playerSays(gf, player, msg);
    }

    public static String sanitize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= 32 && c != 127 && c != '§') sb.append(c);
        }
        String s = sb.toString().trim();
        return s.length() > 256 ? s.substring(0, 256) : s;
    }

    public static void statusRequest(StatusRequestPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        GirlfriendEntity gf = owned(player, p.entityId(), 64);
        if (gf == null) return;
        com.aigirlfriend.network.Net.send(player, new StatusPayload(gf.getId(), StatusSnapshot.of(gf, player, true)));
    }

    public static void requestResponse(RequestResponsePayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        GirlfriendEntity gf = owned(player, p.entityId(), 64);
        if (gf == null) return;
        RequestSystem.respond(gf, player, p.accept());
        com.aigirlfriend.network.Net.send(player, new StatusPayload(gf.getId(), StatusSnapshot.of(gf, player, true)));
    }

    public static void sit(SitPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (player.isSleeping() || player.isFallFlying() || player.isSpectator()) return;
        if (SeatManager.isSeated(player)) {
            SeatManager.standUp(player);
            return;
        }
        if (player.isPassenger()) return;
        if (!p.hasPos()) {
            if (player.onGround() && SeatManager.sitHere(player) == null) player.displayClientMessage(Component.translatable("aigirlfriend.fail.no_space"), true);
            return;
        }
        BlockPos pos = p.pos();
        if (!GFConfig.SIT_ON_ANY_BLOCK.get() && !SeatManager.isChairLike(player.level().getBlockState(pos))) return;
        Vec3 eye = player.getEyePosition();
        if (eye.distanceTo(Vec3.atCenterOf(pos)) > 5.0) return;
        BlockHitResult hit = player.level().clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos) && hit.getBlockPos().distSqr(pos) > 1) return;
        if (SeatManager.sit(player, pos, player.getYRot()) == null) {
            player.displayClientMessage(Component.translatable("aigirlfriend.fail.no_space"), true);
        }
    }

    public static void cancel(CancelInteractionPayload p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) InteractionManager.releasePlayer(player, InteractionManager.StopReason.CANCELLED_PLAYER);
    }

    public static void outfit(OutfitPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        GirlfriendEntity gf = owned(player, p.entityId(), 16);
        if (gf == null || p.outfit() < 0 || p.outfit() >= GFConfig.OUTFIT_COUNT.get()) return;
        gf.setOutfit(p.outfit());
    }
}
