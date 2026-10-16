package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.ImpactFrames;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.hud.CinematicHud;
import com.fantasyweapons.client.hud.CinematicHud.Cue;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Client side of the two ultimate weapons' abilities: their effects, cinematic overlays, sounds and impact frames. */
public final class UltimateFx {
    private UltimateFx() {
    }

    public static void register() {
        FxDispatcher.register(FxIds.GRANT_EXECUTION, UltimateFx::grantExecution);
        FxDispatcher.register(FxIds.CATACLYSM_FRAME, UltimateFx::cataclysmFrame);
        FxDispatcher.register(FxIds.BLACKHOLE, UltimateFx::blackhole);
    }

    /** How far the viewer is from a point (blocks), or a long way if there is no player. */
    static double distance(Vec3 at) {
        var p = Minecraft.getInstance().player;
        return p == null ? 1e9 : p.position().distanceTo(at);
    }

    static boolean isCaster(FxPayload p) {
        var pl = Minecraft.getInstance().player;
        return pl != null && pl.getId() == p.caster();
    }

    /** A sound heard at full strength wherever the viewer is (the cataclysm is that loud). */
    static void sound(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static void sound(Holder<SoundEvent> sound, float volume, float pitch) {
        sound(sound.value(), volume, pitch);
    }

    /** The impact frames of a world-breaking blow, played by those near enough (the server sends them to them). */
    private static void cataclysmFrame(FxPayload p) {
        if (!isCaster(p) && distance(p.pos()) > p.scale()) return;
        if (p.level() == 1) ImpactFrames.cataclysm(1f, p.pos());
        else ImpactFrames.play(true, 0.95f, p.pos());
    }

    // ------------------------------------------------------------------------------------------------------------
    // the Spell Book of Absolute Domination
    // ------------------------------------------------------------------------------------------------------------

    private static void grantExecution(FxPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        int delay = Math.max(20, (int) p.power()), beam = Math.max(20, p.level());
        float radius = p.scale();
        Vec3 at = p.pos();
        VfxManager.add(new GrantExecutionVfx(at, radius, delay, beam, level.getMinBuildHeight()));
        double d = distance(at);
        boolean near = isCaster(p) || d < radius * 3.5;
        long ms = delay * 50L;
        if (near) {
            CinematicHud.play("UPLINK // DOMINION-01 > THE BEAM OF GRANT EXECUTION", at, 0xFFE58A, (delay + beam + 50) * 50L, List.of(
                    Cue.status(0, "ALIGNING THE CIRCLE", 0xFFE58A),
                    Cue.card(400, 2300, "Something is coming", "altitude " + (int) Math.max(220, radius * 2.3f) + " m  ·  32 satellite circles", 0xFFFFFF),
                    Cue.status(ms / 2, "CHARGING", 0xFFD970),
                    Cue.status(ms * 6 / 10, "TARGET LOCKED", 0xFF5A5A),
                    Cue.card(ms * 62 / 100, 1500, "Grant Execution", "radius " + (int) radius + " m  ·  down to bedrock", 0xFFE58A),
                    Cue.status(ms, "FIRING", 0xFFFFFF),
                    Cue.card(ms + 1500, 2600, "[ Impact confirmed ]", "ΔE 4.75 × 10^56 joules  ·  nothing within " + (int) radius + " m remains", 0xFFFFFF),
                    Cue.status((delay + beam) * 50L, "TARGET ERASED", 0x9CD67A)));
        }
        // the charge
        sound(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.5f);
        FxScheduler.after(delay / 3, () -> sound(SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.5f));
        FxScheduler.after(delay * 6 / 10, () -> {
            sound(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.6f);
            sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8f, 0.5f);
        });
        // the beam falls
        FxScheduler.after(delay, () -> {
            double dd = distance(at);
            float strength = (float) Math.max(0.25, 1 - dd / (radius * 5));
            ScreenFx.flash(0xFFFFFF, 0.9f * strength, 16);
            CameraShake.add(at, 3f, radius * 6);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
            sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.45f);
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.6f);
        });
        FxScheduler.after(delay + 12, () -> {
            CameraShake.add(at, 2.4f, radius * 5);
            sound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1f, 0.5f);
        });
        for (int k = 1; k < 4; k++) {
            int when = delay + k * beam / 4;
            FxScheduler.after(when, () -> {
                CameraShake.add(at, 1.6f, radius * 4);
                sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 0.5f);
            });
        }
        FxScheduler.after(delay + beam, () -> sound(SoundEvents.BEACON_DEACTIVATE, 1f, 0.5f));
    }

    private static void blackhole(FxPayload p) {
        Vec3 hole = p.pos();
        float reach = p.scale();
        int collapse = Math.max(60, (int) p.power()), form = Math.max(10, p.level());
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new UltramassiveBlackholeVfx(hole, 28, reach, form, collapse, p.seed()).feeding(debris));
        long ms = collapse * 50L;
        if (isCaster(p) || distance(hole) < reach * 2.5) {
            CinematicHud.play("UPLINK // DOMINION-01 > THE ULTRAMASSIVE BLACKHOLE", p.dir(), 0xB070FF, (collapse + 50) * 50L, List.of(
                    Cue.status(0, "SINGULARITY OPENING", 0xB070FF),
                    Cue.card(form * 50L, 2200, "Absorbing", "mass 4.3 \u00D7 10^36 kg  ·  reach " + (int) reach + " m", 0xFFFFFF),
                    Cue.status(form * 50L + 400, "FEEDING", 0xFF5AE0),
                    Cue.card(ms / 2, 2000, "Nothing escapes", "event horizon 28 m  ·  everything within " + (int) reach + " m is falling", 0xD8B8FF),
                    Cue.status(ms - 1500, "COLLAPSE IMMINENT", 0xFF5A5A),
                    Cue.card(ms, 2600, "Returned to nothing", "\u0394M = all of it", 0xFFFFFF)));
        }
        sound(SoundEvents.WARDEN_EMERGE, 1f, 0.5f);
        sound(SoundEvents.END_PORTAL_SPAWN, 0.8f, 0.5f);
        FxScheduler.after(form, () -> {
            sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.4f);
            CameraShake.add(hole, 2.5f, reach * 3);
            ImpactFrames.play(true, 0.9f, hole);
        });
        for (int k = form + 30; k < collapse; k += 40) {
            FxScheduler.after(k, () -> {
                sound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f);
                sound(SoundEvents.PORTAL_TRAVEL, 0.25f, 0.4f);
                CameraShake.add(hole, 1.2f, reach * 2);
            });
        }
        FxScheduler.after(collapse, () -> {
            sound(SoundEvents.END_GATEWAY_SPAWN, 1f, 0.5f);
            sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.4f);
        });
        FxScheduler.after(collapse + 12, () -> {
            ScreenFx.flash(0xFFFFFF, 0.9f * (float) Math.max(0.3, 1 - distance(hole) / (reach * 4)), 18);
            CameraShake.add(hole, 3f, reach * 4);
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.4f);
        });
    }
}
