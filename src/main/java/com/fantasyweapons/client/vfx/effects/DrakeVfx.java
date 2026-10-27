package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.ArrayList;
import java.util.List;

/**
 * Infernochain's drake, assembled from the weapon's own meshes: the pommel's drake skull (with its hinged jaw) as the
 * head, the seven blade segments joined by chain links as the spine, the blade tip as the tail. The head travels a
 * path (a coiling rise, then a dive); every body part follows the head's path at its own distance behind it, so the
 * whole drake slithers through the air and pours into the impact point.
 */
public class DrakeVfx extends Vfx {
    private static final String GEO = "infernochain";
    private final List<Vec3> path = new ArrayList<>();
    private final List<Double> cum = new ArrayList<>();
    private final double riseLength;
    private final int rise;
    private final int dive;
    private final float scale;
    private final int fire;
    private final int core;
    private double[] offsets;
    private String[][] parts;
    private Vec3 headNow;

    /**
     * @param risePath the head's coiling rise (from the wielder's hand up to the dive start)
     * @param target   where the dive ends
     * @param rise     ticks of the rise
     * @param dive     ticks of the dive
     */
    public DrakeVfx(List<Vec3> risePath, Vec3 target, int rise, int dive, float scale, int fire, int core) {
        super(rise + dive + 40);
        this.rise = Math.max(1, rise);
        this.dive = Math.max(1, dive);
        this.scale = scale;
        this.fire = fire;
        this.core = core;
        double total = 0;
        for (int i = 0; i < risePath.size(); i++) {
            if (i > 0) total += risePath.get(i).distanceTo(risePath.get(i - 1));
            path.add(risePath.get(i));
            cum.add(total);
        }
        riseLength = total;
        Vec3 last = risePath.get(risePath.size() - 1);
        int steps = Math.max(2, (int) (last.distanceTo(target) / 0.5));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = last.lerp(target, (double) i / steps);
            total += p.distanceTo(path.get(path.size() - 1));
            path.add(p);
            cum.add(total);
        }
        this.headNow = risePath.get(0);
    }

    /** Current head position (for trails following the drake). */
    public Vec3 head() {
        return headNow;
    }

    private double totalLength() {
        return cum.get(cum.size() - 1);
    }

    private Vec3 at(double d) {
        if (d <= 0) return path.get(0);
        if (d >= totalLength()) return path.get(path.size() - 1);
        int lo = 0, hi = cum.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cum.get(mid) < d) lo = mid;
            else hi = mid;
        }
        double seg = cum.get(hi) - cum.get(lo);
        return path.get(lo).lerp(path.get(hi), seg < 1e-6 ? 0 : (d - cum.get(lo)) / seg);
    }

    /** Distance travelled by the head at time t (ticks): eased rise, then a dive that keeps going into the ground. */
    private double headDistance(float t) {
        if (t < rise) {
            float k = t / rise;
            return riseLength * (1 - (1 - k) * (1 - k));
        }
        double diveLen = totalLength() - riseLength;
        return riseLength + diveLen * (t - rise) / dive;
    }

    private void layout(BakedGeoModel model) {
        if (offsets != null) return;
        List<String[]> ps = new ArrayList<>();
        List<Double> off = new ArrayList<>();
        double d = 0;
        ps.add(new String[]{"drake_skull"});
        off.add(0.0);
        d += 5.5 / 16 * scale;
        for (int i = 0; i <= 6; i++) {
            ps.add(new String[]{"segment_" + i});
            d += 3.6 / 16 * scale;
            off.add(d);
            d += 3.6 / 16 * scale;
            if (i < 6) {
                ps.add(new String[]{"chain_link_" + (i % 2)});
                d += 1.4 / 16 * scale;
                off.add(d);
                d += 1.4 / 16 * scale;
            }
        }
        parts = ps.toArray(new String[0][]);
        offsets = off.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** Orientation whose +Y points along {@code back} (towards the tail), keeping the parts upright. */
    private static Quaternionf orient(Vec3 back) {
        Vector3f y = new Vector3f((float) back.x, (float) back.y, (float) back.z);
        if (y.lengthSquared() < 1e-8f) y.set(0, -1, 0);
        y.normalize();
        Vector3f up = Math.abs(y.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f x = new Vector3f(up).cross(y).normalize();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    @Override
    public void render(VfxContext ctx) {
        BakedGeoModel model = ModelParts.model(GEO);
        if (model == null) return;
        layout(model);
        float t = age + ctx.partial;
        double hd = headDistance(t);
        float alpha = t > lifetime - 8 ? clamp01((lifetime - t) / 8f) : 1f;
        float appear = clamp01(t / 4f);
        double end = totalLength();
        int n = parts.length;
        Vec3[] pos = new Vec3[n];
        Quaternionf[] rot = new Quaternionf[n];
        boolean[] visible = new boolean[n];
        for (int i = 0; i < n; i++) {
            double d = hd - offsets[i];
            visible[i] = d > 0 && d < end - 0.2;
            if (!visible[i]) continue;
            pos[i] = at(d);
            rot[i] = orient(at(d - 0.4).subtract(at(d + 0.4)));
        }
        headNow = visible[0] ? pos[0] : at(Math.min(hd, end));
        // jaw: roars open as the rise peaks, snaps shut into the dive
        float roar = t < rise - 8 ? 0 : t < rise ? (t - (rise - 8)) / 8f : clamp01(1 - (t - rise) / 5f);
        GeoBone jaw = model.getBone("drake_jaw").orElse(null);
        var tex = ModelParts.texture(GEO);
        // the effect render types are full-bright: darken the artist's texture back to forged black iron and crimson
        int body = Colors.alpha(alpha * appear, Colors.scale(Colors.lerpRgb(0xFFFFFF, fire, 0.15f), 0.62f));
        int glow = Colors.alpha(alpha * appear * 0.28f, fire);
        for (int pass = 0; pass < 2; pass++) {
            var vc = pass == 0 ? ctx.translucent(tex) : ctx.additive(tex);
            int col = pass == 0 ? body : glow;
            for (int i = 0; i < n; i++) {
                if (!visible[i]) continue;
                List<GeoBone> bones = ModelParts.bones(model, parts[i]);
                if (bones.isEmpty()) continue;
                Vec3 pivot = ModelParts.center(bones);
                float s = i == 0 ? scale * 1.25f : scale;
                ModelParts.draw(ctx, vc, bones, pos[i], rot[i], s, pivot, col);
                if (i == 0 && jaw != null) {
                    ModelParts.drawHinged(ctx, vc, jaw, pos[i], rot[i], s, pivot, new Quaternionf().rotationX(-0.9f * roar), col);
                }
            }
        }
        // flames streaming off the body, glowing eyes
        var flame = ctx.additive(VfxTextures.FLAME);
        for (int i = 0; i < n; i++) {
            if (!visible[i]) continue;
            float flick = 0.75f + 0.25f * (float) Math.sin(t * 1.3 + i * 2.1);
            Vec3 back = i + 1 < n && visible[i + 1] ? pos[i + 1].subtract(pos[i]) : new Vec3(0, -1, 0);
            ctx.stretched(flame, pos[i].add(0, 0.2 * scale * 0.25, 0), new Vec3(0, 1, 0).add(back.normalize().scale(0.6)),
                    (0.9f + 0.4f * flick) * scale * 0.45f, 0.55f * scale * 0.45f, Colors.alpha(alpha * appear * 0.8f * flick, Colors.lerpRgb(fire, core, flick - 0.5f)));
        }
        var gl = ctx.additive(VfxTextures.GLOW);
        for (int i = 0; i < n; i += 2) {
            if (visible[i]) ctx.billboard(gl, pos[i], 1.6f * scale * 0.4f, 0, Colors.alpha(alpha * appear * 0.35f, fire));
        }
        if (visible[0]) ctx.billboard(gl, pos[0], 3.2f * scale * 0.4f, 0, Colors.alpha(alpha * (0.4f + 0.4f * roar), core));
    }
}
