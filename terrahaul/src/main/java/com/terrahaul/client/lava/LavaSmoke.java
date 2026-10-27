package com.terrahaul.client.lava;

import com.terrahaul.client.particle.LavaSmokeParticle;
import com.terrahaul.client.particle.TerrahaulParticleProviders;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.config.TerrahaulConfig.AnimationQuality;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Smoke over lava open to the sky in the Overworld: Terrahaul's crater lakes, lava flows, and any
 * other lava, vanilla's or a player's, whose surface is the top of its column (see
 * {@link LavaScanner}). Visual only; nothing here runs on a server.
 * <ul>
 *   <li>thick, dark puffs rise from every stretch of open lava, warm-tinted by its glow where they
 *       are born, swelling and greying as they climb and lean over with the wind
 *       ({@link LavaSmokeParticle});</li>
 *   <li>a lone lava block, with no open lava beside it, gives off only thin, small wisps; flowing
 *       lava somewhat less smoke than a still pool;</li>
 *   <li>a large lava lake (some {@value #LAKE_CELLS} open cells or more, a crater's) also raises a
 *       tall column: large, slow puffs that climb some forty to sixty blocks above its middle before
 *       the wind takes them, seen from across the crater.</li>
 * </ul>
 * The puffs come from the lava near the camera, nearer lava first: the spawn rate of each chunk
 * grows with its open lava but less than in proportion, and the total per tick is capped, so a
 * crater lake hundreds of blocks wide costs no more than a few pools. Rain makes the smoke paler
 * (steam). The amount follows the animation quality and Minecraft's Particles option (half at
 * Decreased, none at Minimal), and the {@code lavaSmoke} option turns it off. The puffs have
 * particle caps of their own, so the smoke never takes room from the water effects or the leaves.
 */
public final class LavaSmoke {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Live particle caps for the LOW, MEDIUM and HIGH quality: the puffs of the surface, and those of the columns. */
    private static final ParticleGroup[] CAP = {new ParticleGroup(200), new ParticleGroup(400), new ParticleGroup(700)};
    private static final ParticleGroup[] COLUMN_CAP = {new ParticleGroup(100), new ParticleGroup(200), new ParticleGroup(320)};
    /** Reach of the scan in chunks, and of the surface smoke in blocks, for the LOW, MEDIUM and HIGH quality. */
    private static final int[] RADIUS = {4, 7, 10};
    private static final double[] RANGE = {56.0, 88.0, 120.0};
    /** Most surface puffs per tick, before the Particles option. */
    private static final double[] MAX_PER_TICK = {3.0, 6.0, 10.0};
    /** Puffs per tick per chunk, for its open lava cells to the power of 0.6 (a full chunk: about a puff). */
    private static final double RATE = 0.035;
    /** Open lava cells of a lake (chunks joined across their borders) that raise a column. */
    private static final int LAKE_CELLS = 400;
    /** Chunks need this much open lava to join a lake; scattered lava blocks never make one. */
    private static final int LAKE_CHUNK = 24;
    private static final int REBUILD_TICKS = 20;

    private static final LavaScanner SCANNER = new LavaScanner();
    private static final List<LavaScanner.Patch> PATCHES = new ArrayList<>();
    private static final List<Lake> LAKES = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static long ticks;
    private static long lastRebuild;
    private static int builtVersion = -1;
    private static long spawned;

    /** A large lava lake: the point its column rises from, and its open lava cells. */
    private record Lake(double x, double y, double z, int cells) {
    }

    private LavaSmoke() {
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
        SpriteSet sprites = TerrahaulParticleProviders.smokeSprites();
        if (!TerrahaulConfig.lavaSmoke() || quality == AnimationQuality.OFF || mc.options.particles().get() == ParticleStatus.MINIMAL
            || sprites == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ticks++;
        int q = qualityIndex(quality);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int radius = Math.min(Math.min(RADIUS[q], LavaScanner.MAX_RADIUS), mc.options.getEffectiveRenderDistance());
        SCANNER.tick(level, cam, radius, ticks);
        if (SCANNER.version() != builtVersion && ticks - lastRebuild >= REBUILD_TICKS) {
            builtVersion = SCANNER.version();
            lastRebuild = ticks;
            rebuild();
        }
        if (underground(level, cam)) {
            // deep in a cave or a magma chamber no smoke over the open lava far above can be seen
            return;
        }
        double scale = (q == 0 ? 0.45 : q == 1 ? 0.7 : 1.0) * (mc.options.particles().get() == ParticleStatus.DECREASED ? 0.5 : 1.0);
        float rain = level.getRainLevel(1.0F);
        spawnSurface(mc, level, cam, q, scale, rain, sprites);
        spawnColumns(mc, level, cam, radius * 16.0 + 16.0, q, scale, rain, sprites);
    }

    /** Whether the camera is in the dark well below the top of its column (no sky light, sixteen blocks or more down). */
    private static boolean underground(ClientLevel level, Vec3 cam) {
        BlockPos pos = BlockPos.containing(cam);
        return level.getBrightness(LightLayer.SKY, pos) == 0
            && cam.y < level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 16;
    }

    /** What the smoke currently sees, for the development screenshot log. */
    public static String describe() {
        int cells = 0;
        for (LavaScanner.Patch p : PATCHES) {
            cells += p.count();
        }
        int biggest = 0;
        for (Lake lake : LAKES) {
            biggest = Math.max(biggest, lake.cells());
        }
        return String.format(Locale.ROOT, "%d chunks with open lava (%d cells), %d lakes with a column (largest %d cells), %d puffs spawned",
            PATCHES.size(), cells, LAKES.size(), biggest, spawned);
    }

    /** Forgets everything, when the player leaves a world or changes dimension. */
    public static void reset() {
        SCANNER.clear();
        PATCHES.clear();
        LAKES.clear();
        builtVersion = -1;
        lastRebuild = ticks - REBUILD_TICKS;
    }

    /** A chunk arrived from the server or was dropped on this client: it is read again. */
    public static void onChunkChanged(ChunkEvent event) {
        // the integrated server posts chunk events of its own levels, on its own threads
        if (!event.getLevel().isClientSide() || event.getLevel() != lastLevel) {
            return;
        }
        SCANNER.forgetChunk(event.getChunk().getPos().x, event.getChunk().getPos().z);
    }

    private static int qualityIndex(AnimationQuality quality) {
        return switch (quality) {
            case OFF, LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
    }

    /**
     * Gathers the chunks with open lava and joins those with enough of it, where they touch
     * (diagonally too), into lakes; a lake large enough gets a column, rising from the sampled
     * cell nearest its centre in the chunk nearest its centre that is mostly lava.
     */
    private static void rebuild() {
        PATCHES.clear();
        PATCHES.addAll(SCANNER.patches());
        LAKES.clear();
        int n = PATCHES.size();
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(n * 2);
        index.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            if (PATCHES.get(i).count() >= LAKE_CHUNK) {
                index.put(PATCHES.get(i).key(), i);
            }
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            LavaScanner.Patch p = PATCHES.get(i);
            if (p.count() < LAKE_CHUNK) {
                continue;
            }
            int cx = ChunkPos.getX(p.key());
            int cz = ChunkPos.getZ(p.key());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int j = index.get(ChunkPos.asLong(cx + dx, cz + dz));
                    if (j >= 0) {
                        union(parent, i, j);
                    }
                }
            }
        }
        // per lake: its cells and centre
        double[] sx = new double[n];
        double[] sz = new double[n];
        int[] cells = new int[n];
        for (int i = 0; i < n; i++) {
            LavaScanner.Patch p = PATCHES.get(i);
            if (p.count() < LAKE_CHUNK) {
                continue;
            }
            int r = find(parent, i);
            sx[r] += p.x() * p.count();
            sz[r] += p.z() * p.count();
            cells[r] += p.count();
        }
        for (int r = 0; r < n; r++) {
            if (find(parent, r) != r || cells[r] < LAKE_CELLS) {
                continue;
            }
            double lx = sx[r] / cells[r];
            double lz = sz[r] / cells[r];
            LavaScanner.Patch best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                LavaScanner.Patch p = PATCHES.get(i);
                if (p.count() >= 128 && find(parent, i) == r) {
                    double d = (p.x() - lx) * (p.x() - lx) + (p.z() - lz) * (p.z() - lz);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
            if (best == null) {
                continue;
            }
            long cell = best.cells()[0];
            float surface = best.surface()[0];
            double cellD = Double.MAX_VALUE;
            for (int k = 0; k < best.cells().length; k++) {
                long c = best.cells()[k];
                double d = Mth.square(BlockPos.getX(c) + 0.5 - lx) + Mth.square(BlockPos.getZ(c) + 0.5 - lz);
                if (d < cellD) {
                    cellD = d;
                    cell = c;
                    surface = best.surface()[k];
                }
            }
            LAKES.add(new Lake(BlockPos.getX(cell) + 0.5, BlockPos.getY(cell) + surface, BlockPos.getZ(cell) + 0.5, cells[r]));
        }
    }

    /** Puffs over the open lava near the camera, spread over the chunks by how much lava each has and how near it is. */
    private static void spawnSurface(Minecraft mc, ClientLevel level, Vec3 cam, int q, double scale, float rain, SpriteSet sprites) {
        int n = PATCHES.size();
        if (n == 0) {
            return;
        }
        double range = RANGE[q];
        double[] cumulative = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            LavaScanner.Patch p = PATCHES.get(i);
            // the distance to the nearest part of the chunk rather than to its lava's centre
            double d = Math.max(0.0, Math.sqrt(cam.distanceToSqr(p.x(), p.y(), p.z())) - 8.0);
            if (d < range) {
                total += Math.pow(p.count(), 0.6) * near(d, range);
            }
            cumulative[i] = total;
        }
        if (total <= 0.0) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = count(Math.min(MAX_PER_TICK[q], RATE * total) * scale); k > 0; k--) {
            double pick = RANDOM.nextDouble() * total;
            int lo = 0;
            int hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] < pick) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            LavaScanner.Patch p = PATCHES.get(lo);
            int s = RANDOM.nextInt(p.cells().length);
            long cell = p.cells()[s];
            pos.set(BlockPos.getX(cell), BlockPos.getY(cell), BlockPos.getZ(cell));
            // still lava open to the air (the sample may be a few seconds old)
            if (!level.getFluidState(pos).is(FluidTags.LAVA) || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            byte flags = p.flags()[s];
            double x = pos.getX() + 0.15 + RANDOM.nextDouble() * 0.7;
            double z = pos.getZ() + 0.15 + RANDOM.nextDouble() * 0.7;
            double y = pos.getY() + p.surface()[s] + 0.15;
            if (cam.distanceToSqr(x, y, z) > range * range) {
                continue;
            }
            float grey = 0.16F + RANDOM.nextFloat() * 0.08F + 0.16F * rain;
            LavaSmokeParticle puff;
            if ((flags & LavaScanner.LONE) != 0) {
                // a lone lava block: a thin wisp
                puff = new LavaSmokeParticle(level, x, y, z, RANDOM.nextGaussian() * 0.004, 0.03 + RANDOM.nextDouble() * 0.02,
                    RANDOM.nextGaussian() * 0.004, 0.18F + RANDOM.nextFloat() * 0.12F, 0.7F + RANDOM.nextFloat() * 0.5F, 50 + RANDOM.nextInt(40),
                    0.24F + RANDOM.nextFloat() * 0.08F, 0.02F + RANDOM.nextFloat() * 0.01F, grey + 0.06F, CAP[q]);
            } else {
                boolean flowing = (flags & LavaScanner.FLOWING) != 0;
                // rises some ten to fifteen blocks while it swells and thins
                puff = new LavaSmokeParticle(level, x, y, z, RANDOM.nextGaussian() * 0.006, 0.07 + RANDOM.nextDouble() * 0.04,
                    RANDOM.nextGaussian() * 0.006, 0.5F + RANDOM.nextFloat() * 0.4F, 2.6F + RANDOM.nextFloat() * 2.0F, 130 + RANDOM.nextInt(100),
                    (flowing ? 0.36F : 0.42F) + RANDOM.nextFloat() * 0.1F, 0.045F + RANDOM.nextFloat() * 0.025F, grey, CAP[q]);
            }
            add(mc, puff.withSprite(sprites.get(RANDOM.nextInt(4), 3)));
        }
    }

    /** The tall columns over large lakes: big, slow puffs that rise high above the lake's middle. */
    private static void spawnColumns(Minecraft mc, ClientLevel level, Vec3 cam, double range, int q, double scale, float rain, SpriteSet sprites) {
        for (Lake lake : LAKES) {
            double d = Math.sqrt(cam.distanceToSqr(lake.x(), lake.y(), lake.z()));
            if (d > range) {
                continue;
            }
            // bigger lakes smoke harder, up to about fourteen puffs a second
            double rate = (0.35 + 0.35 * Math.min(1.0, (lake.cells() - LAKE_CELLS) / 4000.0)) * far(d, range) * scale;
            for (int k = count(rate); k > 0; k--) {
                double spread = 3.0 + Math.min(5.0, Math.sqrt(lake.cells()) * 0.04);
                double x = lake.x() + RANDOM.nextGaussian() * spread;
                double z = lake.z() + RANDOM.nextGaussian() * spread;
                double y = lake.y() + 0.3 + RANDOM.nextDouble() * 1.5;
                float grey = 0.18F + RANDOM.nextFloat() * 0.08F + 0.16F * rain;
                LavaSmokeParticle puff = new LavaSmokeParticle(level, x, y, z, RANDOM.nextGaussian() * 0.01, 0.16 + RANDOM.nextDouble() * 0.06,
                    RANDOM.nextGaussian() * 0.01, 1.6F + RANDOM.nextFloat() * 1.0F, 7.0F + RANDOM.nextFloat() * 5.0F, 320 + RANDOM.nextInt(200),
                    0.44F + RANDOM.nextFloat() * 0.1F, 0.1F + RANDOM.nextFloat() * 0.04F, grey, COLUMN_CAP[q]);
                add(mc, puff.withSprite(sprites.get(RANDOM.nextInt(4), 3)));
            }
        }
    }

    private static void add(Minecraft mc, LavaSmokeParticle puff) {
        mc.particleEngine.add(puff);
        spawned++;
    }

    /** Full rate up close, fading out towards the edge of the range. */
    private static double near(double d, double range) {
        return Mth.clamp(1.15 - d / range, 0.0, 1.0);
    }

    /** Full rate to four fifths of the range, then fading out: for the columns, which are seen from afar. */
    private static double far(double d, double range) {
        return Mth.clamp((range - d) / (0.2 * range), 0.0, 1.0);
    }

    /** A whole number of events for a fractional expected count. */
    private static int count(double expected) {
        int n = (int) expected;
        return RANDOM.nextDouble() < expected - n ? n + 1 : n;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }
}
