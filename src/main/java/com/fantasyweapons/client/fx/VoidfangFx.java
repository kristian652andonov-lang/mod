package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.fx.voidfang.RiftVfx;
import com.fantasyweapons.client.fx.voidfang.VoidDomeVfx;
import com.fantasyweapons.client.fx.voidfang.VoidWaveVfx;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.Vfx;
import com.fantasyweapons.client.vfx.VfxContext;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.ModelPartVfx;
import com.fantasyweapons.client.vfx.effects.RibbonTrailVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SlashArcVfx;
import com.fantasyweapons.client.vfx.effects.SphereVfx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** Client visuals for every Voidfang ability. Palette: void purple, abyss black, violet-white edges. */
public final class VoidfangFx {
    static final int VOID = 0x9B4DFF;
    static final int DEEP = 0x4D1A99;
    static final int EDGE = 0xE2C8FF;
    static final int HOT = 0xF4EAFF;

    private VoidfangFx() {
    }

    static void register() {
        FxDispatcher.register(FxIds.VOID_SLASH, VoidfangFx::voidSlash);
        FxDispatcher.register(FxIds.VOID_BLINK, VoidfangFx::voidBlink);
        FxDispatcher.register(FxIds.VOID_RIFT, VoidfangFx::riftTear);
        FxDispatcher.register(FxIds.VOID_RIFT_COLLAPSE, VoidfangFx::riftCollapse);
        FxDispatcher.register(FxIds.VOID_MARK_POP, VoidfangFx::markPop);
        FxDispatcher.register(FxIds.VOID_EXECUTION, VoidfangFx::execution);
        FxDispatcher.register(FxIds.VOID_DIMENSION, VoidfangFx::dimension);
        FxDispatcher.register(FxIds.VOID_DIMENSION_STRIKE, VoidfangFx::dimensionStrike);
        FxDispatcher.register(FxIds.VOID_DIMENSION_COLLAPSE, VoidfangFx::dimensionCollapse);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void voidSlash(FxPayload p) {
        Vec3 end = p.points().isEmpty() ? p.pos().add(p.dir().scale(14)) : p.points().get(0);
        VoidWaveVfx wave = VfxManager.add(new VoidWaveVfx(p.pos(), p.dir(), end, p.power(), p.scale() * 1.15f,
                Colors.argb(235, VOID), Colors.argb(255, HOT)));
        VfxManager.add(new FlashVfx(p.pos().add(p.dir().scale(0.8)), 0.4f, 2.4f, Colors.argb(220, VOID), 8).energy());
        int travel = wave.travelTicks();
        FxScheduler.after(travel, () -> {
            VfxManager.add(new FlashVfx(end, 0.5f, 2.6f, Colors.argb(220, VOID), 10).energy());
            VfxManager.add(new ShardBurstVfx(end, p.dir(), 0.85f, 0.32f, 18, 0.28f, Colors.argb(255, EDGE), Colors.argb(0, DEEP), 16, p.seed()));
        });
    }

    private static void voidBlink(FxPayload p) {
        Vec3 start = p.pos();
        Vec3 dest = p.dir();
        Vec3 riftPos = p.points().isEmpty() ? start.add(0, 1, 0) : p.points().get(0);
        boolean enhanced = p.level() == 1;
        int riftTicks = Math.max(10, Math.round(p.scale() * 20));
        Vec3 dir = dest.subtract(start);
        Vec3 chest = new Vec3(0, 1.0, 0);

        // 1. the caster dissolves into the void at the origin
        VfxManager.add(new ShardBurstVfx(start.add(chest), new Vec3(0, 1, 0), 1f, 0.18f, 36, 0.22f, Colors.argb(255, EDGE), Colors.argb(0, VOID),
                22, p.seed()).texture(VfxTextures.SHARD, false).physics(-0.006f, 0.9f));
        VfxManager.add(new FlashVfx(start.add(chest), 0.6f, 2.8f, Colors.argb(200, VOID), 10).energy());

        // 2. teleport trail through the void
        List<Vec3> path = new ArrayList<>();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        RandomSource r = RandomSource.create(p.seed());
        for (int i = 0; i <= 14; i++) {
            double t = i / 14.0;
            double wob = Math.sin(t * Math.PI) * (r.nextDouble() - 0.5) * 0.4;
            path.add(start.add(chest).add(dir.scale(t)).add(side.scale(wob)));
        }
        VfxManager.add(new RibbonTrailVfx(path, 0.9f, Colors.argb(240, EDGE), Colors.argb(180, DEEP), 14));
        VfxManager.add(new RibbonTrailVfx(path, 2.0f, Colors.argb(110, VOID), Colors.argb(40, DEEP), 10).texture(VfxTextures.GLOW));

        // 3. reappearing mid-slash at the destination
        Vec3 arrive = dest.add(chest);
        Vec3 fwd = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        VfxManager.add(new SlashArcVfx(arrive, right.add(0, 0.35, 0), fwd, 2.6f, 0.7f, (float) Math.toRadians(-30), (float) Math.toRadians(210),
                Colors.argb(235, VOID), Colors.argb(255, HOT), 12));
        VfxManager.add(new SlashArcVfx(arrive.add(0, -0.25, 0), right.add(0, -0.3, 0), fwd, 2.2f, 0.55f, (float) Math.toRadians(210),
                (float) Math.toRadians(-30), Colors.argb(200, DEEP), Colors.argb(255, EDGE), 14));
        VfxManager.add(new ShockwaveVfx(dest.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4f, 4f, 0.5f, Colors.argb(220, VOID), 14));
        VfxManager.add(new FlashVfx(arrive, 0.5f, 3.0f, Colors.argb(230, HOT), 8, VfxTextures.FLASH));
        CameraShake.add(dest, 0.5f, 8);

        // 4. dimensional tear left at the origin
        VfxManager.add(new RiftVfx(riftPos, fwd, 2.5f, enhanced ? 1.15f : 0.85f, VOID, EDGE, riftTicks, p.seed()));
        VfxManager.add(new FlashVfx(riftPos, 0.2f, 1.8f, Colors.argb(enhanced ? 200 : 120, VOID), riftTicks).peak(0.1f).energy());
        if (enhanced) {
            VfxManager.add(new ShardBurstVfx(riftPos.add(side.scale(2.0)), Vec3.ZERO, 1f, 0.05f, 24, 0.22f, Colors.argb(230, EDGE),
                    Colors.argb(0, VOID), riftTicks, p.seed() + 7).attract(riftPos, 0.025f).physics(0, 0.9f));
        }
        hitFlashes(p, VOID);
    }

    private static void riftTear(FxPayload p) {
        Vec3 c = p.pos();
        float radius = p.scale();
        int duration = Math.round(p.power());
        float height = radius * 2.1f;
        RandomSource r = RandomSource.create(p.seed());

        VfxManager.add(new RiftVfx(c, p.dir(), height, radius * 0.95f, VOID, EDGE, duration + 6, p.seed()));
        // the artist's dimension_rift geometry anchors the centre of the tear
        float modelScale = height / 2.4f;
        VfxManager.add(new ModelPartVfx("voidfang", c, duration + 6, "dimension_rift", "dimension_rift_back")
                .rotation(t -> new Quaternionf().rotationY((float) (Math.atan2(p.dir().x, p.dir().z) + t * 0.6)))
                .scale(t -> modelScale * (t < 0.12f ? Vfx.easeOut(t / 0.12f) : (t > 0.85f ? 1 - (t - 0.85f) / 0.15f : 1f)))
                .alpha(t -> t > 0.9f ? (1 - t) / 0.1f : 1f));
        VfxManager.add(new DecalVfx(groundBelow(c, height * 0.5), new Vec3(0, 1, 0), radius * 1.4f, Colors.argb(220, VOID),
                VfxTextures.RUNE_CIRCLE, duration + 6).spin(0.05f).energy());
        VfxManager.add(new ShockwaveVfx(c, p.dir(), 0.5f, radius * 1.6f, 0.4f, Colors.argb(200, EDGE), 12));
        // matter being dragged in, in waves
        for (int wave = 0; wave < Math.max(1, duration / 12); wave++) {
            long seed = p.seed() + wave * 31L;
            FxScheduler.after(wave * 12, () -> {
                Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).normalize().scale(radius * 1.8);
                VfxManager.add(new ShardBurstVfx(c.add(off), Vec3.ZERO, 1f, 0.06f, 16, 0.25f, Colors.argb(240, EDGE), Colors.argb(0, VOID),
                        20, seed).attract(c, 0.05f).physics(0, 0.88f));
            });
        }
        CameraShake.add(c, 0.4f, 16);
    }

    private static void riftCollapse(FxPayload p) {
        Vec3 c = p.pos();
        float radius = p.scale();
        VfxManager.add(new SphereVfx(c, radius * 1.3f, 0.2f, Colors.argb(230, VOID), 6, SphereVfx.Mode.SHRINK));
        FxScheduler.after(5, () -> {
            VfxManager.add(new FlashVfx(c, 1f, radius * 3.5f, Colors.argb(255, HOT), 10, VfxTextures.FLASH));
            VfxManager.add(new FlashVfx(c, 1f, radius * 4.5f, Colors.argb(200, VOID), 16).energy());
            VfxManager.add(new ShockwaveVfx(groundBelow(c, radius), new Vec3(0, 1, 0), 0.5f, radius * 2.6f, 0.8f, Colors.argb(230, EDGE), 18));
            VfxManager.add(new ShardBurstVfx(c, Vec3.ZERO, 1f, 0.55f, 40, 0.32f, Colors.argb(255, EDGE), Colors.argb(0, DEEP), 22, p.seed()));
            CameraShake.add(c, 1.0f, 20);
        });
        hitFlashes(p, VOID);
    }

    private static void markPop(FxPayload p) {
        float s = Math.max(1f, p.scale());
        int stacks = Math.max(1, p.level());
        VfxManager.add(new FlashVfx(p.pos(), 0.3f, 1.4f * s + stacks * 0.25f, Colors.argb(230, VOID), 9, VfxTextures.STAR).spin(0.2f));
        VfxManager.add(new FlashVfx(p.pos(), 0.3f, 1.8f * s, Colors.argb(200, EDGE), 7).energy());
        VfxManager.add(new ShardBurstVfx(p.pos(), Vec3.ZERO, 1f, 0.3f, 8 + stacks * 4, 0.22f, Colors.argb(255, EDGE), Colors.argb(0, VOID), 14,
                (long) (p.pos().x * 1000)));
    }

    private static void execution(FxPayload p) {
        Vec3 c = p.pos();
        Vec3 start = p.dir();
        Vec3 dest = p.points().isEmpty() ? start : p.points().get(0);
        boolean executed = p.power() > 0;
        float h = Math.max(1.2f, p.scale());
        Vec3 toward = c.subtract(dest);
        Vec3 fwd = toward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(toward.x, 0, toward.z).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();

        // afterimage path start → behind target
        VfxManager.add(new RibbonTrailVfx(List.of(start.add(0, 1, 0), start.lerp(dest, 0.5).add(0, 1.4, 0), dest.add(0, 1, 0)), 0.8f,
                Colors.argb(220, EDGE), Colors.argb(140, DEEP), 12));
        // X slash through the target
        VfxManager.add(new SlashArcVfx(c, right.add(0, 1, 0).normalize(), fwd, h * 1.3f, 0.6f, (float) Math.toRadians(-60), (float) Math.toRadians(240),
                Colors.argb(240, VOID), Colors.argb(255, HOT), 10));
        FxScheduler.after(3, () -> VfxManager.add(new SlashArcVfx(c, right.add(0, -1, 0).normalize(), fwd, h * 1.3f, 0.6f,
                (float) Math.toRadians(240), (float) Math.toRadians(-60), Colors.argb(240, VOID), Colors.argb(255, HOT), 10)));
        // vertical void slice
        VfxManager.add(new BeamVfx(c.add(0, h * 1.6, 0), c.subtract(0, h * 1.1, 0), 0.45f, Colors.argb(255, EDGE), 12));
        VfxManager.add(new RiftVfx(c, fwd, h * 2.4f, 0.5f, VOID, EDGE, 16, p.seed()));
        VfxManager.add(new FlashVfx(c, 0.5f, 3.4f, Colors.argb(255, HOT), 8, VfxTextures.FLASH));
        VfxManager.add(new ShardBurstVfx(c, Vec3.ZERO, 1f, 0.45f, 30, 0.28f, Colors.argb(255, EDGE), Colors.argb(0, DEEP), 20, p.seed()));
        if (executed) {
            FxScheduler.after(4, () -> {
                VfxManager.add(new ShockwaveVfx(c, new Vec3(0, 1, 0), 0.5f, 6f, 0.6f, Colors.argb(240, VOID), 16));
                VfxManager.add(new FlashVfx(c, 1f, 6f, Colors.argb(180, VOID), 14).energy());
            });
            if (isLocalCaster(p)) ScreenFx.flash(0x2A0A55, 0.35f, 10);
            CameraShake.add(c, 1.2f, 14);
        } else {
            CameraShake.add(c, 0.7f, 12);
        }
        hitFlashes(p, VOID);
    }

    private static void dimension(FxPayload p) {
        Vec3 c = p.pos();
        float radius = p.scale();
        int duration = Math.round(p.power());
        VfxManager.add(new VoidDomeVfx(c, radius, VOID, EDGE, duration + 10, p.seed()));
        VfxManager.add(new ShockwaveVfx(c.subtract(0, 0.9, 0), new Vec3(0, 1, 0), 0.5f, radius, 1f, Colors.argb(240, EDGE), 16));
        VfxManager.add(new FlashVfx(c, 1f, radius * 1.2f, Colors.argb(200, VOID), 14).energy());
        ScreenFx.zoneVignette(0x14002E, 0.75f, duration + 10, c, radius);
        CameraShake.add(c, 0.8f, radius * 2);
    }

    private static void dimensionStrike(FxPayload p) {
        RandomSource r = RandomSource.create(p.seed());
        Vec3 c = p.pos();
        Vec3 axis = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
        Vec3[] b = VfxContext.basis(axis);
        float rad = Math.max(1.2f, p.scale()) * 1.1f;
        float a0 = r.nextFloat() * 6.28f;
        VfxManager.add(new SlashArcVfx(c, b[0], b[1], rad, 0.45f, a0, a0 + 3.4f, Colors.argb(230, VOID), Colors.argb(255, HOT), 8));
        VfxManager.add(new FlashVfx(c, 0.3f, 1.6f, Colors.argb(220, EDGE), 6, VfxTextures.SPARK).rotation(a0));
    }

    private static void dimensionCollapse(FxPayload p) {
        Vec3 c = p.pos();
        float radius = p.scale();
        VfxManager.add(new SphereVfx(c, radius, 0.3f, Colors.argb(230, VOID), 10, SphereVfx.Mode.SHRINK).voidShell());
        FxScheduler.after(9, () -> {
            VfxManager.add(new FlashVfx(c, 1f, radius * 2.6f, Colors.argb(255, HOT), 12, VfxTextures.FLASH));
            VfxManager.add(new FlashVfx(c, 1f, radius * 3.2f, Colors.argb(220, VOID), 20).energy());
            VfxManager.add(new ShockwaveVfx(c.subtract(0, 0.9, 0), new Vec3(0, 1, 0), 0.5f, radius * 1.8f, 1.2f, Colors.argb(240, EDGE), 22));
            VfxManager.add(new ShardBurstVfx(c, Vec3.ZERO, 1f, 0.9f, 70, 0.4f, Colors.argb(255, EDGE), Colors.argb(0, DEEP), 30, p.seed()));
            CameraShake.add(c, 1.6f, radius * 3);
            ScreenFx.flash(0xE2C8FF, 0.25f, 8);
        });
        hitFlashes(p, VOID);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void hitFlashes(FxPayload p, int color) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int id : p.entities()) {
            Entity e = level.getEntity(id);
            if (e == null) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            VfxManager.add(new FlashVfx(c, 0.3f, 1.4f, Colors.argb(220, color), 8).energy());
            VfxManager.add(new FlashVfx(c, 0.2f, 0.8f, Colors.argb(255, HOT), 5, VfxTextures.SPARK).rotation(id));
        }
    }

    private static boolean isLocalCaster(FxPayload p) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == p.caster();
    }

    static Vec3 groundBelow(Vec3 c, double maxDown) {
        var level = Minecraft.getInstance().level;
        if (level == null) return c.subtract(0, maxDown, 0);
        for (double d = 0; d <= maxDown + 3; d += 0.25) {
            Vec3 q = c.subtract(0, d, 0);
            var pos = BlockPos.containing(q);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return new Vec3(c.x, pos.getY() + 1.02, c.z);
        }
        return c.subtract(0, maxDown, 0);
    }
}
