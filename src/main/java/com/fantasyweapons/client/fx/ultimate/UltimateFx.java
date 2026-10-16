package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.ImpactFrames;
import com.fantasyweapons.client.fx.ScreenFx;
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

/** Client side of the two ultimate weapons' abilities: their effects, sounds and impact frames. */
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
        FxDispatcher.register(FxIds.ARCH_DRAGON, UltimateFx::archDragon);
        FxDispatcher.register(FxIds.FOURTH_DIMENSION, UltimateFx::fourthDimension);
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

    private static void archDragon(FxPayload p) {
        Vec3 target = p.pos();
        var pts = p.points();
        if (pts.size() < 2) return;
        Vec3 caster = pts.get(0), rift = pts.get(1);
        float length = p.scale(), radius = p.power();
        int flight = Math.max(40, p.level());
        var path = new com.fantasyweapons.weapons.ultimate.DragonPath(caster, rift, target, flight, p.seed());
        var cracks = com.fantasyweapons.weapons.obliteration.ObliterationAbilities.dragonCracks(p.seed(), com.fantasyweapons.world.wreck.WorldWreck.radius(radius));
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new ArchDragonVfx(path, length, radius, cracks, p.seed()).tearing(debris));
        long strike = flight * 50L;
        sound(SoundEvents.ENDER_DRAGON_GROWL, 1f, 0.45f);
        sound(SoundEvents.WITHER_SPAWN, 0.6f, 0.4f);
        CameraShake.add(rift, 2.2f, 200);
        for (int k = 8; k < flight; k += 12) {
            int when = k;
            FxScheduler.after(when, () -> sound(SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.45f + 0.1f * (when % 3)));
        }
        FxScheduler.after((int) (flight * 0.72), () -> {
            sound(SoundEvents.ENDER_DRAGON_GROWL, 1f, 0.6f);
            sound(SoundEvents.BLAZE_SHOOT, 1f, 0.4f);
        });
        FxScheduler.after(flight, () -> {
            ScreenFx.flash(0xFFD8A0, 0.75f, 12);
            CameraShake.add(target, 3f, radius * 3);
            sound(SoundEvents.GENERIC_EXPLODE, 1f, 0.3f);
            sound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1f, 0.4f);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.4f);
        });
        for (int k = 6; k < 50; k += 6) {
            int when = flight + k;
            FxScheduler.after(when, () -> {
                CameraShake.add(target, 1.6f, radius * 2);
                if (when % 12 == 0) sound(SoundEvents.LAVA_POP, 1f, 0.5f);
                if (when % 18 == 0) sound(SoundEvents.GENERIC_EXPLODE, 0.45f, 0.4f);
            });
        }
    }

    private static void fourthDimension(FxPayload p) {
        Vec3 at = p.pos();
        float radius = p.scale();
        int delay = Math.max(20, (int) p.power());
        int minY = p.level(), height = p.entities().length > 0 ? p.entities()[0] : 384;
        var plan = new com.fantasyweapons.weapons.ultimate.ShufflePlan(at, radius, p.seed(), minY, height);
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new FourthDimensionVfx(at, radius, delay, plan, minY, p.seed()).lifting(debris));
        sound(SoundEvents.BEACON_ACTIVATE, 1f, 0.5f);
        sound(SoundEvents.PORTAL_TRIGGER, 0.5f, 0.5f);
        for (int k = 10; k < delay; k += 10) {
            int when = k;
            FxScheduler.after(when, () -> {
                CameraShake.add(at, 0.4f + 1.2f * when / delay, radius * 4);
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.5f + when / (float) delay);
            });
        }
        FxScheduler.after(delay, () -> {
            ScreenFx.flash(0xF0E0FF, 0.8f, 10);
            CameraShake.add(at, 3f, radius * 5);
            sound(SoundEvents.END_PORTAL_SPAWN, 1f, 0.6f);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.4f);
            sound(SoundEvents.GLASS_BREAK, 1f, 0.3f);
        });
        for (int k = 3; k < 24; k += 3) FxScheduler.after(delay + k, () -> sound(SoundEvents.GLASS_BREAK, 0.6f, 0.4f + (float) Math.random() * 0.4f));
    }
}
