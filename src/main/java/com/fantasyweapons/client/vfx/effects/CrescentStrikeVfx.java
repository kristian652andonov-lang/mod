package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * A crescent of light or shadow flung out of an eclipse: a thick, sharp-horned crescent blade that spins as it falls
 * along a curving path from {@code from} onto {@code to}, trailing a ribbon of its own light, and vanishes as it cuts.
 */
public class CrescentStrikeVfx extends Vfx {
    private static final int TRAIL = 14;
    private final Vec3 from, to, bend;
    private final float size;
    private final int color, edge;
    private final int flight;
    private final Vec3[] trail = new Vec3[TRAIL];
    private int trailCount;

    /**
     * @param flight ticks from leaving the eclipse to striking
     * @param side   which way the path bows (any horizontal vector; scaled by the distance)
     */
    public CrescentStrikeVfx(Vec3 from, Vec3 to, Vec3 side, float size, int color, int edge, int flight) {
        super(flight + 2);
        this.from = from;
        this.to = to;
        this.size = size;
        this.color = color;
        this.edge = edge;
        this.flight = Math.max(2, flight);
        double dist = from.distanceTo(to);
        this.bend = from.lerp(to, 0.45).add(side.normalize().scale(dist * 0.35)).add(0, dist * 0.1, 0);
    }

    private Vec3 at(float t) {
        double u = 1 - t;
        return from.scale(u * u).add(bend.scale(2 * u * t)).add(to.scale(t * t));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 p = at(Math.min(1, age / (float) flight));
        System.arraycopy(trail, 0, trail, 1, TRAIL - 1);
        trail[0] = p;
        trailCount = Math.min(TRAIL, trailCount + 1);
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        float t = Math.min(1, time / flight);
        float a = Math.min(1, time / 2f) * (time > flight ? clamp01(1 - (time - flight) / 2f) : 1);
        if (a <= 0.01f) return;
        Vec3 p = at(easeIn(t) * 0.35f + t * 0.65f);

        // the trail of light behind it
        if (trailCount >= 2) {
            Vec3[] pts = new Vec3[trailCount + 1];
            float[] w = new float[trailCount + 1];
            int[] c = new int[trailCount + 1];
            pts[0] = p;
            w[0] = size * 0.9f;
            c[0] = Colors.alpha(a * 0.8f, color);
            for (int i = 0; i < trailCount; i++) {
                float k = (i + 1) / (float) trailCount;
                pts[i + 1] = trail[i];
                w[i + 1] = size * 0.9f * (1 - k);
                c[i + 1] = Colors.alpha(a * 0.7f * (1 - k), color);
            }
            ctx.ribbon(ctx.additive(VfxTextures.STREAK), pts, w, c, 0, 1f / trailCount);
        }

        // the crescent: an arc of about 220 degrees, thick in the middle and sharp at both horns, facing the viewer
        Vec3 right = ctx.camLeft.scale(-1), up = ctx.camUp;
        float spin = time * 0.55f;
        int n = 18;
        Vec3[] arc = new Vec3[n];
        float[] w = new float[n], wc = new float[n];
        int[] c = new int[n], cc = new int[n];
        for (int i = 0; i < n; i++) {
            float s = i / (float) (n - 1);
            double ang = spin + (s - 0.5) * Math.toRadians(220);
            arc[i] = p.add(right.scale(Math.cos(ang) * size)).add(up.scale(Math.sin(ang) * size));
            float thick = (float) Math.sin(s * Math.PI);
            w[i] = size * 0.7f * thick + 0.01f;
            wc[i] = size * 0.3f * thick + 0.005f;
            c[i] = Colors.alpha(a, edge);
            cc[i] = Colors.alpha(a, color);
        }
        ctx.ribbon(ctx.additive(VfxTextures.STREAK), arc, w, c, 0, 1f / n);
        ctx.ribbon(ctx.additive(VfxTextures.STREAK), arc, wc, cc, 0, 1f / n);
        ctx.billboard(ctx.additive(VfxTextures.GLOW), p, size * 3.4f, 0, Colors.alpha(a * 0.6f, edge));
    }
}
