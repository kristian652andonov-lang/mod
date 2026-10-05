package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A spectral iron chain of real interlocking links. It bursts out of its anchor, curves smoothly onto its (moving)
 * target like a seeking serpent, pulls taut once it has hold, carries pulses of energy along its length, and at the
 * end retracts back into its anchor. With {@link #shackle} it closes a ring around the bound target.
 */
public class ChainVfx extends Vfx {
    /** End point of the chain at a given partial tick, or null when the target is gone. */
    @FunctionalInterface
    public interface EndPoint {
        @Nullable
        Vec3 at(float partial);
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 LIGHT = new Vec3(0.35, 0.85, 0.4).normalize();
    private static final float LINK_LEN = 0.34f, LINK_WIDTH = 0.2f, WIRE = 0.055f;
    private static final float PITCH = LINK_LEN - WIRE * 2.2f;
    private static final int LOOP = 12;

    private final Vec3 anchor;
    private final EndPoint end;
    private final int color;
    private final int linkColor;
    private final Vec3 rise;
    private int shoot = 8;
    private int retract = 8;
    private float shackle;
    private float scale = 1f;

    public ChainVfx(Vec3 anchor, EndPoint end, int color, int linkColor, int lifetime) {
        super(lifetime);
        this.anchor = anchor;
        this.end = end;
        this.color = color;
        this.linkColor = linkColor;
        // each chain leaves the ground at its own slight angle, so a bundle of them fans out
        double a = (anchor.x * 12.9898 + anchor.z * 78.233) % (Math.PI * 2);
        this.rise = new Vec3(Math.cos(a) * 0.35, 1, Math.sin(a) * 0.35).normalize();
    }

    /** The point on an entity's body at {@code heightFrac} of its height, interpolated per frame. */
    public static EndPoint toEntity(Entity e, double heightFrac) {
        return partial -> e.isAlive() ? e.getPosition(partial).add(0, e.getBbHeight() * heightFrac, 0) : null;
    }

    /** Ticks the chain takes to reach its target. */
    public ChainVfx shoot(int ticks) {
        this.shoot = Math.max(1, ticks + 4);
        return this;
    }

    /** Close a ring of this radius around the end point once the chain has hold. */
    public ChainVfx shackle(float radius) {
        this.shackle = radius;
        return this;
    }

    public ChainVfx scale(float s) {
        this.scale = s;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 target = end.at(ctx.partial);
        if (target == null) return;
        float time = age + ctx.partial;
        float out = easeInOut(Math.min(1f, time / shoot));
        float back = clamp01((time - (lifetime - retract)) / retract);
        float ext = out * (1 - easeIn(back));
        if (ext <= 0.01f) return;
        float alpha = Math.min(1f, time / 3f);
        float hold = clamp01((time - shoot) / 6f);                  // 0 while seeking, 1 once taut

        // a cubic curve: out of the ground along `rise`, bending onto the target; it straightens as it pulls taut
        double dist = anchor.distanceTo(target);
        Vec3 c1 = anchor.add(rise.scale(dist * 0.55 * (1 - 0.6 * hold)));
        Vec3 toward = target.subtract(anchor).normalize();
        Vec3 c2 = target.subtract(toward.scale(dist * 0.35)).add(0, dist * 0.25 * (1 - hold) - 0.15 * hold * dist * 0.1, 0);
        double wobble = (1 - hold) * 0.25;

        // sample the visible part of the curve and walk it in equal steps, one link per step
        int samples = 48;
        Vec3[] curve = new Vec3[samples + 1];
        double[] arc = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            double t = ext * i / samples;
            Vec3 p = bezier(anchor, c1, c2, target, t);
            double w = Math.sin(t * Math.PI * 3 + time * 0.5) * wobble * Math.sin(t * Math.PI);
            p = p.add(toward.cross(UP).normalize().scale(Double.isNaN(w) ? 0 : w));
            curve[i] = p;
            arc[i] = i == 0 ? 0 : arc[i - 1] + p.distanceTo(curve[i - 1]);
        }
        double total = arc[samples];
        float pitch = PITCH * scale;
        int links = Math.max(1, (int) (total / pitch));
        Vec3[] pos = new Vec3[links];
        Vec3[] tan = new Vec3[links];
        int seg = 0;
        for (int k = 0; k < links; k++) {
            double s = (k + 0.5) * pitch;
            while (seg < samples - 1 && arc[seg + 1] < s) seg++;
            double f = (s - arc[seg]) / Math.max(1e-6, arc[seg + 1] - arc[seg]);
            pos[k] = curve[seg].lerp(curve[seg + 1], f);
            Vec3 d = curve[seg + 1].subtract(curve[seg]);
            tan[k] = d.lengthSqr() < 1e-10 ? UP : d.normalize();
        }

        // soft soul glow along the whole chain
        Vec3[] glowPts = new Vec3[samples + 1];
        float[] gw = new float[samples + 1];
        int[] gc = new int[samples + 1];
        for (int i = 0; i <= samples; i++) {
            glowPts[i] = curve[i];
            gw[i] = 0.42f * scale;
            gc[i] = Colors.alpha(0.28f * alpha, color);
        }
        ctx.ribbon(ctx.additive(VfxTextures.GLOW), glowPts, gw, gc, 0.5f, 0f);

        // iron links, alternating 90 degrees about the chain, lit from above and rimmed with soul light
        VertexConsumer iron = ctx.solid(VfxTextures.WHITE);
        for (int k = 0; k < links; k++) link(ctx, iron, pos[k], tan[k], k, alpha, false);
        VertexConsumer rim = ctx.additive(VfxTextures.WHITE);
        for (int k = 0; k < links; k++) link(ctx, rim, pos[k], tan[k], k, alpha, true);

        // the shackle around the target
        if (shackle > 0 && hold > 0) {
            float r = shackle * easeOut(hold);
            VertexConsumer sv = ctx.energy(VfxTextures.RING);
            ctx.disc(sv, target, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * 1.25f, time * 0.05f, Colors.alpha(0.85f * alpha * hold, linkColor));
            ctx.disc(sv, target.add(0, 0.25, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, -time * 0.07f, Colors.alpha(0.6f * alpha * hold, color));
        }

        // pulses of energy flowing towards the target, and a bright head while seeking
        VertexConsumer spark = ctx.additive(VfxTextures.GLOW);
        for (int j = 0; j < 3; j++) {
            double f = ((time * 0.045 + j / 3.0) % 1.0);
            int idx = (int) Math.min(samples, Math.round(f * samples));
            ctx.billboard(spark, curve[idx], 0.45f * scale, 0, Colors.alpha(0.7f * alpha * hold, linkColor));
        }
        if (hold < 1) ctx.billboard(spark, curve[samples], 0.7f * scale, 0, Colors.alpha(0.9f * alpha * (1 - hold), linkColor));
    }

    /** One stadium-shaped link: a square-section wire tube following the link's outline. */
    private void link(VfxContext ctx, VertexConsumer vc, Vec3 c, Vec3 t, int index, float alpha, boolean rimPass) {
        Vec3[] b = VfxContext.basis(t);
        double turn = (index % 2 == 0 ? 0 : Math.PI / 2) + 0.15 * Math.sin(index * 1.7);
        Vec3 side = b[0].scale(Math.cos(turn)).add(b[1].scale(Math.sin(turn)));
        Vec3 normal = t.cross(side).normalize();
        float half = (LINK_LEN * 0.5f - LINK_WIDTH * 0.5f) * scale;
        float rad = LINK_WIDTH * 0.5f * scale;
        float wire = WIRE * 0.5f * scale;
        Vec3[] p = new Vec3[LOOP];
        Vec3[] out = new Vec3[LOOP];
        for (int i = 0; i < LOOP; i++) {
            // two semicircles joined by straights
            double a = i * Math.PI * 2 / LOOP;
            double cx = Math.cos(a), cy = Math.sin(a);
            Vec3 o = t.scale(cx).add(side.scale(cy));
            p[i] = c.add(t.scale((cx >= 0 ? half : -half) + cx * rad)).add(side.scale(cy * rad));
            out[i] = o.normalize();
        }
        for (int i = 0; i < LOOP; i++) {
            int j = (i + 1) % LOOP;
            for (int s = 0; s < 4; s++) {
                Vec3 da = corner(out[i], normal, s, wire), db = corner(out[i], normal, s + 1, wire);
                Vec3 ea = corner(out[j], normal, s, wire), eb = corner(out[j], normal, s + 1, wire);
                Vec3 faceN = corner(out[i], normal, s, 1).add(corner(out[i], normal, s + 1, 1)).normalize();
                int col;
                if (rimPass) {
                    // soul light catches the edges facing away from the light
                    float rimK = (float) Math.max(0, 0.6 - faceN.dot(LIGHT));
                    if (rimK <= 0.05f) continue;
                    col = Colors.alpha(0.55f * rimK * alpha, linkColor);
                } else {
                    float shade = 0.35f + 0.65f * (float) Math.max(0, faceN.dot(LIGHT));
                    col = Colors.argb(Math.round(235 * alpha), Colors.scale(Colors.lerpRgb(0x2E3533, color, 0.18f), 0.55f + 0.9f * shade));
                }
                ctx.quad(vc, p[i].add(da), p[j].add(ea), p[j].add(eb), p[i].add(db), 0, 0, 1, 1, col, col, col, col);
            }
        }
    }

    /** Corner {@code s} (0-3, wrapping) of the wire's square cross-section around a point on the loop. */
    private static Vec3 corner(Vec3 out, Vec3 normal, int s, float h) {
        int k = s & 3;
        double u = (k == 0 || k == 3) ? 1 : -1;
        double v = (k == 0 || k == 1) ? 1 : -1;
        return out.scale(u * h).add(normal.scale(v * h));
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        double u = 1 - t;
        return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t));
    }
}
