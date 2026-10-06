package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * A modelled wraith: a hooded head with hollow, burning eyes and a gaping mouth, broad shoulders and a shroud that
 * hangs beneath it and narrows into a rippling tail streaming out behind, and two thin arms reaching ahead with long
 * hooked fingers. The body is a real tube of rings round a curved spine (translucent, deeper at the core and pale at
 * the edges like mist), wrapped in a soft glow and shedding wisps. It flies along {@code path} (t = 0..1 of its
 * life) upright, facing where it goes, its shroud whipping behind; it fades in and out.
 */
public class GhostVfx extends Vfx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int RINGS = 24;
    private static final int SIDES = 16;
    private final Function<Float, Vec3> path;
    private final float size;
    private final int color;
    private final int core;
    private final float phase;
    private float fadeIn = 0.15f, fadeOut = 0.3f;
    private java.util.function.Supplier<Vec3> heading;
    private Vec3 lastDir = new Vec3(1, 0, 0);
    private Vec3 lastFlat = new Vec3(1, 0, 0);

    public GhostVfx(Function<Float, Vec3> path, float size, int color, int core, int lifetime, long seed) {
        super(lifetime);
        this.path = path;
        this.size = size;
        this.color = color;
        this.core = core;
        this.phase = (seed & 1023) / 1023f * 6.28f;
    }

    /** Takes the direction of flight from {@code heading} (e.g. a seeker's velocity) instead of from the path. */
    public GhostVfx heading(java.util.function.Supplier<Vec3> heading) {
        this.heading = heading;
        return this;
    }

    /** Fractions of the life spent fading in and out. */
    public GhostVfx fades(float in, float out) {
        this.fadeIn = in;
        this.fadeOut = out;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float time = age + ctx.partial;
        float a = Math.min(1, t / Math.max(1e-3f, fadeIn)) * Math.min(1, (1 - t) / Math.max(1e-3f, fadeOut));
        if (a <= 0.01f) return;
        Vec3 head = path.apply(t);
        Vec3 ahead = heading != null ? heading.get().scale(0.06) : path.apply(Math.min(1f, t + 0.03f)).subtract(path.apply(Math.max(0f, t - 0.03f)));
        Vec3 dir = ahead.lengthSqr() < 1e-6 ? lastDir : ahead.normalize();
        lastDir = dir;
        float speed = (float) Math.min(1, ahead.length() * 6);
        // it faces where it is going across the ground, however steeply it climbs or dives
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() > 1e-4) lastFlat = flat.normalize();
        Vec3 fwd = lastFlat;
        Vec3 side = fwd.cross(UP).normalize();

        // spine: from the crown down through the hood and shoulders into a shroud that hangs below and streams out
        // behind, rippling like cloth in the wind
        Vec3 back = dir.scale(-(0.45 + 0.5 * speed)).add(UP.scale(-0.9));
        back = back.lengthSqr() < 1e-4 ? UP.scale(-1) : back.normalize();
        Vec3[] spine = new Vec3[RINGS];
        float[] radius = new float[RINGS];
        float len = size * (2.0f + 0.5f * speed);
        for (int i = 0; i < RINGS; i++) {
            float s = i / (float) (RINGS - 1);
            double wave = Math.sin(time * 0.3 + phase - s * 5.0) * s * s * size * 0.3;
            double sway = Math.sin(time * 0.2 + phase - s * 3.0) * s * size * 0.15;
            // the lower body bends back further than the shoulders, so the shroud trails behind
            Vec3 bend = dir.scale(-s * s * size * 0.6 * speed);
            spine[i] = head.add(back.scale(len * s)).add(bend).add(side.scale(wave)).add(fwd.scale(sway));
            radius[i] = profile(s) * size;
        }

        // rings round the spine (parallel transported) with a softly fluttering hem on the shroud
        Vec3[][] ring = new Vec3[RINGS][SIDES];
        Vec3[][] nrm = new Vec3[RINGS][SIDES];
        Vec3 n = side;
        for (int i = 0; i < RINGS; i++) {
            Vec3 tan = spine[Math.min(RINGS - 1, i + 1)].subtract(spine[Math.max(0, i - 1)]).normalize();
            n = n.subtract(tan.scale(n.dot(tan)));
            n = n.lengthSqr() < 1e-8 ? VfxContext.basis(tan)[0] : n.normalize();
            Vec3 b = tan.cross(n);
            float s = i / (float) (RINGS - 1);
            for (int k = 0; k < SIDES; k++) {
                double ang = k * Math.PI * 2 / SIDES;
                float rag = s > 0.45f ? 1 + 0.16f * (float) Math.sin(k * 3.0 + time * 0.4 + phase + i * 0.7) * (s - 0.45f) * 2 : 1;
                Vec3 o = n.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
                nrm[i][k] = o;
                ring[i][k] = spine[i].add(o.scale(radius[i] * rag));
            }
        }

        // ---- soft glow round the whole shape ----
        var glow = ctx.additive(VfxTextures.GLOW);
        ctx.billboard(glow, spine[RINGS / 5], size * 2.0f, 0, Colors.alpha(a * 0.3f, color));
        ctx.billboard(glow, spine[RINGS / 2], size * 2.2f, 0, Colors.alpha(a * 0.2f, color));

        // ---- the body: misty, pale at the silhouette, the core deeper ----
        Vec3 cam = ctx.cam;
        VertexConsumer body = ctx.translucent(VfxTextures.WHITE);
        for (int i = 0; i < RINGS - 1; i++) {
            float s0 = i / (float) (RINGS - 1), s1 = (i + 1) / (float) (RINGS - 1);
            for (int k = 0; k < SIDES; k++) {
                int k1 = (k + 1) % SIDES;
                float u0 = k / (float) SIDES, u1 = (k + 1) / (float) SIDES;
                ctx.vertex(body, ring[i][k], u0, s0, shade(ring[i][k], nrm[i][k], cam, s0, a));
                ctx.vertex(body, ring[i][k1], u1, s0, shade(ring[i][k1], nrm[i][k1], cam, s0, a));
                ctx.vertex(body, ring[i + 1][k1], u1, s1, shade(ring[i + 1][k1], nrm[i + 1][k1], cam, s1, a));
                ctx.vertex(body, ring[i + 1][k], u0, s1, shade(ring[i + 1][k], nrm[i + 1][k], cam, s1, a));
            }
        }

        // ---- arms reaching out ahead from the shoulders, ending in long hooked fingers ----
        int sh0 = Math.round(0.3f * (RINGS - 1));
        Vec3[][] hands = new Vec3[2][];
        int hi = 0;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double reach = Math.sin(time * 0.25 + phase + sgn * 1.3) * 0.2;
            Vec3 shoulder = spine[sh0].add(side.scale(sgn * radius[sh0] * 0.8)).add(fwd.scale(radius[sh0] * 0.2));
            Vec3 elbow = shoulder.add(fwd.scale(size * (0.42 + reach * 0.3))).add(side.scale(sgn * size * 0.2)).add(UP.scale(-size * 0.1));
            Vec3 hand = elbow.add(fwd.scale(size * (0.42 + 0.1 * speed))).add(side.scale(-sgn * size * 0.04)).add(UP.scale(size * (0.08 + reach * 0.25)));
            tube(ctx, body, new Vec3[]{shoulder, shoulder.lerp(elbow, 0.5), elbow, elbow.lerp(hand, 0.5), hand},
                    new float[]{0.12f, 0.09f, 0.075f, 0.065f, 0.055f}, cam, a);
            hands[hi++] = new Vec3[]{hand, elbow};
        }
        int nail = Colors.argb(Math.round(210 * a), Colors.scale(color, 0.3f));
        for (Vec3[] h : hands) {
            Vec3 along = h[0].subtract(h[1]).normalize();
            for (int f = -1; f <= 1; f++) {
                Vec3 knuckle = h[0].add(side.scale(f * size * 0.045));
                Vec3 mid = knuckle.add(along.scale(size * 0.1)).add(side.scale(f * size * 0.02));
                Vec3 tip = mid.add(along.scale(size * 0.07)).add(UP.scale(-size * 0.08));
                ctx.beam(body, knuckle, mid, size * 0.032f, nail, 0, 1);
                ctx.beam(body, mid, tip, size * 0.024f, nail, 0, 1);
            }
        }

        // ---- face under the hood: deep hollow eye sockets with burning pupils, and a gaping mouth ----
        int hc = Math.round(0.1f * (RINGS - 1));
        float hr = radius[hc];
        Vec3 face = spine[hc].add(fwd.scale(hr * 1.02));
        Vec3 eyeL = face.add(side.scale(-hr * 0.38)).add(UP.scale(hr * 0.12));
        Vec3 eyeR = face.add(side.scale(hr * 0.38)).add(UP.scale(hr * 0.12));
        Vec3 mouth = face.add(UP.scale(-hr * 0.42));
        VertexConsumer hollow = ctx.translucent(VfxTextures.GLOW);
        int dark = Colors.argb(Math.round(250 * a), 0x010403);
        ctx.billboard(hollow, eyeL, hr * 0.85f, 0, dark);
        ctx.billboard(hollow, eyeR, hr * 0.85f, 0, dark);
        float gape = 0.75f + 0.25f * (float) Math.sin(time * 0.5 + phase);
        ctx.stretched(hollow, mouth, UP, hr * 0.75f * gape, hr * 0.42f, dark);
        VertexConsumer fire = ctx.additive(VfxTextures.GLOW);
        float flick = 0.8f + 0.2f * (float) Math.sin(time * 1.3 + phase);
        ctx.billboard(fire, eyeL, hr * 0.32f * flick, 0, Colors.alpha(a, core));
        ctx.billboard(fire, eyeR, hr * 0.32f * flick, 0, Colors.alpha(a, core));
        ctx.billboard(fire, eyeL, hr * 0.8f, 0, Colors.alpha(a * 0.5f, color));
        ctx.billboard(fire, eyeR, hr * 0.8f, 0, Colors.alpha(a * 0.5f, color));

        // ---- wisps shed from the hem ----
        VertexConsumer wisp = ctx.additive(VfxTextures.MIST);
        for (int i = 0; i < 6; i++) {
            float cyc = (time * 0.05f + i / 6f + phase) % 1f;
            int idx = Math.min(RINGS - 1, RINGS / 2 + (int) (cyc * (RINGS / 2)));
            Vec3 p = spine[idx].add(UP.scale(cyc * size * 0.3)).add(side.scale(Math.sin(i * 2.1 + time * 0.2) * size * 0.3));
            ctx.billboard(wisp, p, size * (0.35f + 0.3f * cyc), time * 0.02f + i, Colors.alpha(a * 0.25f * (1 - cyc), color));
        }
    }

    /** Radius of the body along the spine (0 = crown, 1 = tip of the tail), as a fraction of the ghost's size. */
    private static float profile(float s) {
        if (s < 0.2f) {
            // the hooded head: a rounded cap from the crown
            float u = 1 - s / 0.2f;
            return (float) Math.sqrt(Math.max(0, 1 - u * u)) * 0.36f + 0.02f;
        }
        if (s < 0.27f) return 0.38f - (s - 0.2f) * 1.2f;
        if (s < 0.4f) return 0.296f + (s - 0.27f) * 2.0f;
        return 0.556f * (float) Math.pow(1 - (s - 0.4f) / 0.6f, 1.1) + 0.012f;
    }

    /** A thin tapered misty tube through {@code pts} (radii as fractions of the ghost's size). */
    private void tube(VfxContext ctx, VertexConsumer vc, Vec3[] pts, float[] r, Vec3 cam, float a) {
        final int sides = 8;
        Vec3[][] ring = new Vec3[pts.length][sides];
        Vec3[][] nrm = new Vec3[pts.length][sides];
        Vec3 n = null;
        for (int i = 0; i < pts.length; i++) {
            Vec3 tan = pts[Math.min(pts.length - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]).normalize();
            if (n == null) n = VfxContext.basis(tan)[0];
            n = n.subtract(tan.scale(n.dot(tan)));
            n = n.lengthSqr() < 1e-8 ? VfxContext.basis(tan)[0] : n.normalize();
            Vec3 b = tan.cross(n);
            for (int k = 0; k < sides; k++) {
                double ang = k * Math.PI * 2 / sides;
                Vec3 o = n.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
                nrm[i][k] = o;
                ring[i][k] = pts[i].add(o.scale(r[i] * size));
            }
        }
        for (int i = 0; i < pts.length - 1; i++) {
            for (int k = 0; k < sides; k++) {
                int k1 = (k + 1) % sides;
                ctx.vertex(vc, ring[i][k], 0, 0, shade(ring[i][k], nrm[i][k], cam, 0.3f, a));
                ctx.vertex(vc, ring[i][k1], 1, 0, shade(ring[i][k1], nrm[i][k1], cam, 0.3f, a));
                ctx.vertex(vc, ring[i + 1][k1], 1, 1, shade(ring[i + 1][k1], nrm[i + 1][k1], cam, 0.3f, a));
                ctx.vertex(vc, ring[i + 1][k], 0, 1, shade(ring[i + 1][k], nrm[i + 1][k], cam, 0.3f, a));
            }
        }
    }

    /** Mist colour of a body vertex: pale and bright at the silhouette, deeper at the core, fading towards the tail. */
    private int shade(Vec3 p, Vec3 normal, Vec3 cam, float s, float a) {
        Vec3 view = cam.subtract(p);
        double facing = view.lengthSqr() < 1e-6 ? 1 : Math.abs(normal.dot(view.normalize()));
        float rim = (float) (1 - facing);
        float alpha = a * (0.42f + 0.5f * rim) * (1 - 0.7f * s * s);
        int rgb = Colors.lerpRgb(Colors.lerpRgb(Colors.scale(color, 0.6f), core, 0.15f), Colors.lerpRgb(color, core, 0.65f), rim);
        return Colors.argb(Math.round(255 * Math.min(1, alpha)), rgb);
    }
}
