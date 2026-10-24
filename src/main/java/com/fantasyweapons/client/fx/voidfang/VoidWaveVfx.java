package com.fantasyweapons.client.fx.voidfang;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/**
 * Travelling void crescent for Void Slash. Same speed and stopping point as the server-side hitbox. The crescent bows
 * back from its tips, carries afterimages, and splits apart into fading fragments once it reaches its end.
 */
public class VoidWaveVfx extends Vfx {
    private static final int SEGS = 18;
    private final Vec3 start;
    private final Vec3 dir;
    private final Vec3 side;
    private final Vec3 up;
    private final double dist;
    private final float speed;
    private final float halfWidth;
    private final int color;
    private final int edge;
    private final int travelTicks;

    public VoidWaveVfx(Vec3 start, Vec3 dir, Vec3 end, float speed, float halfWidth, int color, int edge) {
        super((int) Math.ceil(start.distanceTo(end) / Math.max(0.1f, speed)) + 8);
        this.start = start;
        this.dir = dir.normalize();
        Vec3 s = this.dir.cross(new Vec3(0, 1, 0));
        this.side = s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize();
        this.up = side.cross(this.dir).normalize();
        this.dist = start.distanceTo(end);
        this.speed = speed;
        this.halfWidth = halfWidth;
        this.color = color;
        this.edge = edge;
        this.travelTicks = (int) Math.ceil(dist / Math.max(0.1f, speed));
    }

    public Vec3 headAt(float ticks) {
        return start.add(dir.scale(Math.min(dist, speed * ticks)));
    }

    public int travelTicks() {
        return travelTicks;
    }

    @Override
    public void render(VfxContext ctx) {
        float ticks = age + ctx.partial;
        float fade = ticks <= travelTicks ? 1f : 1f - (ticks - travelTicks) / 8f;
        if (fade <= 0) return;
        float grow = Math.min(1f, ticks / 3f);
        float spread = ticks > travelTicks ? 1 + (ticks - travelTicks) * 0.12f : 1f;
        // afterimages first (dimmer, behind)
        for (int k = 3; k >= 0; k--) {
            float tk = Math.max(0, ticks - k * 1.2f);
            float a = fade * (k == 0 ? 1f : 0.35f / k);
            drawCrescent(ctx, headAt(tk), halfWidth * grow * spread, a, k == 0);
        }
    }

    private void drawCrescent(VfxContext ctx, Vec3 head, float hw, float alpha, boolean main) {
        var body = ctx.energy(VfxTextures.SLASH);
        float depth = hw * 0.55f;
        float thick = hw * 0.32f;
        Vec3 prevIn = null, prevOut = null;
        float prevU = 0;
        for (int i = 0; i <= SEGS; i++) {
            float s = i / (float) SEGS * 2 - 1; // -1..1
            float bow = s * s;
            float taper = 1 - bow * 0.85f;
            Vec3 c = head.add(side.scale(s * hw)).subtract(dir.scale(bow * depth)).add(up.scale(s * hw * 0.12f));
            Vec3 in = c.subtract(dir.scale(thick * taper));
            Vec3 out = c.add(dir.scale(thick * 0.25f * taper));
            float u = (s + 1) * 0.5f;
            if (prevIn != null) {
                int c0 = Colors.alpha(alpha * 0.85f, color);
                ctx.vertex(body, prevIn, prevU, 0, c0);
                ctx.vertex(body, in, u, 0, c0);
                ctx.vertex(body, out, u, 1, c0);
                ctx.vertex(body, prevOut, prevU, 1, c0);
            }
            prevIn = in;
            prevOut = out;
            prevU = u;
        }
        if (!main) return;
        // bright leading edge
        Vec3[] pts = new Vec3[SEGS + 1];
        float[] w = new float[SEGS + 1];
        int[] cols = new int[SEGS + 1];
        for (int i = 0; i <= SEGS; i++) {
            float s = i / (float) SEGS * 2 - 1;
            float bow = s * s;
            pts[i] = head.add(side.scale(s * hw)).subtract(dir.scale(bow * depth)).add(up.scale(s * hw * 0.12f));
            w[i] = (0.18f + 0.22f * (1 - bow)) * Math.max(0.6f, hw * 0.35f);
            cols[i] = Colors.alpha(alpha * (1 - bow * 0.6f), edge);
        }
        ctx.ribbon(ctx.additive(VfxTextures.LIGHTNING), pts, w, cols, 0, 1f / SEGS);
        ctx.billboard(ctx.additive(VfxTextures.GLOW), head.subtract(dir.scale(depth * 0.4f)), hw * 2.4f, 0, Colors.alpha(alpha * 0.25f, color));
    }
}
