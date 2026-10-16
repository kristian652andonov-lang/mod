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
        FxDispatcher.register(FxIds.ERUPTION, UltimateFx::eruption);
        FxDispatcher.register(FxIds.TECTONIC, UltimateFx::tectonic);
        FxDispatcher.register(FxIds.GREAT_TREE, UltimateFx::greatTree);
        FxDispatcher.register(FxIds.GREAT_TREE_GRAB, UltimateFx::greatTreeGrab);
        FxDispatcher.register(FxIds.ICE_AGE, UltimateFx::iceAge);
        FxDispatcher.register(FxIds.GREAT_SLASH, UltimateFx::greatSlash);
        FxDispatcher.register(FxIds.GRAND_EARTHQUAKE, UltimateFx::grandEarthquake);
        FxDispatcher.register(FxIds.ICE_RAIN, UltimateFx::iceRain);
        FxDispatcher.register(FxIds.THORNSPIRE, UltimateFx::thornspire);
        FxDispatcher.register(FxIds.GREAT_SLASH_CUT, p -> VfxManager.add(new GreatSlashVfx.Cut(p.pos(), p.dir(), p.scale())));
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

    private static void eruption(FxPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 at = p.pos();
        float radius = p.scale();
        int delay = Math.max(20, (int) p.power());
        var cracks = com.fantasyweapons.weapons.domination.DominationAbilities.eruptionCracks(p.seed(),
                com.fantasyweapons.world.wreck.WorldWreck.radius(radius));
        VfxManager.add(new EruptionVfx(at, radius, delay, cracks, p.seed()));
        long ms = delay * 50L;
        if (isCaster(p) || distance(at) < radius * 4) {
            CinematicHud.play("UPLINK // DOMINION-01 > THE EARTHSPLITTING ERUPTION", at, 0xFF6A2A, (delay + 160) * 50L, List.of(
                    Cue.status(0, "MANTLE BREACHED", 0xFF6A2A),
                    Cue.card(300, 2000, "The ground splits", "11 fault lines  ·  magma rising at 900 m/s", 0xFFFFFF),
                    Cue.status(ms * 6 / 10, "PRESSURE CRITICAL", 0xFF4040),
                    Cue.status(ms, "ERUPTION", 0xFFD050),
                    Cue.card(ms + 300, 2400, "Ignition", "radius " + (int) radius + " m  ·  only ash remains", 0xFFB040)));
        }
        sound(SoundEvents.WARDEN_DIG, 1f, 0.5f);
        for (int k = 0; k < delay; k += 15) {
            int when = k;
            FxScheduler.after(when, () -> {
                CameraShake.add(at, 0.8f + 1.4f * when / delay, radius * 3);
                sound(SoundEvents.DEEPSLATE_BREAK, 1f, 0.4f);
                if (when % 30 == 0) sound(SoundEvents.LAVA_POP, 1f, 0.5f);
            });
        }
        FxScheduler.after(delay, () -> {
            ScreenFx.flash(0xFFC080, 0.85f * (float) Math.max(0.3, 1 - distance(at) / (radius * 5)), 16);
            CameraShake.add(at, 3f, radius * 6);
            sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.35f);
            sound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1f, 0.4f);
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.5f);
            throwLand(at, radius, p.seed());
        });
        FxScheduler.after(delay + 15, () -> sound(SoundEvents.FIRECHARGE_USE, 1f, 0.4f));
    }

    /** Real blocks of the land flung up and out by a blast. */
    private static void throwLand(Vec3 at, float radius, long seed) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(seed));
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed);
        int n = Math.round(380 * com.fantasyweapons.config.ClientConfig.quality().density());
        var m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            int x = (int) Math.floor(at.x + Math.cos(a) * d), z = (int) Math.floor(at.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1 - (r.nextFloat() < 0.4f ? r.nextInt(6) : 0);
            var state = level.getBlockState(m.set(x, top, z));
            double k = 1 - d / radius;
            Vec3 vel = new Vec3(Math.cos(a) * (0.4 + 1.6 * r.nextDouble()), 1.0 + 2.6 * k * r.nextDouble() + 0.4, Math.sin(a) * (0.4 + 1.6 * r.nextDouble()));
            debris.throwBlock(state, new Vec3(x + 0.5, top + 1, z + 0.5), vel, r.nextFloat() < 0.15f ? 2.5f : 1f, 50 + r.nextInt(50));
        }
        debris.close();
    }

    private static void tectonic(FxPayload p) {
        Vec3 at = p.pos();
        float length = p.scale(), width = p.power();
        int duration = Math.max(60, p.level());
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new TectonicVfx(at, p.dir(), length, width, duration, p.seed()).shedding(debris));
        long ms = duration * 50L;
        if (isCaster(p) || distance(at) < length * 2) {
            CinematicHud.play("UPLINK // DOMINION-01 > TECTONIC BREAKAGE", at, 0xD8A868, (duration + 60) * 50L, List.of(
                    Cue.status(0, "FAULT LINE FOUND", 0xD8A868),
                    Cue.card(500, 2200, "The plates split", (int) length + " m of fault  ·  drift 4 m/s", 0xFFFFFF),
                    Cue.status(1500, "PLATES SEPARATING", 0xFFB040),
                    Cue.card(ms / 2, 2200, "Down to bedrock", "canyon " + (int) width + " m wide  ·  the mantle rises", 0xFFB040),
                    Cue.status(ms, "RIFT COMPLETE", 0x9CD67A)));
        }
        sound(SoundEvents.WARDEN_DIG, 1f, 0.4f);
        FxScheduler.after(10, () -> {
            sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.35f);
            CameraShake.add(at, 2.5f, length * 2);
        });
        for (int k = 20; k < duration; k += 12) {
            int when = k;
            FxScheduler.after(when, () -> {
                CameraShake.add(at, 1.1f, length * 1.6);
                sound(when % 36 == 0 ? SoundEvents.DEEPSLATE_BREAK : SoundEvents.STONE_BREAK, 1f, 0.4f);
                if (when % 48 == 0) sound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.4f);
            });
        }
    }

    /** The last Great Tree effect started, so its seizing can be added to it. */
    private static GreatTreeVfx lastTree;

    private static void greatTree(FxPayload p) {
        Vec3 at = p.pos();
        float radius = p.scale();
        int duration = Math.max(60, (int) p.power());
        java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> roots = new java.util.ArrayList<>();
        var pts = p.points();
        int[] thick = p.entities();
        for (int i = 0; i + 3 < pts.size() && i / 4 < thick.length; i += 4) {
            roots.add(new com.fantasyweapons.weapons.ultimate.RootSpline(pts.get(i), pts.get(i + 1), pts.get(i + 2), pts.get(i + 3), thick[i / 4] / 10f));
        }
        lastTree = VfxManager.add(new GreatTreeVfx(at, radius, duration, roots));
        long ms = duration * 50L;
        if (isCaster(p) || distance(at) < radius * 3) {
            CinematicHud.play("UPLINK // DOMINION-01 > THE ROOTS OF THE GREAT TREE", at, 0x6BE873, (duration + 50) * 50L, List.of(
                    Cue.status(0, "THE WORLD TREE STIRS", 0x6BE873),
                    Cue.card(700, 2200, "Something is coming", "from beneath  ·  " + roots.size() + " roots rising", 0xFFFFFF),
                    Cue.status(2000, "SEIZING", 0x9CFF6A),
                    Cue.card(2100, 2200, "Held fast", "nothing within " + (int) radius + " m can move", 0xB8FF9A),
                    Cue.status(ms, "THE ROOTS REMAIN", 0x9CD67A),
                    Cue.card(ms, 2600, "Returned to the earth", "the roots will stand forever", 0xFFFFFF)));
        }
        sound(SoundEvents.WARDEN_EMERGE, 1f, 0.6f);
        for (int k = 14; k < 70; k += 7) {
            int when = k;
            FxScheduler.after(when, () -> {
                CameraShake.add(at, 1.6f, radius * 2.5);
                sound(when % 14 == 0 ? SoundEvents.ROOTED_DIRT_BREAK : SoundEvents.WOOD_BREAK, 1f, 0.4f);
            });
        }
        FxScheduler.after(16, () -> sound(SoundEvents.GENERIC_EXPLODE, 0.8f, 0.5f));
        FxScheduler.after(duration, () -> {
            CameraShake.add(at, 2.5f, radius * 3);
            sound(SoundEvents.WOOD_BREAK, 1f, 0.3f);
            sound(SoundEvents.GENERIC_EXPLODE, 0.7f, 0.4f);
        });
    }

    private static void greatTreeGrab(FxPayload p) {
        if (lastTree != null && !lastTree.isDead()) lastTree.seize(p.entities());
        sound(SoundEvents.CHAIN_PLACE, 1f, 0.4f);
    }

    private static void iceAge(FxPayload p) {
        Vec3 at = p.pos();
        float radius = p.scale(), height = p.power(), speed = Math.max(0.3f, p.level() / 100f);
        int surge = 24, run = (int) (radius / speed);
        VfxManager.add(new IceAgeVfx(at, radius, height, speed, surge));
        if (isCaster(p) || distance(at) < radius * 2.5) {
            CinematicHud.play("UPLINK // DOMINION-01 > THE ICE AGE", at, 0x8FE4FF, (surge + run + 80) * 50L, List.of(
                    Cue.status(0, "SEA RISING", 0x8FE4FF),
                    Cue.card(300, 2000, "Something is coming", "wave height " + (int) height + " m  ·  -89 \u00B0C", 0xFFFFFF),
                    Cue.status(surge * 50L, "TSUNAMI", 0xCFF4FF),
                    Cue.card(surge * 50L + 1200, 2400, "The Ice Age", "everything within " + (int) radius + " m is frozen", 0xBFEFFF),
                    Cue.status((surge + run) * 50L, "ALL IS ICE", 0x9CD67A)));
        }
        sound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.5f);
        sound(SoundEvents.AMBIENT_UNDERWATER_ENTER, 1f, 0.5f);
        FxScheduler.after(surge, () -> {
            CameraShake.add(at, 2.6f, radius * 3);
            sound(SoundEvents.GENERIC_SPLASH, 1f, 0.4f);
            sound(SoundEvents.GENERIC_EXPLODE, 0.8f, 0.4f);
        });
        for (int k = surge + 10; k < surge + run; k += 14) {
            FxScheduler.after(k, () -> {
                double d = distance(at);
                CameraShake.add(at, 1.2f, radius * 2);
                sound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 0.4f);
                sound(SoundEvents.GLASS_BREAK, 0.6f, 0.4f);
            });
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the Great Sword of Obliteration
    // ------------------------------------------------------------------------------------------------------------

    private static void greatSlash(FxPayload p) {
        Vec3 eye = p.pos(), look = p.dir();
        Vec3 blade = p.points().isEmpty() ? new Vec3(1, 0, 0) : p.points().get(0);
        float length = p.scale(), half = p.power(), speed = Math.max(1, p.level() / 100f);
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new GreatSlashVfx(eye, look, blade, length, half, speed, p.seed()).shattering(debris));
        if (isCaster(p)) {
            CinematicHud.play("UPLINK // OBLITERATION-01 > THE GREAT SLASH", eye.add(look.scale(length)), 0xFFE58A, 3200, List.of(
                    Cue.status(0, "CUTTING", 0xFFE58A),
                    Cue.card(200, 2200, "Cut", (int) length + " m  ·  nothing in its path remains whole", 0xFFFFFF)));
        }
        sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        sound(SoundEvents.TRIDENT_RIPTIDE_3.value(), 1f, 0.6f);
        sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.4f);
        CameraShake.add(eye, 1.6f, 60);
        ScreenFx.flash(0xFFFFFF, isCaster(p) ? 0.5f : 0.3f, 8);
        FxScheduler.after(4, () -> sound(SoundEvents.GLASS_BREAK, 1f, 0.5f));
    }

    private static void grandEarthquake(FxPayload p) {
        Vec3 at = p.pos();
        float radius = p.scale(), safe = p.power();
        int duration = Math.max(60, p.level());
        var cracks = com.fantasyweapons.weapons.obliteration.ObliterationAbilities.quakeCracks(p.seed(), com.fantasyweapons.world.wreck.WorldWreck.radius(radius));
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new GrandEarthquakeVfx(at, radius, safe, duration, cracks, p.seed()).tearing(debris));
        if (isCaster(p) || distance(at) < radius * 1.5) {
            CinematicHud.play("UPLINK // OBLITERATION-01 > THE GRAND EARTHQUAKE", at, 0xD8A868, (duration + 50) * 50L, List.of(
                    Cue.status(0, "MAGNITUDE 12.4", 0xD8A868),
                    Cue.card(150, 2200, "Fury", "the earth breaks for " + (int) radius + " m  ·  only you stand", 0xFF8A4A),
                    Cue.status(1500, "PLATES BREAKING", 0xFFB040),
                    Cue.card(duration * 50L - 1500, 2400, "Nothing stands", "\u0394E 2.1 \u00D7 10^22 joules", 0xFFFFFF),
                    Cue.status(duration * 50L, "SILENCE", 0x9CD67A)));
        }
        sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.35f);
        sound(SoundEvents.ANVIL_LAND, 1f, 0.4f);
        sound(SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.5f);
        ScreenFx.flash(0xFFE0B0, 0.6f, 10);
        for (int k = 0; k < duration; k += 6) {
            int when = k;
            FxScheduler.after(when, () -> {
                CameraShake.add(at, 2.4f - 1.4f * when / duration, radius * 1.5);
                if (when % 18 == 0) sound(when % 36 == 0 ? SoundEvents.DEEPSLATE_BREAK : SoundEvents.STONE_BREAK, 1f, 0.35f);
                if (when % 30 == 0) sound(SoundEvents.GENERIC_EXPLODE, 0.5f, 0.3f);
            });
        }
    }

    private static void iceRain(FxPayload p) {
        Vec3 at = p.pos();
        float radius = p.scale();
        int duration = Math.max(60, (int) p.power());
        int[] times = p.entities();
        VfxManager.add(new IceRainVfx(at, radius, duration, p.points(), times, p.seed()));
        if (isCaster(p) || distance(at) < radius * 2) {
            CinematicHud.play("UPLINK // OBLITERATION-01 > ICE RAIN", at, 0x8FE4FF, (duration + 40) * 50L, List.of(
                    Cue.status(0, "STORM GATHERING", 0x8FE4FF),
                    Cue.card(400, 2000, "Something is coming", "from above  ·  " + times.length + " spikes of ice", 0xFFFFFF),
                    Cue.status(1500, "IMPACTS", 0xCFF4FF),
                    Cue.card(duration * 50L - 1800, 2400, "Carnage", "everything within " + (int) radius + " m is frozen", 0xBFEFFF)));
        }
        sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
        sound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.6f, 0.6f);
        for (int i = 0; i < times.length && i < p.points().size(); i++) {
            Vec3 hit = p.points().get(i);
            FxScheduler.after(times[i], () -> {
                double d = distance(hit);
                CameraShake.add(hit, 1.4f, 70);
                if (d < 90) {
                    float v = (float) Math.max(0.15, 1 - d / 90);
                    sound(SoundEvents.GLASS_BREAK, v, 0.4f + (float) Math.random() * 0.3f);
                    sound(SoundEvents.GENERIC_EXPLODE, v * 0.6f, 0.6f);
                }
            });
        }
        FxScheduler.after(duration - 10, () -> {
            sound(SoundEvents.GLASS_BREAK, 1f, 0.3f);
            CameraShake.add(at, 2f, radius * 2);
        });
    }

    private static void thornspire(FxPayload p) {
        Vec3 at = p.pos();
        float radius = p.scale(), safe = p.power();
        int[] codes = p.entities();
        int last = 0;
        for (int c : codes) last = Math.max(last, c / 1000);
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new ThornspireVfx(at, radius, safe, p.points(), codes, p.seed()).bursting(debris));
        if (isCaster(p) || distance(at) < radius * 2) {
            CinematicHud.play("UPLINK // OBLITERATION-01 > THORNSPIRE CATACLYSM", at, 0x7BE86B, (last + 50) * 50L, List.of(
                    Cue.status(0, "THE OLD FOREST WAKES", 0x7BE86B),
                    Cue.card(300, 2000, "Something is coming", "from beneath  \u00B7  " + codes.length + " thorns rising", 0xFFFFFF),
                    Cue.status(1200, "IMPALING", 0x9CFF6A),
                    Cue.card(last * 50L - 600, 2600, "The Thornwood", "it will stand forever  \u00B7  " + (int) radius + " m", 0xC8FFA8),
                    Cue.status(last * 50L + 400, "OVERGROWN", 0x9CD67A)));
        }
        sound(SoundEvents.WARDEN_EMERGE, 1f, 0.7f);
        sound(SoundEvents.GENERIC_EXPLODE, 0.7f, 0.5f);
        java.util.Set<Integer> loud = new java.util.HashSet<>();
        for (int i = 0; i < codes.length && i * 2 < p.points().size(); i++) {
            int when = Math.max(0, codes[i] / 1000 - 5);
            Vec3 foot = p.points().get(i * 2);
            FxScheduler.after(when, () -> {
                double d = distance(foot);
                CameraShake.add(foot, 1.2f, 60);
                if (d < 100) {
                    float v = (float) Math.max(0.2, 1 - d / 100);
                    sound(SoundEvents.ROOTED_DIRT_BREAK, v, 0.5f);
                    sound(SoundEvents.WOOD_BREAK, v, 0.4f + (float) Math.random() * 0.2f);
                }
            });
            if (loud.add(when / 8)) FxScheduler.after(when, () -> sound(SoundEvents.GENERIC_EXPLODE, 0.35f, 0.5f));
        }
    }
}
