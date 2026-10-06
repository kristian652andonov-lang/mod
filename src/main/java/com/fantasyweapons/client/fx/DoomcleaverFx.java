package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.WeaponAnchor;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.RibbonTrailVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.client.vfx.effects.SpikeVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Client visuals for Doomcleaver. Palette: arterial red, dark blood, pale crimson light. */
public final class DoomcleaverFx {
    static final int BLOOD = 0xE0213A;
    static final int DARK = 0x5A0010;
    static final int LIGHT = 0xFF9AA8;
    static final int HOT = 0xFFD6DC;
    static final Blast.Palette PALETTE = new Blast.Palette(0xFF5A6E, BLOOD, DARK);

    private DoomcleaverFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.DOOMCLEAVER_CLEAVE, DoomcleaverFx::cleave);
        FxDispatcher.register(FxIds.DOOMCLEAVER_RAGE, DoomcleaverFx::rage);
        FxDispatcher.register(FxIds.DOOMCLEAVER_FEED, DoomcleaverFx::feed);
        FxDispatcher.register(FxIds.DOOMCLEAVER_LEAP, DoomcleaverFx::leap);
        FxDispatcher.register(FxIds.DOOMCLEAVER_LEAP_LAND, DoomcleaverFx::leapLand);
        FxDispatcher.register(FxIds.DOOMCLEAVER_APOCALYPSE, DoomcleaverFx::apocalypse);
        FxDispatcher.register(FxIds.DOOMCLEAVER_DRAIN, DoomcleaverFx::drain);
        FxDispatcher.register(FxIds.DOOMCLEAVER_APOCALYPSE_END, DoomcleaverFx::apocalypseEnd);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    /** Blood streams flowing from victims back into the caster. */
    static void bloodStreams(int casterId, int[] victims, int life) {
        Entity caster = entity(casterId);
        if (caster == null) return;
        Vec3 to = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        for (int id : victims) {
            Entity v = entity(id);
            if (v == null) continue;
            Vec3 from = v.position().add(0, v.getBbHeight() * 0.5, 0);
            List<Vec3> path = new ArrayList<>();
            Vec3 side = to.subtract(from).cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
            for (int i = 0; i <= 12; i++) {
                double t = i / 12.0;
                double wave = Math.sin(t * Math.PI) * 0.8;
                path.add(from.lerp(to, t).add(0, wave, 0).add(side.scale(Math.sin(t * Math.PI * 2) * 0.35)));
            }
            VfxManager.add(new RibbonTrailVfx(path, 0.9f, Colors.argb(150, DARK), Colors.argb(90, DARK), life).texture(VfxTextures.GLOW));
            VfxManager.add(new RibbonTrailVfx(path, 0.32f, Colors.argb(240, BLOOD), Colors.argb(220, 0xA0102A), life));
        }
    }

    private static void cleave(FxPayload p) {
        Vec3 o = p.pos();
        Vec3 fwd = new Vec3(p.dir().x, 0, p.dir().z).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        float range = p.scale();
        // the overhead chop: a vertical crescent coming down in front
        Vec3 chest = o.add(0, 1.3, 0);
        VfxManager.add(new SlashArcVfx(chest, fwd, new Vec3(0, 1, 0), 2.6f, 0.9f, (float) Math.toRadians(110), (float) Math.toRadians(-40),
                Colors.argb(240, BLOOD), Colors.argb(255, 0xFF7080), 10));
        // the blood wave: crystal spikes erupting in a widening fan
        RandomSource r = RandomSource.create(p.seed());
        SpikeVfx spikes = new SpikeVfx(SpikeVfx.Style.CRYSTAL, Colors.argb(230, BLOOD), LIGHT, 34).timing(2, 10);
        float half = (float) Math.toRadians(p.power() / 2);
        for (double d = 1.2; d <= range; d += 0.7) {
            int n = 1 + (int) (d * half * 0.9);
            for (int k = 0; k < n; k++) {
                double a = (n == 1 ? 0 : -half + 2 * half * k / (n - 1)) + (r.nextDouble() - 0.5) * 0.15;
                Vec3 dir = fwd.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
                Vec3 at = FrostrendFx.ground(o.add(dir.scale(d)));
                spikes.add(at, dir.scale(0.45).add(0, 1, 0), (0.5f + r.nextFloat() * 0.7f) * (1.1f - (float) (d / range) * 0.4f), 0.18f,
                        (int) (d * 1.4));
            }
        }
        VfxManager.add(spikes);
        Vec3 cg = FrostrendFx.ground(o.add(fwd.scale(range * 0.5)));
        GroundShatter.cracks(cg, range * 0.6f, com.fantasyweapons.client.vfx.GroundMaterial.at(cg), 60, BLOOD, 0xFF8A9A, p.seed() * 7);
        VfxManager.add(new ShardBurstVfx(o.add(fwd.scale(2)).add(0, 0.5, 0), fwd.add(0, 0.8, 0), 0.6f, 0.4f, 24, 0.22f, Colors.argb(255, BLOOD),
                Colors.argb(0, DARK), 22, p.seed()).texture(VfxTextures.SHARD, false).physics(0.04f, 0.95f));
        CameraShake.add(o, 0.6f, 12);
        bloodStreams(p.caster(), p.entities(), 14);
    }

    private static void rage(FxPayload p) {
        Entity e = entity(p.caster());
        Vec3 o = p.pos();
        VfxManager.add(new ShockwaveVfx(o.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3f, 5f, 0.6f, Colors.argb(230, BLOOD), 14).energy());
        VfxManager.add(new DecalVfx(o.add(0, 0.04, 0), new Vec3(0, 1, 0), 2.5f, Colors.argb(220, BLOOD), VfxTextures.RUNE_CIRCLE, 30)
                .spin(0.1f).energy().timing(0.1f, 0.5f));
        VfxManager.add(new ShardBurstVfx(o.add(0, 1, 0), new Vec3(0, 1, 0), 0.8f, 0.3f, 30, 0.35f, Colors.argb(255, LIGHT), Colors.argb(0, DARK), 22,
                p.seed()).texture(VfxTextures.FLAME, false).physics(-0.01f, 0.9f));
        if (e != null) VfxManager.add(new FlashVfx(Vec3.ZERO, 1f, 4f, Colors.argb(200, BLOOD), 12).follow(e, new Vec3(0, 1, 0)).energy());
        if (e == Minecraft.getInstance().player) ScreenFx.flash(BLOOD, 0.3f, 12);
        CameraShake.add(o, 0.5f, 10);
    }

    private static void feed(FxPayload p) {
        Entity e = entity(p.caster());
        if (e == null) return;
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.5f, 2f, Colors.argb(180, BLOOD), 10).follow(e, new Vec3(0, 1, 0)).energy());
        VfxManager.add(new ShardBurstVfx(e.position().add(0, 1, 0), new Vec3(0, 1, 0), 1f, 0.12f, 10, 0.2f, Colors.argb(255, LIGHT),
                Colors.argb(0, BLOOD), 16, e.getId() + e.tickCount).texture(VfxTextures.GLOW, false).attract(e.position().add(0, 1, 0), 0.05f));
    }

    private static void leap(FxPayload p) {
        Entity e = entity(p.caster());
        Vec3 o = p.pos();
        VfxManager.add(new ShockwaveVfx(o.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3f, 3f, 0.4f, Colors.argb(220, BLOOD), 10).energy());
        VfxManager.add(new ShardBurstVfx(o.add(0, 0.3, 0), new Vec3(0, 1, 0), 0.7f, 0.3f, 16, 0.25f, Colors.argb(255, BLOOD), Colors.argb(0, DARK),
                18, p.seed()).texture(VfxTextures.SHARD, false).physics(0.04f, 0.95f));
        if (e instanceof LivingEntity le) {
            FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(() -> le.isAlive() ? WeaponAnchor.blade(le, 1f, 1.6) : null, 1.1f,
                    Colors.argb(230, BLOOD), Colors.argb(0, DARK), 12, 60));
            FollowTrailVfx body = VfxManager.add(new FollowTrailVfx(() -> le.isAlive() ? le.position().add(0, 1, 0) : null, 1.6f,
                    Colors.argb(140, BLOOD), Colors.argb(0, DARK), 10, 60).texture(VfxTextures.GLOW, false));
            FxProjectiles.track(le.getId() * 7919L + 13, trail, body);
        }
    }

    private static void leapLand(FxPayload p) {
        FxProjectiles.end(p.caster() * 7919L + 13);
        Vec3 g = p.pos();
        float r = p.scale();
        Blast.explode(g, r, PALETTE, p.seed(), 1, VfxTextures.SHARD);
        RandomSource rnd = RandomSource.create(p.seed());
        SpikeVfx crown = new SpikeVfx(SpikeVfx.Style.CRYSTAL, Colors.argb(235, BLOOD), LIGHT, 40).timing(2, 12);
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + rnd.nextDouble() * 0.2;
            double d = r * (0.45 + rnd.nextDouble() * 0.4);
            Vec3 at = FrostrendFx.ground(g.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            crown.add(at, new Vec3(Math.cos(a) * 0.7, 1, Math.sin(a) * 0.7), 1.0f + rnd.nextFloat() * 1.2f, 0.3f, (int) (d / r * 4));
        }
        VfxManager.add(crown);
        bloodStreams(p.caster(), p.entities(), 16);
    }

    private static void apocalypse(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new ShockwaveVfx(c.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5f, r, 0.8f, Colors.argb(230, BLOOD), 18).energy());
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(200, BLOOD), VfxTextures.RUNE_CIRCLE, duration)
                .spin(0.02f).energy().timing(0.1f, 0.1f));
        ScreenFx.zoneVignette(BLOOD, 0.35f, duration, c, r + 4);
        CameraShake.add(c, 0.4f, r * 2);
    }

    private static void drain(FxPayload p) {
        bloodStreams(p.caster(), p.entities(), 10);
        for (int id : p.entities()) {
            Entity v = entity(id);
            if (v == null) continue;
            VfxManager.add(new ShardBurstVfx(v.position().add(0, v.getBbHeight() * 0.6, 0), Vec3.ZERO, 1f, 0.1f, 5, 0.2f, Colors.argb(255, BLOOD),
                    Colors.argb(0, DARK), 12, id * 31L + v.tickCount).texture(VfxTextures.SHARD, false).physics(0.03f, 0.92f));
        }
    }

    private static void apocalypseEnd(FxPayload p) {
        Vec3 c = p.pos();
        float r = p.scale();
        Blast.explode(c, r * 0.8f, PALETTE, p.seed(), 2, VfxTextures.SHARD);
        for (int id : p.entities()) {
            Entity v = entity(id);
            if (v == null) continue;
            Vec3 at = v.position();
            VfxManager.add(new SpikeVfx(SpikeVfx.Style.CRYSTAL, Colors.argb(235, BLOOD), LIGHT, 30).timing(2, 10)
                    .add(at.add(0.3, 0, 0), new Vec3(0.4, 1, 0), v.getBbHeight() * 1.3f, 0.3f, 0)
                    .add(at.add(-0.3, 0, 0.2), new Vec3(-0.4, 1, 0.2), v.getBbHeight(), 0.25f, 1)
                    .add(at.add(0, 0, -0.3), new Vec3(0, 1, -0.4), v.getBbHeight() * 1.1f, 0.25f, 2));
        }
        bloodStreams(p.caster(), p.entities(), 16);
    }
}
