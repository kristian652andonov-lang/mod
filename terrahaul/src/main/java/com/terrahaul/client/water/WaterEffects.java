package com.terrahaul.client.water;

import com.terrahaul.client.particle.FoamParticle;
import com.terrahaul.client.particle.MistParticle;
import com.terrahaul.client.particle.SprayParticle;
import com.terrahaul.client.particle.TerrahaulParticleProviders;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.config.TerrahaulConfig.AnimationQuality;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Client-side effects of falling and running water: spray, splashes, mist, foam and sound, for
 * every fall the {@link WaterScanner} finds (Terrahaul's waterfalls and cascades, vanilla falls,
 * player-made ones, in any world).
 * <p>
 * Per column of falling water, scaled by its height and by how far it is from the camera:
 * <ul>
 *   <li>fine spray leaves the curtain on every side open to the air, most of it low down, where
 *       the water breaks up; drops arc off the lip above;</li>
 *   <li>the landing water bursts up in white splashes, higher for taller falls; on a step one
 *       block high it is a small churn of whitewater;</li>
 *   <li>mist rolls off the foot of falls three blocks and taller, and a fall fourteen blocks or
 *       taller also raises a large, slowly billowing cloud whose size grows with the fall;</li>
 *   <li>foam floats away from where the water lands, downstream (the way the water left the lip)
 *       and outwards, and running water carries flecks of foam along its current.</li>
 * </ul>
 * Mist and the big clouds are seen from about twice as far as spray and foam. Every effect has
 * an option, and the particle count follows the animation quality, the density option and
 * Minecraft's Particles setting (half at Decreased, none at Minimal); each kind of particle has a
 * cap on how many can live at once. Nothing here runs on a server.
 */
public final class WaterEffects {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Live particle caps per kind for the LOW, MEDIUM and HIGH quality. */
    private static final ParticleGroup[] SPRAY_CAP = {new ParticleGroup(300), new ParticleGroup(600), new ParticleGroup(1000)};
    private static final ParticleGroup[] MIST_CAP = {new ParticleGroup(160), new ParticleGroup(300), new ParticleGroup(480)};
    private static final ParticleGroup[] FOAM_CAP = {new ParticleGroup(220), new ParticleGroup(420), new ParticleGroup(700)};
    private static final double SPRAY_RANGE = 48.0;
    private static final double FOAM_RANGE = 40.0;
    private static final double MIST_RANGE = 96.0;
    /** Falls this tall raise the big billowing cloud. */
    private static final int BILLOW_HEIGHT = 14;
    private static final int REBUILD_TICKS = 10;

    private static final WaterScanner SCANNER = new WaterScanner();
    private static final WaterSounds SOUNDS = new WaterSounds();
    private static final List<Fall> FALLS = new ArrayList<>();
    private static final List<WaterScanner.RapidPatch> PATCHES = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static long ticks;
    private static long lastRebuild;
    private static int builtVersion = -1;

    private WaterEffects() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        if (level == null || mc.player == null || mc.isPaused() || !mc.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        AnimationQuality quality = TerrahaulConfig.animationQuality();
        boolean particles = quality != AnimationQuality.OFF && mc.options.particles().get() != ParticleStatus.MINIMAL
            && TerrahaulParticleProviders.waterReady()
            && (TerrahaulConfig.waterfallSpray() || TerrahaulConfig.waterfallMist() || TerrahaulConfig.waterFoam());
        boolean sound = TerrahaulConfig.waterSounds();
        if (!particles && !sound) {
            SOUNDS.stopAll(mc);
            return;
        }
        ticks++;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int radius = switch (quality) {
            case OFF, LOW -> 3;
            case MEDIUM -> 4;
            case HIGH -> 5;
        };
        SCANNER.tick(level, cam, radius, radius + 1, ticks);
        if (SCANNER.version() != builtVersion && ticks - lastRebuild >= REBUILD_TICKS) {
            builtVersion = SCANNER.version();
            lastRebuild = ticks;
            SCANNER.rebuild(FALLS, PATCHES);
            SOUNDS.update(FALLS, PATCHES);
        }
        if (particles) {
            spawn(mc, level, cam, quality);
        }
        if (sound) {
            SOUNDS.tick(mc, cam);
        } else {
            SOUNDS.stopAll(mc);
        }
    }

    /** What the effects currently see, for the development screenshot log. */
    public static String describe() {
        int columns = 0;
        int tallest = 0;
        int widest = 0;
        for (Fall fall : FALLS) {
            columns += fall.columns.length;
            tallest = Math.max(tallest, fall.height);
            widest = Math.max(widest, fall.width);
        }
        int cells = 0;
        for (WaterScanner.RapidPatch patch : PATCHES) {
            cells += patch.cells().length;
        }
        return String.format(java.util.Locale.ROOT, "%d falls (%d columns, tallest %d, widest %d), %d running water cells, %d sounds playing",
            FALLS.size(), columns, tallest, widest, cells, SOUNDS.playing());
    }

    /** Forgets everything, when the player leaves a world or changes dimension. */
    public static void reset() {
        SCANNER.clear();
        SOUNDS.stopAll(Minecraft.getInstance());
        FALLS.clear();
        PATCHES.clear();
        builtVersion = -1;
    }

    /** A chunk arrived from the server or was dropped on this client: its sections are read again. */
    public static void onChunkChanged(ChunkEvent event) {
        // the integrated server posts chunk events of its own levels, on its own threads
        if (!event.getLevel().isClientSide() || event.getLevel() != lastLevel) {
            return;
        }
        var chunk = event.getChunk();
        SCANNER.forgetChunk(chunk.getPos().x, chunk.getPos().z, chunk.getMinSection(), chunk.getMaxSection());
    }

    // ---- particles ----------------------------------------------------------------------------

    private static void spawn(Minecraft mc, ClientLevel level, Vec3 cam, AnimationQuality quality) {
        int q = switch (quality) {
            case OFF, LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
        double scale = TerrahaulConfig.waterEffectDensity() * (q == 0 ? 0.45 : q == 1 ? 0.7 : 1.0)
            * (mc.options.particles().get() == ParticleStatus.DECREASED ? 0.5 : 1.0);
        if (scale <= 0.0) {
            return;
        }
        double reach = q == 0 ? 0.6 : q == 1 ? 0.8 : 1.0;
        boolean spray = TerrahaulConfig.waterfallSpray();
        boolean mist = TerrahaulConfig.waterfallMist();
        boolean foam = TerrahaulConfig.waterFoam();
        Ctx ctx = new Ctx(mc, level, cam, scale, SPRAY_CAP[q], MIST_CAP[q], FOAM_CAP[q], SPRAY_RANGE * reach, FOAM_RANGE * reach,
            MIST_RANGE * reach, TerrahaulParticleProviders.spraySprites(), TerrahaulParticleProviders.mistSprites(),
            TerrahaulParticleProviders.foamSprites());
        double range = mist ? ctx.mistRange : Math.max(ctx.sprayRange, ctx.foamRange);
        for (Fall fall : FALLS) {
            double d = fall.distance(cam.x, cam.y, cam.z);
            if (d > range) {
                continue;
            }
            if (mist && fall.height >= BILLOW_HEIGHT) {
                billow(ctx, fall, d);
            }
            for (WaterScanner.Column c : fall.columns) {
                double dc = distance(cam, c);
                if (spray) {
                    curtainSpray(ctx, c, dc);
                    lipDrops(ctx, c, dc);
                    splash(ctx, c, dc);
                }
                if (foam && c.base() == WaterScanner.BASE_POOL) {
                    poolFoam(ctx, c, dc);
                }
                if (mist && c.height() >= 3) {
                    footMist(ctx, c, dc);
                }
            }
        }
        if (foam || spray) {
            for (WaterScanner.RapidPatch patch : PATCHES) {
                rapids(ctx, patch, foam, spray);
            }
        }
    }

    /** What every spawner needs for this tick. */
    private record Ctx(Minecraft mc, ClientLevel level, Vec3 cam, double scale, ParticleGroup sprayCap, ParticleGroup mistCap,
                       ParticleGroup foamCap, double sprayRange, double foamRange, double mistRange,
                       SpriteSet spraySprites, SpriteSet mistSprites, SpriteSet foamSprites) {
    }

    /** Distance from the camera to the nearest block of a column. */
    private static double distance(Vec3 cam, WaterScanner.Column c) {
        double ny = Mth.clamp(cam.y, c.y(), c.y() + c.height());
        double dx = c.x() + 0.5 - cam.x;
        double dy = ny - cam.y;
        double dz = c.z() + 0.5 - cam.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Full rate up close, fading out towards the edge of the range. */
    private static double near(double d, double range) {
        return Mth.clamp(1.15 - d / range, 0.0, 1.0);
    }

    /** A whole number of events for a fractional expected count. */
    private static int count(double expected) {
        int n = (int) expected;
        return RANDOM.nextDouble() < expected - n ? n + 1 : n;
    }

    private static void curtainSpray(Ctx ctx, WaterScanner.Column c, double d) {
        if (c.height() < 2 || c.open() == 0 || d > ctx.sprayRange) {
            return;
        }
        int faces = Integer.bitCount(c.open());
        // edge columns have more open sides and shed more spray
        double rate = 0.05 * Math.pow(Math.min(c.height(), 160), 0.75) * (0.6 + 0.4 * Math.min(faces, 3)) * near(d, ctx.sprayRange)
            * ctx.scale;
        for (int n = count(rate); n > 0; n--) {
            int face = pickFace(c.open(), faces);
            double nx = WaterScanner.DX[face];
            double nz = WaterScanner.DZ[face];
            // most spray comes off low down, where the falling water has broken up
            double u = Math.pow(RANDOM.nextDouble(), 1.6);
            double y = c.y() + u * c.height();
            double t = RANDOM.nextDouble() - 0.5;
            double out = 0.55 + RANDOM.nextDouble() * 0.15;
            double x = c.x() + 0.5 + nx * out - nz * t * 0.95;
            double z = c.z() + 0.5 + nz * out + nx * t * 0.95;
            if (ctx.cam.distanceToSqr(x, y, z) > ctx.sprayRange * ctx.sprayRange) {
                continue;
            }
            double speed = 0.012 + RANDOM.nextDouble() * 0.035;
            // the water drags the spray down with it, faster the further it has fallen
            double fallen = Math.min(1.0, c.height() * (1.0 - u) / 20.0);
            double down = 0.06 + RANDOM.nextDouble() * 0.12 + 0.15 * fallen;
            SprayParticle p = new SprayParticle(ctx.level, x, y, z, nx * speed, -down, nz * speed, SprayParticle.Kind.SPRAY, ctx.sprayCap);
            add(ctx, p.withSprite(sprite(ctx.spraySprites, RANDOM.nextBoolean() ? 1 : 3)));
        }
    }

    private static int pickFace(byte open, int faces) {
        int pick = RANDOM.nextInt(faces);
        for (int i = 0; i < 4; i++) {
            if ((open & 1 << i) != 0 && pick-- == 0) {
                return i;
            }
        }
        return 0;
    }

    private static void lipDrops(Ctx ctx, WaterScanner.Column c, double d) {
        int top = c.y() + c.height() - 1;
        if (c.height() < 2 || c.lipY() <= top || c.dirX() == 0.0F && c.dirZ() == 0.0F) {
            return;
        }
        double ly = c.lipY() + 0.3 + RANDOM.nextDouble() * 0.5;
        double dl = Math.sqrt(ctx.cam.distanceToSqr(c.x() + 0.5, ly, c.z() + 0.5));
        if (dl > ctx.sprayRange * 0.85) {
            return;
        }
        for (int n = count(0.035 * near(dl, ctx.sprayRange * 0.85) * ctx.scale); n > 0; n--) {
            double t = RANDOM.nextDouble() - 0.5;
            double x = c.x() + 0.5 + c.dirX() * 0.55 - c.dirZ() * t * 0.9;
            double z = c.z() + 0.5 + c.dirZ() * 0.55 + c.dirX() * t * 0.9;
            double speed = 0.03 + RANDOM.nextDouble() * 0.06;
            SprayParticle p = new SprayParticle(ctx.level, x, ly, z, c.dirX() * speed, RANDOM.nextDouble() * 0.04, c.dirZ() * speed,
                SprayParticle.Kind.DROPLET, ctx.sprayCap);
            add(ctx, p.withSprite(sprite(ctx.spraySprites, RANDOM.nextBoolean() ? 0 : 2)));
        }
    }

    private static void splash(Ctx ctx, WaterScanner.Column c, double d) {
        if (d > ctx.sprayRange) {
            return;
        }
        int h = Math.min(c.height(), 100);
        double rate = (c.height() >= 2 ? 0.2 * (0.4 + 0.5 * Math.sqrt(h)) : 0.08) * near(d, ctx.sprayRange) * ctx.scale;
        // taller falls land harder and throw the water higher and further
        double power = c.height() >= 2 ? Math.min(1.7, 0.5 + 0.12 * Math.sqrt(h)) : 0.4;
        for (int n = count(rate); n > 0; n--) {
            double ox = (RANDOM.nextDouble() - 0.5) * 1.4;
            double oz = (RANDOM.nextDouble() - 0.5) * 1.4;
            double x = c.x() + 0.5 + ox;
            double z = c.z() + 0.5 + oz;
            double y = c.surface() + 0.05;
            if (ctx.level.getBlockState(BlockPos.containing(x, y, z)).blocksMotion()) {
                continue;
            }
            double xd = (ox * 0.1 + c.dirX() * 0.03 + RANDOM.nextGaussian() * 0.025) * power;
            double zd = (oz * 0.1 + c.dirZ() * 0.03 + RANDOM.nextGaussian() * 0.025) * power;
            double yd = (0.1 + RANDOM.nextDouble() * 0.18) * power;
            // two in three are bursts of white water, the rest single drops
            boolean burst = RANDOM.nextInt(3) != 0;
            SprayParticle.Kind kind = burst ? SprayParticle.Kind.SPLASH : SprayParticle.Kind.DROPLET;
            SprayParticle p = new SprayParticle(ctx.level, x, y, z, xd, yd, zd, kind, ctx.sprayCap);
            add(ctx, p.withSprite(sprite(ctx.spraySprites, burst && RANDOM.nextBoolean() ? 3 : 0)));
        }
    }

    private static void poolFoam(Ctx ctx, WaterScanner.Column c, double d) {
        if (d > ctx.foamRange) {
            return;
        }
        int h = Math.min(c.height(), 100);
        double rate = (c.height() >= 2 ? 0.09 * (0.5 + 0.35 * Math.sqrt(h)) : 0.06) * near(d, ctx.foamRange) * ctx.scale;
        for (int n = count(rate); n > 0; n--) {
            // most of the foam is pushed out in front of the curtain, some to its sides and behind
            double along = 0.6 + RANDOM.nextDouble() * (1.0 + Math.min(h, 60) * 0.03);
            double side = (RANDOM.nextDouble() - 0.5) * 1.4;
            double ax = c.dirX();
            double az = c.dirZ();
            if (ax == 0.0F && az == 0.0F || RANDOM.nextInt(4) == 0) {
                double angle = RANDOM.nextDouble() * Mth.TWO_PI;
                ax = Math.cos(angle);
                az = Math.sin(angle);
            }
            double x = c.x() + 0.5 + ax * along - az * side;
            double z = c.z() + 0.5 + az * along + ax * side;
            double surface = surfaceAt(ctx.level, x, c.surface(), z);
            if (Double.isNaN(surface)) {
                continue;
            }
            double ox = x - (c.x() + 0.5);
            double oz = z - (c.z() + 0.5);
            double len = Math.max(0.3, Math.sqrt(ox * ox + oz * oz));
            double push = (0.015 + RANDOM.nextDouble() * 0.025) * (1.0 + h / 40.0);
            double xd = ox / len * push + c.dirX() * (0.01 + RANDOM.nextDouble() * 0.02);
            double zd = oz / len * push + c.dirZ() * (0.01 + RANDOM.nextDouble() * 0.02);
            float size = c.height() >= 2 ? 0.16F + RANDOM.nextFloat() * 0.2F : 0.1F + RANDOM.nextFloat() * 0.12F;
            FoamParticle p = new FoamParticle(ctx.level, x, surface + 0.025, z, xd, zd, size, 90 + RANDOM.nextInt(110), ctx.foamCap);
            add(ctx, p.withSprite(sprite(ctx.foamSprites, RANDOM.nextInt(4))));
        }
    }

    /**
     * The water surface near {@code nearY} at a point, if the block there (or the one below) is
     * water open to the air; NaN otherwise.
     */
    private static double surfaceAt(ClientLevel level, double x, double nearY, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(nearY - 0.05), Mth.floor(z));
        for (int i = 0; i < 2; i++) {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER)) {
                if (level.getFluidState(pos.above()).isEmpty() && !level.getBlockState(pos.above()).blocksMotion()) {
                    return pos.getY() + fluid.getOwnHeight();
                }
                return Double.NaN;
            }
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }

    private static void footMist(Ctx ctx, WaterScanner.Column c, double d) {
        int h = Math.min(c.height(), 120);
        double rate = 0.012 * Math.pow(h, 0.8) * near(d, ctx.mistRange * 0.75) * ctx.scale;
        for (int n = count(rate); n > 0; n--) {
            double spread = 0.5 + h * 0.03;
            double ax = c.dirX() == 0.0F && c.dirZ() == 0.0F ? RANDOM.nextGaussian() * 0.5 : c.dirX();
            double az = c.dirX() == 0.0F && c.dirZ() == 0.0F ? RANDOM.nextGaussian() * 0.5 : c.dirZ();
            double forward = 0.3 + RANDOM.nextDouble() * spread;
            double x = c.x() + 0.5 + ax * forward + RANDOM.nextGaussian() * spread * 0.5;
            double z = c.z() + 0.5 + az * forward + RANDOM.nextGaussian() * spread * 0.5;
            double y = c.surface() + 0.1 + RANDOM.nextDouble() * (0.4 + h * 0.025);
            double ox = x - (c.x() + 0.5);
            double oz = z - (c.z() + 0.5);
            double len = Math.max(0.3, Math.sqrt(ox * ox + oz * oz));
            double push = (0.015 + RANDOM.nextDouble() * 0.03) * (1.0 + h / 50.0);
            float start = 0.35F + RANDOM.nextFloat() * 0.35F + h * 0.006F;
            float end = Math.min(3.0F, start * 2.2F + RANDOM.nextFloat() * 0.6F + h * 0.012F);
            // a thin veil for a small fall, thicker for a tall one
            float alpha = (0.22F + RANDOM.nextFloat() * 0.16F) * (0.75F + 0.25F * Math.min(1.0F, h / 16.0F));
            int life = 50 + RANDOM.nextInt(50) + (int) (Math.min(h, 80) * 0.8F);
            MistParticle p = new MistParticle(ctx.level, x, y, z, ox / len * push, 0.005 + RANDOM.nextDouble() * 0.01, oz / len * push,
                start, end, life, alpha, 0.006F + RANDOM.nextFloat() * 0.012F, false, ctx.mistCap);
            add(ctx, p.withSprite(sprite(ctx.mistSprites, RANDOM.nextInt(4))));
        }
    }

    /** The big cloud of a tall fall: large, slow puffs that rise above the pool and drift off. */
    private static void billow(Ctx ctx, Fall fall, double d) {
        int h = Math.min(fall.height, 120);
        double rate = 0.0035 * Math.pow(fall.width, 0.8) * Math.pow(h, 0.6) * near(d, ctx.mistRange) * ctx.scale;
        double reach = 1.0 + Math.sqrt(fall.width * (double) h) * 0.18;
        for (int n = count(rate); n > 0; n--) {
            double forward = reach * (0.3 + RANDOM.nextDouble() * 0.6);
            double x = fall.cx + fall.dirX * forward + RANDOM.nextGaussian() * reach * 0.5;
            double z = fall.cz + fall.dirZ * forward + RANDOM.nextGaussian() * reach * 0.5;
            double y = fall.surface + 0.5 + RANDOM.nextDouble() * h * 0.08;
            float start = (float) (1.0 + RANDOM.nextDouble() * 0.8 + reach * 0.15);
            float end = Math.min(7.0F, start * (1.8F + RANDOM.nextFloat() * 0.5F));
            double push = 0.01 + RANDOM.nextDouble() * 0.02;
            MistParticle p = new MistParticle(ctx.level, x, y, z, fall.dirX * push + RANDOM.nextGaussian() * 0.006, 0.01,
                fall.dirZ * push + RANDOM.nextGaussian() * 0.006, start, end, 160 + RANDOM.nextInt(140),
                0.16F + RANDOM.nextFloat() * 0.1F, 0.012F + RANDOM.nextFloat() * 0.016F, true, ctx.mistCap);
            add(ctx, p.withSprite(sprite(ctx.mistSprites, RANDOM.nextInt(4))));
        }
    }

    /** Running water: flecks of foam riding the current, and a little whitewater thrown up. */
    private static void rapids(Ctx ctx, WaterScanner.RapidPatch patch, boolean foam, boolean spray) {
        double d = Math.max(0.0, Math.sqrt(ctx.cam.distanceToSqr(patch.x(), patch.y(), patch.z())) - 8.0);
        if (d > ctx.foamRange) {
            return;
        }
        long[] cells = patch.cells();
        double near = near(d, ctx.foamRange);
        int foamCount = foam ? count(cells.length * 0.015 * near * ctx.scale) : 0;
        int sprayCount = spray ? count(cells.length * 0.004 * near * ctx.scale) : 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = foamCount + sprayCount; n > 0; n--) {
            long cell = cells[RANDOM.nextInt(cells.length)];
            pos.set(BlockPos.getX(cell), BlockPos.getY(cell), BlockPos.getZ(cell));
            FluidState fluid = ctx.level.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || fluid.isSource() || !ctx.level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            Vec3 flow = fluid.getFlow(ctx.level, pos);
            double x = pos.getX() + 0.15 + RANDOM.nextDouble() * 0.7;
            double z = pos.getZ() + 0.15 + RANDOM.nextDouble() * 0.7;
            double y = pos.getY() + fluid.getOwnHeight();
            if (n > sprayCount) {
                FoamParticle p = new FoamParticle(ctx.level, x, y + 0.025, z, flow.x * 0.05, flow.z * 0.05, 0.08F + RANDOM.nextFloat() * 0.12F,
                    50 + RANDOM.nextInt(70), ctx.foamCap);
                add(ctx, p.withSprite(sprite(ctx.foamSprites, RANDOM.nextInt(4))));
            } else {
                SprayParticle p = new SprayParticle(ctx.level, x, y + 0.05, z, flow.x * 0.04 + RANDOM.nextGaussian() * 0.015,
                    0.05 + RANDOM.nextDouble() * 0.07, flow.z * 0.04 + RANDOM.nextGaussian() * 0.015, SprayParticle.Kind.SPLASH, ctx.sprayCap);
                add(ctx, p.withSprite(sprite(ctx.spraySprites, 3)));
            }
        }
    }

    /** One of the four sprites of a set. */
    private static TextureAtlasSprite sprite(SpriteSet sprites, int index) {
        return sprites.get(index, 3);
    }

    private static void add(Ctx ctx, Particle particle) {
        ctx.mc.particleEngine.add(particle);
    }
}
