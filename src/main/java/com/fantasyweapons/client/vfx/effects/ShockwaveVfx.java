package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Expanding flat ring in an arbitrary plane (ground shockwaves, radial bursts, rings around impacts). */
public class ShockwaveVfx extends Vfx {
    private final Vec3 center;
    private final Vec3 ax;
    private final Vec3 ay;
    private final float r0;
    private final float r1;
    private final float thickness;
    private final int color;
    private ResourceLocation texture = VfxTextures.SHOCKWAVE;
    private boolean energy;
    private float spin;

    public ShockwaveVfx(Vec3 center, Vec3 normal, float r0, float r1, float thickness, int color, int lifetime) {
        super(lifetime);
        this.center = center;
        Vec3[] b = VfxContext.basis(normal);
        this.ax = b[0];
        this.ay = b[1];
        this.r0 = r0;
        this.r1 = r1;
        this.thickness = thickness;
        this.color = color;
    }

    public ShockwaveVfx texture(ResourceLocation tex) {
        this.texture = tex;
        return this;
    }

    public ShockwaveVfx energy() {
        this.energy = true;
        return this;
    }

    public ShockwaveVfx spin(float s) {
        this.spin = s;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float r = r0 + (r1 - r0) * easeOut(t);
        float alpha = (1 - easeIn(t)) * ((color >>> 24) & 255) / 255f;
        var vc = energy ? ctx.energy(texture) : ctx.additive(texture);
        if (texture == VfxTextures.SHOCKWAVE || texture == VfxTextures.RING || texture == VfxTextures.RUNE_CIRCLE) {
            ctx.disc(vc, center, ax, ay, r, spin * (age + ctx.partial), Colors.alpha(alpha, color));
        } else {
            float th = thickness * (1 - t * 0.5f);
            ctx.ring(vc, center, ax, ay, Math.max(0, r - th), r, ctx.segments(48), Colors.alpha(alpha, color));
        }
    }
}
