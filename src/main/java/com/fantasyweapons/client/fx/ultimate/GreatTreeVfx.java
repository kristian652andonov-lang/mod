package com.fantasyweapons.client.fx.ultimate;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.weapons.ultimate.RootSpline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * THE ROOTS OF THE GREAT TREE. The roots that hold up the world heave out of the ground - a crown of colossal ones from
 * the middle, many more all over the land - each bursting up in a spray of earth, arching high and plunging back in;
 * then they wrap around everything they seize. When the spell ends the server builds the same roots into the world, and
 * these fade as the real ones appear.
 */
public class GreatTreeVfx extends Vfx {
    private static final Vec3 LIGHT = new Vec3(0.35, 0.9, 0.25).normalize();
    private static final int BARK = 0x5A4430, MOSS = 0x5E7A34, GLOW = 0x9CFF6A;
    private static final int SEGMENTS = 24, SIDES = 10;

    private final Vec3 center;
    private final float radius;
    private final int duration;
    private final List<RootSpline> roots;
    private final float[] delays;
    private int[] held = new int[0];
    private int heldFrom;

    public GreatTreeVfx(Vec3 center, float radius, int duration, List<RootSpline> roots) {
        super(duration + 110);
        this.center = center;
        this.radius = radius;
        this.duration = duration;
        this.roots = roots;
        delays = new float[roots.size()];
        for (int i = 0; i < roots.size(); i++) {
            // the crown first, then the rest outward
            double d = Math.hypot(roots.get(i).p0().x - center.x, roots.get(i).p0().z - center.z);
            delays[i] = 16 + (float) (d / radius) * 26 + (i % 5) * 1.5f;
        }
    }

    /** The roots seize these creatures now. */
    public void seize(int[] ids) {
        held = ids;
        heldFrom = age;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = age + ctx.partial;
        // the roots fade as the real ones are built in their place
        float fade = 1 - clamp01((t - duration - 20) / 85f);
        if (fade <= 0) return;
        ctx.farClamp = VfxContext.viewReach() * 0.86;
        VertexConsumer bark = ctx.solid(VfxTextures.BARK);
        var glow = ctx.additive(VfxTextures.GLOW);
        var mist = ctx.translucent(VfxTextures.MIST);
        for (int i = 0; i < roots.size(); i++) {
            float grow = easeOut(clamp01((t - delays[i]) / 30f));
            if (grow <= 0) continue;
            RootSpline r = roots.get(i);
            tube(ctx, bark, r, grow, fade);
            // the earth bursting where it breaks out
            float burst = clamp01((t - delays[i]) / 20f);
            if (burst < 1) {
                Vec3 out = r.at(0.08);
                ctx.billboard(mist, out, r.thickness() * (3 + burst * 6), i, Colors.alpha((1 - burst) * 0.7f * fade, 0x6A5440));
            }
            // a faint green light running along the roots as they live
            if (t < duration) {
                float run = ((t * 0.02f) + i * 0.13f) % 1f;
                if (run <= grow) ctx.billboard(glow, r.at(run), r.thickness() * 2.2f, 0, Colors.alpha(0.5f * fade, GLOW));
            }
        }
        drawHeld(ctx, bark, t, fade);
        ctx.farClamp = 0;
    }

    /** A root's tube, grown out to {@code grow} of its length, shaded by a fixed light. */
    private void tube(VfxContext ctx, VertexConsumer vc, RootSpline r, float grow, float fade) {
        int n = Math.max(2, Math.round(SEGMENTS * grow));
        Vec3[] rings = new Vec3[(n + 1) * SIDES];
        Vec3[] normals = new Vec3[(n + 1) * SIDES];
        for (int i = 0; i <= n; i++) {
            double u = grow * i / n;
            Vec3 p = r.at(u), tan = r.tangent(u);
            Vec3[] b = VfxContext.basis(tan);
            // the growing tip is pointed
            double rad = r.radius(u) * (i == n ? 0.25 : 1) * (1 + 0.08 * Math.sin(u * 37 + r.thickness()));
            for (int s = 0; s < SIDES; s++) {
                double a = s * Math.PI * 2 / SIDES;
                Vec3 o = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                normals[i * SIDES + s] = o;
                rings[i * SIDES + s] = p.add(o.scale(rad));
            }
        }
        float len = (float) r.p0().distanceTo(r.p3());
        for (int i = 0; i < n; i++) {
            for (int s = 0; s < SIDES; s++) {
                int s1 = (s + 1) % SIDES;
                int a = i * SIDES + s, b = i * SIDES + s1, c = (i + 1) * SIDES + s1, d = (i + 1) * SIDES + s;
                float u0 = i * len / n * 0.12f, u1 = (i + 1) * len / n * 0.12f;
                float v0 = s / (float) SIDES * 2, v1 = (s + 1) / (float) SIDES * 2;
                ctx.vertex(vc, rings[a], u0, v0, shade(normals[a], fade));
                ctx.vertex(vc, rings[b], u0, v1, shade(normals[b], fade));
                ctx.vertex(vc, rings[c], u1, v1, shade(normals[c], fade));
                ctx.vertex(vc, rings[d], u1, v0, shade(normals[d], fade));
            }
        }
    }

    private static int shade(Vec3 normal, float fade) {
        float lit = 0.45f + 0.55f * (float) Math.max(0, normal.dot(LIGHT));
        int base = normal.y > 0.6 ? Colors.lerpRgb(BARK, MOSS, 0.6f) : BARK;
        return Colors.argb(Math.round(255 * fade), Colors.scale(base, lit * 1.6f));
    }

    /** Roots coiled round everything seized, tightening. */
    private void drawHeld(VfxContext ctx, VertexConsumer vc, float t, float fade) {
        if (held.length == 0) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float wrap = easeOut(clamp01((t - heldFrom) / 16f));
        for (int id : held) {
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) continue;
            Vec3 base = e.getPosition(ctx.partial);
            float w = e.getBbWidth() * 0.75f + 0.4f, h = e.getBbHeight() + 0.4f;
            for (int k = 0; k < 3; k++) {
                double phase = k * Math.PI * 2 / 3;
                Vec3 p0 = base.add(Math.cos(phase) * w * 2.5, -2, Math.sin(phase) * w * 2.5);
                Vec3 p1 = base.add(Math.cos(phase + 1.6) * w * 1.4, h * 0.3, Math.sin(phase + 1.6) * w * 1.4);
                Vec3 p2 = base.add(Math.cos(phase + 3.4) * w, h * 0.75, Math.sin(phase + 3.4) * w);
                Vec3 p3 = base.add(Math.cos(phase + 5.0) * w * 0.6, h, Math.sin(phase + 5.0) * w * 0.6);
                tube(ctx, vc, new RootSpline(p0, p1, p2, p3, 0.35f + w * 0.12f), wrap, fade);
            }
        }
    }
}
