package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * A spectral chain between an anchor and a (moving) end point: shoots out, sags slightly, links drawn as small glowing
 * rings along an energy strand, then fades.
 */
public class ChainVfx extends Vfx {
    private final Vec3 anchor;
    private final Supplier<Vec3> end;
    private final int color;
    private final int linkColor;
    private int shoot = 4;

    public ChainVfx(Vec3 anchor, Supplier<Vec3> end, int color, int linkColor, int lifetime) {
        super(lifetime);
        this.anchor = anchor;
        this.end = end;
        this.color = color;
        this.linkColor = linkColor;
    }

    public ChainVfx shoot(int ticks) {
        this.shoot = Math.max(1, ticks);
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 e = end.get();
        if (e == null) return;
        float time = age + ctx.partial;
        float ext = easeOut(Math.min(1, time / shoot));
        float fade = clamp01((lifetime - time) / 6f);
        Vec3 tip = anchor.lerp(e, ext);
        double len = tip.distanceTo(anchor);
        int links = Math.max(2, (int) (len / 0.35));
        Vec3[] pts = new Vec3[links + 1];
        float[] w = new float[links + 1];
        int[] c = new int[links + 1];
        for (int i = 0; i <= links; i++) {
            double t = i / (double) links;
            double sag = Math.sin(t * Math.PI) * len * 0.06 * (1 - ext * 0.5);
            pts[i] = anchor.lerp(tip, t).subtract(0, sag, 0);
            w[i] = 0.12f;
            c[i] = Colors.alpha(fade * 0.8f, color);
        }
        ctx.ribbon(ctx.energy(VfxTextures.STREAK), pts, w, c, -time * 0.2f, 0.25f);
        var ring = ctx.additive(VfxTextures.RING);
        for (int i = 1; i < links; i++) {
            Vec3 d = pts[i + 1].subtract(pts[i - 1]);
            Vec3[] b = VfxContext.basis(d.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : d);
            Vec3 ax = i % 2 == 0 ? b[0] : b[1];
            ctx.plane(ring, pts[i], ax.scale(0.13), d.normalize().scale(0.2), Colors.alpha(fade, linkColor));
        }
    }
}
