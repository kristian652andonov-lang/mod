package com.fantasyweapons.client.vfx.effects;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Camera-facing glow/flash sprite that grows and fades. Optionally follows an entity. */
public class FlashVfx extends Vfx {
    private final Vec3 pos;
    private final float size0;
    private final float size1;
    private final int color;
    private final ResourceLocation texture;
    private float spin;
    private float rotation;
    private boolean energy;
    private int followId = -1;
    private Vec3 followOffset = Vec3.ZERO;
    private float peak = 0.15f;

    public FlashVfx(Vec3 pos, float size0, float size1, int color, int lifetime) {
        this(pos, size0, size1, color, lifetime, VfxTextures.GLOW);
    }

    public FlashVfx(Vec3 pos, float size0, float size1, int color, int lifetime, ResourceLocation texture) {
        super(lifetime);
        this.pos = pos;
        this.size0 = size0;
        this.size1 = size1;
        this.color = color;
        this.texture = texture;
    }

    public FlashVfx spin(float radiansPerTick) {
        this.spin = radiansPerTick;
        return this;
    }

    public FlashVfx rotation(float r) {
        this.rotation = r;
        return this;
    }

    public FlashVfx energy() {
        this.energy = true;
        return this;
    }

    public FlashVfx peak(float p) {
        this.peak = p;
        return this;
    }

    public FlashVfx follow(Entity e, Vec3 offset) {
        this.followId = e.getId();
        this.followOffset = offset;
        return this;
    }

    @Override
    public void render(VfxContext ctx) {
        float t = progress(ctx.partial);
        float size = size0 + (size1 - size0) * easeOut(t);
        float alpha = bump(t, peak) * ((color >>> 24) & 255) / 255f;
        Vec3 p = pos;
        if (followId >= 0 && Minecraft.getInstance().level != null) {
            Entity e = Minecraft.getInstance().level.getEntity(followId);
            if (e == null || e.isRemoved()) {
                kill(); // the followed entity is gone
                return;
            }
            p = e.getPosition(ctx.partial).add(followOffset);
        }
        var vc = energy ? ctx.energy(texture) : ctx.additive(texture);
        ctx.billboard(vc, p, size, rotation + spin * (age + ctx.partial), Colors.alpha(alpha, color));
    }
}
