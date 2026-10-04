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
        boolean heavy;
        double castStart = -1;
        double formStart = -1;
        boolean wasCharging;
        float lastCharge;
        // development screenshot pins (see debugPin)
        float pinSwing = -1, pinCast = -1, pinForm = -1, pinCharge = -1;
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

    /** Swing duration in ticks for a weapon (heavy attacks wind up longer). */
    public static int swingDuration(WeaponDefinition def, ItemStack stack, boolean heavy) {
        WeaponForm form = def.form(FantasyWeaponItem.data(stack));
        int base = switch (def.weaponClass()) {
            case LONGSWORD, LANCE -> 7;
            case CHAINBLADE -> form != null && "chainblade".equals(form.id()) ? 11 : 7;
            case GREATSWORD, BATTLEAXE, SCYTHE -> 10;
            case WARHAMMER -> 12;
            case COLOSSAL -> 16;
        };
        return heavy ? Math.round(base * 1.4f) : base;
    }

    public static void onSwing(LivingEntity e, WeaponDefinition def, ItemStack stack, boolean heavy) {
        State s = state(e);
        double now = now(0);
        // a new swing while the previous one is still early continues the combo instead of snapping back
        s.swingIndex++;
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
    public static void debugPin(LivingEntity e, float swing, boolean heavy, boolean backhand, float charge, float cast, float form) {
        State s = state(e);
        s.pinSwing = swing;
        s.heavy = heavy;
        s.swingIndex = backhand ? 0 : 1;
        s.pinCharge = charge;
        s.pinCast = cast;
        s.pinForm = form;
    }

    // ---- queries ----

    /** Swing progress 0..1, or -1 when not swinging. */
    public static float swingProgress(LivingEntity e, float partial) {
        State s = STATES.get(e.getId());
        if (s != null && s.pinSwing >= 0) return s.pinSwing;
        if (s == null || s.swingStart < 0) return -1;
        double p = (now(partial) - s.swingStart) / s.swingDuration;
        return p >= 1 ? -1 : (float) Math.max(0, p);
    }

    public static boolean mirrored(LivingEntity e) {
        State s = STATES.get(e.getId());
        return s != null && s.swingIndex % 2 == 0;
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
