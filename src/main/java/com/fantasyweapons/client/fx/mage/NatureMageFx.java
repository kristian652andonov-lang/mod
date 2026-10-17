package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.fx.ScreenFx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FlowerVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.SpikeVfx;
import com.fantasyweapons.client.vfx.effects.TetherVfx;
import com.fantasyweapons.client.vfx.effects.VineVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.fantasyweapons.client.fx.mage.MageFx.NATURE;
import static com.fantasyweapons.client.fx.mage.MageFx.UP;
import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/** Client visuals for the nature mage: the Thornwake's bloom, grove and Worldtree; the Verdant Almanac's vines, pollen and spring. */
final class NatureMageFx {
    static final int BARK = 0x5A3E22, BLOSSOM = 0xFFB8E0, POLLEN = 0xFFE27A, LEAF = 0x4CBF3C;

    private NatureMageFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.THORN_LIFEBLOOM, NatureMageFx::lifebloom);
        FxDispatcher.register(FxIds.THORN_GROVE, NatureMageFx::grove);
        FxDispatcher.register(FxIds.THORN_WORLDTREE, NatureMageFx::worldtree);
        FxDispatcher.register(FxIds.VERDANT_VINES, NatureMageFx::vines);
        FxDispatcher.register(FxIds.VERDANT_POLLEN, NatureMageFx::pollen);
        FxDispatcher.register(FxIds.VERDANT_SPRING, NatureMageFx::spring);
    }

    /** A curving stem from {@code base}: {@code len} long, leaning out along {@code out} and curling. */
    static Vec3[] stem(Vec3 base, Vec3 out, double len, double lean, double curl, long seed, int n) {
        Vec3[] pts = new Vec3[n];
        Vec3 side = out.cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i < n; i++) {
            double u = i / (double) (n - 1);
            pts[i] = base.add(out.scale(u * lean * len)).add(0, u * len * (1 - lean * 0.5), 0).add(side.scale(Math.sin(u * 3 + seed % 7) * curl * u));
        }
        return pts;
    }

    /** A seed arcing from the caster's focus to the ground, bursting into a great flower ringed with sprouting vines. */
    private static void lifebloom(FxPayload p) {
        Vec3 g = p.pos(), from = p.points().isEmpty() ? g.add(0, 2, 0) : p.points().get(0);
        float r = p.scale();
        int life = Math.round(p.power());
        long seed = p.seed();
        int fly = 8;
        OrbVfx seedOrb = VfxManager.add(new OrbVfx(MageFx.arcTo(from, () -> g.add(0, 0.2, 0), fly, 2.2), 0.25f, NATURE.main(), NATURE.core(), fly + 1)
                .sprite(VfxTextures.LEAF, NATURE.core(), 0.4f));
        VfxManager.add(new FollowTrailVfx(seedOrb::now, 0.25f, Colors.argb(220, NATURE.main()), Colors.argb(0, NATURE.deep()), 10, fly + 12).texture(VfxTextures.STREAK, true));
        FxScheduler.after(fly, () -> {
            VfxManager.add(new FlowerVfx(g.add(0, 0.08, 0), 1.8f, BLOSSOM, 0xFFF4FA, POLLEN, life - fly).timing(14, 16));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + hash(seed, i);
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                VfxManager.add(new VineVfx(stem(g.add(out.scale(0.6)), out, 1.2 + hash(seed, 10 + i), 0.7, 0.4, seed + i, 7), 0.09f, 0x4F6A2A, LEAF, 12,
                        life - fly, seed + i).flower(i % 2 == 0 ? BLOSSOM : POLLEN));
            }
            VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(150, NATURE.main()), VfxTextures.RUNE_CIRCLE, life - fly).spin(0.015f).energy().timing(0.1f, 0.15f));
            VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r * 0.8f, Colors.argb(200, 0x3A6A24), VfxTextures.TURF, life - fly).translucent().round().timing(0.15f, 0.2f));
            VfxManager.add(new SpellVfx(life - fly, (ctx, t, ticks) -> {
                float k = SpellVfx.envelope(t, 0.15f, 0.12f);
                var glow = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < 16; i++) {
                    float ph = hash(seed, 50 + i);
                    float cyc = (ticks * 0.02f + ph) % 1f;
                    double a = ph * 6.283 + ticks * 0.03, rr = r * 0.8 * hash(seed, 70 + i);
                    ctx.billboard(glow, g.add(Math.cos(a) * rr, 0.4 + cyc * 3, Math.sin(a) * rr), 0.2f, 0, Colors.alpha(k * (float) Math.sin(cyc * Math.PI), POLLEN));
                }
            }));
            VfxManager.add(new FlashVfx(g.add(0, 0.6, 0), 0.3f, 2.6f, Colors.argb(200, NATURE.main()), 10).energy());
        });
    }

    /** A thicket springing up: thorny stems and roots bursting out of the ground and curling inwards. */
    private static void grove(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + hash(seed, i) * 0.5;
            double rr = r * (0.3 + 0.7 * hash(seed, 20 + i));
            Vec3 base = MageFx.ground(g.add(Math.cos(a) * rr, 0, Math.sin(a) * rr));
            Vec3 inward = new Vec3(-Math.cos(a), 0, -Math.sin(a));
            VfxManager.add(new VineVfx(stem(base, inward, 1.8 + 1.2 * hash(seed, 40 + i), 0.6, 0.5, seed + i, 8), 0.14f, BARK, LEAF, 6 + i % 4, life, seed * 3 + i));
        }
        SpikeVfx thorns = new SpikeVfx(SpikeVfx.Style.THORN, 0x3A5A22, 0x9ACC5A, life).timing(5, 10);
        for (int i = 0; i < 10; i++) {
            double a = hash(seed, 60 + i) * 6.283, rr = r * Math.sqrt(hash(seed, 80 + i));
            Vec3 base = MageFx.ground(g.add(Math.cos(a) * rr, 0, Math.sin(a) * rr));
            thorns.add(base, UP.add((hash(seed, 90 + i) - 0.5) * 0.6, 0, (hash(seed, 95 + i) - 0.5) * 0.6).normalize(), 0.9f + hash(seed, 99 + i), 0.18f, i % 4);
        }
        VfxManager.add(thorns);
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(210, 0x3A5A24), VfxTextures.TURF, life).translucent().round().timing(0.1f, 0.2f));
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r * 0.9f, Colors.argb(180, BARK), VfxTextures.SOIL, life).translucent().round().timing(0.05f, 0.2f));
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.5, 0), UP, 1.6f, 0.2f, 24, 0.2f, Colors.argb(240, LEAF), Colors.argb(0, NATURE.deep()), 26, seed)
                .texture(VfxTextures.LEAF_SOFT, false).physics(0.01f, 0.93f));
        CameraShake.add(g, 0.3f, 14);
        MageFx.hit(p, NATURE.main());
    }

    /** The Worldtree: a trunk twisting up out of the ground into spreading boughs, a canopy of leaves and falling light. */
    private static void worldtree(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 20;
        long seed = p.seed();
        double height = 9;
        // trunk: a few thick stems braided together
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + hash(seed, i);
            Vec3 base = g.add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5);
            Vec3[] pts = new Vec3[12];
            for (int k = 0; k < 12; k++) {
                double u = k / 11.0;
                double tw = a + u * 2.4;
                double rr = 0.55 * (1 - u * 0.6);
                pts[k] = g.add(Math.cos(tw) * rr, u * height, Math.sin(tw) * rr);
            }
            pts[0] = base;
            VfxManager.add(new VineVfx(pts, 0.55f, BARK, LEAF, 20, life, seed + i));
        }
        // boughs spreading from the crown
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7 + hash(seed, 10 + i);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 crown = g.add(0, height * (0.7 + 0.2 * hash(seed, 20 + i)), 0);
            Vec3[] pts = new Vec3[8];
            for (int k = 0; k < 8; k++) {
                double u = k / 7.0;
                pts[k] = crown.add(out.scale(u * (3.5 + 1.5 * hash(seed, 30 + i)))).add(0, Math.sin(u * Math.PI) * 1.2 - u * 0.4, 0);
            }
            int delay = 14;
            VfxManager.add(new VineVfx(pts, 0.25f, BARK, LEAF, 10 + delay, life, seed * 7 + i).flower(i % 2 == 0 ? BLOSSOM : POLLEN));
        }
        // roots spreading across the ground
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + hash(seed, 40 + i);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3[] pts = new Vec3[7];
            for (int k = 0; k < 7; k++) {
                double u = k / 6.0;
                pts[k] = MageFx.ground(g.add(out.scale(0.4 + u * 3.2))).add(0, 0.12 * (1 - u), 0);
            }
            VfxManager.add(new VineVfx(pts, 0.28f, BARK, LEAF, 14, life, seed * 11 + i));
        }
        VfxManager.add(new DecalVfx(g.add(0, 0.04, 0), UP, r, Colors.argb(140, NATURE.main()), VfxTextures.RUNE_CIRCLE, life).spin(0.006f).energy().timing(0.06f, 0.1f).satellites(6));
        VfxManager.add(new DecalVfx(g.add(0, 0.03, 0), UP, 4.5f, Colors.argb(220, 0x3A5A24), VfxTextures.TURF, life).translucent().round().timing(0.1f, 0.15f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float grow = Math.min(1, ticks / 22f);
            float k = SpellVfx.envelope(t, 0.05f, 0.08f) * grow;
            Vec3 crown = g.add(0, height * 0.85, 0);
            var leaf = ctx.translucent(VfxTextures.LEAF_SOFT);
            var glow = ctx.additive(VfxTextures.GLOW);
            // the canopy: clusters of leaves in a broad dome, stirring in the wind
            for (int i = 0; i < 90; i++) {
                float ph = hash(seed, 100 + i);
                double a = ph * 6.283;
                double el = hash(seed, 300 + i) * 1.2;
                double rr = (2.5 + 2.8 * hash(seed, 500 + i)) * grow;
                Vec3 q = crown.add(Math.cos(a) * rr * Math.cos(el * 0.6), Math.sin(el) * 2.2 * grow - 0.5, Math.sin(a) * rr * Math.cos(el * 0.6));
                q = q.add(Math.sin(ticks * 0.05 + ph * 9) * 0.12, 0, Math.cos(ticks * 0.04 + ph * 7) * 0.12);
                int col = Colors.lerpRgb(0x2E8A2A, 0x8ADF5A, hash(seed, 700 + i));
                ctx.billboard(leaf, q, 1.3f + 0.7f * ph, ph * 6 + ticks * 0.01f, Colors.alpha(k * 0.95f, col));
            }
            // light filtering down, and leaves drifting to the ground across the whole blessing
            for (int i = 0; i < 26; i++) {
                float ph = hash(seed, 900 + i);
                float cyc = (ticks * 0.01f + ph) % 1f;
                double a = ph * 6.283, rr = r * Math.sqrt(hash(seed, 950 + i));
                Vec3 q = g.add(Math.cos(a) * rr + Math.sin(cyc * 8) * 0.4, height * (1 - cyc), Math.sin(a) * rr);
                ctx.billboard(leaf, q, 0.35f, cyc * 12 + ph, Colors.alpha(k * (float) Math.sin(cyc * Math.PI), 0x9AD84A));
                ctx.billboard(glow, q.add(0, 0.6, 0), 0.25f, 0, Colors.alpha(k * 0.6f * (float) Math.sin(cyc * Math.PI), POLLEN));
            }
            ctx.billboard(glow, crown, 9f * grow, 0, Colors.alpha(k * 0.18f, POLLEN));
        }));
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.5, 0), UP, 1.4f, 0.25f, 30, 0.25f, Colors.argb(240, LEAF), Colors.argb(0, NATURE.deep()), 30, seed)
                .texture(VfxTextures.LEAF_SOFT, false).physics(0.005f, 0.94f));
        ScreenFx.zoneVignette(NATURE.main(), 0.14f, life, g, r);
        CameraShake.add(g, 0.5f, 20);
    }

    /** A living vine from the book to the ally, life flowing along it. */
    private static void vines(FxPayload p) {
        Entity caster = MageFx.entity(p.caster());
        Entity ally = (p.entities().length == 0) ? null : MageFx.entity(p.entities()[0]);
        if (ally == null) return;
        int life = Math.max(10, Math.round(p.power()));
        java.util.function.Supplier<Vec3> from = caster == null || caster == ally ? () -> ally.position().add(0, ally.getBbHeight() + 1.2, 0) : MageFx.focus(caster);
        java.util.function.Supplier<Vec3> to = MageFx.chest(ally);
        VfxManager.add(new TetherVfx(from, to, 0.22f, 0x6A8A3A, NATURE.core(), life).texture(VfxTextures.BARK, true).motes(VfxTextures.LEAF_SOFT, 7, 1.2f).moteLook(0x6FC24A, 1.5f).shape(0.8f, 0.35f).grow(8));
        VfxManager.add(new TetherVfx(from, to, 0.12f, NATURE.main(), NATURE.core(), life).motes(VfxTextures.GLOW, 5, 1.6f).shape(0.8f, 0.35f).grow(8));
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, ally.getBbHeight() * 1.4f, Colors.argb(180, NATURE.main()), 10).follow(ally, new Vec3(0, ally.getBbHeight() * 0.5, 0)).energy());
    }

    /** A golden cloud of pollen drifting and swirling low over the ground, with petals in it. */
    private static void pollen(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        int life = Math.round(p.power()) + 10;
        long seed = p.seed();
        VfxManager.add(new DecalVfx(g.add(0, 0.05, 0), UP, r, Colors.argb(120, POLLEN), VfxTextures.GLOW, life).timing(0.15f, 0.15f));
        VfxManager.add(new SpellVfx(life, (ctx, t, ticks) -> {
            float k = SpellVfx.envelope(t, 0.12f, 0.15f);
            var mist = ctx.translucent(VfxTextures.MIST);
            var glow = ctx.additive(VfxTextures.GLOW);
            var petal = ctx.translucent(VfxTextures.PETAL_SOFT);
            for (int i = 0; i < 18; i++) {
                float ph = hash(seed, i);
                double a = ph * 6.283 + ticks * 0.008 * (i % 2 == 0 ? 1 : -1), rr = r * 0.75 * Math.sqrt(hash(seed, 30 + i));
                Vec3 q = g.add(Math.cos(a) * rr, 0.8 + 1.2 * hash(seed, 60 + i) + 0.2 * Math.sin(ticks * 0.04 + ph * 7), Math.sin(a) * rr);
                ctx.billboard(mist, q, 3f + 1.5f * ph, ph * 6 + ticks * 0.01f, Colors.alpha(k * 0.3f, 0xF4D86A));
            }
            for (int i = 0; i < 60; i++) {
                float ph = hash(seed, 100 + i);
                double a = ph * 6.283 + ticks * (0.02 + 0.02 * ph), rr = r * Math.sqrt(hash(seed, 200 + i));
                Vec3 q = g.add(Math.cos(a) * rr, 0.3 + 2.4 * hash(seed, 300 + i) + 0.3 * Math.sin(ticks * 0.06 + ph * 11), Math.sin(a) * rr);
                ctx.billboard(glow, q, 0.12f + 0.1f * ph, 0, Colors.alpha(k * (0.6f + 0.4f * (float) Math.sin(ticks * 0.2 + ph * 20)), POLLEN));
                if (i % 5 == 0) ctx.billboard(petal, q, 0.3f, ticks * 0.05f + ph * 6, Colors.alpha(k * 0.8f, BLOSSOM));
            }
        }));
        MageFx.hit(p, POLLEN);
    }

    /** Spring rolling out over the land: a green wave, flowers opening in rings, petals raining on the blessed. */
    private static void spring(FxPayload p) {
        Vec3 g = p.pos();
        float r = p.scale();
        long seed = p.seed();
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.07, 0), UP, 0.5f, r, 0.6f, Colors.argb(200, NATURE.main()), 24).energy());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.08, 0), UP, 0.3f, r * 0.7f, 0.4f, Colors.argb(200, BLOSSOM), 20).energy());
        for (int i = 0; i < 18; i++) {
            double a = hash(seed, i) * 6.283, rr = r * (0.15 + 0.85 * Math.sqrt(hash(seed, 30 + i)));
            Vec3 spot = MageFx.ground(g.add(Math.cos(a) * rr, 0, Math.sin(a) * rr));
            int delay = (int) (rr / r * 18);
            int col = i % 3 == 0 ? BLOSSOM : i % 3 == 1 ? POLLEN : 0xC8A0FF;
            FxScheduler.after(delay, () -> VfxManager.add(new FlowerVfx(spot.add(0, 0.06, 0), 0.6f + 0.4f * hash(seed, 60 + (int) rr), col, 0xFFFFFF, POLLEN, 70).timing(8, 16)));
        }
        for (int id : p.entities()) {
            Entity e = MageFx.entity(id);
            if (e == null) continue;
            Vec3 top = e.position().add(0, e.getBbHeight() + 0.3, 0);
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.6f, Colors.argb(200, NATURE.main()), 12).follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
            VfxManager.add(new ShardBurstVfx(top.add(0, 1.5, 0), UP.scale(-1), 1.2f, 0.06f, 16, 0.25f, Colors.argb(255, BLOSSOM), Colors.argb(0, 0xFFE0F0), 36, seed + id)
                    .texture(VfxTextures.PETAL_SOFT, false).physics(0.004f, 0.96f));
        }
        VfxManager.add(new FlashVfx(g.add(0, 1, 0), 0.5f, 5f, Colors.argb(200, NATURE.core()), 14).energy());
        CameraShake.add(g, 0.3f, r * 2);
    }
}
