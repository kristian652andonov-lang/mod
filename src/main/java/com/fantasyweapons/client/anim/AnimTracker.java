package com.fantasyweapons.client.anim;

import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side animation timelines per entity: weapon swings (with their own, per-weapon durations instead of
 * vanilla's fixed 6-tick swing), alternating forehand/backhand, heavy attacks, ability charge, ability release and
 * form switches. Read by {@link WeaponPoses} for third person and by the first-person transform.
 */
public final class AnimTracker {
    public static final class State {
        double swingStart = -1;
        int swingDuration = 7;
        int swingIndex;
        int combo;
        double lastSwing = -1000;
        boolean heavy;
        double castStart = -1;
        double formStart = -1;
        double plantStart = -1;
        double spinStart = -1;
        int spinTicks;
        int plantTicks;
        int plantDrive = 4;
        boolean wasCharging;
        float lastCharge;
        // development screenshot pins (see debugPin)
        float pinSwing = -1, pinCast = -1, pinForm = -1, pinCharge = -1, pinPlant = -1;
        int pinVariant = -1;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private AnimTracker() {
    }

    public static State state(LivingEntity e) {
        return STATES.computeIfAbsent(e.getId(), k -> new State());
    }

    public static double now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    /**
     * Swing duration in ticks: about 80% of the weapon's recovery time, so the follow-through settles just before the
     * next swing is allowed (heavy attacks take longer still).
     */
    public static int swingDuration(WeaponDefinition def, ItemStack stack, boolean heavy) {
        WeaponForm form = def.form(FantasyWeaponItem.data(stack));
        float speed = def.weaponClass().attackSpeed() * (form != null ? form.attackSpeedFactor() : 1f);
        int base = Math.max(8, Math.round(20f / speed * 0.8f));
        return heavy ? Math.round(base * 1.25f) : base;
    }

    public static void onSwing(LivingEntity e, WeaponDefinition def, ItemStack stack, boolean heavy) {
        State s = state(e);
        double now = now(0);
        // a new swing while the previous one is still early continues the combo instead of snapping back
        s.swingIndex++;
        // a chain of swings walks through the weapon's three attacks; a pause starts the chain over
        s.combo = now - s.lastSwing > s.swingDuration * 2.2 ? 0 : (s.combo + 1) % VARIANTS;
        s.lastSwing = now;
        s.swingStart = now;
        s.heavy = heavy;
        s.swingDuration = swingDuration(def, stack, heavy);
    }

    public static void onFormSwitch(int entityId) {
        State s = STATES.get(entityId);
        if (s == null) {
            var level = Minecraft.getInstance().level;
            if (level == null || !(level.getEntity(entityId) instanceof LivingEntity le)) return;
            s = state(le);
        }
        s.formStart = now(0);
    }

    /**
     * The weapon is driven into the ground for {@code ticks} (Monolith): {@code drive} ticks to bring it down, held
     * planted, then pulled free during the last ticks.
     */
    public static void onPlant(int entityId, int ticks, int drive) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(entityId) instanceof LivingEntity le)) return;
        State s = state(le);
        s.plantStart = now(0);
        s.plantTicks = Math.max(drive + 2, ticks);
        s.plantDrive = Math.max(1, drive);
        s.castStart = -1; // the plant replaces the generic release pose
    }

    /** The whole body whirls around for {@code ticks} (Infernochain's Cinder Cyclone). */
    public static void onSpin(int entityId, int ticks) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(entityId) instanceof LivingEntity le)) return;
        State s = state(le);
        s.spinStart = now(0);
        s.spinTicks = Math.max(1, ticks);
    }

    /** Detects charge → release transitions for every visible player (works for remote players too). */
    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            STATES.clear();
            return;
        }
        for (Player p : level.players()) {
            AbilityRuntime rt = p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
            State s = state(p);
            boolean charging = rt != null && rt.isCharging();
            if (charging) s.lastCharge = rt.chargeFraction(level.getGameTime());
            if (s.wasCharging && !charging && s.lastCharge > 0.05f) s.castStart = now(0);
            s.wasCharging = charging;
        }
        if (level.getGameTime() % 200 == 0) STATES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    public static void clear() {
        STATES.clear();
    }

    /**
     * DEVELOPMENT ONLY, used by the screenshot director: pins an entity's timeline channels at fixed progress values
     * (-1 = not pinned) so individual keyframes can be captured. Never called in normal play.
     */
    /** DEVELOPMENT ONLY: forces which attack variant is shown (-1 = normal). */
    public static void debugVariant(LivingEntity e, int variant) {
        state(e).pinVariant = variant;
    }

    public static void debugPin(LivingEntity e, float swing, boolean heavy, boolean backhand, float charge, float cast, float form) {
        State s = state(e);
        s.pinSwing = swing;
        s.heavy = heavy;
        s.swingIndex = backhand ? 0 : 1;
        s.pinVariant = backhand ? 1 : s.pinVariant;
        s.pinCharge = charge;
        s.pinCast = cast;
        s.pinForm = form;
    }

    /** DEVELOPMENT ONLY: pins the plant timeline at {@code elapsed} ticks of a {@code total}-tick plant (-1 releases). */
    public static void debugPlant(LivingEntity e, float elapsed, int total) {
        State s = state(e);
        s.pinPlant = elapsed;
        s.plantTicks = total;
        s.plantDrive = 4;
    }

    // ---- queries ----

    /** Ticks since the weapon started being driven into the ground, or -1 when not planted. */
    public static float plantElapsed(LivingEntity e, float partial) {
        State s = STATES.get(e.getId());
        if (s != null && s.pinPlant >= 0) return s.pinPlant;
        if (s == null || s.plantStart < 0) return -1;
        double t = now(partial) - s.plantStart;
        return t >= s.plantTicks ? -1 : (float) Math.max(0, t);
    }

    /** Extra yaw (radians) of a whole-body spin, or 0 when not spinning. Spins up and winds down smoothly. */
    public static float spinAngle(LivingEntity e, float partial) {
        State s = STATES.get(e.getId());
        if (s == null || s.spinStart < 0) return 0;
        double t = now(partial) - s.spinStart;
        if (t < 0 || t >= s.spinTicks) return 0;
        double ramp = 4, perTick = Math.PI * 2 / 6; // one turn every 6 ticks at full speed
        double up = Math.min(t, ramp), down = Math.max(0, t - (s.spinTicks - ramp));
        double angle = perTick * (up * up / (2 * ramp) + Math.max(0, Math.min(t, s.spinTicks - ramp) - ramp) + (down > 0 ? down - down * down / (2 * ramp) : 0));
        return (float) -angle;
    }

    public static int plantTicks(LivingEntity e) {
        State s = STATES.get(e.getId());
        return s == null ? 0 : s.plantTicks;
    }

    public static int plantDrive(LivingEntity e) {
        State s = STATES.get(e.getId());
        return s == null ? 4 : s.plantDrive;
    }

    /**
     * Weight of the planted pose (0..1) and the drive progress, shared by third and first person: returns
     * {weight, drive (0..1, eased in), impact shudder (decaying)} or null when not planted.
     */
    public static float[] plantPhase(LivingEntity e, float partial) {
        float el = plantElapsed(e, partial);
        if (el < 0) return null;
        int total = plantTicks(e), drive = plantDrive(e);
        int out = Math.min(8, Math.max(3, total / 4));
        float d = Math.min(1, el / drive);
        float driveK = d * d;
        float weight = el > total - out ? Math.max(0, (total - el) / out) : 1;
        weight = weight * weight * (3 - 2 * weight);
        float since = el - drive;
        float shudder = since < 0 ? 0 : (float) (Math.sin(since * 2.6) * Math.exp(-since * 0.35));
        return new float[]{weight, driveK, shudder};
    }

    /** Swing progress 0..1, or -1 when not swinging. */
    public static float swingProgress(LivingEntity e, float partial) {
        State s = STATES.get(e.getId());
        if (s != null && s.pinSwing >= 0) return s.pinSwing;
        if (s == null || s.swingStart < 0) return -1;
        double p = (now(partial) - s.swingStart) / s.swingDuration;
        return p >= 1 ? -1 : (float) Math.max(0, p);
    }

    /** Each weapon class has this many different attacks, played in turn while the attacks are chained. */
    public static final int VARIANTS = 3;

    /** Which of the weapon's attacks the current (or last) swing is: 0, 1 or 2. */
    public static int variant(LivingEntity e) {
        State s = STATES.get(e.getId());
        if (s != null && s.pinVariant >= 0) return s.pinVariant;
        return s == null ? 0 : s.combo;
    }

    /** The second attack of the chain is the backhand return of the first. */
    public static boolean mirrored(LivingEntity e) {
        return variant(e) == 1;
    }

    public static boolean heavy(LivingEntity e) {
        State s = STATES.get(e.getId());
        return s != null && s.heavy;
    }

    /** Ability release progress 0..1 over ~10 ticks, or -1. */
    public static float castProgress(LivingEntity e, float partial) {
        State s = STATES.get(e.getId());
        if (s != null && s.pinCast >= 0) return s.pinCast;
        if (s == null || s.castStart < 0) return -1;
        double p = (now(partial) - s.castStart) / 10.0;
        return p >= 1 ? -1 : (float) Math.max(0, p);
    }

    /** Form switch progress 0..1 over ~16 ticks, or -1. */
    public static float formProgress(LivingEntity e, float partial) {
        State s = STATES.get(e.getId());
        if (s != null && s.pinForm >= 0) return s.pinForm;
        if (s == null || s.formStart < 0) return -1;
        double p = (now(partial) - s.formStart) / 16.0;
        return p >= 1 ? -1 : (float) Math.max(0, p);
    }

    /** Current charge fraction of a player (0 if not charging). */
    public static float charge(LivingEntity e, float partial) {
        State pinned = STATES.get(e.getId());
        if (pinned != null && pinned.pinCharge >= 0) return pinned.pinCharge;
        if (!(e instanceof Player p)) return 0;
        AbilityRuntime rt = p.getExistingDataOrNull(ModAttachments.ABILITY_RUNTIME);
        var level = Minecraft.getInstance().level;
        if (rt == null || !rt.isCharging() || level == null) return 0;
        return Math.min(1f, (float) ((level.getGameTime() + partial - rt.chargeStart()) / Math.max(1, rt.chargeTicks())));
    }

    public static boolean isTwoHanded(WeaponClass cls, ItemStack stack) {
        return cls.twoHanded();
    }
}
