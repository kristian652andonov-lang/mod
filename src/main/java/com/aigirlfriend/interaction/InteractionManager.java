package com.aigirlfriend.interaction;

import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.Expression;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueTrigger;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.entity.SeatEntity;
import com.aigirlfriend.entity.SleepHelper;
import com.aigirlfriend.seat.SeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative state machine for coordinated player/companion interactions.
 * <p>
 * Every interaction validates its trigger conditions, places both characters (or seats/carries them),
 * syncs one compact state value to all clients (so every client animates both characters identically),
 * enforces its interruption policy (sneak, distance, damage, danger, partner leaving) and always runs a
 * clean exit (dismount, stand up, put down, wake) so neither character can get stuck.
 */
public final class InteractionManager {
    public enum StopReason { COMPLETED, CANCELLED_PLAYER, CANCELLED_SELF, DISTANCE, DAMAGE, DANGER, PARTNER_GONE, REMOVED, FAINTED, REPLACED, SLEEP, INVALID }

    /** Player UUID -> companion currently interacting with them (server side). */
    private static final Map<UUID, GirlfriendEntity> PARTNERS = new HashMap<>();

    private final GirlfriendEntity gf;
    @Nullable private InteractionType current;
    @Nullable private UUID partnerId;
    private int ticks;
    private boolean selfInitiated;
    private boolean shy;
    private Vec3 startPos = Vec3.ZERO;
    @Nullable private Vec3 targetPos;
    private float facingYaw;
    private Vec3 partnerAnchor = Vec3.ZERO;
    @Nullable private BlockPos bed;
    private final EnumMap<InteractionType, Long> cooldownUntil = new EnumMap<>(InteractionType.class);

    public InteractionManager(GirlfriendEntity gf) {
        this.gf = gf;
    }

    @Nullable
    public static GirlfriendEntity partnerOf(Player player) {
        GirlfriendEntity g = PARTNERS.get(player.getUUID());
        if (g != null && (g.isRemoved() || !g.interactions().isActive())) {
            PARTNERS.remove(player.getUUID());
            return null;
        }
        return g;
    }

    public static void releasePlayer(Player player, StopReason reason) {
        GirlfriendEntity g = partnerOf(player);
        if (g != null) g.interactions().stop(reason);
    }

    public static void clearAll() {
        PARTNERS.clear();
    }

    public boolean isActive() {
        return current != null;
    }

    @Nullable
    public InteractionType current() {
        return current;
    }

    public boolean isSelfInitiated() {
        return selfInitiated;
    }

    public int ticks() {
        return ticks;
    }

    @Nullable
    public BlockPos bed() {
        return bed;
    }

    @Nullable
    public ServerPlayer partner() {
        if (partnerId == null || !(gf.level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(partnerId);
    }

    private long now() {
        return gf.level().getGameTime();
    }

    public boolean onCooldown(InteractionType type) {
        Long until = cooldownUntil.get(type);
        return until != null && now() < until;
    }

    // ------------------------------------------------------------------ validation

    /** Returns null if the interaction may start, otherwise the reason it cannot. */
    @Nullable
    public Component check(InteractionType type, ServerPlayer player, boolean self) {
        if (gf.isFainted()) return reason("fainted");
        if (!player.isAlive() || player.isSpectator() || player.level() != gf.level()) return reason("unavailable");
        if (!gf.isOwnedBy(player)) return reason("not_owner");
        if (!gf.data().relationship.stage().atLeast(type.stage)) return Component.translatable("aigirlfriend.fail.stage", Component.translatable(type.stage.key()));
        if (onCooldown(type) && current != type) return reason("cooldown");
        double max = GFConfig.INTERACTION_DISTANCE.get();
        if (type == InteractionType.SLEEP_TOGETHER || type == InteractionType.LEAD_TO_BED || type == InteractionType.HOLD_HANDS) max += 4;
        if (player.distanceTo(gf) > max) return reason("too_far");
        GirlfriendEntity other = partnerOf(player);
        if (other != null && other != gf) return reason("busy");
        if (gf.controller().fear.afraid() && type != InteractionType.HOLD_HANDS && type != InteractionType.CARRY) return reason("scared");
        if (player.isSleeping() && type != InteractionType.SLEEP_TOGETHER) return reason("player_busy");
        switch (type.positioning) {
            case FACE, BEHIND, SIDE -> {
                if (player.isPassenger() || player.isFallFlying() || !player.onGround() || gf.isCarried()) return reason("player_busy");
                if (computeStandPos(type, player) == null) return reason("no_space");
            }
            case CARRIED -> {
                if (player.isPassenger() || player.isVehicle() || player.isFallFlying() || player.isSleeping() || player.isSwimming()) return reason("player_busy");
            }
            case SEATED -> {
                if (type != InteractionType.SIT_TOGETHER) {
                    boolean seatedPair = current != null && current.isSeated() && SeatManager.isSeated(player) && gf.isSeated();
                    if (!seatedPair) return reason("need_sit");
                } else if (player.isFallFlying() || (player.isPassenger() && !SeatManager.isSeated(player))) {
                    return reason("player_busy");
                }
            }
            case BED -> {
                if (type == InteractionType.CUDDLE_BED && findBedNear(player, 3) == null) return reason("no_bed");
            }
            case FREE -> {
                if (player.isPassenger() || gf.isCarried()) return reason("player_busy");
            }
        }
        return null;
    }

    private static Component reason(String key) {
        return Component.translatable("aigirlfriend.fail." + key);
    }

    @Nullable
    public BlockPos findBedNear(Player player, int radius) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            BlockPos head = SleepHelper.bedHead(level, p);
            if (head == null || SleepHelper.isOccupied(level, head)) continue;
            double d = head.distSqr(center);
            if (d < bestDist) {
                bestDist = d;
                best = head.immutable();
            }
        }
        return best;
    }

    /** Where she should stand for FACE/BEHIND/SIDE interactions, trying several angles if the ideal spot is blocked. */
    @Nullable
    private Vec3 computeStandPos(InteractionType type, Player player) {
        Vec3 pp = player.position();
        double baseAngle;
        switch (type.positioning) {
            case FACE -> {
                Vec3 d = gf.position().subtract(pp);
                baseAngle = d.horizontalDistanceSqr() < 0.04 ? player.getYRot() : Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG;
            }
            case BEHIND -> baseAngle = player.getYRot() + 180f;
            case SIDE -> baseAngle = player.getYRot() + 90f;
            default -> {
                return gf.position();
            }
        }
        float[] offsets = type.positioning == Positioning.FACE ? new float[]{0, 30, -30, 60, -60, 90, -90, 180} : new float[]{0, 20, -20, 180};
        for (float off : offsets) {
            double a = (baseAngle + off) * Mth.DEG_TO_RAD;
            Vec3 target = new Vec3(pp.x - Math.sin(a) * type.distance, pp.y, pp.z + Math.cos(a) * type.distance);
            if (isStandable(target)) {
                facingYaw = switch (type.positioning) {
                    case FACE -> (float) (baseAngle + off + 180f);
                    default -> player.getYRot();
                };
                return target;
            }
        }
        return null;
    }

    private boolean isStandable(Vec3 target) {
        Level level = gf.level();
        AABB box = gf.getDimensions(gf.getPose()).makeBoundingBox(target).deflate(0.05);
        if (!level.noCollision(gf, box)) return false;
        BlockPos below = BlockPos.containing(target.x, target.y - 0.1, target.z);
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getFluidState(below.above()).isEmpty();
    }

    // ------------------------------------------------------------------ start

    public boolean start(InteractionType type, ServerPlayer player, boolean self) {
        Component fail = check(type, player, self);
        if (fail != null) {
            if (!self) player.displayClientMessage(fail, true);
            if (!self && fail.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().endsWith("cooldown")) {
                gf.controller().say(DialogueTrigger.INTERACTION_COOLDOWN);
            }
            return false;
        }
        // seated sub-interactions swap in place without unseating anyone
        if (current != null && current.isSeated() && type.isSeated() && player.getUUID().equals(partnerId)) {
            current = type;
            ticks = 0;
            selfInitiated = self;
            if (type != InteractionType.SIT_TOGETHER) pullSeatsTogether(player, 0.26);
            gf.setSyncedInteraction(type, player.getId(), self);
            afterStart(type, player, self);
            return true;
        }
        if (current != null) stop(StopReason.REPLACED);
        if (gf.isSleeping() && type.positioning != Positioning.BED) gf.stopSleeping();
        if (gf.isSeated() && !type.isSeated()) {
            SeatManager.standUp(gf);
            gf.setFlag(GirlfriendEntity.FLAG_GROUND_SIT, false);
        }

        startPos = gf.position();
        targetPos = null;
        bed = null;
        switch (type.positioning) {
            case FACE, BEHIND, SIDE -> {
                targetPos = computeStandPos(type, player);
                if (targetPos == null) return false;
                partnerAnchor = player.position();
            }
            case CARRIED -> {
                if (!gf.startRiding(player, true)) return false;
                player.connection.send(new ClientboundSetPassengersPacket(player));
            }
            case SEATED -> {
                if (!seatTogether(player)) {
                    player.displayClientMessage(reason("no_space"), true);
                    return false;
                }
            }
            case BED -> {
                if (type == InteractionType.CUDDLE_BED && !lieOnBed(player)) {
                    player.displayClientMessage(reason("no_bed"), true);
                    return false;
                }
            }
            case FREE -> {}
        }
        current = type;
        partnerId = player.getUUID();
        ticks = 0;
        selfInitiated = self;
        shy = !self && computeShy(type);
        PARTNERS.put(player.getUUID(), gf);
        gf.setSyncedInteraction(type, player.getId(), self);
        gf.setFlag(GirlfriendEntity.FLAG_SHY, shy);
        gf.setFlag(GirlfriendEntity.FLAG_LEADING, type == InteractionType.LEAD_TO_BED);
        gf.getNavigation().stop();
        afterStart(type, player, self);
        return true;
    }

    private void afterStart(InteractionType type, ServerPlayer player, boolean self) {
        gf.controller().markInteraction();
        gf.controller().feel(shy ? com.aigirlfriend.companion.Emotion.EMBARRASSMENT : type.emotion, 0.6f, Math.max(100, type.duration));
        String action = type.name().toLowerCase().replace('_', ' ');
        if (self) gf.controller().say(DialogueTrigger.INITIATIVE, Map.of("action", type.name()));
        else if (shy) gf.controller().say(DialogueTrigger.INTERACTION_SHY, Map.of("action", type.name()));
        else if (type == InteractionType.CARRY) gf.controller().say(DialogueTrigger.CARRIED);
        else if (type == InteractionType.SIT_TOGETHER) gf.controller().say(DialogueTrigger.SIT_TOGETHER);
        else if (gf.getRandom().nextFloat() < 0.6f) gf.controller().say(DialogueTrigger.INTERACTION_START, Map.of("action", type.name(), "action_words", action));
        if (type.isRomantic() || type == InteractionType.HUG) gf.controller().heartParticles(type == InteractionType.KISS ? 6 : 3);
        if (type == InteractionType.KISS || type == InteractionType.KISS_CHEEK || type == InteractionType.FOREHEAD_KISS) {
            gf.level().playSound(null, gf.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5f, 1.6f);
        } else if (type == InteractionType.HUG || type == InteractionType.CUDDLE || type == InteractionType.BACK_HUG) {
            gf.level().playSound(null, gf.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 0.6f, 1.2f);
        }
    }

    private boolean computeShy(InteractionType type) {
        var d = gf.data();
        float shyness = d.personality.shyness;
        if (shyness < 0.35f) return false;
        int above = d.relationship.stage().ordinal() - type.stage.ordinal();
        float chance = shyness * Math.max(0.05f, 0.75f - above * 0.3f);
        if (type.isRomantic()) chance *= 1.2f;
        return gf.getRandom().nextFloat() < chance;
    }

    /** Seats the player (if needed) and her right beside them. */
    private boolean seatTogether(ServerPlayer player) {
        if (!SeatManager.isSeated(player)) {
            if (SeatManager.sitHere(player) == null) return false;
        }
        if (!(player.getVehicle() instanceof SeatEntity playerSeat)) return false;
        if (gf.getVehicle() instanceof SeatEntity mine && mine.distanceTo(playerSeat) < 1.6) return true;
        float yaw = playerSeat.getYRot();
        for (BlockPos p : SeatManager.seatsBeside(gf.level(), playerSeat.getSeatBlock(), yaw)) {
            double y1 = SeatManager.absoluteSeatY(gf.level(), p);
            if (Double.isNaN(y1) || Math.abs(y1 - playerSeat.getY()) > 0.55) continue;
            if (SeatManager.sit(gf, p, yaw) != null) {
                if (gf.getVehicle() instanceof SeatEntity s) s.setYRot(yaw);
                pullSeatsTogether(player, 0.18);
                return true;
            }
        }
        return false;
    }

    /** Slides both seats towards each other so shoulders touch. */
    private void pullSeatsTogether(ServerPlayer player, double amount) {
        if (!(player.getVehicle() instanceof SeatEntity a) || !(gf.getVehicle() instanceof SeatEntity b)) return;
        Vec3 d = b.position().subtract(a.position());
        double len = d.horizontalDistance();
        if (len < 0.55 || len > 1.8) return;
        Vec3 dir = new Vec3(d.x / len, 0, d.z / len);
        double move = Math.min(amount, (len - 0.55) / 2);
        a.setPos(a.position().add(dir.scale(move)));
        b.setPos(b.position().subtract(dir.scale(move)));
    }

    /** Both lie down on a bed side by side (cuddling, without sleeping). */
    private boolean lieOnBed(ServerPlayer player) {
        BlockPos head = findBedNear(player, 3);
        if (head == null) return false;
        Direction facing = SleepHelper.facing(gf.level(), head);
        if (facing == null) return false;
        float yaw = facing.getOpposite().toYRot();
        BlockPos foot = head.relative(facing.getOpposite());
        Vec3 mid = new Vec3((head.getX() + foot.getX()) / 2.0 + 0.5, head.getY() + 0.5625, (head.getZ() + foot.getZ()) / 2.0 + 0.5);
        Direction side = facing.getClockWise();
        Vec3 playerPos = mid.add(side.getStepX() * 0.22, 0, side.getStepZ() * 0.22);
        Vec3 herPos = mid.add(-side.getStepX() * 0.22, 0, -side.getStepZ() * 0.22);
        if (player.isPassenger()) player.stopRiding();
        SeatEntity ps = SeatEntity.create(gf.level(), head, playerPos, yaw);
        SeatEntity gs = SeatEntity.create(gf.level(), head, herPos, yaw);
        if (!gf.level().addFreshEntity(ps)) return false;
        if (!gf.level().addFreshEntity(gs)) {
            ps.discard();
            return false;
        }
        if (!player.startRiding(ps, true) || !gf.startRiding(gs, true)) {
            player.stopRiding();
            gf.stopRiding();
            ps.discard();
            gs.discard();
            return false;
        }
        bed = head;
        return true;
    }

    /** Puts her in (or beside) the bed the owner just lay down in. Called by the sleep event handler. */
    public boolean startSleepTogether(ServerPlayer player, BlockPos playerBed) {
        if (!gf.data().relationship.stage().atLeast(InteractionType.SLEEP_TOGETHER.stage)) return false;
        if (gf.isFainted() || gf.controller().fear.afraid()) return false;
        if (current != null && current != InteractionType.SLEEP_TOGETHER) stop(StopReason.REPLACED);
        Level level = gf.level();
        BlockPos head = SleepHelper.bedHead(level, playerBed);
        if (head == null) return false;
        if (gf.isPassenger()) gf.stopRiding();
        if (!(gf.isSleeping() && gf.getSleepingPos().map(p -> p.closerThan(head, 2)).orElse(false))) {
            if (gf.isSleeping()) gf.stopSleeping();
            BlockPos adjacent = SleepHelper.adjacentFreeBed(level, head);
            if (adjacent != null) {
                gf.startSleeping(adjacent);
            } else {
                Direction facing = SleepHelper.facing(level, head);
                boolean right = facing != null && gf.position().subtract(Vec3.atCenterOf(head))
                        .dot(new Vec3(facing.getClockWise().getStepX(), 0, facing.getClockWise().getStepZ())) >= 0;
                gf.startSleepingBeside(head, SleepHelper.besidePosition(level, head, right));
            }
        }
        current = InteractionType.SLEEP_TOGETHER;
        partnerId = player.getUUID();
        ticks = 0;
        selfInitiated = false;
        shy = false;
        bed = head;
        PARTNERS.put(player.getUUID(), gf);
        gf.setSyncedInteraction(current, player.getId(), false);
        gf.controller().say(DialogueTrigger.GOODNIGHT, Map.of("together", "1"));
        gf.controller().heartParticles(3);
        return true;
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (current == null) return;
        ServerPlayer p = partner();
        if (p == null || !p.isAlive() || p.level() != gf.level() || p.isSpectator()) {
            stop(StopReason.PARTNER_GONE);
            return;
        }
        ticks++;
        InteractionType type = current;

        if (p.isShiftKeyDown() && ticks > 6 && type.positioning != Positioning.SEATED && type.positioning != Positioning.BED) {
            stop(StopReason.CANCELLED_PLAYER);
            return;
        }
        if (gf.hurtTime == 9 && type.positioning != Positioning.CARRIED && type != InteractionType.HOLD_HANDS && type != InteractionType.SLEEP_TOGETHER) {
            stop(StopReason.DAMAGE);
            return;
        }
        if (p.distanceTo(gf) > type.breakDistance) {
            stop(StopReason.DISTANCE);
            return;
        }
        switch (type.positioning) {
            case FACE, BEHIND, SIDE -> {
                if (partnerAnchor.distanceTo(p.position()) > (type.locksPlayer ? 1.0 : 1.6)) {
                    stop(StopReason.CANCELLED_PLAYER);
                    return;
                }
                if (targetPos != null) {
                    float t = Math.min(1f, ticks / 6f);
                    Vec3 pos = startPos.lerp(targetPos, t);
                    gf.setPos(pos.x, pos.y, pos.z);
                    gf.setDeltaMovement(Vec3.ZERO);
                }
                gf.setYRot(facingYaw);
                gf.yBodyRot = facingYaw;
                gf.setYHeadRot(facingYaw);
                gf.getNavigation().stop();
            }
            case CARRIED -> {
                if (gf.getVehicle() != p || p.isSleeping() || p.isFallFlying() || p.isUnderWater()) {
                    stop(StopReason.INVALID);
                    return;
                }
            }
            case SEATED -> {
                if (!SeatManager.isSeated(p) || !gf.isSeated()) {
                    stop(StopReason.CANCELLED_PLAYER);
                    return;
                }
            }
            case BED -> {
                if (type == InteractionType.CUDDLE_BED && (!SeatManager.isSeated(p) || !gf.isPassenger())) {
                    stop(StopReason.CANCELLED_PLAYER);
                    return;
                }
                if (type == InteractionType.SLEEP_TOGETHER && !gf.isSleeping()) {
                    stop(StopReason.INVALID);
                    return;
                }
            }
            case FREE -> {}
        }
        if (ticks % 20 == 0) gf.controller().onInteractionSecond(type, ticks / 20);
        if ((type.isRomantic() || type == InteractionType.HUG) && ticks % 50 == 25 && type.positioning != Positioning.FREE) gf.controller().heartParticles(1);
        if (type.duration > 0 && ticks >= type.duration) stop(StopReason.COMPLETED);
    }

    // ------------------------------------------------------------------ stop

    public void stop(StopReason reason) {
        if (current == null) return;
        InteractionType type = current;
        ServerPlayer p = partner();
        int lasted = ticks;
        boolean wasSelf = selfInitiated;
        boolean wasShy = shy;
        current = null;

        switch (type.positioning) {
            case CARRIED -> putDown(p);
            case SEATED -> {
                boolean stillSeated = p != null && SeatManager.isSeated(p) && gf.isSeated();
                if (type != InteractionType.SIT_TOGETHER && stillSeated && reason != StopReason.REPLACED && reason != StopReason.DANGER
                        && reason != StopReason.REMOVED && reason != StopReason.FAINTED) {
                    // fall back to simply sitting together
                    rewards(type, p, lasted, reason, wasSelf, wasShy);
                    current = InteractionType.SIT_TOGETHER;
                    ticks = 0;
                    selfInitiated = false;
                    shy = false;
                    gf.setSyncedInteraction(current, p.getId(), false);
                    return;
                }
                if (reason != StopReason.REPLACED && gf.isSeated() && gf.getCommand() != CommandMode.SIT) SeatManager.standUp(gf);
            }
            case BED -> {
                if (type == InteractionType.CUDDLE_BED) {
                    if (gf.getVehicle() instanceof SeatEntity) {
                        gf.stopRiding();
                        if (bed != null) {
                            Vec3 v = SleepHelper.findStandUpPosition(gf, bed);
                            gf.moveTo(v.x, v.y, v.z, gf.getYRot(), 0f);
                        }
                    }
                    if (p != null && p.getVehicle() instanceof SeatEntity seat && bed != null && seat.getSeatBlock().equals(bed)) p.stopRiding();
                } else if (type == InteractionType.SLEEP_TOGETHER && reason != StopReason.SLEEP && gf.isSleeping()) {
                    gf.stopSleeping();
                }
            }
            case FREE, FACE, BEHIND, SIDE -> {}
        }
        rewards(type, p, lasted, reason, wasSelf, wasShy);

        long cd = (long) (type.cooldown * GFConfig.COOLDOWN_MULTIPLIER.get());
        cooldownUntil.put(type, now() + cd);
        if (partnerId != null) PARTNERS.remove(partnerId, gf);
        partnerId = null;
        ticks = 0;
        targetPos = null;
        bed = null;
        selfInitiated = false;
        shy = false;
        gf.setSyncedInteraction(null, -1, false);
        gf.setFlag(GirlfriendEntity.FLAG_SHY, false);
        gf.setFlag(GirlfriendEntity.FLAG_LEADING, false);
    }

    private void rewards(InteractionType type, @Nullable ServerPlayer p, int lasted, StopReason reason, boolean wasSelf, boolean wasShy) {
        if (p == null || reason == StopReason.REMOVED) return;
        if (type.duration > 0) {
            if (reason == StopReason.COMPLETED || lasted >= type.duration * 0.6f) {
                gf.controller().onInteractionCompleted(type, p, wasSelf, wasShy);
            } else if (wasSelf && reason == StopReason.CANCELLED_PLAYER && lasted < 25) {
                gf.controller().onInteractionDeclined(type);
            }
        } else {
            if (lasted > 200) gf.controller().onInteractionCompleted(type, p, wasSelf, wasShy);
            else if (wasSelf && reason == StopReason.CANCELLED_PLAYER && lasted < 40) gf.controller().onInteractionDeclined(type);
        }
        if (type == InteractionType.CARRY && reason != StopReason.REPLACED) gf.controller().say(DialogueTrigger.PUT_DOWN);
    }

    private void putDown(@Nullable ServerPlayer p) {
        if (gf.isPassenger()) gf.stopRiding();
        if (p == null) return;
        p.connection.send(new ClientboundSetPassengersPacket(p));
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 front = p.position().add(-Mth.sin(yaw) * 0.9, 0, Mth.cos(yaw) * 0.9);
        Vec3 target = isStandable(front) ? front : p.position();
        gf.moveTo(target.x, target.y, target.z, p.getYRot() + 180f, 0f);
        gf.setDeltaMovement(Vec3.ZERO);
        gf.fallDistance = 0;
    }

    /** Face to show during the current interaction, or null for the normal mood face. */
    @Nullable
    public Expression expressionOverride() {
        if (current == null) return null;
        if (shy) return Expression.EMBARRASSED;
        return switch (current) {
            case KISS, KISS_CHEEK, FOREHEAD_KISS, CUDDLE, BACK_HUG, HEAD_ON_SHOULDER, CUDDLE_SITTING, CUDDLE_BED -> Expression.AFFECTION;
            case HUG, CARRY, HOLD_HANDS, LEAD_TO_BED, SIT_TOGETHER, LEAN_ON -> Expression.HAPPY;
            case SLEEP_TOGETHER -> Expression.SLEEPY;
        };
    }
}
