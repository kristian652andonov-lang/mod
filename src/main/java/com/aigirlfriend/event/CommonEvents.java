package com.aigirlfriend.event;

import com.aigirlfriend.ai.SecretStore;
import com.aigirlfriend.command.GirlfriendCommand;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueService;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.interaction.InteractionManager;
import com.aigirlfriend.interaction.InteractionType;
import com.aigirlfriend.network.ServerPayloadHandler;
import com.aigirlfriend.seat.SeatManager;
import com.aigirlfriend.world.ActiveCompanions;
import com.aigirlfriend.world.CompanionRegistry;
import com.aigirlfriend.world.CompanionTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Game-bus wiring between vanilla events and the companion systems. */
public final class CommonEvents {
    private static final Set<UUID> SLEEPING = new HashSet<>();
    private static final Map<UUID, List<UUID>> PORTAL_FOLLOWERS = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHAT = new HashMap<>();

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        SecretStore.ensureTemplate();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ActiveCompanions.clear();
        InteractionManager.clearAll();
        CompanionTransfer.clear();
        SLEEPING.clear();
        PORTAL_FOLLOWERS.clear();
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        GirlfriendCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        CompanionTransfer.tick(event.getServer());
    }

    // ------------------------------------------------------------------ entities

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (e instanceof GirlfriendEntity gf && event.getLevel() instanceof ServerLevel sl) {
            CompanionRegistry reg = CompanionRegistry.get(sl.getServer());
            if (!ActiveCompanions.onJoin(gf, event.loadedFromDisk(), reg)) {
                event.setCanceled(true);
                return;
            }
            if (gf.hasOwner()) reg.update(gf, false);
            return;
        }
        if (e instanceof Mob mob && GFConfig.HOSTILES_TARGET_HER.get() && targetsCompanion(mob)) {
            mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, GirlfriendEntity.class, true, g -> !((GirlfriendEntity) g).isFainted()));
        }
    }

    private static boolean targetsCompanion(Mob mob) {
        if (mob instanceof ZombifiedPiglin) return false;
        return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Spider || mob instanceof Creeper
                || mob instanceof AbstractIllager || mob instanceof Vex || mob instanceof Witch || mob instanceof Blaze
                || mob instanceof Silverfish || mob instanceof Endermite;
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof GirlfriendEntity gf) ActiveCompanions.onLeave(gf);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (dead instanceof Enemy && event.getSource().getEntity() instanceof ServerPlayer killer) {
            for (GirlfriendEntity gf : ActiveCompanions.ownedBy(killer.getUUID())) {
                if (gf.level() == dead.level() && gf.distanceTo(dead) < 24) gf.controller().onOwnerDefeatedThreat(dead);
            }
        }
        if (dead instanceof ServerPlayer player) {
            InteractionManager.releasePlayer(player, InteractionManager.StopReason.PARTNER_GONE);
            for (GirlfriendEntity gf : ActiveCompanions.ownedBy(player.getUUID())) {
                if (gf.level() == player.level() && gf.distanceTo(player) < 24) {
                    gf.feel(com.aigirlfriend.companion.Emotion.FEAR, 1f, 1200);
                    gf.playGesture(com.aigirlfriend.interaction.Gesture.STARTLED);
                }
            }
        }
    }

    // ------------------------------------------------------------------ players

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        InteractionManager.releasePlayer(player, InteractionManager.StopReason.PARTNER_GONE);
        SLEEPING.remove(player.getUUID());
        for (GirlfriendEntity gf : ActiveCompanions.ownedBy(player.getUUID())) {
            if (gf.isSeated() && gf.getCommand() != com.aigirlfriend.companion.CommandMode.SIT) SeatManager.standUp(gf);
            if (gf.level() == player.level() && gf.distanceTo(player) < 16) gf.playGesture(com.aigirlfriend.interaction.Gesture.WAVE_GOODBYE);
            if (gf.level() instanceof ServerLevel sl) CompanionRegistry.get(sl.getServer()).update(gf, true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean sleeping = player.isSleeping();
        if (sleeping && SLEEPING.add(player.getUUID())) onStartSleeping(player);
        else if (!sleeping) SLEEPING.remove(player.getUUID());
    }

    private static void onStartSleeping(ServerPlayer player) {
        BlockPos bed = player.getSleepingPos().orElse(null);
        if (bed == null) return;
        for (GirlfriendEntity gf : ActiveCompanions.ownedBy(player.getUUID())) {
            if (gf.level() != player.level() || gf.distanceTo(player) > 12 || gf.isFainted()) continue;
            if (gf.data().relationship.stage().atLeast(InteractionType.SLEEP_TOGETHER.stage)) {
                gf.controller().waitingBed = null;
                gf.interactions().startSleepTogether(player, bed);
            } else {
                gf.controller().say(com.aigirlfriend.dialogue.DialogueTrigger.GOODNIGHT);
            }
        }
    }

    @SubscribeEvent
    public void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SLEEPING.remove(player.getUUID());
        for (GirlfriendEntity gf : ActiveCompanions.ownedBy(player.getUUID())) {
            if (gf.interactions().current() == InteractionType.SLEEP_TOGETHER && player.getUUID().equals(partnerId(gf))) {
                gf.controller().flushNeeds(true);
                gf.interactions().stop(InteractionManager.StopReason.SLEEP);
                if (gf.isSleeping()) gf.stopSleeping();
                gf.controller().onWokeUp(true);
            }
        }
    }

    private static UUID partnerId(GirlfriendEntity gf) {
        ServerPlayer p = gf.interactions().partner();
        return p == null ? null : p.getUUID();
    }

    // ------------------------------------------------------------------ dimension travel

    @SubscribeEvent
    public void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !GFConfig.FOLLOW_DIMENSIONS.get()) return;
        List<UUID> followers = new ArrayList<>();
        for (GirlfriendEntity gf : ActiveCompanions.ownedBy(player.getUUID())) {
            if (gf.level() != player.level() || gf.isFainted()) continue;
            boolean carried = gf.getVehicle() == player;
            boolean following = gf.getCommand() == com.aigirlfriend.companion.CommandMode.FOLLOW || gf.getCommand() == com.aigirlfriend.companion.CommandMode.STAY_SAFE;
            if (carried || (following && gf.distanceTo(player) < 16)) {
                gf.interactions().stop(InteractionManager.StopReason.INVALID);
                followers.add(gf.data().companionId);
            }
        }
        if (!followers.isEmpty()) PORTAL_FOLLOWERS.put(player.getUUID(), followers);
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<UUID> followers = PORTAL_FOLLOWERS.remove(player.getUUID());
        if (followers == null) return;
        for (UUID id : followers) {
            GirlfriendEntity gf = ActiveCompanions.get(id);
            if (gf != null) CompanionTransfer.bringTo(gf, player);
        }
    }

    // ------------------------------------------------------------------ chat & sitting

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!GFConfig.LISTEN_TO_CHAT.get()) return;
        ServerPlayer player = event.getPlayer();
        String raw = event.getRawText();
        double radius = GFConfig.CHAT_LISTEN_RADIUS.get();
        for (GirlfriendEntity gf : ActiveCompanions.ownedBy(player.getUUID())) {
            if (gf.level() != player.level() || gf.distanceTo(player) > radius) continue;
            String name = gf.data().name;
            String lower = raw.toLowerCase();
            String n = name.toLowerCase();
            boolean addressed = lower.startsWith("@" + n) || lower.matches(".*\\b" + java.util.regex.Pattern.quote(n) + "\\b.*");
            if (!addressed) continue;
            long now = player.level().getGameTime();
            Long last = LAST_CHAT.get(player.getUUID());
            if (last != null && now - last < 15) return;
            LAST_CHAT.put(player.getUUID(), now);
            String message = ServerPayloadHandler.sanitize(raw.replaceFirst("(?i)^@?" + java.util.regex.Pattern.quote(name) + "[,:]?\\s*", ""));
            if (message.isEmpty()) message = "Hey " + name;
            final String msg = message;
            player.server.execute(() -> {
                if (!gf.isRemoved()) DialogueService.playerSays(gf, player, msg);
            });
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !GFConfig.SIT_ON_RIGHT_CLICK.get()) return;
        var player = event.getEntity();
        if (!player.getMainHandItem().isEmpty() || player.isPassenger()) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        boolean chair = SeatManager.isChairLike(state) && !player.isShiftKeyDown();
        boolean bedSit = state.getBlock() instanceof BedBlock && player.isShiftKeyDown();
        if (!chair && !bedSit) return;
        if (Vec3.atCenterOf(pos).distanceTo(player.position()) > 3.5) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide()) return;
        if (SeatManager.sit(player, pos, player.getYRot()) == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("aigirlfriend.fail.no_space"), true);
        }
    }
}
