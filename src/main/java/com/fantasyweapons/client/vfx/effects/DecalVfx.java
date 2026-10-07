package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
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
    /** Small rune circles orbiting the rim; -1 = automatic (rune circles get 2-4 by size, other decals none). */
    private int satellites = -1;
    /** Where it is drawn each frame instead of {@link #center} (hidden while it gives null). */
    private java.util.function.Function<Float, Vec3> track;

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

    /** Number of small circles orbiting the rim (0 disables them). */
    public DecalVfx satellites(int n) {
        this.satellites = n;
        return this;
    }

    /** Moves with {@code where} (given the partial tick), hidden while it gives null. */
    public DecalVfx follow(java.util.function.Function<Float, Vec3> where) {
        this.track = where;
        return this;
    }

    /** Alpha-blended (for dark decals like cracks). */
    public DecalVfx translucent() {
        this.translucent = true;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 center = track == null ? this.center : track.apply(ctx.partial);
        if (center == null) return;
        float t = progress(ctx.partial);
        float grow = growTime <= 0 ? 1 : easeOut(Math.min(1, t / growTime));
        float fade = t > 1 - fadeTime ? 1 - (t - (1 - fadeTime)) / fadeTime : 1;
        float alpha = clamp01(fade) * Math.min(1, grow * 1.5f) * ((color >>> 24) & 255) / 255f;
        var vc = translucent ? ctx.translucent(texture) : (energy ? ctx.energy(texture) : ctx.additive(texture));
        float r = radius * (0.6f + 0.4f * grow);
        float time = age + ctx.partial;
        ctx.disc(vc, center, ax, ay, r, spin * time, Colors.alpha(alpha, color));
        // smaller circles orbiting the rim, counter-rotating
        int n = satellites >= 0 ? satellites : texture.equals(VfxTextures.RUNE_CIRCLE) ? (radius >= 8 ? 4 : radius >= 4 ? 3 : 2) : 0;
        if (n > 0) {
            float dir = spin < 0 ? 1 : -1;
            float phase = dir * time * (Math.abs(spin) * 0.6f + 0.012f);
            float size = Math.min(2.4f, r * (n >= 4 ? 0.2f : 0.26f));
            ctx.satellites(vc, center, ax, ay, r * 1.12f, size, n, phase, -spin * 2.2f * time, Colors.alpha(alpha * 0.9f, color));
            if (!translucent) {
                ctx.satellites(ctx.additive(VfxTextures.GLOW), center, ax, ay, r * 1.12f, size * 1.25f, n, phase, 0, Colors.alpha(alpha * 0.22f, color));
            }
        }
    }
}
