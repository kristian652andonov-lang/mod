package com.fantasyweapons.client.vfx;

import com.fantasyweapons.client.render.FWRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Per-frame render context for VFX with low-level geometry emitters. All positions are world space; the context
 * converts to camera-relative coordinates.
 */
public final class VfxContext {
    public final Vec3 cam;
    public final Vec3 camLeft;
    public final Vec3 camUp;
    public final Vec3 camLook;
    public final float partial;
    /** Seconds since client start; drives animated UVs. */
    public final float time;
    /** Quality multiplier for segment / element counts. */
    public final float density;
    private final MultiBufferSource buffers;

    public VfxContext(Vec3 cam, Vector3f left, Vector3f up, Vector3f look, float partial, float time, float density, MultiBufferSource buffers) {
        this.cam = cam;
        this.camLeft = new Vec3(left.x(), left.y(), left.z());
        this.camUp = new Vec3(up.x(), up.y(), up.z());
        this.camLook = new Vec3(look.x(), look.y(), look.z());
        this.partial = partial;
        this.time = time;
        this.density = density;
        this.buffers = buffers;
    }

    public VertexConsumer additive(ResourceLocation tex) {
        return buffers.getBuffer(FWRenderTypes.additive(tex));
    }

    public VertexConsumer energy(ResourceLocation tex) {
        return buffers.getBuffer(FWRenderTypes.energy(tex));
    }

    public VertexConsumer voidInterior(ResourceLocation mask) {
        return buffers.getBuffer(FWRenderTypes.voidInterior(mask));
    }

    public VertexConsumer translucent(ResourceLocation tex) {
        return buffers.getBuffer(FWRenderTypes.translucent(tex));
    }

    public MultiBufferSource buffers() {
        return buffers;
    }

    public int segments(int base) {
        return Math.max(3, Math.round(base * density));
    }

    // ------------------------------------------------------------------------------------------------------------
    // vertex emitters
    // ------------------------------------------------------------------------------------------------------------

    public void vertex(VertexConsumer vc, double x, double y, double z, float u, float v, int argb) {
        vc.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setUv(u, v).setColor(argb);
    }

    public void vertex(VertexConsumer vc, Vec3 p, float u, float v, int argb) {
        vertex(vc, p.x, p.y, p.z, u, v, argb);
    }

    /** Quad a-b-c-d (counter-clockwise), UV rect u0..u1 / v0..v1, per-corner colours. */
    public void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int ca, int cb, int cc, int cd) {
        vertex(vc, a, u0, v1, ca);
        vertex(vc, b, u1, v1, cb);
        vertex(vc, c, u1, v0, cc);
        vertex(vc, d, u0, v0, cd);
    }

    public void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        quad(vc, a, b, c, d, 0, 0, 1, 1, argb, argb, argb, argb);
    }

    /** Camera-facing square sprite. */
    public void billboard(VertexConsumer vc, Vec3 p, float size, float rotation, int argb) {
        float s = size * 0.5f;
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        Vec3 right = camLeft.scale(-1);
        Vec3 ax = right.scale(cos * s).add(camUp.scale(sin * s));
        Vec3 ay = right.scale(-sin * s).add(camUp.scale(cos * s));
        quad(vc, p.subtract(ax).subtract(ay), p.add(ax).subtract(ay), p.add(ax).add(ay), p.subtract(ax).add(ay), argb);
    }

    /** Camera-facing sprite stretched along a direction (sparks, streaks). */
    public void stretched(VertexConsumer vc, Vec3 p, Vec3 dir, float length, float width, int argb) {
        Vec3 d = dir.normalize();
        Vec3 toCam = cam.subtract(p);
        Vec3 side = d.cross(toCam);
        if (side.lengthSqr() < 1e-8) side = camUp;
        side = side.normalize().scale(width * 0.5);
        Vec3 half = d.scale(length * 0.5);
        quad(vc, p.subtract(half).subtract(side), p.add(half).subtract(side), p.add(half).add(side), p.subtract(half).add(side), argb);
    }

    /** Flat quad in an arbitrary plane given two half-axis vectors. */
    public void plane(VertexConsumer vc, Vec3 center, Vec3 halfX, Vec3 halfY, int argb) {
        quad(vc, center.subtract(halfX).subtract(halfY), center.add(halfX).subtract(halfY), center.add(halfX).add(halfY),
                center.subtract(halfX).add(halfY), argb);
    }

    /** Camera-facing ribbon through points. widths/colors per point; u runs along the ribbon. */
    public void ribbon(VertexConsumer vc, Vec3[] pts, float[] widths, int[] colors, float u0, float uPerPoint) {
        if (pts.length < 2) return;
        Vec3 prevL = null, prevR = null;
        for (int i = 0; i < pts.length; i++) {
            Vec3 t = pts[Math.min(pts.length - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 toCam = cam.subtract(pts[i]);
            Vec3 side = t.cross(toCam);
            if (side.lengthSqr() < 1e-10) side = camUp;
            side = side.normalize().scale(widths[i] * 0.5);
            Vec3 l = pts[i].add(side), r = pts[i].subtract(side);
            if (prevL != null) {
                float ua = u0 + (i - 1) * uPerPoint, ub = u0 + i * uPerPoint;
                vertex(vc, prevR, ua, 1, colors[i - 1]);
                vertex(vc, r, ub, 1, colors[i]);
                vertex(vc, l, ub, 0, colors[i]);
                vertex(vc, prevL, ua, 0, colors[i - 1]);
            }
            prevL = l;
            prevR = r;
        }
    }

    /** Camera-facing beam segment. */
    public void beam(VertexConsumer vc, Vec3 from, Vec3 to, float width, int argb, float u0, float u1) {
        Vec3 d = to.subtract(from);
        Vec3 side = d.cross(cam.subtract(from.add(to).scale(0.5)));
        if (side.lengthSqr() < 1e-10) side = camUp;
        side = side.normalize().scale(width * 0.5);
        vertex(vc, from.subtract(side), u0, 1, argb);
        vertex(vc, to.subtract(side), u1, 1, argb);
        vertex(vc, to.add(side), u1, 0, argb);
        vertex(vc, from.add(side), u0, 0, argb);
    }

    @FunctionalInterface
    public interface ArcColor {
        int at(float t, boolean outer);
    }

    /**
     * Flat annular arc in the plane spanned by axisX/axisY around center, angles in radians.
     * u runs along the arc (0 at a0 → 1 at a1), v from inner (0) to outer (1).
     */
    public void arc(VertexConsumer vc, Vec3 center, Vec3 axisX, Vec3 axisY, float rIn, float rOut, float a0, float a1, int segs, ArcColor color) {
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            double b0 = a0 + (a1 - a0) * t0, b1 = a0 + (a1 - a0) * t1;
            Vec3 d0 = axisX.scale(Math.cos(b0)).add(axisY.scale(Math.sin(b0)));
            Vec3 d1 = axisX.scale(Math.cos(b1)).add(axisY.scale(Math.sin(b1)));
            vertex(vc, center.add(d0.scale(rIn)), t0, 0, color.at(t0, false));
            vertex(vc, center.add(d1.scale(rIn)), t1, 0, color.at(t1, false));
            vertex(vc, center.add(d1.scale(rOut)), t1, 1, color.at(t1, true));
            vertex(vc, center.add(d0.scale(rOut)), t0, 1, color.at(t0, true));
        }
    }

    /** Full ring textured with a radial texture across v. */
    public void ring(VertexConsumer vc, Vec3 center, Vec3 axisX, Vec3 axisY, float rIn, float rOut, int segs, int argb) {
        arc(vc, center, axisX, axisY, rIn, rOut, 0, (float) (Math.PI * 2), segs, (t, o) -> argb);
    }

    /** Flat disc quad (texture spans the square) in a plane. */
    public void disc(VertexConsumer vc, Vec3 center, Vec3 axisX, Vec3 axisY, float radius, float rotation, int argb) {
        double c = Math.cos(rotation), s = Math.sin(rotation);
        Vec3 ax = axisX.scale(c * radius).add(axisY.scale(s * radius));
        Vec3 ay = axisX.scale(-s * radius).add(axisY.scale(c * radius));
        plane(vc, center, ax, ay, argb);
    }

    /** UV sphere. With fresnel, alpha is highest at the silhouette (energy shell look). */
    public void sphere(VertexConsumer vc, Vec3 center, float radius, int lat, int lon, int argb, boolean fresnel) {
        int a = (argb >>> 24) & 255;
        for (int i = 0; i < lat; i++) {
            double th0 = Math.PI * i / lat, th1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double ph0 = 2 * Math.PI * j / lon, ph1 = 2 * Math.PI * (j + 1) / lon;
                Vec3 n00 = sph(th0, ph0), n01 = sph(th0, ph1), n11 = sph(th1, ph1), n10 = sph(th1, ph0);
                int c00 = fres(argb, a, n00, center, radius, fresnel), c01 = fres(argb, a, n01, center, radius, fresnel);
                int c11 = fres(argb, a, n11, center, radius, fresnel), c10 = fres(argb, a, n10, center, radius, fresnel);
                float u0 = j / (float) lon, u1 = (j + 1) / (float) lon, v0 = i / (float) lat, v1 = (i + 1) / (float) lat;
                vertex(vc, center.add(n00.scale(radius)), u0, v0, c00);
                vertex(vc, center.add(n10.scale(radius)), u0, v1, c10);
                vertex(vc, center.add(n11.scale(radius)), u1, v1, c11);
                vertex(vc, center.add(n01.scale(radius)), u1, v0, c01);
            }
        }
    }

    private static Vec3 sph(double th, double ph) {
        return new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
    }

    private int fres(int argb, int alpha, Vec3 n, Vec3 center, float radius, boolean fresnel) {
        if (!fresnel) return argb;
        Vec3 p = center.add(n.scale(radius));
        Vec3 view = cam.subtract(p).normalize();
        double f = 1 - Math.abs(view.dot(n));
        int na = (int) (alpha * Math.min(1, 0.12 + f * f * 1.3));
        return (argb & 0x00FFFFFF) | (na << 24);
    }

    /** Two perpendicular unit vectors orthogonal to n. */
    public static Vec3[] basis(Vec3 n) {
        Vec3 nn = n.normalize();
        Vec3 ref = Math.abs(nn.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 x = ref.cross(nn).normalize();
        Vec3 y = nn.cross(x).normalize();
        return new Vec3[]{x, y};
    }
}
