package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.world.phys.Vec3;

/** Straight energy beam (camera facing) with a white-hot core, that flares and thins out. */
public class BeamVfx extends Vfx {
    private final Vec3 from;
    private final Vec3 to;
    private final float width;
    private final int color;

    public BeamVfx(Vec3 from, Vec3 to, float width, int color, int lifetime) {
        super(lifetime);
        this.from = from;
        this.to = to;
        this.width = width;
        this.color = color;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float w = width * (t < 0.15f ? t / 0.15f : 1 - easeIn((t - 0.15f) / 0.85f));
        float alpha = (1 - easeIn(t)) * ((color >>> 24) & 255) / 255f;
        float len = (float) from.distanceTo(to);
        float scroll = -(age + ctx.partial) * 0.2f;
        ctx.beam(ctx.energy(VfxTextures.STREAK), from, to, w * 2.2f, Colors.alpha(alpha * 0.7f, color), scroll, scroll + len * 0.25f);
        ctx.beam(ctx.additive(VfxTextures.LIGHTNING), from, to, w, Colors.alpha(alpha, Colors.brighten(color, 0.6f)), 0, 1);
    }
}
