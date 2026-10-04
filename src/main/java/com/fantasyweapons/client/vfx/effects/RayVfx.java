package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.WeaponAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A sustained giant energy ray from a living entity's weapon tip along its current aim (re-traced every frame against
 * blocks): layered beam, spiralling rings travelling down it and a blazing impact flare.
 */
public class RayVfx extends Vfx {
    private final LivingEntity caster;
    private final double range;
    private final float radius;
    private final int color;
    private final int core;
    private final double tipReach;

    public RayVfx(LivingEntity caster, double range, float radius, int color, int core, double tipReach, int lifetime) {
        super(lifetime);
        this.caster = caster;
        this.range = range;
        this.radius = radius;
        this.color = color;
        this.core = core;
        this.tipReach = tipReach;
    }

    @Override
    public void render(VfxContext ctx) {
        if (!caster.isAlive()) {
            kill();
            return;
        }
        float time = age + ctx.partial;
        float in = easeOut(Math.min(1, time / 6f));
        float out = clamp01((lifetime - time) / 6f);
        float a = in * out;
        if (a <= 0.01f) return;
        Vec3 start = WeaponAnchor.blade(caster, ctx.partial, tipReach);
        Vec3 look = caster.getViewVector(ctx.partial);
        Vec3 end = start.add(look.scale(range));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
        }
        float w = radius * 1.2f * a * (0.92f + 0.08f * (float) Math.sin(time * 1.9f));
        float len = (float) start.distanceTo(end);
        float scroll = -time * 0.35f;
        ctx.beam(ctx.additive(VfxTextures.GLOW), start, end, w * 1.8f, Colors.alpha(a * 0.22f, color), 0, 1);
        ctx.beam(ctx.energy(VfxTextures.STREAK), start, end, w * 1.1f, Colors.alpha(a * 0.75f, color), scroll, scroll + len * 0.15f);
        ctx.beam(ctx.additive(VfxTextures.LIGHTNING), start, end, w * 0.45f, Colors.alpha(a * 0.9f, core), 0, 1);
        // rings racing down the beam
        Vec3 d = end.subtract(start).normalize();
        Vec3[] b = VfxContext.basis(d);
        var ring = ctx.additive(VfxTextures.RING);
        for (int i = 0; i < 6; i++) {
            float cyc = (time * 0.08f + i / 6f) % 1f;
            Vec3 p = start.lerp(end, cyc);
            ctx.ring(ring, p, b[0], b[1], w * 0.6f, w * 1.0f, 20, Colors.alpha(a * (1 - cyc) * 0.6f, core));
        }
        ctx.billboard(ctx.additive(VfxTextures.FLASH), start, w * 1.6f, time * 0.2f, Colors.alpha(a * 0.8f, core));
        ctx.billboard(ctx.additive(VfxTextures.FLASH), end, w * 2.2f, -time * 0.25f, Colors.alpha(a * 0.7f, color));
        ctx.billboard(ctx.additive(VfxTextures.STAR), end, w * 1.6f, time * 0.1f, Colors.alpha(a * 0.8f, core));
    }
}
