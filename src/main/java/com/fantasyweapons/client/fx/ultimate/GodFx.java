package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/** Client side of the Weapon of God: its summonings and its own three powers, their effects and sounds. */
public final class GodFx {
    private GodFx() {
    }

    public static void register() {
        FxDispatcher.register(FxIds.DIVINE_SUMMON, GodFx::summon);
        FxDispatcher.register(FxIds.HEAVEN_TRIDENT, GodFx::trident);
        FxDispatcher.register(FxIds.SIX_GODS, GodFx::sixGods);
        FxDispatcher.register(FxIds.DIMENSION_BREAKAGE, GodFx::breakage);
    }

    private static boolean far(FxPayload p) {
        return UltimateFx.distance(p.pos()) > Cataclysm.SEEN && !UltimateFx.isCaster(p);
    }

    /** The summoning: a choir of chimes rising, a heartbeat quickening, thunder rolling nearer; at the last, a blaze. */
    private static void summon(FxPayload p) {
        Vec3 feet = p.points().isEmpty() ? p.pos() : p.points().get(0);
        if (UltimateFx.distance(feet) > Cataclysm.SEEN && far(p)) return;
        int ticks = Math.max(40, (int) p.power());
        VfxManager.add(new DivineSummonVfx(p.caster(), feet, p.pos(), p.scale(), ticks, p.level(), p.seed()));
        UltimateFx.sound(SoundEvents.BEACON_ACTIVATE, 1f, 0.5f);
        UltimateFx.sound(SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.4f);
        UltimateFx.sound(SoundEvents.WARDEN_EMERGE, 0.6f, 0.7f);
        // the choir: chimes climbing a chord, over and over, higher each time
        float[] chord = {0.5f, 0.63f, 0.75f, 1f};
        for (int k = 0; k < ticks - 20; k += 10) {
            int when = k;
            float pitch = chord[(k / 10) % chord.length] * (1 + 0.5f * k / ticks);
            FxScheduler.after(when, () -> UltimateFx.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, Math.min(2f, pitch)));
        }
        for (int k = 20; k < ticks - 4; k += Math.max(5, 16 - k / 14)) {
            int when = k;
            FxScheduler.after(when, () -> UltimateFx.sound(SoundEvents.WARDEN_HEARTBEAT, 0.6f + 0.5f * when / ticks, 0.6f + 0.5f * when / ticks));
        }
        for (int k = 30; k < ticks; k += 45) FxScheduler.after(k, () -> UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f));
        FxScheduler.after(ticks / 2, () -> UltimateFx.sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.5f));
        FxScheduler.after(ticks - 30, () -> UltimateFx.sound(SoundEvents.BEACON_POWER_SELECT, 1f, 0.6f));
        FxScheduler.after(ticks - 14, () -> UltimateFx.sound(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.7f));
        FxScheduler.after(ticks, () -> {
            UltimateFx.sound(SoundEvents.TOTEM_USE, 0.8f, 0.6f);
            UltimateFx.sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.2f);
            if (UltimateFx.isCaster(p) || UltimateFx.distance(feet) < 80) ScreenFx.flash(0xFFF4D6, 0.7f, 14);
            CameraShake.add(feet, 2.5f, 160);
        });
    }

    private static void trident(FxPayload p) {
        if (far(p)) return;
        Vec3 at = p.pos(), sky = p.points().isEmpty() ? at.add(0, 300, 0) : p.points().get(0);
        int strike = Math.max(60, (int) p.power()), open = Math.max(20, p.level());
        int emerge = p.entities().length == 0 ? (strike - open) / 2 : p.entities()[0];
        float radius = p.scale();
        VfxManager.add(new HeavenTridentVfx(at, sky, radius, open, emerge, strike, p.seed()));
        // the sky tears
        UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.4f);
        UltimateFx.sound(SoundEvents.END_PORTAL_SPAWN, 1f, 0.5f);
        for (int k = 10; k < open; k += 18) {
            FxScheduler.after(k, () -> {
                UltimateFx.sound(SoundEvents.GLASS_BREAK, 0.8f, 0.4f);
                UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 0.5f);
                CameraShake.add(sky, 1.2f, radius * 4);
            });
        }
        // the trident comes out
        FxScheduler.after(open, () -> {
            UltimateFx.sound(SoundEvents.BEACON_ACTIVATE, 1f, 0.4f);
            UltimateFx.sound(SoundEvents.TRIDENT_RETURN, 1f, 0.5f);
        });
        for (int k = open + 10; k < open + emerge; k += 20) FxScheduler.after(k, () -> UltimateFx.sound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8f, 0.4f));
        // it falls
        FxScheduler.after(open + emerge, () -> {
            UltimateFx.sound(SoundEvents.TRIDENT_RIPTIDE_3.value(), 1f, 0.4f);
            UltimateFx.sound(SoundEvents.ELYTRA_FLYING, 1f, 0.5f);
            UltimateFx.sound(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
        });
        // it strikes
        FxScheduler.after(strike, () -> {
            double d = UltimateFx.distance(at);
            float strength = (float) Math.max(0.3, 1 - d / (radius * 5));
            ScreenFx.flash(0xFFFFFF, 0.95f * strength, 20);
            CameraShake.add(at, 4f, radius * 6);
            UltimateFx.sound(SoundEvents.TRIDENT_THUNDER.value(), 1f, 0.5f);
            UltimateFx.sound(SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.35f);
            UltimateFx.sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
        });
        FxScheduler.after(strike + 10, () -> {
            CameraShake.add(at, 3f, radius * 5);
            UltimateFx.sound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1f, 0.4f);
        });
        FxScheduler.after(strike + 30, () -> UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.4f));
    }

    private static void sixGods(FxPayload p) {
        if (far(p)) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        int delay = Math.max(60, (int) p.power()), beam = Math.max(40, p.level());
        float radius = p.scale();
        Vec3 at = p.pos();
        VfxManager.add(new SixGodsVfx(at, radius, delay, beam, level.getMinBuildHeight()));
        UltimateFx.sound(SoundEvents.BEACON_ACTIVATE, 1f, 0.4f);
        UltimateFx.sound(SoundEvents.END_PORTAL_SPAWN, 1f, 0.4f);
        // each of the six circles opening, a note higher than the last
        for (int i = 0; i < 6; i++) {
            int when = 30 + i * 18;
            float pitch = 0.5f + i * 0.1f;
            FxScheduler.after(when, () -> {
                UltimateFx.sound(SoundEvents.BEACON_POWER_SELECT, 0.9f, pitch);
                UltimateFx.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, pitch * 1.5f);
            });
        }
        // the power pouring in
        FxScheduler.after((int) (delay * 0.55f), () -> UltimateFx.sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.5f));
        FxScheduler.after((int) (delay * 0.75f), () -> UltimateFx.sound(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f));
        for (int k = (int) (delay * 0.55f); k < delay; k += 12) {
            int when = k;
            FxScheduler.after(when, () -> {
                UltimateFx.sound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f + 0.4f * when / delay);
                CameraShake.add(at, 0.8f + 1.2f * when / delay, radius * 4);
            });
        }
        // the beam falls
        FxScheduler.after(delay, () -> {
            double d = UltimateFx.distance(at);
            float strength = (float) Math.max(0.3, 1 - d / (radius * 5));
            ScreenFx.flash(0xFFFFFF, 0.95f * strength, 24);
            CameraShake.add(at, 4f, radius * 6);
            UltimateFx.sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.4f);
            UltimateFx.sound(SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.3f);
            UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.5f);
            UltimateFx.sound(SoundEvents.TOTEM_USE, 1f, 0.5f);
        });
        for (int k = 1; k < 6; k++) {
            FxScheduler.after(delay + k * beam / 6, () -> {
                CameraShake.add(at, 2f, radius * 4);
                UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 0.5f);
                UltimateFx.sound(SoundEvents.BEACON_AMBIENT, 1f, 0.5f);
            });
        }
        FxScheduler.after(delay + beam, () -> UltimateFx.sound(SoundEvents.BEACON_DEACTIVATE, 1f, 0.4f));
    }

    private static void breakage(FxPayload p) {
        if (far(p)) return;
        int delay = Math.max(60, (int) p.power()), hold = Math.max(100, p.level());
        float radius = p.scale();
        Vec3 at = p.pos();
        Vec3 low = p.points().size() > 1 ? p.points().get(0) : at.add(-120, 16, -120);
        Vec3 high = p.points().size() > 1 ? p.points().get(1) : at.add(120, 256, 120);
        BlockDebrisVfx debris = VfxManager.add(new BlockDebrisVfx(p.seed()));
        VfxManager.add(new DimensionBreakageVfx(at, radius, delay, hold, low, high, p.seed()).lifting(debris));
        Vec3 heart = low.add(high).scale(0.5);
        UltimateFx.sound(SoundEvents.END_PORTAL_SPAWN, 1f, 0.4f);
        UltimateFx.sound(SoundEvents.PORTAL_TRIGGER, 0.8f, 0.5f);
        UltimateFx.sound(SoundEvents.WARDEN_EMERGE, 0.8f, 0.5f);
        for (int k = 8; k < delay; k += 14) {
            int when = k;
            FxScheduler.after(when, () -> {
                UltimateFx.sound(SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.4f + 0.3f * when / delay);
                CameraShake.add(at, 0.5f + 1.5f * when / delay, radius * 2);
            });
        }
        // space breaks
        FxScheduler.after(delay, () -> {
            ScreenFx.flash(0xE2C8FF, 0.8f, 16);
            CameraShake.add(at, 3.5f, radius * 3);
            UltimateFx.sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.3f);
            UltimateFx.sound(SoundEvents.GLASS_BREAK, 1f, 0.3f);
            UltimateFx.sound(SoundEvents.END_PORTAL_SPAWN, 1f, 0.3f);
        });
        // half a minute in the cube: a heartbeat, quickening, the hum rising
        for (int k = delay + 20; k < delay + hold - 4; ) {
            int when = k;
            float s = (k - delay) / (float) hold;
            FxScheduler.after(when, () -> {
                UltimateFx.sound(SoundEvents.WARDEN_HEARTBEAT, 0.7f + 0.5f * s, 0.5f + 0.6f * s);
                CameraShake.add(heart, 0.6f + 2f * s * s, radius * 2);
            });
            k += Math.max(4, (int) (24 - 20 * s));
        }
        for (int k = delay + 40; k < delay + hold; k += 60) {
            int when = k;
            FxScheduler.after(when, () -> UltimateFx.sound(SoundEvents.BEACON_AMBIENT, 1f, 0.4f + 0.8f * (when - delay) / hold));
        }
        FxScheduler.after(delay + hold - 60, () -> UltimateFx.sound(SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.4f));
        FxScheduler.after(delay + hold - 20, () -> UltimateFx.sound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1f, 0.4f));
        // it explodes
        FxScheduler.after(delay + hold, () -> {
            ScreenFx.flash(0xFFFFFF, 1f, 30);
            CameraShake.add(heart, 5f, radius * 4);
            UltimateFx.sound(SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.25f);
            UltimateFx.sound(SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.3f);
            UltimateFx.sound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1f, 0.3f);
            UltimateFx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.3f);
        });
        FxScheduler.after(delay + hold + 12, () -> {
            CameraShake.add(heart, 3f, radius * 3);
            UltimateFx.sound(SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.4f);
        });
        FxScheduler.after(delay + hold + 40, () -> UltimateFx.sound(SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.3f));
    }
}
