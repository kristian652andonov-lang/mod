package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.LightningVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.StormCloudVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client visuals for Stormbreaker. Palette: electric blue, white-hot arc core, storm indigo. */
public final class StormbreakerFx {
    static final int BOLT = 0x5CB8FF;
    static final int CORE = 0xE6F3FF;
    static final int STORM = 0x2B3A6B;
    static final int CLOUD = 0x3A4256;
    static final Blast.Palette PALETTE = new Blast.Palette(CORE, BOLT, STORM);

    private StormbreakerFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.STORMBREAKER_CHAIN, StormbreakerFx::chain);
        FxDispatcher.register(FxIds.STORMBREAKER_ARCS, StormbreakerFx::arcs);
        FxDispatcher.register(FxIds.STORMBREAKER_SPIN, StormbreakerFx::spin);
        FxDispatcher.register(FxIds.STORMBREAKER_STRIKE_WARN, StormbreakerFx::strikeWarn);
        FxDispatcher.register(FxIds.STORMBREAKER_STRIKE, StormbreakerFx::strike);
        FxDispatcher.register(FxIds.STORMBREAKER_WRATH, StormbreakerFx::wrath);
        FxDispatcher.register(FxIds.STORMBREAKER_BOLT, StormbreakerFx::bolt);
        FxDispatcher.register(FxIds.STORMBREAKER_WRATH_END, StormbreakerFx::wrathEnd);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    private static Vec3 chest(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0);
    }

    /** Impact on a struck entity: flash, sparks, a short crackle ring. */
    static void zap(Vec3 at, float size, long seed) {
        VfxManager.add(new FlashVfx(at, 0.3f, 1.4f * size, Colors.argb(240, BOLT), 6).energy());
        VfxManager.add(new FlashVfx(at, 0.2f, 0.8f * size, Colors.argb(255, CORE), 4, VfxTextures.FLASH));
        VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, 0.25f, 8, 0.18f, Colors.argb(255, CORE), Colors.argb(0, BOLT), 8, seed)
                .texture(VfxTextures.SPARK, true).physics(0.02f, 0.85f));
    }

    private static void chain(FxPayload p) {
        Vec3 prev = p.pos();
        long seed = p.seed();
        int i = 0;
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            Vec3 to = chest(e);
            float w = i == 0 ? 0.28f : 0.22f;
            int delay = i * 2;
            Vec3 from = prev;
            long s = seed + i * 31L;
            FxScheduler.after(delay, () -> {
                VfxManager.add(new LightningVfx(from, to, w, Colors.argb(255, BOLT), 10, s).branches(3));
                zap(to, 1f, s);
            });
            prev = to;
            i++;
        }
    }

    private static void arcs(FxPayload p) {
        Vec3 from = p.pos();
        int i = 0;
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            Vec3 to = chest(e);
            VfxManager.add(new LightningVfx(from, to, 0.16f, Colors.argb(240, BOLT), 6, p.seed() + i).branches(1).jag(0.3f));
            zap(to, 0.7f, p.seed() + i);
            i++;
        }
    }

    private static void spin(FxPayload p) {
        Entity e = entity(p.caster());
        float r = p.scale();
        int duration = Math.round(p.power());
        if (e == null) return;
        // a whirling ring of arcs around the caster for the whole spin
        for (int t = 0; t < duration; t += 2) {
            int tt = t;
            FxScheduler.after(t, () -> {
                if (!e.isAlive()) return;
                Vec3 c = e.position().add(0, 1, 0);
                double a0 = tt * 0.9;
                for (int k = 0; k < 2; k++) {
                    double a = a0 + k * Math.PI;
                    Vec3 tip = c.add(Math.cos(a) * r * 0.9, (k == 0 ? 0.3 : -0.3), Math.sin(a) * r * 0.9);
                    VfxManager.add(new LightningVfx(c, tip, 0.14f, Colors.argb(230, BOLT), 3, (long) (tt * 13 + k)).branches(2).jag(0.35f));
                }
                if (tt % 6 == 0) {
                    VfxManager.add(new ShockwaveVfx(e.position().add(0, 0.15, 0), new Vec3(0, 1, 0), r * 0.3f, r, 0.25f, Colors.argb(200, BOLT), 6).energy());
                }
            });
        }
        VfxManager.add(new DecalVfx(e.position().add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(200, BOLT), VfxTextures.SWIRL, duration)
                .spin(0.4f).energy().timing(0.1f, 0.2f));
        VfxManager.add(new FlashVfx(Vec3.ZERO, r * 0.8f, r * 1.4f, Colors.argb(140, BOLT), duration).follow(e, new Vec3(0, 1, 0)).peak(0.1f).energy());
    }

    private static void strikeWarn(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int delay = Math.round(p.power());
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), new Vec3(0, 1, 0), r, Colors.argb(230, BOLT), VfxTextures.RUNE_CIRCLE, delay + 4)
                .spin(0.12f).energy().timing(0.3f, 0.05f));
        VfxManager.add(new StormCloudVfx(() -> g.add(0, 14, 0), r * 0.9f, CLOUD, BOLT, delay + 30, p.seed()));
        for (int i = 0; i < 3; i++) {
            int t = i * 3;
            FxScheduler.after(t, () -> VfxManager.add(new LightningVfx(g.add(0, 14, 0), g.add(0, 14 - 3, 0).add((t - 3) * 0.5, 0, 0), 0.12f,
                    Colors.argb(200, BOLT), 3, t * 7L)));
        }
    }

    private static void strike(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Vec3 top = g.add(0, 14, 0);
        VfxManager.add(new LightningVfx(top, g, 0.9f, Colors.argb(255, BOLT), 12, p.seed()).branches(6).jag(0.18f));
        VfxManager.add(new LightningVfx(top, g, 0.45f, Colors.argb(255, CORE), 8, p.seed() + 1).branches(2).jag(0.12f));
        Blast.explode(g, r, PALETTE, p.seed(), 1, VfxTextures.SPARK);
        RandomSource rnd = RandomSource.create(p.seed());
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 end = g.add(Math.cos(a) * r * 1.3, 0.1, Math.sin(a) * r * 1.3);
            VfxManager.add(new LightningVfx(g.add(0, 0.2, 0), end, 0.15f, Colors.argb(230, BOLT), 6, p.seed() + i * 11).branches(1).jag(0.35f));
        }
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceTo(g) < 30) ScreenFx.flash(CORE, 0.25f, 6);
    }

    private static void wrath(FxPayload p) {
        // the storm stays where it was called down
        Vec3 c = p.pos();
        float r = p.scale();
        int duration = Math.round(p.power());
        Vec3 cloud = c.add(0, 12, 0);
        VfxManager.add(new StormCloudVfx(() -> cloud, r, CLOUD, BOLT, duration + 20, p.seed()));
        VfxManager.add(new DecalVfx(c.add(0, 0.04, 0), new Vec3(0, 1, 0), r, Colors.argb(150, BOLT), VfxTextures.RUNE_CIRCLE, duration)
                .spin(0.03f).energy().timing(0.1f, 0.1f));
        ScreenFx.zoneVignette(STORM, 0.4f, duration, c, r + 4);
    }

    private static void bolt(FxPayload p) {
        Vec3 g = p.pos();
        Vec3 from = p.points().isEmpty() ? g.add(0, 12, 0) : p.points().get(0);
        RandomSource rnd = RandomSource.create(p.seed());
        Vec3 start = from.add(rnd.nextGaussian() * 3, 0, rnd.nextGaussian() * 3);
        VfxManager.add(new LightningVfx(start, g, 0.45f, Colors.argb(255, BOLT), 8, p.seed()).branches(4));
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), new Vec3(0, 1, 0), 0.2f, 2.4f, 0.3f, Colors.argb(220, CORE), 8).energy());
        GroundShatter.cracks(FrostrendFx.ground(g), 1.3f, com.fantasyweapons.client.vfx.GroundMaterial.at(g), 40);
        zap(g.add(0, 0.5, 0), 1.3f, p.seed());
        Vec3 prev = g.add(0, 1, 0);
        int i = 0;
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            if (i > 0) VfxManager.add(new LightningVfx(prev, chest(e), 0.18f, Colors.argb(230, BOLT), 6, p.seed() + i).branches(1));
            prev = chest(e);
            i++;
        }
        CameraShake.add(g, 0.2f, 16);
    }

    private static void wrathEnd(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        Vec3 top = p.points().isEmpty() ? g.add(0, 12, 0) : p.points().get(0);
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            Vec3 at = g.add(Math.cos(a) * r * 0.5, 0, Math.sin(a) * r * 0.5);
            VfxManager.add(new LightningVfx(top, at, 0.6f, Colors.argb(255, BOLT), 12, p.seed() + i).branches(4));
        }
        VfxManager.add(new LightningVfx(top, g, 1.4f, Colors.argb(255, CORE), 14, p.seed() + 99).branches(8));
        Blast.explode(g, r * 0.8f, PALETTE, p.seed(), 2, VfxTextures.SPARK);
        ScreenFx.flash(CORE, 0.35f, 10);
    }
}
