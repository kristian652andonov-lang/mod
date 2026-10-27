package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/** Energy sphere / shell with a fresnel rim. Can grow, shrink (implosion) or pulse. */
public class SphereVfx extends Vfx {
    public enum Mode { GROW, SHRINK, HOLD }

    private final Vec3 center;
    private final float r0;
    private final float r1;
    private final int color;
    private final Mode mode;
    private boolean voidShell;

    public SphereVfx(Vec3 center, float r0, float r1, int color, int lifetime, Mode mode) {
        super(lifetime);
        this.center = center;
        this.r0 = r0;
        this.r1 = r1;
        this.color = color;
        this.mode = mode;
    }

    /** Render as a dark void shell instead of an additive energy shell. */
    public SphereVfx voidShell() {
        this.voidShell = true;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float k = switch (mode) {
            case GROW -> easeOut(t);
            case SHRINK -> easeIn(t);
            case HOLD -> 1f;
        };
        float r = r0 + (r1 - r0) * k;
        float alpha = (mode == Mode.HOLD ? bump(t, 0.1f) : (mode == Mode.GROW ? 1 - easeIn(t) : 0.3f + 0.7f * t)) * ((color >>> 24) & 255) / 255f;
        int lat = ctx.segments(14), lon = ctx.segments(22);
        if (voidShell) {
            ctx.sphere(ctx.voidInterior(VfxTextures.WHITE), center, r, lat, lon, Colors.alpha(alpha * 0.85f, color), false);
            ctx.sphere(ctx.energy(VfxTextures.WHITE), center, r * 1.01f, lat, lon, Colors.alpha(alpha, color), true);
        } else {
            ctx.sphere(ctx.energy(VfxTextures.WHITE), center, r, lat, lon, Colors.alpha(alpha, color), true);
        }
    }
}
