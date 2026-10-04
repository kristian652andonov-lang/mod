package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the custom status effects on every nearby entity that carries them (synced attachment): flames for Solar
 * Burn, an ice shell for Frozen, spores for poison, a blood aura for Berserker, sigils for marks, roots for Rooted...
 * No vanilla particles or potion swirls.
 */
public final class StatusVisuals {
    private static final double RANGE = 48;

    private StatusVisuals() {
    }

    public static void render(VfxContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity e) || !e.isAlive()) continue;
            StatusEffects effects = e.getExistingDataOrNull(ModAttachments.STATUS);
            if (effects == null || effects.isEmpty()) continue;
            if (e.distanceToSqr(ctx.cam) > RANGE * RANGE) continue;
            boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 feet = e.getPosition(ctx.partial);
            AABB box = e.getBoundingBox().move(feet.subtract(e.position()));
            float t = (e.tickCount + ctx.partial);
            long seed = e.getId() * 341873128712L;
            for (StatusEffects.Instance inst : effects.all()) {
                float fadeOut = Math.min(1, inst.duration / 10f);
                try {
                    draw(ctx, e, inst, box, feet, t, seed, fadeOut, self);
                } catch (RuntimeException ignored) {
                    // never let a status visual break the frame
                }
            }
        }
    }

    private static void draw(VfxContext ctx, LivingEntity e, StatusEffects.Instance inst, AABB box, Vec3 feet, float t, long seed,
                             float fade, boolean self) {
        double w = Math.max(box.getXsize(), box.getZsize());
        double h = box.getYsize();
        Vec3 center = box.getCenter();
        Vec3 top = new Vec3(center.x, box.maxY, center.z);
        switch (inst.type) {
            case SOLAR_BURN, INFERNO_OVERHEAT, SEARED -> {
                if (self && inst.type != StatusType.INFERNO_OVERHEAT) return;
                boolean solar = inst.type == StatusType.SOLAR_BURN;
                int n = solar ? 7 : Math.min(9, 2 + inst.stacks + (inst.type == StatusType.SEARED ? 2 : 0));
                int col = solar ? 0xFFB627 : inst.type == StatusType.SEARED ? 0xFF3A10 : 0xFF5A1F;
                var flame = ctx.additive(VfxTextures.FLAME);
                for (int i = 0; i < n; i++) {
                    float ph = ((seed >> (i * 3)) & 63) / 63f;
                    float cyc = (t * 0.06f + ph) % 1f;
                    double a = ph * 6.283 + t * 0.03;
                    Vec3 p = new Vec3(center.x + Math.cos(a) * w * 0.45, box.minY + h * (0.1 + 0.8 * cyc), center.z + Math.sin(a) * w * 0.45);
                    float s = (float) (0.35 + w * 0.35) * (1 - cyc * 0.6f);
                    ctx.stretched(flame, p, new Vec3(0, 1, 0), s * 1.6f, s, Colors.alpha(fade * (1 - cyc) * 0.85f, Colors.lerpRgb(col, 0xFFF0B0, 1 - cyc)));
                }
                if (!self) ctx.billboard(ctx.additive(VfxTextures.GLOW), center, (float) (w + h) * 0.9f, 0, Colors.alpha(fade * 0.22f, col));
            }
            case FROSTBITE -> {
                var mist = ctx.additive(VfxTextures.MIST);
                int n = 2 + Math.min(4, inst.stacks);
                for (int i = 0; i < n; i++) {
                    double a = i * 6.283 / n + t * 0.02;
                    Vec3 p = new Vec3(feet.x + Math.cos(a) * w * 0.5, feet.y + 0.15, feet.z + Math.sin(a) * w * 0.5);
                    ctx.billboard(mist, p, (float) (0.6 + w * 0.5), t * 0.01f + i, Colors.alpha(fade * 0.35f, 0xBFEFFF));
                }
                var spark = ctx.additive(VfxTextures.SPARK);
                for (int i = 0; i < inst.stacks + 1; i++) {
                    float ph = ((seed >> (i * 5)) & 63) / 63f;
                    float cyc = (t * 0.04f + ph) % 1f;
                    double a = ph * 6.283;
                    Vec3 p = new Vec3(center.x + Math.cos(a) * w * 0.6, box.minY + h * cyc, center.z + Math.sin(a) * w * 0.6);
                    ctx.billboard(spark, p, 0.15f, 0, Colors.alpha(fade * (float) Math.sin(cyc * Math.PI), 0xE8FBFF));
                }
            }
            case FROZEN -> {
                if (self) return;
                // translucent ice shell (hexagonal prism) with a few jutting crystals
                var ice = ctx.translucent(VfxTextures.ICE);
                double r = w * 0.75 + 0.1;
                Vec3 base = new Vec3(center.x, box.minY - 0.02, center.z);
                Vec3 lid = new Vec3(center.x, box.maxY + 0.1, center.z);
                Vec3[] lo = new Vec3[6], hi = new Vec3[6];
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + (seed & 7);
                    double rr = r * (0.9 + 0.2 * (((seed >> i) & 3) / 3.0));
                    lo[i] = base.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                    hi[i] = lid.add(Math.cos(a) * rr * 0.85, -0.05 * (i % 2), Math.sin(a) * rr * 0.85);
                }
                int c = Colors.alpha(0.55f * fade, 0xCFF4FF);
                int ct = Colors.alpha(0.35f * fade, 0xFFFFFF);
                for (int i = 0; i < 6; i++) {
                    int j = (i + 1) % 6;
                    ctx.quad(ice, lo[i], lo[j], hi[j], hi[i], 0, 0, 1, 1, c, c, ct, ct);
                    ctx.quad(ice, hi[i], hi[j], lid, lid, ct);
                }
                var edge = ctx.additive(VfxTextures.LIGHTNING);
                for (int i = 0; i < 6; i += 2) ctx.beam(edge, lo[i], hi[i], 0.08f, Colors.alpha(0.5f * fade, 0xE8FBFF), 0, 1);
            }
            case BERSERKER -> {
                var flame = ctx.additive(VfxTextures.FLAME);
                int n = self ? 3 : 9;
                for (int i = 0; i < n; i++) {
                    float ph = ((seed >> (i * 2)) & 63) / 63f;
                    float cyc = (t * 0.08f + ph) % 1f;
                    double a = ph * 6.283 + t * 0.05;
                    double rr = self ? 0.9 : w * 0.6;
                    Vec3 p = new Vec3(center.x + Math.cos(a) * rr, box.minY + h * (0.05 + 0.9 * cyc), center.z + Math.sin(a) * rr);
                    float s = (float) (0.45 + w * 0.3) * (1 - cyc * 0.5f);
                    ctx.stretched(flame, p, new Vec3(0, 1, 0), s * 1.8f, s * 0.8f, Colors.alpha(fade * (1 - cyc) * 0.7f, Colors.lerpRgb(0xE0213A, 0xFF7A8A, cyc)));
                }
                if (!self) {
                    ctx.billboard(ctx.additive(VfxTextures.GLOW), center, (float) (w + h) * 1.1f, 0, Colors.alpha(fade * 0.25f, 0xE0213A));
                    ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), feet.add(0, 0.04, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                            (float) (w + 0.6), t * 0.05f, Colors.alpha(fade * 0.5f, 0xE0213A));
                }
            }
            case SOUL_DRAIN -> {
                var soul = ctx.additive(VfxTextures.SOUL);
                for (int i = 0; i < 3; i++) {
                    float cyc = (t * 0.03f + i / 3f) % 1f;
                    double a = i * 2.1 + t * 0.07;
                    Vec3 p = new Vec3(center.x + Math.cos(a) * w * 0.7, box.minY + h * (0.2 + 0.9 * cyc), center.z + Math.sin(a) * w * 0.7);
                    ctx.billboard(soul, p, 0.35f, 0, Colors.alpha(fade * (float) Math.sin(cyc * Math.PI), 0x8FB8FF));
                }
            }
            case NATURE_POISON -> {
                var glow = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < 4 + inst.stacks * 2; i++) {
                    float ph = ((seed >> (i * 3 % 60)) & 63) / 63f;
                    float cyc = (t * 0.03f + ph) % 1f;
                    double a = ph * 6.283 + cyc * 2;
                    Vec3 p = new Vec3(center.x + Math.cos(a) * w * 0.6, box.minY + h * (0.2 + cyc), center.z + Math.sin(a) * w * 0.6);
                    ctx.billboard(glow, p, 0.16f, 0, Colors.alpha(fade * (float) Math.sin(cyc * Math.PI) * 0.9f, 0x8CFF5A));
                }
                ctx.billboard(ctx.translucent(VfxTextures.MIST), feet.add(0, 0.2, 0), (float) (w + 0.8), t * 0.01f, Colors.alpha(fade * 0.25f, 0x4CAF50));
            }
            case ECLIPSE_LIGHT, ECLIPSE_DARKNESS -> {
                boolean light = inst.type == StatusType.ECLIPSE_LIGHT;
                Vec3 p = top.add(light ? -0.25 : 0.25, 0.45, 0);
                ctx.billboard(ctx.additive(VfxTextures.GLOW), p, 0.6f, 0, Colors.alpha(fade * 0.6f, light ? 0xFFE7A0 : 0x8B3DFF));
                ctx.billboard(ctx.additive(light ? VfxTextures.STAR : VfxTextures.SWIRL), p, 0.35f + 0.05f * inst.stacks, t * (light ? 0.05f : -0.08f), Colors.alpha(fade, light ? 0xFFF6D0 : 0xC9A2FF));
            }
            case GRAVITY_BOUND -> {
                Vec3 mid = center.add(0, -h * 0.1, 0);
                double r = w * 0.8 + 0.2;
                ctx.ring(ctx.additive(VfxTextures.RING), mid, new Vec3(Math.cos(t * 0.1), 0.25, Math.sin(t * 0.1)).normalize(),
                        new Vec3(-Math.sin(t * 0.1), 0, Math.cos(t * 0.1)), (float) r * 0.8f, (float) r * 1.1f, 24, Colors.alpha(fade * 0.7f, 0x8A6CFF));
            }
            case ROOTED -> {
                var bark = ctx.translucent(VfxTextures.BARK);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + (seed & 3);
                    Vec3[] pts = new Vec3[6];
                    float[] ws = new float[6];
                    int[] cs = new int[6];
                    for (int k = 0; k < 6; k++) {
                        double kt = k / 5.0;
                        double rr = w * 0.6 * (1 - kt * 0.4);
                        double aa = a + kt * 1.8;
                        pts[k] = new Vec3(center.x + Math.cos(aa) * rr, feet.y + h * 0.55 * kt, center.z + Math.sin(aa) * rr);
                        ws[k] = 0.2f * (1 - (float) kt * 0.8f);
                        cs[k] = Colors.alpha(fade, 0x5A3E22);
                    }
                    ctx.ribbon(bark, pts, ws, cs, 0, 0.3f);
                }
            }
            case STAGGERED -> {
                // stone chips circling the head, dust shaken loose at the feet
                var rock = ctx.translucent(VfxTextures.ROCK);
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.18 + i * 2.094;
                    Vec3 p = top.add(Math.cos(a) * (w * 0.5 + 0.2), 0.25 + Math.sin(t * 0.3 + i) * 0.05, Math.sin(a) * (w * 0.5 + 0.2));
                    ctx.billboard(rock, p, 0.2f, (float) a, Colors.alpha(fade, 0x8A7356));
                }
                ctx.billboard(ctx.translucent(VfxTextures.MIST), feet.add(0, 0.15, 0), (float) (w + 0.6), t * 0.01f, Colors.alpha(fade * 0.3f, 0xA08A6C));
            }
            case VOID_MARK -> {
                var vc = ctx.additive(VfxTextures.SHARD);
                for (int i = 0; i < inst.stacks; i++) {
                    double a = t * 0.06 + i * 6.283 / inst.stacks;
                    Vec3 p = top.add(Math.cos(a) * 0.35, 0.35, Math.sin(a) * 0.35);
                    ctx.billboard(vc, p, 0.28f, 0, Colors.alpha(fade, 0xC99BFF));
                }
            }
        }
    }
}
