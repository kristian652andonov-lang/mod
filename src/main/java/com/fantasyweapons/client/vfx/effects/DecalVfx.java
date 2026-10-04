package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A flat textured decal (rune circle, cracks, scorch) lying in a plane, with grow-in, optional spin and fade-out. */
public class DecalVfx extends Vfx {
    private final Vec3 center;
    private final Vec3 ax;
    private final Vec3 ay;
    private final float radius;
    private final int color;
    private final ResourceLocation texture;
    private float spin;
    private float growTime = 0.15f;
    private float fadeTime = 0.3f;
    private boolean energy;
    private boolean translucent;

    public DecalVfx(Vec3 center, Vec3 normal, float radius, int color, ResourceLocation texture, int lifetime) {
        super(lifetime);
        this.center = center;
        Vec3[] b = VfxContext.basis(normal);
        this.ax = b[0];
        this.ay = b[1];
        this.radius = radius;
        this.color = color;
        this.texture = texture;
    }

    public DecalVfx spin(float radiansPerTick) {
        this.spin = radiansPerTick;
        return this;
    }

    public DecalVfx timing(float grow, float fade) {
        this.growTime = grow;
        this.fadeTime = fade;
        return this;
    }

    public DecalVfx energy() {
        this.energy = true;
        return this;
    }

    /** Alpha-blended (for dark decals like cracks). */
    public DecalVfx translucent() {
        this.translucent = true;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float grow = growTime <= 0 ? 1 : easeOut(Math.min(1, t / growTime));
        float fade = t > 1 - fadeTime ? 1 - (t - (1 - fadeTime)) / fadeTime : 1;
        float alpha = clamp01(fade) * Math.min(1, grow * 1.5f) * ((color >>> 24) & 255) / 255f;
        var vc = translucent ? ctx.translucent(texture) : (energy ? ctx.energy(texture) : ctx.additive(texture));
        ctx.disc(vc, center, ax, ay, radius * (0.6f + 0.4f * grow), spin * (age + ctx.partial), Colors.alpha(alpha, color));
    }
}
