package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.ModelPartVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.JudgementRayVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client visuals for Aetherlance. Palette: aether cyan, white light, celestial gold accents. */
public final class AetherlanceFx {
    static final int AETHER = 0x5FF3FF;
    static final int LIGHT = 0xF2FFFF;
    static final int GOLD = 0xFFD978;
    static final int DEEP = 0x0E5A73;

    private AetherlanceFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.AETHERLANCE_THRUST, AetherlanceFx::thrust);
        FxDispatcher.register(FxIds.AETHERLANCE_BOLT, AetherlanceFx::bolt);
        FxDispatcher.register(FxIds.AETHERLANCE_PIERCE, AetherlanceFx::pierce);
        FxDispatcher.register(FxIds.AETHERLANCE_BOLT_END, AetherlanceFx::boltEnd);
        FxDispatcher.register(FxIds.AETHERLANCE_CHARGE, AetherlanceFx::charge);
        FxDispatcher.register(FxIds.AETHERLANCE_RAY, AetherlanceFx::ray);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    private static void thrust(FxPayload p) {
        Vec3 from = p.pos();
        Vec3 dir = p.dir().normalize();
        int pierced = Math.round(p.power());
        VfxManager.add(new BeamVfx(from.subtract(dir.scale(1.2)), from.add(dir.scale(1.5 + pierced * 1.2)), 0.35f, Colors.argb(230, AETHER), 6));
        VfxManager.add(new FlashVfx(from, 0.3f, 1.6f, Colors.argb(220, LIGHT), 6, VfxTextures.STAR).spin(0.3f));
    }

    private static void bolt(FxPayload p) {
        Vec3 start = p.pos();
        Vec3 vel = p.dir();
        float scale = Math.max(0.4f, p.scale());
        int life = (int) Math.ceil(p.power() / Math.max(0.1, vel.length())) + 2;
        Quaternionf face = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) vel.x, (float) vel.y, (float) vel.z).normalize());
        // the artist's projectile model rides the bolt
        ModelPartVfx model = VfxManager.add(new ModelPartVfx("aetherlance", start, life, "projectile")
                .position(t -> start.add(vel.scale(t * life))).rotation(t -> new Quaternionf(face).rotateY(t * life * 0.6f)).scale(t -> 2.4f * scale));
        OrbVfx glow = VfxManager.add(new OrbVfx(t -> start.add(vel.scale(t)), 0.55f * scale, AETHER, LIGHT, life).fades(1, 1));
        FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(glow::now, 0.9f * scale, Colors.argb(230, AETHER), Colors.argb(0, DEEP), 9, life + 12));
        FollowTrailVfx core = VfxManager.add(new FollowTrailVfx(glow::now, 0.3f * scale, Colors.argb(255, LIGHT), Colors.argb(0, AETHER), 6, life + 12)
                .texture(VfxTextures.LIGHTNING, false));
        VfxManager.add(new FlashVfx(start, 0.3f, 2.2f * scale, Colors.argb(230, AETHER), 7).energy());
        VfxManager.add(new ShockwaveVfx(start, vel.normalize(), 0.2f, 1.6f * scale, 0.2f, Colors.argb(220, LIGHT), 7).energy());
        FxProjectiles.track(p.seed(), model, glow);
    }

    private static void pierce(FxPayload p) {
        Vec3 at = p.pos();
        Vec3 d = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        VfxManager.add(new FlashVfx(at, 0.3f, 1.8f, Colors.argb(230, AETHER), 6).energy());
        VfxManager.add(new ShardBurstVfx(at, d, 0.5f, 0.35f, 10, 0.2f, Colors.argb(255, LIGHT), Colors.argb(0, AETHER), 10, p.seed() + (long) (at.x * 13))
                .texture(VfxTextures.SPARK, true).physics(0.01f, 0.85f));
        VfxManager.add(new ShockwaveVfx(at, d, 0.1f, 1.3f, 0.18f, Colors.argb(220, AETHER), 6).energy());
    }

    private static void boltEnd(FxPayload p) {
        FxProjectiles.end(p.seed());
        Vec3 at = p.pos();
        float s = Math.max(0.4f, p.scale());
        VfxManager.add(new FlashVfx(at, 0.4f, 2.6f * s, Colors.argb(230, AETHER), 8).energy());
        VfxManager.add(new FlashVfx(at, 0.3f, 1.5f * s, Colors.argb(255, LIGHT), 6, VfxTextures.STAR).spin(0.2f));
        VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, 0.3f, 14, 0.22f, Colors.argb(255, LIGHT), Colors.argb(0, AETHER), 12, p.seed())
                .texture(VfxTextures.SPARK, true).physics(0.02f, 0.86f));
    }

    private static void charge(FxPayload p) {
        Entity e = entity(p.caster());
        int ticks = Math.round(p.power());
        // a ring bursts off the ground at the start of the charge (not when charging out of mid-air)
        Vec3 o = p.pos();
        Vec3 g = FrostrendFx.groundOrNull(o, 1.5);
        if (g != null) VfxManager.add(new ShockwaveVfx(g.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.3f, 3f, 0.4f, Colors.argb(220, AETHER), 10).energy());
        VfxManager.add(new FlashVfx(o.add(0, 1, 0), 0.5f, 2.5f, Colors.argb(200, LIGHT), 8).energy());
        if (e instanceof LivingEntity le) {
            int[] left = {ticks + 2};
            VfxManager.add(new FollowTrailVfx(() -> left[0]-- > 0 && le.isAlive() ? le.position().add(0, 1, 0) : null, 2.2f, Colors.argb(200, AETHER),
                    Colors.argb(0, DEEP), 10, ticks + 30).texture(VfxTextures.GLOW, false));
            int[] left2 = {ticks + 2};
            VfxManager.add(new FollowTrailVfx(() -> left2[0]-- > 0 && le.isAlive() ? com.fantasyweapons.client.vfx.WeaponAnchor.blade(le, 1f, 2.6) : null,
                    0.5f, Colors.argb(255, LIGHT), Colors.argb(0, AETHER), 10, ticks + 30).texture(VfxTextures.LIGHTNING, false));
        }
    }

    private static void ray(FxPayload p) {
        Entity e = entity(p.caster());
        if (!(e instanceof LivingEntity le)) return;
        int duration = Math.round(p.power());
        VfxManager.add(new JudgementRayVfx(le, p.level(), Math.max(0.6f, p.scale()), AETHER, LIGHT, GOLD, 2.6, duration));
        if (le == Minecraft.getInstance().player) ScreenFx.flash(AETHER, 0.2f, 8);
        CameraShake.add(le.position(), 0.25f, 12);
    }
}
