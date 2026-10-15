package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.status.StatusEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.fantasyweapons.client.fx.mage.MageFx.hash;

/**
 * How the mage's statuses show on whoever bears them: a shimmer of shield, a bubble, shards circling, a halo, wings of
 * light, flowers spiralling up, curses coiling, plague mist and rot. Seen in first person, only what keeps clear of
 * the view is drawn.
 */
public final class MageStatusVisuals {
    private MageStatusVisuals() {
    }

    public static void draw(VfxContext ctx, LivingEntity e, StatusEffects.Instance inst, AABB box, Vec3 feet, float t, long seed, float fade, boolean self) {
        double w = Math.max(box.getXsize(), box.getZsize()), h = box.getYsize();
        Vec3 c = box.getCenter(), top = new Vec3(c.x, box.maxY, c.z);
        switch (inst.type) {
            case KINDLED -> {
                if (self) return;
                var flame = ctx.additive(VfxTextures.FLAME);
                for (int i = 0; i < inst.stacks; i++) {
                    double a = t * 0.08 + i * 6.283 / inst.stacks;
                    Vec3 p = top.add(Math.cos(a) * 0.4, 0.35 + 0.05 * Math.sin(t * 0.3 + i), Math.sin(a) * 0.4);
                    ctx.stretched(flame, p, MageFx.UP, 0.35f, 0.2f, Colors.alpha(fade, i == inst.stacks - 1 ? 0xFFF0B0 : 0xFF6A1F));
                }
            }
            case SOAKED -> {
                if (self) return; // drops falling past your own eyes would fill the view
                var drop = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < 5; i++) {
                    float ph = hash(seed, i);
                    float cyc = (t * 0.05f + ph) % 1f;
                    double a = ph * 6.283;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * w * 0.45, box.maxY - cyc * h, c.z + Math.sin(a) * w * 0.45);
                    ctx.billboard(drop, p, 0.1f, 0, Colors.alpha(fade * 0.8f * (1 - cyc), 0xBFE8FF));
                }
            }
            case BARRIER -> {
                if (self) return;
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.15);
                ctx.sphere(ctx.additive(VfxTextures.HEX), c, (float) (Math.max(w, h) * 0.62), 10, 16, Colors.alpha(fade * (0.12f + 0.06f * pulse), 0xBFE6FF), true);
            }
            case GLACIAL_WARD -> {
                if (self) return;
                var shard = ctx.translucent(VfxTextures.SHARD);
                for (int i = 0; i < 6; i++) {
                    double a = t * 0.04 + i * Math.PI / 3;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * (w * 0.75 + 0.2), box.minY + h * (0.25 + 0.5 * hash(seed, i)), c.z + Math.sin(a) * (w * 0.75 + 0.2));
                    ctx.billboard(shard, p, 0.45f, (float) a, Colors.alpha(fade * 0.85f, 0xDDF8FF));
                }
                ctx.billboard(ctx.translucent(VfxTextures.MIST), feet.add(0, 0.2, 0), (float) (w + 1), t * 0.01f, Colors.alpha(fade * 0.35f, 0xCFF4FF));
            }
            case BUBBLE -> {
                if (self) return;
                float r = Math.max(0.75f, (float) (Math.max(w, h) * 0.72));
                float wob = 1 + 0.03f * (float) Math.sin(t * 0.25);
                ctx.sphere(ctx.translucent(VfxTextures.WHITE), c, r * wob, 12, 18, Colors.alpha(fade * 0.12f, 0x6FD0FF), true);
                // the bubble's skin catching the light at its edge, so it reads from afar
                ctx.ring(ctx.additive(VfxTextures.RING), c, ctx.camLeft, ctx.camUp, r * wob * 0.9f, r * wob * 1.02f, 40, Colors.alpha(fade * 0.55f, 0xBFEFFF));
                ctx.sphere(ctx.additive(VfxTextures.GLOW), c, r * wob, 10, 16, Colors.alpha(fade * 0.18f, 0xCDEFFF), true);
                ctx.billboard(ctx.additive(VfxTextures.FLASH), c.add(-r * 0.4, r * 0.45, 0), r * 0.4f, 0, Colors.alpha(fade * 0.4f, 0xFFFFFF));
                var bub = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < 6; i++) {
                    float ph = hash(seed, 20 + i);
                    float cyc = (t * 0.03f + ph) % 1f;
                    double a = ph * 6.283;
                    ctx.billboard(bub, c.add(Math.cos(a) * r * 0.6, -r * 0.8 + cyc * r * 1.6, Math.sin(a) * r * 0.6), 0.1f, 0, Colors.alpha(fade * 0.8f, 0xE0F8FF));
                }
            }
            case FROST_WARD, PERMAFROST -> {
                boolean strong = inst.type == com.fantasyweapons.status.StatusType.FROST_WARD;
                var spark = ctx.additive(VfxTextures.SNOWFLAKE);
                int n = strong ? 6 : 3;
                for (int i = 0; i < n; i++) {
                    float ph = hash(seed, 40 + i);
                    float cyc = (t * 0.02f + ph) % 1f;
                    double a = ph * 6.283 + t * 0.02;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * w * 0.7, box.maxY + 0.2 - cyc * (h + 0.2), c.z + Math.sin(a) * w * 0.7);
                    ctx.billboard(spark, p, 0.16f, t * 0.05f + i, Colors.alpha(fade * (float) Math.sin(cyc * Math.PI) * (strong ? 0.9f : 0.5f), 0xE8FBFF));
                }
                if (strong && !self) ctx.disc(ctx.additive(VfxTextures.SNOWFLAKE), feet.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) (w + 0.6), t * 0.02f,
                        Colors.alpha(fade * 0.35f, 0xCFF4FF));
            }
            case RIME_ARMOR -> {
                if (self) return;
                var shard = ctx.translucent(VfxTextures.ICE);
                for (int i = 0; i < inst.stacks; i++) {
                    double a = i * 2.094 + 0.6;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * w * 0.55, box.minY + h * (0.45 + 0.12 * i), c.z + Math.sin(a) * w * 0.55);
                    ctx.billboard(shard, p, 0.5f, (float) a, Colors.alpha(fade * 0.6f, 0xA8E0FF));
                }
            }
            case SHATTERGUARD -> {
                var shard = ctx.additive(VfxTextures.SHARD);
                double r = self ? 1.1 : w * 0.8 + 0.4;
                for (int i = 0; i < inst.stacks; i++) {
                    double a = t * 0.12 + i * 6.283 / Math.max(1, inst.stacks);
                    Vec3 p = new Vec3(c.x + Math.cos(a) * r, self ? box.minY + h * 0.3 : c.y + 0.15 * Math.sin(t * 0.2 + i), c.z + Math.sin(a) * r);
                    ctx.billboard(shard, p, 0.45f, (float) a, Colors.alpha(fade, 0xDDF8FF));
                    ctx.billboard(ctx.additive(VfxTextures.GLOW), p, 0.6f, 0, Colors.alpha(fade * 0.4f, 0x8FE3FF));
                }
            }
            case VALOR, DAWN -> {
                boolean dawn = inst.type == com.fantasyweapons.status.StatusType.DAWN;
                var spark = ctx.additive(VfxTextures.SPARK);
                int n = self ? 2 : dawn ? 9 : 5;
                for (int i = 0; i < n; i++) {
                    float ph = hash(seed, 60 + i);
                    float cyc = (t * 0.05f + ph) % 1f;
                    double a = ph * 6.283 + t * 0.03;
                    double rr = self ? 0.9 : w * 0.6;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * rr, box.minY + h * cyc, c.z + Math.sin(a) * rr);
                    ctx.stretched(spark, p, MageFx.UP, 0.3f, 0.06f, Colors.alpha(fade * (1 - cyc), dawn ? 0xFFF6D0 : 0xFFC94A));
                }
                if (!self) {
                    ctx.billboard(ctx.additive(VfxTextures.GLOW), c, (float) (w + h) * (dawn ? 1.2f : 0.8f), 0, Colors.alpha(fade * (dawn ? 0.3f : 0.18f), 0xFFC94A));
                    if (dawn) ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), feet.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) (w + 0.8), t * 0.04f,
                            Colors.alpha(fade * 0.5f, 0xFFD86A));
                }
            }
            case RADIANCE -> {
                if (self) return;
                var glow = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < 3; i++) {
                    float ph = hash(seed, 80 + i);
                    float cyc = (t * 0.03f + ph) % 1f;
                    double a = ph * 6.283;
                    ctx.billboard(glow, new Vec3(c.x + Math.cos(a) * w * 0.6, box.minY + h * cyc, c.z + Math.sin(a) * w * 0.6), 0.15f, 0,
                            Colors.alpha(fade * (float) Math.sin(cyc * Math.PI) * 0.7f, 0xFFE9A8));
                }
            }
            case SWIFT -> {
                var streak = ctx.additive(VfxTextures.STREAK);
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.35 + i * 2.094;
                    double rr = self ? 0.8 : w * 0.7;
                    Vec3 p = new Vec3(feet.x + Math.cos(a) * rr, feet.y + 0.2 + i * 0.15, feet.z + Math.sin(a) * rr);
                    ctx.stretched(streak, p, new Vec3(-Math.sin(a), 0.1, Math.cos(a)), 0.8f, 0.06f, Colors.alpha(fade * 0.6f, 0xFFF6D8));
                }
            }
            case CHOIR -> {
                if (self) return;
                var star = ctx.additive(VfxTextures.STAR);
                for (int i = 0; i < inst.stacks; i++) {
                    double a = t * 0.06 + i * 6.283 / inst.stacks;
                    ctx.billboard(star, top.add(Math.cos(a) * 0.5, 0.5 + 0.08 * Math.sin(t * 0.25 + i), Math.sin(a) * 0.5), 0.2f, t * 0.1f, Colors.alpha(fade, 0xFFF6D8));
                }
            }
            case HALO -> {
                if (self) return;
                Vec3 ring = top.add(0, 0.35 + 0.04 * Math.sin(t * 0.15), 0);
                ctx.ring(ctx.additive(VfxTextures.RING), ring, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.28f, 0.42f, 24, Colors.alpha(fade, 0xFFEBA0));
                ctx.billboard(ctx.additive(VfxTextures.GLOW), ring, 1.1f, 0, Colors.alpha(fade * 0.35f, 0xFFE58A));
            }
            case ASCENDED -> {
                if (self) return;
                // wings of light spread from the shoulders, beating slowly
                float bodyYaw = (float) Math.toRadians(-e.yBodyRot);
                Vec3 fwd = new Vec3(Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
                Vec3 side = fwd.cross(MageFx.UP).normalize();
                Vec3 back = c.add(0, h * 0.2, 0).subtract(fwd.scale(w * 0.4));
                float flap = (float) Math.sin(t * 0.12) * 0.3f;
                var streak = ctx.additive(VfxTextures.STREAK);
                for (int s = -1; s <= 1; s += 2) {
                    int n = 6;
                    Vec3[] pts = new Vec3[n];
                    float[] ws = new float[n];
                    int[] cs = new int[n];
                    for (int i = 0; i < n; i++) {
                        float u = i / (float) (n - 1);
                        pts[i] = back.add(side.scale(s * u * 1.5)).add(0, (flap + 0.6) * u - u * u * 0.4, 0).subtract(fwd.scale(u * 0.4));
                        ws[i] = 0.9f * (1 - u * 0.6f);
                        cs[i] = Colors.alpha(fade * 0.7f, Colors.lerpRgb(0xFFFFFF, 0xFFE58A, u));
                    }
                    ctx.ribbon(streak, pts, ws, cs, -t * 0.05f, 0.25f);
                }
                ctx.billboard(ctx.additive(VfxTextures.GLOW), back, 1.6f, 0, Colors.alpha(fade * 0.3f, 0xFFFDE0));
            }
            case SPRING, MENDING -> {
                boolean spring = inst.type == com.fantasyweapons.status.StatusType.SPRING;
                var leaf = ctx.translucent(spring ? VfxTextures.PETAL_SOFT : VfxTextures.LEAF_SOFT);
                int n = self ? 2 : 5;
                for (int i = 0; i < n; i++) {
                    float ph = hash(seed, 100 + i);
                    float cyc = (t * 0.03f + ph) % 1f;
                    double a = ph * 6.283 + cyc * 4;
                    double rr = self ? 0.9 : w * 0.65;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * rr, box.minY + h * cyc, c.z + Math.sin(a) * rr);
                    ctx.billboard(leaf, p, 0.28f, (float) a, Colors.alpha(fade * (float) Math.sin(cyc * Math.PI), spring ? 0xFFB8E0 : 0x7DE36A));
                }
                if (spring && !self) ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), feet.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) (w + 0.6), t * 0.03f,
                        Colors.alpha(fade * 0.4f, 0x8CFF7A));
            }
            case WITHERING -> {
                if (self) return;
                var streak = ctx.additive(VfxTextures.STREAK);
                for (int k = 0; k < 2; k++) {
                    int n = 10;
                    Vec3[] pts = new Vec3[n];
                    float[] ws = new float[n];
                    int[] cs = new int[n];
                    for (int i = 0; i < n; i++) {
                        float u = i / (float) (n - 1);
                        double a = u * 6 + t * 0.12 + k * Math.PI;
                        pts[i] = new Vec3(c.x + Math.cos(a) * w * 0.6, box.minY + h * u, c.z + Math.sin(a) * w * 0.6);
                        ws[i] = 0.12f;
                        cs[i] = Colors.alpha(fade * 0.8f * (float) Math.sin(u * Math.PI), 0x9B4DFF);
                    }
                    ctx.ribbon(streak, pts, ws, cs, -t * 0.1f, 0.2f);
                }
            }
            case ROT, PLAGUE, DECAY -> {
                if (self && inst.type != com.fantasyweapons.status.StatusType.ROT) return;
                boolean plague = inst.type == com.fantasyweapons.status.StatusType.PLAGUE;
                var mist = ctx.translucent(VfxTextures.MIST);
                var mote = ctx.additive(VfxTextures.GLOW);
                int n = plague ? 6 : inst.type == com.fantasyweapons.status.StatusType.DECAY ? 2 : Math.min(8, 1 + inst.stacks);
                for (int i = 0; i < n; i++) {
                    float ph = hash(seed, 120 + i);
                    float cyc = (t * 0.03f + ph) % 1f;
                    double a = ph * 6.283 + t * 0.02;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * w * 0.6, box.minY + h * (0.1 + 0.9 * cyc), c.z + Math.sin(a) * w * 0.6);
                    ctx.billboard(mote, p, 0.12f, 0, Colors.alpha(fade * (float) Math.sin(cyc * Math.PI), plague ? 0xA6D86A : 0x8A5AC8));
                }
                if (!self) ctx.billboard(mist, feet.add(0, plague ? 0.6 : 0.25, 0), (float) (w + (plague ? 1.6 : 0.8)), t * 0.01f,
                        Colors.alpha(fade * (plague ? 0.4f : 0.25f), plague ? 0x3A4A20 : 0x2A1440));
            }
            case RUIN -> {
                if (self) return;
                Vec3 sig = top.add(0, 0.55, 0);
                ctx.disc(ctx.additive(VfxTextures.RUNE_CIRCLE), sig, ctx.camLeft, ctx.camUp, 0.45f, t * 0.06f, Colors.alpha(fade, 0x9B4DFF));
                ctx.billboard(ctx.translucent(VfxTextures.GLOW), c, (float) (w + h) * 0.8f, 0, Colors.alpha(fade * 0.35f, 0x0A0210));
                var ash = ctx.translucent(VfxTextures.SHARD);
                for (int i = 0; i < 4; i++) {
                    float ph = hash(seed, 140 + i);
                    float cyc = (t * 0.03f + ph) % 1f;
                    double a = ph * 6.283;
                    ctx.billboard(ash, new Vec3(c.x + Math.cos(a) * w * 0.7, box.maxY + 0.3 - cyc * (h + 0.3), c.z + Math.sin(a) * w * 0.7), 0.12f, cyc * 6,
                            Colors.alpha(fade * 0.8f, 0x1A0A24));
                }
            }
            case LEECH -> {
                if (self) return;
                var drop = ctx.additive(VfxTextures.GLOW);
                for (int i = 0; i < 4; i++) {
                    float ph = hash(seed, 160 + i);
                    float cyc = (t * 0.06f + ph) % 1f;
                    double a = ph * 6.283;
                    ctx.billboard(drop, new Vec3(c.x + Math.cos(a) * w * 0.4, c.y + cyc * h * 0.8, c.z + Math.sin(a) * w * 0.4), 0.14f, 0,
                            Colors.alpha(fade * (1 - cyc), 0xFF4A7A));
                }
            }
            case BANE -> {
                if (self) return;
                var seedTex = ctx.additive(VfxTextures.SOUL);
                for (int i = 0; i < inst.stacks; i++) {
                    double a = i * 2.094 + 0.4;
                    float pulse = 0.8f + 0.2f * (float) Math.sin(t * 0.3 + i);
                    Vec3 p = new Vec3(c.x + Math.cos(a) * w * 0.5, box.minY + h * (0.4 + 0.15 * i), c.z + Math.sin(a) * w * 0.5);
                    ctx.billboard(seedTex, p, 0.35f * pulse, 0, Colors.alpha(fade, 0xB070FF));
                    ctx.billboard(ctx.additive(VfxTextures.GLOW), p, 0.6f * pulse, 0, Colors.alpha(fade * 0.4f, 0x6A2AB0));
                }
            }
            default -> {
            }
        }
    }
}
