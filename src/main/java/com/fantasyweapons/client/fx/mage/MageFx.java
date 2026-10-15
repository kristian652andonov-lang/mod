package com.fantasyweapons.client.fx.mage;

import com.fantasyweapons.client.fx.Blast;
import com.fantasyweapons.client.fx.CameraShake;
import com.fantasyweapons.client.fx.FxDispatcher;
import com.fantasyweapons.client.fx.FxProjectiles;
import com.fantasyweapons.client.fx.FxScheduler;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.BeamVfx;
import com.fantasyweapons.client.vfx.effects.DamageNumberVfx;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FlowerVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.client.vfx.effects.OrbVfx;
import com.fantasyweapons.client.vfx.effects.SeekerVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SpellVfx;
import com.fantasyweapons.client.vfx.effects.SphereVfx;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client visuals shared by the mage's twelve weapons: the bolts of every focus, blessings carried to an ally, healing
 * numbers, a bubble bursting, a shard turning a blow, a ray of ascension, the plague leaping. Each element's own spells
 * are drawn by its own class ({@link FireMageFx}, {@link IceMageFx}...).
 */
public final class MageFx {
    /** An element's colours: white-hot core, main colour, deep shade. */
    public record Palette(int core, int main, int deep) {
        public Blast.Palette blast() {
            return new Blast.Palette(core, main, deep);
        }
    }

    public static final Palette FIRE = new Palette(0xFFF0B0, 0xFF6A1F, 0xB0200A);
    public static final Palette ICE = new Palette(0xF0FCFF, 0x8FE3FF, 0x2A6FB0);
    public static final Palette WATER = new Palette(0xE0F8FF, 0x3FA8FF, 0x0A3A8A);
    public static final Palette NATURE = new Palette(0xF6FFD0, 0x6BE36F, 0x1E6B22);
    public static final Palette LIGHT = new Palette(0xFFFFF0, 0xFFD86A, 0xC08A1A);
    public static final Palette VOID = new Palette(0xEED8FF, 0x9B4DFF, 0x2A0A55);

    private MageFx() {
    }

    public static Palette palette(int elementOrdinal) {
        Element[] all = Element.values();
        Element e = all[Math.max(0, Math.min(all.length - 1, elementOrdinal))];
        return switch (e) {
            case FIRE, SOLAR -> FIRE;
            case ICE -> ICE;
            case WATER -> WATER;
            case NATURE -> NATURE;
            case LIGHT -> LIGHT;
            default -> VOID;
        };
    }

    public static void register() {
        FxDispatcher.register(FxIds.MAGE_BOLT, MageFx::bolt);
        FxDispatcher.register(FxIds.MAGE_BOLT_HIT, MageFx::boltHit);
        FxDispatcher.register(FxIds.HEAL_NUMBER, MageFx::healNumber);
        FxDispatcher.register(FxIds.MAGE_BLESS, MageFx::bless);
        FxDispatcher.register(FxIds.MAGE_WARD_SHATTER, MageFx::wardShatter);
        FxDispatcher.register(FxIds.MAGE_BUBBLE_POP, MageFx::bubblePop);
        FxDispatcher.register(FxIds.MAGE_SHARD_BLOCK, MageFx::shardBlock);
        FxDispatcher.register(FxIds.MAGE_ASCENSION_RAY, MageFx::ascensionRay);
        FxDispatcher.register(FxIds.MAGE_SPRING_SAVE, MageFx::springSave);
        FxDispatcher.register(FxIds.MAGE_BANE_BLOOM, MageFx::baneBloom);
        FxDispatcher.register(FxIds.MAGE_PLAGUE_SPREAD, MageFx::plagueSpread);
        FxDispatcher.register(FxIds.THORN_PULSE, MageFx::healPulse);
        FireMageFx.register();
        IceMageFx.register();
        WaterMageFx.register();
        NatureMageFx.register();
        LightMageFx.register();
        VoidMageFx.register();
    }

    // ------------------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------------------

    @Nullable
    static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    /** Where an entity's chest is this frame (it keeps up with it as it moves), or null once it is gone. */
    static Supplier<Vec3> chest(Entity e) {
        return () -> e.isRemoved() ? null : e.getPosition(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)).add(0, e.getBbHeight() * 0.55, 0);
    }

    /** The caster's focus this frame (as the server's {@link Spells#focus}), following them as they move. */
    static Supplier<Vec3> focus(Entity caster) {
        return () -> {
            if (caster.isRemoved()) return null;
            float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            Vec3 look = caster.getViewVector(pt);
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
            return caster.getEyePosition(pt).add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.1, 0);
        };
    }

    /** The ground under a point (a couple of blocks down at most), or the point. */
    static Vec3 ground(Vec3 p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return p;
        var hit = level.clip(new net.minecraft.world.level.ClipContext(p.add(0, 1, 0), p.subtract(0, 4, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? p : hit.getLocation();
    }

    /** A short flash of {@code color} on every creature the server reported as struck. */
    static void hit(FxPayload p, int color) {
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, e.getBbHeight() * 1.4f, Colors.argb(200, color), 8)
                    .follow(e, new Vec3(0, e.getBbHeight() * 0.5, 0)).energy());
        }
    }

    /** A small deterministic hash in [0, 1) for scattering things in a pattern that does not flicker. */
    static float hash(long seed, int i) {
        long x = seed * 6364136223846793005L + i * 1442695040888963407L;
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        return (x >>> 40) / (float) (1L << 24);
    }

    static final Vec3 UP = new Vec3(0, 1, 0);

    /** An orb carried from {@code from} to a moving target over {@code ticks}, arcing up on the way. */
    static Function<Float, Vec3> arcTo(Vec3 from, Supplier<Vec3> to, int ticks, double arc) {
        return t -> {
            Vec3 b = to.get();
            if (b == null) b = from;
            float u = Math.min(1, t / ticks);
            float e = u * u * (3 - 2 * u);
            return from.lerp(b, e).add(0, Math.sin(u * Math.PI) * arc, 0);
        };
    }

    // ------------------------------------------------------------------------------------------------------------
    // bolts
    // ------------------------------------------------------------------------------------------------------------

    /** Each element's bolt sprite: a flame, a shard of ice, a droplet, a leaf, a star, a swirl of void. */
    static ResourceLocation boltSprite(Palette p) {
        if (p == FIRE) return VfxTextures.FLAME;
        if (p == ICE) return VfxTextures.SHARD;
        if (p == WATER) return VfxTextures.GLOW;
        if (p == NATURE) return VfxTextures.LEAF;
        if (p == LIGHT) return VfxTextures.STAR;
        return VfxTextures.SWIRL;
    }

    private static void bolt(FxPayload p) {
        Palette pal = palette(p.level());
        boolean staff = p.scale() > 0.5f;
        Vec3 start = p.pos(), vel = p.dir();
        int life = (int) Math.ceil(p.power() / Math.max(0.05, vel.length())) + 2;
        ResourceLocation sprite = boltSprite(pal);
        if (staff) {
            OrbVfx orb = VfxManager.add(new OrbVfx(t -> start.add(vel.scale(t)), 0.32f, pal.main(), pal.core(), life).sprite(sprite, pal.core(), 0.4f).fades(1, 1));
            FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(orb::now, 0.5f, Colors.argb(230, pal.main()), Colors.argb(0, pal.deep()), 10, life + 12)
                    .headGlow(0.9f, Colors.argb(160, pal.main())));
            FollowTrailVfx core = VfxManager.add(new FollowTrailVfx(orb::now, 0.16f, Colors.argb(255, pal.core()), Colors.argb(0, pal.main()), 6, life + 8)
                    .texture(VfxTextures.LIGHTNING, false));
            FxProjectiles.track(p.seed(), orb, trail, core);
        } else {
            int target = (p.entities().length == 0) ? -1 : p.entities()[0];
            SeekerVfx seeker = VfxManager.add(new SeekerVfx(start, vel, target, 0.18, 0.3f, pal.main(), pal.core(), life).sprite(sprite));
            FollowTrailVfx trail = VfxManager.add(new FollowTrailVfx(seeker::now, 0.34f, Colors.argb(210, pal.main()), Colors.argb(0, pal.deep()), 12, life + 12)
                    .texture(VfxTextures.STREAK, true));
            FxProjectiles.track(p.seed(), seeker, trail);
        }
        // the spark of the cast at the focus
        VfxManager.add(new FlashVfx(start, 0.2f, staff ? 0.9f : 0.7f, Colors.argb(200, pal.main()), 5).energy());
        VfxManager.add(new FlashVfx(start, 0.1f, 0.5f, Colors.argb(230, pal.core()), 4, VfxTextures.STAR).spin(0.3f));
    }

    private static void boltHit(FxPayload p) {
        FxProjectiles.end(p.seed());
        Palette pal = palette(p.level());
        Vec3 at = p.pos();
        boolean hit = p.power() > 0.5f;
        float size = p.scale() > 0.5f ? 1.1f : 0.85f;
        VfxManager.add(new FlashVfx(at, 0.2f, size * (hit ? 1.3f : 0.9f), Colors.argb(220, pal.main()), 7).energy());
        VfxManager.add(new FlashVfx(at, 0.1f, size * 0.6f, Colors.argb(255, pal.core()), 5, VfxTextures.FLASH));
        VfxManager.add(new ShardBurstVfx(at, UP, 1f, 0.18f, hit ? 10 : 6, 0.16f, Colors.argb(255, pal.core()), Colors.argb(0, pal.main()), 12, p.seed())
                .texture(boltSprite(pal), pal == ICE).physics(pal == FIRE || pal == LIGHT ? -0.004f : 0.01f, 0.9f));
        if (hit) VfxManager.add(new ShockwaveVfx(at, camFacing(at), 0.1f, size * 1.1f, 0.25f, Colors.argb(200, pal.main()), 7).energy());
    }

    /** The direction from a point back to the camera (rings facing the viewer). */
    static Vec3 camFacing(Vec3 at) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 d = cam.subtract(at);
        return d.lengthSqr() < 1e-6 ? UP : d.normalize();
    }

    // ------------------------------------------------------------------------------------------------------------
    // healing
    // ------------------------------------------------------------------------------------------------------------

    private static void healNumber(FxPayload p) {
        if (ClientConfig.get(ClientConfig.DAMAGE_NUMBERS, true)) {
            float v = p.power();
            String text = v >= 10 ? String.format("+%,.0f", v) : String.format("+%.1f", v);
            VfxManager.add(new DamageNumberVfx(p.pos().add(0, 0.25, 0), text, 0x7CFF6A, v >= 6 ? 1.15f : 0.95f, p.pos().hashCode()));
        }
        Entity e = (p.entities().length == 0) ? null : entity(p.entities()[0]);
        if (e != null) healSparkle(e, NATURE.main());
    }

    /** Green motes rising around a healed creature, and a soft glow. */
    static void healSparkle(Entity e, int color) {
        long seed = e.getId() * 31L + e.tickCount;
        Supplier<Vec3> at = chest(e);
        int n = 7;
        float h = e.getBbHeight(), w = e.getBbWidth();
        VfxManager.add(new SpellVfx(22, (ctx, t, ticks) -> {
            Vec3 c = at.get();
            if (c == null) return;
            var glow = ctx.additive(VfxTextures.GLOW);
            var plus = ctx.additive(VfxTextures.STAR);
            for (int i = 0; i < n; i++) {
                float ph = hash(seed, i);
                float u = Math.min(1, t * 1.2f + ph * 0.3f);
                double a = ph * 6.283 + ticks * 0.08;
                Vec3 q = c.add(Math.cos(a) * w * 0.6, -h * 0.45 + u * h * 1.1, Math.sin(a) * w * 0.6);
                float alpha = (float) Math.sin(u * Math.PI);
                ctx.billboard(glow, q, 0.28f, 0, Colors.alpha(0.8f * alpha, color));
                ctx.billboard(plus, q, 0.16f, ticks * 0.1f, Colors.alpha(alpha, 0xEFFFE0));
            }
        }));
        VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, h * 1.3f, Colors.argb(120, color), 10).follow(e, new Vec3(0, h * 0.5, 0)).energy());
    }

    /**
     * Healing pulses: level 0 a pulse of life leaping from a struck foe to the healed ally (Verdant Pulse), 1 green
     * sparkles on everyone healed, 3 blue ones (water), 2 a ring of healing breathed out of a Lifebloom.
     */
    private static void healPulse(FxPayload p) {
        switch (p.level()) {
            case 0 -> {
                Entity ally = (p.entities().length == 0) ? null : entity(p.entities()[0]);
                if (ally == null) return;
                Vec3 from = p.pos();
                Supplier<Vec3> to = chest(ally);
                OrbVfx orb = VfxManager.add(new OrbVfx(arcTo(from, to, 10, 1.5), 0.22f, NATURE.main(), NATURE.core(), 11).sprite(VfxTextures.LEAF, NATURE.core(), 0.3f));
                VfxManager.add(new FollowTrailVfx(orb::now, 0.22f, Colors.argb(220, NATURE.main()), Colors.argb(0, NATURE.deep()), 10, 22).texture(VfxTextures.STREAK, true));
            }
            case 2 -> {
                Vec3 c = p.pos();
                VfxManager.add(new ShockwaveVfx(ground(c).add(0, 0.08, 0), UP, 0.3f, p.scale(), 0.4f, Colors.argb(170, NATURE.main()), 16).energy());
                VfxManager.add(new ShardBurstVfx(c, UP, 1.2f, 0.06f, 10, 0.18f, Colors.argb(230, NATURE.core()), Colors.argb(0, NATURE.main()), 24, c.hashCode())
                        .texture(VfxTextures.PETAL_SOFT, false).physics(-0.002f, 0.95f));
            }
            default -> {
            }
        }
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e != null && p.level() != 0) healSparkle(e, p.level() == 3 ? WATER.main() : NATURE.main());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // blessings carried to an ally
    // ------------------------------------------------------------------------------------------------------------

    private static void bless(FxPayload p) {
        Entity ally = (p.entities().length == 0) ? null : entity(p.entities()[0]);
        if (ally == null) return;
        Vec3 from = p.pos();
        Supplier<Vec3> to = chest(ally);
        Palette pal = switch (p.level()) {
            case Spells.BLESS_WARD, Spells.BLESS_SHATTERGUARD -> ICE;
            case Spells.BLESS_BUBBLE -> WATER;
            default -> LIGHT;
        };
        boolean self = ally == entity(p.caster());
        int travel = self ? 4 : 8;
        if (!self) {
            OrbVfx orb = VfxManager.add(new OrbVfx(arcTo(from, to, travel, 1.2), 0.28f, pal.main(), pal.core(), travel + 1)
                    .sprite(p.level() == Spells.BLESS_BUBBLE ? VfxTextures.GLOW : VfxTextures.STAR, pal.core(), 0.4f));
            VfxManager.add(new FollowTrailVfx(orb::now, 0.4f, Colors.argb(230, pal.main()), Colors.argb(0, pal.deep()), 12, travel + 14)
                    .texture(VfxTextures.STREAK, true));
        }
        long seed = p.seed();
        FxScheduler.after(travel, () -> {
            if (ally.isRemoved()) return;
            float h = ally.getBbHeight(), w = ally.getBbWidth();
            Vec3 feet = ally.position();
            VfxManager.add(new FlashVfx(Vec3.ZERO, 0.3f, h * 1.6f, Colors.argb(200, pal.main()), 10).follow(ally, new Vec3(0, h * 0.5, 0)).energy());
            switch (p.level()) {
                case Spells.BLESS_WARD -> {
                    // crystals snap up around the ally and a rune of frost turns under them
                    VfxManager.add(new DecalVfx(feet.add(0, 0.05, 0), UP, w + 1.2f, Colors.argb(220, ICE.main()), VfxTextures.SNOWFLAKE, 24)
                            .spin(0.05f).energy().timing(0.15f, 0.5f));
                    VfxManager.add(new ShardBurstVfx(feet.add(0, h * 0.5, 0), UP, 1.1f, 0.12f, 14, 0.22f, Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), 16, seed)
                            .texture(VfxTextures.SHARD, true).attract(feet.add(0, h * 0.5, 0), 0.04f));
                }
                case Spells.BLESS_SHATTERGUARD -> VfxManager.add(new ShockwaveVfx(feet.add(0, h * 0.55, 0), UP, 0.2f, w + 1.4f, 0.25f,
                        Colors.argb(220, ICE.core()), 10).energy());
                case Spells.BLESS_BUBBLE -> {
                    VfxManager.add(new SphereVfx(feet.add(0, h * 0.5, 0), 0.2f, Math.max(w, h) * 0.75f, Colors.argb(150, WATER.main()), 10, SphereVfx.Mode.GROW));
                    VfxManager.add(new ShardBurstVfx(feet.add(0, h * 0.5, 0), UP, 1f, 0.1f, 12, 0.18f, Colors.argb(230, WATER.core()), Colors.argb(0, WATER.main()), 18, seed)
                            .texture(VfxTextures.GLOW, false).physics(0.01f, 0.9f));
                }
                case Spells.BLESS_VALOR -> {
                    // a blade of light flashes above the blessed, and embers of valor rise
                    VfxManager.add(new BeamVfx(feet.add(0, h + 2.4, 0), feet.add(0, h + 0.4, 0), 0.5f, Colors.argb(240, LIGHT.main()), 12).fade(0.4f, 0.12f));
                    VfxManager.add(new DecalVfx(feet.add(0, 0.05, 0), UP, w + 1f, Colors.argb(220, LIGHT.main()), VfxTextures.RUNE_CIRCLE, 26)
                            .spin(0.08f).energy().timing(0.1f, 0.5f));
                    VfxManager.add(new ShardBurstVfx(feet.add(0, 0.3, 0), UP, 0.5f, 0.12f, 16, 0.14f, Colors.argb(255, LIGHT.core()), Colors.argb(0, LIGHT.main()), 22, seed)
                            .texture(VfxTextures.SPARK, true).physics(-0.006f, 0.92f));
                }
                case Spells.BLESS_HALO -> {
                    // the halo comes down out of the light onto the ally's head
                    VfxManager.add(new BeamVfx(feet.add(0, h + 6, 0), feet.add(0, h, 0), 1.2f, Colors.argb(200, LIGHT.core()), 10).fade(0.45f, 0.1f));
                    VfxManager.add(new ShockwaveVfx(feet.add(0, 0.06, 0), UP, 0.2f, w + 2f, 0.4f, Colors.argb(200, LIGHT.main()), 14).energy());
                }
                default -> {
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // status moments
    // ------------------------------------------------------------------------------------------------------------

    private static void wardShatter(FxPayload p) {
        Vec3 at = p.pos();
        boolean projectile = p.level() == 1;
        Entity e = entity(p.caster());
        Vec3 c = projectile || e == null ? at : at.add(0, e.getBbHeight() * 0.5, 0);
        VfxManager.add(new ShardBurstVfx(c, UP, 1.2f, projectile ? 0.12f : 0.2f, projectile ? 8 : 22, projectile ? 0.14f : 0.24f,
                Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), projectile ? 12 : 22, (long) (at.x * 997 + at.z))
                .texture(VfxTextures.SHARD, true).physics(0.025f, 0.95f).translucent());
        VfxManager.add(new FlashVfx(c, 0.2f, projectile ? 0.8f : 1.8f, Colors.argb(200, ICE.main()), 7).energy());
    }

    private static void bubblePop(FxPayload p) {
        Entity e = entity(p.caster());
        float h = e == null ? 1.8f : e.getBbHeight(), w = e == null ? 0.6f : e.getBbWidth();
        Vec3 c = p.pos().add(0, h * 0.5, 0);
        float r = Math.max(w, h) * 0.75f;
        VfxManager.add(new SphereVfx(c, r, r * 1.5f, Colors.argb(130, WATER.core()), 8, SphereVfx.Mode.GROW));
        VfxManager.add(new ShardBurstVfx(c, UP, 1.4f, 0.22f, 26, 0.2f, Colors.argb(240, WATER.core()), Colors.argb(0, WATER.main()), 20, (long) (c.x * 31 + c.z))
                .texture(VfxTextures.GLOW, false).physics(0.03f, 0.94f));
        VfxManager.add(new ShockwaveVfx(p.pos().add(0, 0.06, 0), UP, 0.3f, r * 2.4f, 0.35f, Colors.argb(180, WATER.main()), 14).energy());
        if (e != null) healSparkle(e, WATER.main());
    }

    private static void shardBlock(FxPayload p) {
        Vec3 from = p.pos(), to = p.points().isEmpty() ? from : p.points().get(0);
        VfxManager.add(new FlashVfx(from, 0.3f, 1.6f, Colors.argb(230, ICE.core()), 8, VfxTextures.STAR).spin(0.2f));
        OrbVfx shard = VfxManager.add(new OrbVfx(t -> from.lerp(to, Math.min(1, t / 5)), 0.3f, ICE.main(), ICE.core(), 6).sprite(VfxTextures.SHARD, ICE.core(), 0));
        VfxManager.add(new FollowTrailVfx(shard::now, 0.3f, Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), 8, 16).texture(VfxTextures.LIGHTNING, false));
        FxScheduler.after(5, () -> {
            VfxManager.add(new ShardBurstVfx(to, UP, 1.3f, 0.16f, 16, 0.2f, Colors.argb(240, ICE.core()), Colors.argb(0, ICE.main()), 16, (long) (to.x * 13))
                    .texture(VfxTextures.SHARD, true).physics(0.02f, 0.93f).translucent());
            VfxManager.add(new FlashVfx(to, 0.2f, 1.4f, Colors.argb(200, ICE.main()), 8).energy());
        });
    }

    private static void ascensionRay(FxPayload p) {
        Vec3 g = p.pos();
        VfxManager.add(new BeamVfx(g.add(0, 14, 0), g, 0.9f, Colors.argb(240, LIGHT.core()), 8).fade(0.45f, 0.04f));
        VfxManager.add(new BeamVfx(g.add(0, 14, 0), g, 0.35f, Colors.argb(255, 0xFFFFFF), 6).fade(0.45f, 0.04f));
        VfxManager.add(new FlashVfx(g.add(0, 1, 0), 0.4f, 2.2f, Colors.argb(220, LIGHT.main()), 8).energy());
        VfxManager.add(new ShockwaveVfx(g.add(0, 0.06, 0), UP, 0.2f, 1.8f, 0.3f, Colors.argb(200, LIGHT.core()), 10).energy());
        VfxManager.add(new ShardBurstVfx(g.add(0, 0.5, 0), UP, 0.8f, 0.14f, 10, 0.12f, Colors.argb(255, LIGHT.core()), Colors.argb(0, LIGHT.main()), 14, (long) (g.x * 7))
                .texture(VfxTextures.SPARK, true));
    }

    private static void springSave(FxPayload p) {
        Entity e = entity(p.caster());
        Vec3 g = p.pos();
        float h = e == null ? 1.8f : e.getBbHeight();
        VfxManager.add(new FlowerVfx(g.add(0, 0.05, 0), 2.6f, 0xFFB8E0, 0xFFF0F8, NATURE.core(), 36).timing(6, 16));
        VfxManager.add(new FlashVfx(g.add(0, h * 0.5, 0), 0.5f, 3.5f, Colors.argb(230, NATURE.main()), 14).energy());
        VfxManager.add(new ShardBurstVfx(g.add(0, h * 0.6, 0), UP, 1.4f, 0.16f, 40, 0.22f, Colors.argb(255, 0xFFE0F0), Colors.argb(0, NATURE.main()), 40, (long) (g.x * 5 + g.z))
                .texture(VfxTextures.PETAL_SOFT, false).physics(0.004f, 0.95f));
        VfxManager.add(new BeamVfx(g, g.add(0, 9, 0), 1.6f, Colors.argb(200, NATURE.core()), 16).fade(0.04f, 0.5f));
        if (e == Minecraft.getInstance().player) com.fantasyweapons.client.fx.ScreenFx.flash(NATURE.main(), 0.3f, 14);
    }

    private static void baneBloom(FxPayload p) {
        Vec3 c = p.pos();
        int stacks = Math.max(1, p.level());
        VfxManager.add(new FlowerVfx(c, 0.9f + 0.3f * stacks, VOID.main(), VOID.core(), VOID.deep(), 16).timing(3, 10));
        VfxManager.add(new FlashVfx(c, 0.3f, 2f + stacks * 0.5f, Colors.argb(220, VOID.main()), 9).energy());
        VfxManager.add(new ShardBurstVfx(c, UP, 1.4f, 0.2f, 12 + stacks * 6, 0.22f, Colors.argb(240, VOID.core()), Colors.argb(0, VOID.deep()), 18, (long) (c.x * 11))
                .texture(VfxTextures.SHARD, true).physics(0.01f, 0.9f));
        CameraShake.add(c, 0.2f, 10);
    }

    private static void plagueSpread(FxPayload p) {
        Vec3 from = p.pos();
        for (int id : p.entities()) {
            Entity e = entity(id);
            if (e == null) continue;
            OrbVfx wisp = VfxManager.add(new OrbVfx(arcTo(from, chest(e), 10, 1.2), 0.24f, 0x7AB84A, VOID.core(), 11).sprite(VfxTextures.SOUL, 0xC8FF9A, 0.2f));
            VfxManager.add(new FollowTrailVfx(wisp::now, 0.26f, Colors.argb(200, 0x6A8A3A), Colors.argb(0, VOID.deep()), 10, 22).texture(VfxTextures.STREAK, true));
        }
    }
}
