package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * A glowing energy body moving along a path function (time in ticks → position): projectiles, souls, orbs. Layered
 * glow + bright core + optional rotating sprite (soul wisp, star...).
 */
public class OrbVfx extends Vfx {
    private Function<Float, Vec3> path;
    private final float size;
    private final int color;
    private final int coreColor;
    private ResourceLocation sprite;
    private int spriteColor;
    private float spin;
    private int fadeIn = 3;
    private int fadeOut = 4;

    public OrbVfx(Function<Float, Vec3> path, float size, int color, int coreColor, int lifetime) {
        super(lifetime);
        this.path = path;
        this.size = size;
        this.color = color;
        this.coreColor = coreColor;
    }

    public OrbVfx sprite(ResourceLocation tex, int color, float spin) {
        this.sprite = tex;
        this.spriteColor = color;
        this.spin = spin;
        return this;
    }

    public OrbVfx fades(int in, int out) {
        this.fadeIn = in;
        this.fadeOut = out;
        return this;
    }

    public Vec3 position(float time) {
        return path.apply(time);
    }

    /** Current position (for trails). */
    public Vec3 now() {
        return isDead() ? null : path.apply((float) age);
    }

    @Override
    public void render(VfxContext ctx) {
        float time = age + ctx.partial;
        Vec3 p = path.apply(time);
        if (p == null) return;
        float a = Math.min(1, time / Math.max(1, fadeIn)) * Math.min(1, (lifetime - time) / Math.max(1, fadeOut));
        if (a <= 0) return;
        float pulse = 0.9f + 0.1f * (float) Math.sin(time * 1.3f);
        ctx.billboard(ctx.additive(VfxTextures.GLOW), p, size * 2.6f * pulse, 0, Colors.alpha(a * 0.55f, color));
        ctx.billboard(ctx.energy(VfxTextures.FLASH), p, size * 1.3f, time * 0.2f, Colors.alpha(a, color));
        ctx.billboard(ctx.additive(VfxTextures.FLASH), p, size * 0.7f, 0, Colors.alpha(a, coreColor));
        if (sprite != null) ctx.billboard(ctx.additive(sprite), p, size * 1.6f, time * spin, Colors.alpha(a, spriteColor));
    }
}
