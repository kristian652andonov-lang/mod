package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client mirror of a server homing projectile: integrates the same velocity/steering rule every tick so it follows
 * the server's path closely, rendered as a layered glowing body with an optional sprite. Ended by the impact payload.
 */
public class SeekerVfx extends Vfx {
    private Vec3 pos;
    private Vec3 prev;
    private Vec3 vel;
    private final int targetId;
    private final double turn;
    private final float size;
    private final int color;
    private final int core;
    private ResourceLocation sprite;
    private Vec3 lastRender;

    public SeekerVfx(Vec3 start, Vec3 vel, int targetId, double turn, float size, int color, int core, int lifetime) {
        super(lifetime);
        this.pos = this.prev = this.lastRender = start;
        this.vel = vel;
        this.targetId = targetId;
        this.turn = turn;
        this.size = size;
        this.color = color;
        this.core = core;
    }

    public SeekerVfx sprite(ResourceLocation tex) {
        this.sprite = tex;
        return this;
    }

    /** Last rendered (interpolated) position; null once dead. */
    public Vec3 now() {
        return isDead() ? null : lastRender;
    }

    public Vec3 velocity() {
        return vel;
    }

    @Override
    public void tick() {
        super.tick();
        prev = pos;
        var level = Minecraft.getInstance().level;
        Entity t = targetId >= 0 && level != null ? level.getEntity(targetId) : null;
        if (t != null && t.isAlive()) {
            Vec3 want = t.getBoundingBox().getCenter().subtract(pos).normalize().scale(vel.length());
            vel = vel.add(want.subtract(vel).scale(turn));
        }
        pos = pos.add(vel);
    }

    @Override
    public void render(VfxContext ctx) {
        Vec3 p = prev.lerp(pos, ctx.partial);
        lastRender = p;
        float time = age + ctx.partial;
        float a = Math.min(1, time / 3f);
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 1.7f);
        ctx.billboard(ctx.additive(VfxTextures.GLOW), p, size * 2.8f * pulse, 0, Colors.alpha(a * 0.5f, color));
        ctx.billboard(ctx.additive(VfxTextures.FLASH), p, size * 0.8f, 0, Colors.alpha(a, core));
        if (sprite != null) {
            Vec3 d = vel.lengthSqr() < 1e-6 ? ctx.camUp : vel.normalize();
            ctx.stretched(ctx.additive(sprite), p.subtract(d.scale(size * 0.3)), d.scale(-1), size * 2.2f, size * 1.4f, Colors.alpha(a, color));
        }
    }
}
