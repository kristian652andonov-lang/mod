package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * A living thread between two moving points (a caster's focus and their target): a vine that mends, a hex that drains,
 * a curse that feeds. It sags and writhes along its length, with motes flowing from {@code from} to {@code to} (or
 * back, for a drain).
 */
public class TetherVfx extends Vfx {
    private final Supplier<Vec3> from, to;
    private final int color, core;
    private final float width;
    private ResourceLocation texture = VfxTextures.STREAK;
    private ResourceLocation mote = VfxTextures.GLOW;
    private boolean translucent;
    private float sag = 0.6f, writhe = 0.25f, flow = 1;
    private int motes = 6;
    /** -1: motes glow in the core colour; otherwise drawn alpha-blended in this colour. */
    private int moteColor = -1;
    private float moteSize = 2.4f;
    private int grow = 6;

    public TetherVfx(Supplier<Vec3> from, Supplier<Vec3> to, float width, int color, int core, int lifetime) {
        super(lifetime);
        this.from = from;
        this.to = to;
        this.width = width;
        this.color = color;
        this.core = core;
    }

    /** A textured thread (bark for a vine), drawn translucent instead of glowing. */
    public TetherVfx texture(ResourceLocation tex, boolean translucent) {
        this.texture = tex;
        this.translucent = translucent;
        return this;
    }

    public TetherVfx motes(ResourceLocation tex, int count, float flow) {
        this.mote = tex;
        this.motes = count;
        this.flow = flow;
        return this;
    }

    /**
     * Motes drawn alpha-blended in their own colour and size (times the thread's width) instead of glowing in the core
     * colour: for solid things carried along the thread, like leaves on a vine.
     */
    public TetherVfx moteLook(int color, float size) {
        this.moteColor = color;
        this.moteSize = size;
        return this;
    }

    public TetherVfx shape(float sag, float writhe) {
        this.sag = sag;
        this.writhe = writhe;
        return this;
    }

    public TetherVfx grow(int ticks) {
        this.grow = Math.max(1, ticks);
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 a = from.get(), b = to.get();
        if (a == null || b == null) return;
        float ticks = age + ctx.partial;
        float reach = Math.min(1, ticks / grow);
        int n = 18;
        Vec3[] pts = new Vec3[n + 1];
        float[] ws = new float[n + 1];
        int[] cs = new int[n + 1];
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        double len = d.length();
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n * reach;
            double sagY = -Math.sin(u * Math.PI) * sag * Math.min(1, len / 8);
            double w1 = Math.sin(u * 9 + ticks * 0.35) * writhe * Math.sin(u * Math.PI);
            double w2 = Math.cos(u * 7 - ticks * 0.28) * writhe * 0.7 * Math.sin(u * Math.PI);
            pts[i] = a.add(d.scale(u)).add(0, sagY + w2, 0).add(side.scale(w1));
            ws[i] = width * (0.75f + 0.25f * (float) Math.sin(u * 12 + ticks * 0.4));
            cs[i] = Colors.alpha(0.9f, color);
        }
        float scroll = -ticks * 0.15f * flow;
        ctx.ribbon(translucent ? ctx.translucent(texture) : ctx.additive(texture), pts, ws, cs, scroll, 0.25f);
        if (!translucent) {
            float[] cw = new float[n + 1];
            int[] cc = new int[n + 1];
            for (int i = 0; i <= n; i++) {
                cw[i] = ws[i] * 0.35f;
                cc[i] = Colors.alpha(0.9f, core);
            }
            ctx.ribbon(ctx.additive(VfxTextures.LIGHTNING), pts, cw, cc, 0, 1f / n);
        }
        // motes flowing along the thread
        var glow = moteColor == -1 ? ctx.additive(mote) : ctx.translucent(mote);
        for (int k = 0; k < motes; k++) {
            float u = ((ticks * 0.04f * flow + k / (float) motes) % 1f + 1f) % 1f;
            if (u > reach) continue;
            float f = u * n;
            int i = Math.min(n - 1, (int) f);
            Vec3 p = pts[i].lerp(pts[i + 1], f - i);
            ctx.billboard(glow, p, width * moteSize, ticks * 0.1f + k, Colors.alpha(0.85f * (float) Math.sin(u * Math.PI), moteColor == -1 ? core : moteColor));
        }
        ctx.billboard(ctx.additive(VfxTextures.GLOW), pts[n], width * 4f, 0, Colors.alpha(0.5f, color));
    }
}
