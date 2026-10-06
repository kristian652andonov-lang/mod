package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.ChainVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.SeekerVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SkyCircleVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client visuals for Soulreaper. Palette: soul blue, pale spirit white, abyssal violet. */
public final class SoulreaperFx {
    static final int SOUL = 0x5A8CFF;
    static final int SPIRIT = 0xCFE0FF;
    static final int ABYSS = 0x2A1458;

    private SoulreaperFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.SOULREAPER_REAP, SoulreaperFx::reap);
        FxDispatcher.register(FxIds.SOULREAPER_CATCH, SoulreaperFx::catchFx);
        FxDispatcher.register(FxIds.SOULREAPER_REND, SoulreaperFx::rend);
        FxDispatcher.register(FxIds.SOULREAPER_TOLL, SoulreaperFx::toll);
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    /** A soul torn out of a victim drifts up and flies to the reaper. */
    static void soulToCaster(int casterId, Vec3 from, long seed) {
        Entity caster = entity(casterId);
        if (caster == null) return;
        SeekerVfx s = VfxManager.add(new SeekerVfx(from, new Vec3(0, 0.3, 0), caster.getId(), 0.18, 0.28f, SOUL, SPIRIT, 26).sprite(VfxTextures.SOUL));
        VfxManager.add(new FollowTrailVfx(s::now, 0.35f, Colors.argb(200, SOUL), Colors.argb(0, ABYSS), 9, 40));
    }

    private static void reap(FxPayload p) {
        Vec3 at = p.pos();
        VfxManager.add(new FlashVfx(at, 0.3f, 2f, Colors.argb(220, SOUL), 8).energy());
        VfxManager.add(new ShardBurstVfx(at, Vec3.ZERO, 1f, 0.2f, 10, 0.22f, Colors.argb(255, SPIRIT), Colors.argb(0, SOUL), 12, p.seed())
                .texture(VfxTextures.SPARK, true).physics(0.01f, 0.86f));
        soulToCaster(p.caster(), at, p.seed());
    }

    private static void catchFx(FxPayload p) {
        Entity e = entity(p.caster());
        if (e == null) return;
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.4f, 2.2f, Colors.argb(200, SOUL), 9).follow(e, new Vec3(0, 1.1, 0)).energy());
        VfxManager.add(new ShockwaveVfx(e.position().add(0, 1.1, 0), new Vec3(0, 1, 0), 0.2f, 1.8f, 0.2f, Colors.argb(200, SPIRIT), 8).energy());
    }

    private static void rend(FxPayload p) {
        Vec3 o = p.pos();
        Vec3 fwd = new Vec3(p.dir().x, 0, p.dir().z).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        float range = p.scale();
        float half = (float) Math.toRadians(p.power() / 2);
        float a0 = (float) (Math.PI / 2 + half), a1 = (float) (Math.PI / 2 - half);
        // the reaping arc sweeps from left to right, a wide soul-blue crescent with a spirit edge
        VfxManager.add(new SlashArcVfx(o, right, fwd, range * 0.85f, range * 0.35f, a0, a1, Colors.argb(230, SOUL), Colors.argb(255, SPIRIT), 12));
        VfxManager.add(new SlashArcVfx(o.add(0, -0.3, 0), right, fwd, range * 1.0f, range * 0.2f, a0, a1, Colors.argb(170, ABYSS), Colors.argb(220, SOUL), 15));
        VfxManager.add(new DecalVfx(o.subtract(0, 0.95, 0), new Vec3(0, 1, 0), range, Colors.argb(150, SOUL), VfxTextures.SWIRL, 20)
                .spin(-0.15f).energy().timing(0.1f, 0.5f));
        for (int id : p.entities()) {
            Entity v = entity(id);
            if (v == null) continue;
            soulToCaster(p.caster(), v.position().add(0, v.getBbHeight() * 0.6, 0), p.seed() + id);
        }
        CameraShake.add(o, 0.3f, 10);
    }

    private static void toll(FxPayload p) {
        Entity caster = entity(p.caster());
        Vec3 o = p.pos();
        int delay = Math.round(p.power());
        float radius = p.scale() > 0 ? p.scale() : 18f;
        // the spectral bell opens in the sky over the reaper, and tolls three times: rings sweep out over the whole
        // marked area and down from the bell
        SkyCircleVfx bell = VfxManager.add(new SkyCircleVfx(o.add(0, 15, 0), radius * 0.42f, 15, SOUL, SPIRIT, 0xE6FFFF, delay + 50));
        for (int i = 0; i < 3; i++) {
            int t = i * Math.max(1, delay / 3);
            FxScheduler.after(t, () -> {
                VfxManager.add(new ShockwaveVfx(o.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5f, radius, 0.5f, Colors.argb(200, SOUL), 22).energy());
                VfxManager.add(new ShockwaveVfx(o.add(0, 2.2, 0), new Vec3(0, 1, 0), 0.5f, radius * 0.55f, 0.25f, Colors.argb(160, SPIRIT), 16));
                VfxManager.add(new ShockwaveVfx(o.add(0, 14.6, 0), new Vec3(0, 1, 0), radius * 0.2f, radius * 0.7f, 0.3f, Colors.argb(170, SPIRIT), 18));
                bell.emit(o);
            });
        }
        VfxManager.add(new DecalVfx(o.add(0, 0.04, 0), new Vec3(0, 1, 0), radius * 0.36f, Colors.argb(220, SOUL), VfxTextures.RUNE_CIRCLE, delay + 40)
                .spin(0.06f).energy().timing(0.1f, 0.2f));
        VfxManager.add(new DecalVfx(o.add(0, 0.06, 0), new Vec3(0, 1, 0), radius * 0.7f, Colors.argb(170, SPIRIT), VfxTextures.RUNE_CIRCLE, delay + 40)
                .spin(-0.03f).energy().timing(0.15f, 0.2f));
        // every marked soul is chained to the reaper and burns with a sigil
        for (int id : p.entities()) {
            Entity v = entity(id);
            if (v == null || caster == null) continue;
            VfxManager.add(new ChainVfx(caster.position().add(0, 1.2, 0), ChainVfx.toEntity(v, 0.6),
                    Colors.argb(200, SOUL), Colors.argb(220, SPIRIT), delay + 6).shoot(6));
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.5f, 1.2f, Colors.argb(230, SOUL), delay + 30).follow(v, new Vec3(0, v.getBbHeight() + 0.6, 0))
                    .peak(0.1f).energy());
        }
        ScreenFx.zoneVignette(ABYSS, 0.35f, delay + 40, o, radius);
    }
}
