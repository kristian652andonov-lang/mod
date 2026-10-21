package com.terrahaul.devtools;

import com.terrahaul.worldgen.trees.TreeBlueprint;
import com.terrahaul.worldgen.trees.TreeShapes;
import com.terrahaul.worldgen.trees.TreeSize;
import com.terrahaul.worldgen.trees.TreeSpecies;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * Development tool: plants whole stands of trees on flat ground the way the game's tree placements
 * would, and measures what single blueprints cannot show: how many trees a biome really gets once
 * crowds reject some of them, their size mix, the blocks written per chunk, and how dark the forest
 * floor becomes.
 * <p>
 * Run with {@code ./gradlew treeStudy -Pargs="stand <outDir> <biome,...|conifers> [seed] [chunks] [flags]"}, where a
 * biome is a vanilla biome name such as {@code old_growth_pine_taiga}. The tree placements of each
 * biome are read from the mod's data pack (the {@code trees/} biome modifiers, their placed features'
 * {@code density_count} and the random selectors and size weights of their configured features), so
 * the study follows the generated data; so are its fallen logs ({@code details/} modifiers of
 * {@code terrahaul:fallen_log} features), which the game places in the same step just before the
 * trees, lying on the ground with a stump at the root end of some. Each chunk of a square of
 * {@code chunks} x {@code chunks} gets each placement's count of candidates (at the noise field's
 * mean density) at random spots; every candidate is sized, built, checked and placed like
 * {@code ProceduralTreeFeature} does it: ancient trees move to the middle of their chunk and giant
 * pines three blocks inside it, a tree whose trunk runs into wood or whose crown is more than 15%
 * blocked is retried one size smaller once, leaves replace leaves, ground roots are laid where no
 * other tree's wood stands, and a conifer keeps its foot a block clear of every other trunk while
 * its stem may grow through a neighbour's limbs (logs on their side and bark blocks above the root
 * zone with no upright log beside them, see {@link #limbWood}). {@code flags} is a comma-separated
 * list: {@code strict} turns the conifer rules off (the trunk passes no wood at all, as saplings
 * grow), {@code nologs} leaves the fallen logs out. Statistics cover the chunks at least two chunks
 * inside the square. Besides placements, size mix and canopy height, the study reports what the
 * stems grew through (limbs, ground roots and root flares, other trunks' columns, fallen logs) and
 * how many canopy trees (medium or larger) stand with their foot touching another one's, or with
 * their stems touching six blocks up.
 * <p>
 * Floor light is vanilla-like sky light: full light comes straight down through air, each leaf
 * block takes one level off it, wood stops it, and below the canopy light spreads sideways losing
 * one level per block. The study reports the mean sky light of the open floor and the share at 7 or
 * less (where hostile mobs may spawn in daylight) and at 4 or less, the share of floor columns
 * where wood or leaves block the feet or the head of a walker, and writes a plan view, a floor
 * light map and a walker's view from inside the stand per biome.
 */
public final class StandStudy {
    private static final String DATA = "src/main/resources/data/terrahaul";
    private static final List<String> CONIFERS = List.of("old_growth_pine_taiga", "old_growth_spruce_taiga", "taiga", "snowy_taiga", "grove",
        "windswept_forest", "forest", "dark_forest");
    /** Space around the planted chunks so the crowns of the outer trees fit. */
    private static final int MARGIN = TreeBlueprint.ANCIENT_REACH + 2;
    private static final int HEIGHT = 112;
    private static final byte AIR = 0;
    private static final byte SOIL = 9;
    /** Layers of a stem whose wood is never displaced (see ProceduralTreeFeature.ROOT_ZONE). */
    private static final int ROOT_ZONE = 4;
    /** What a cell of wood belongs to: a tree's trunk columns, its ground roots, its other wood, a fallen log. */
    private static final byte PART_TRUNK = 1;
    private static final byte PART_ROOT = 2;
    private static final byte PART_LIMB = 3;
    private static final byte PART_FALLEN = 4;

    private StandStudy() {
    }

    /** One tree configuration: species and size weights. */
    private record TreeConfig(TreeSpecies species, Map<TreeSize, Double> sizes) {
    }

    /** One placement: count per chunk and a random selector over tree configurations (null: not a tree). */
    private record Placement(String name, double count, List<Double> chances, List<TreeConfig> features, TreeConfig fallback) {
    }

    private static final class Stats {
        int candidates;
        int placed;
        int rejected;
        int built;
        long written;
        /** Placed trees whose stem displaced: limbs, ground roots, another tree's trunk columns, fallen logs. */
        final int[] through = new int[5];
        int trunkHits;
        int crowded;
        int againstTrunk;
        int fallenLogs;
        /** Placed canopy trees (medium or larger), their summed height, and how many are 30 or more tall. */
        int canopy;
        long canopyHeight;
        int tall;
        int large;
        int giant;
        final Map<String, int[]> rolled = new TreeMap<>();
        final Map<String, int[]> grown = new TreeMap<>();
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/stands");
        String biomeArg = args.length > 1 ? args[1] : "conifers";
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 12345L;
        int chunks = args.length > 3 ? Integer.parseInt(args[3]) : 12;
        List<String> flags = args.length > 4 ? List.of(args[4].split(",")) : List.of();
        boolean strict = flags.contains("strict");
        Path data = Path.of(System.getProperty("terrahaul.data", DATA));
        Files.createDirectories(out);
        List<String> biomes = biomeArg.equals("conifers") ? CONIFERS : List.of(biomeArg.split(","));
        List<String> report = new ArrayList<>();
        for (String biome : biomes) {
            List<Placement> placements = placements(data, biome);
            double logs = flags.contains("nologs") ? 0.0 : fallenLogs(data, biome);
            report.addAll(new StandStudy.Stand(placements, logs, chunks, seed, strict).run(biome, out));
        }
        Files.write(out.resolve("stands.txt"), report);
        report.forEach(System.out::println);
    }

    // ------------------------------------------------------------------------------------------
    // The simulated stand
    // ------------------------------------------------------------------------------------------

    private static final class Stand {
        private final List<Placement> placements;
        private final int chunks;
        private final int size;
        private final SplittableRandom random;
        private final boolean strict;
        private final double logs;
        private final byte[] vox;
        /** What each cell of wood belongs to (PART_*). */
        private final byte[] part;
        private final List<int[]> marks = new ArrayList<>();
        /** Placed canopy trees: their foot, their stem six blocks up, and whether they are counted. */
        private final List<Canopy> canopies = new ArrayList<>();
        private final Stats stats = new Stats();

        Stand(List<Placement> placements, double logs, int chunks, long seed, boolean strict) {
            this.placements = placements;
            this.logs = logs;
            this.chunks = chunks;
            this.size = chunks * 16 + 2 * MARGIN;
            this.random = new SplittableRandom(seed);
            this.strict = strict;
            this.vox = new byte[size * size * HEIGHT];
            this.part = new byte[size * size * HEIGHT];
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    vox[idx(x, 0, z)] = SOIL;
                }
            }
        }

        private int idx(int x, int iy, int z) {
            return (iy * size + x) * size + z;
        }

        /** The cell at world column (x, z) and tree layer y (0 = the first layer above the ground). */
        private byte get(int x, int y, int z) {
            int iy = y + 1;
            if (x < 0 || z < 0 || x >= size || z >= size || iy < 0 || iy >= HEIGHT) {
                return AIR;
            }
            return vox[idx(x, iy, z)];
        }

        private void set(int x, int y, int z, byte code) {
            int iy = y + 1;
            if (x >= 0 && z >= 0 && x < size && z < size && iy >= 0 && iy < HEIGHT) {
                vox[idx(x, iy, z)] = code;
            }
        }

        private void setPart(int x, int y, int z, byte p) {
            int iy = y + 1;
            if (x >= 0 && z >= 0 && x < size && z < size && iy >= 0 && iy < HEIGHT) {
                part[idx(x, iy, z)] = p;
            }
        }

        private byte partAt(int x, int y, int z) {
            int iy = y + 1;
            if (x < 0 || z < 0 || x >= size || z >= size || iy < 0 || iy >= HEIGHT) {
                return 0;
            }
            return part[idx(x, iy, z)];
        }

        /**
         * A fallen log as {@code FallenLogFeature} lays it on flat ground: on a spot with no wood
         * above it, a run of 4 to 9 logs on the first layer above the ground in one direction, as
         * far as the floor is free, and a stump of one or two upright logs at the root end of some.
         */
        private void fallenLog(int cx, int cz) {
            int ox = MARGIN + cx * 16 + random.nextInt(16);
            int oz = MARGIN + cz * 16 + random.nextInt(16);
            int dir = random.nextInt(4);
            int length = 4 + random.nextInt(6);
            boolean stump = random.nextFloat() < 0.35f;
            int stumpHeight = 1 + random.nextInt(2);
            for (int y = 0; y < HEIGHT - 1; y++) {
                if (TreeBlueprint.isWoody(get(ox, y, oz))) {
                    return; // the placement's heightmap stands on this wood, not on the ground
                }
            }
            int dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
            int dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
            int usable = 0;
            while (usable < length && get(ox + dx * usable, 0, oz + dz * usable) == AIR) {
                usable++;
            }
            if (usable < 3) {
                return;
            }
            byte code = dx != 0 ? TreeBlueprint.LOG_X : TreeBlueprint.LOG_Z;
            for (int i = 0; i < usable; i++) {
                set(ox + dx * i, 0, oz + dz * i, code);
                setPart(ox + dx * i, 0, oz + dz * i, PART_FALLEN);
            }
            if (stump) {
                for (int y = 0; y < stumpHeight; y++) {
                    if (get(ox - dx, y, oz - dz) == AIR) {
                        set(ox - dx, y, oz - dz, TreeBlueprint.LOG_Y);
                        setPart(ox - dx, y, oz - dz, PART_FALLEN);
                    }
                }
            }
            if (inner(cx, cz)) {
                stats.fallenLogs++;
            }
        }

        private boolean inner(int cx, int cz) {
            return cx >= 2 && cz >= 2 && cx < chunks - 2 && cz < chunks - 2;
        }

        List<String> run(String biome, Path out) throws IOException {
            for (int cx = 0; cx < chunks; cx++) {
                for (int cz = 0; cz < chunks; cz++) {
                    // fallen logs come first in the decoration step (their biome modifiers sort before the trees')
                    int n = (int) logs;
                    if (random.nextDouble() < logs - n) {
                        n++;
                    }
                    for (int i = 0; i < n; i++) {
                        fallenLog(cx, cz);
                    }
                    for (Placement p : placements) {
                        int k = (int) p.count;
                        if (random.nextDouble() < p.count - k) {
                            k++;
                        }
                        for (int i = 0; i < k; i++) {
                            candidate(p, cx, cz);
                        }
                    }
                }
            }
            double[] light = floorLight();
            int innerChunks = (chunks - 4) * (chunks - 4);
            List<String> lines = new ArrayList<>();
            StringBuilder counts = new StringBuilder();
            for (Placement p : placements) {
                counts.append(String.format(Locale.ROOT, " %s %.2f", p.name, p.count));
            }
            lines.add(String.format(Locale.ROOT, "%s (placements:%s, fallen logs %.2f)%s", biome, counts, logs,
                strict ? " [strict: stems never pass other wood]" : ""));
            lines.add(String.format(Locale.ROOT,
                "  per chunk: candidates %.2f, placed %.2f, rejected %.2f, blueprints built %.2f, blocks written %.0f",
                stats.candidates / (double) innerChunks, stats.placed / (double) innerChunks, stats.rejected / (double) innerChunks,
                stats.built / (double) innerChunks, stats.written / (double) innerChunks));
            lines.add(String.format(Locale.ROOT, "  failed attempts: trunk ran into wood %d, foot against another trunk %d, crown over 15%% blocked %d",
                stats.trunkHits, stats.againstTrunk, stats.crowded));
            lines.add(String.format(Locale.ROOT,
                "  stems grown through other wood, trees per chunk: limbs %.3f, ground roots or root flares %.3f, another trunk's columns %.3f,"
                    + " fallen logs %.3f (fallen logs per chunk %.2f)",
                stats.through[PART_LIMB] / (double) innerChunks, stats.through[PART_ROOT] / (double) innerChunks,
                stats.through[PART_TRUNK] / (double) innerChunks, stats.through[PART_FALLEN] / (double) innerChunks,
                stats.fallenLogs / (double) innerChunks));
            lines.add(touching(innerChunks));
            lines.add(String.format(Locale.ROOT,
                "  canopy (medium or larger) per chunk %.2f, mean height %.1f; large or larger %.2f, giant or larger %.2f, 30 or more tall %.2f",
                stats.canopy / (double) innerChunks, stats.canopy == 0 ? 0.0 : stats.canopyHeight / (double) stats.canopy,
                stats.large / (double) innerChunks, stats.giant / (double) innerChunks, stats.tall / (double) innerChunks));
            for (Map.Entry<String, int[]> e : stats.rolled.entrySet()) {
                int[] rolled = e.getValue();
                int[] grown = stats.grown.getOrDefault(e.getKey(), new int[TreeSize.values().length]);
                StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  %-12s rolled/placed per chunk:", e.getKey()));
                for (TreeSize s : TreeSize.values()) {
                    if (rolled[s.ordinal()] + grown[s.ordinal()] > 0) {
                        sb.append(String.format(Locale.ROOT, " %s %.2f/%.2f", s.serializedName(), rolled[s.ordinal()] / (double) innerChunks,
                            grown[s.ordinal()] / (double) innerChunks));
                    }
                }
                lines.add(sb.toString());
            }
            lines.add(String.format(Locale.ROOT, "  floor sky light: mean %.2f, at 7 or less %.1f%%, at 4 or less %.1f%% (%d open floor cells);"
                + " columns blocked at feet or head %.1f%%", light[0], light[1], light[2], (int) light[3], light[4]));
            String tag = biome + (strict ? "_strict" : "");
            ImageIO.write(picture(biome), "png", out.resolve("stand_" + tag + ".png").toFile());
            ImageIO.write(view(), "png", out.resolve("view_" + tag + ".png").toFile());
            return lines;
        }

        private void candidate(Placement p, int cx, int cz) {
            int ox = MARGIN + cx * 16 + random.nextInt(16);
            int oz = MARGIN + cz * 16 + random.nextInt(16);
            TreeConfig config = p.fallback;
            for (int i = 0; i < p.chances.size(); i++) {
                if (random.nextFloat() < p.chances.get(i)) {
                    config = p.features.get(i);
                    break;
                }
            }
            if (config == null) {
                return;
            }
            boolean counted = inner(cx, cz);
            TreeSize size = pick(config.sizes);
            String name = config.species.serializedName();
            if (counted) {
                stats.candidates++;
                stats.rolled.computeIfAbsent(name, k -> new int[TreeSize.values().length])[size.ordinal()]++;
            }
            if (size == TreeSize.ANCIENT) {
                ox = MARGIN + cx * 16 + 7 + random.nextInt(2);
                oz = MARGIN + cz * 16 + 7 + random.nextInt(2);
            } else if (TreeShapes.reach(config.species, size) > TreeBlueprint.MAX_REACH) {
                // giant pines: the chunk-local position squeezed from 0..15 into 3..12
                ox = MARGIN + cx * 16 + 3 + (ox - MARGIN - cx * 16) * 10 / 16;
                oz = MARGIN + cz * 16 + 3 + (oz - MARGIN - cz * 16) * 10 / 16;
            }
            SplittableRandom shape = new SplittableRandom(random.nextLong());
            for (int attempt = 0; attempt < 2; attempt++) {
                TreeBlueprint bp = TreeShapes.create(config.species, size, shape);
                if (counted) {
                    stats.built++;
                }
                int fit = fits(bp, ox, oz);
                if (fit >= 0) {
                    long n = place(bp, ox, oz);
                    if (counted) {
                        stats.placed++;
                        stats.written += n;
                        stats.grown.computeIfAbsent(name, k -> new int[TreeSize.values().length])[size.ordinal()]++;
                        if (size.atLeast(TreeSize.MEDIUM)) {
                            stats.canopy++;
                            stats.canopyHeight += bp.height();
                            stats.tall += bp.height() >= 30 ? 1 : 0;
                            stats.large += size.atLeast(TreeSize.LARGE) ? 1 : 0;
                            stats.giant += size.atLeast(TreeSize.GIANT) ? 1 : 0;
                        }
                    }
                    if (size.atLeast(TreeSize.MEDIUM)) {
                        canopies.add(new Canopy(bp, ox, oz, counted));
                    }
                    marks.add(new int[]{ox, oz, 1});
                    return;
                }
                if (size == TreeSize.YOUNG) {
                    break;
                }
                size = size.smaller();
            }
            marks.add(new int[]{ox, oz, 0});
            if (counted) {
                stats.rejected++;
            }
        }

        private TreeSize pick(Map<TreeSize, Double> weights) {
            double total = 0;
            for (double w : weights.values()) {
                total += w;
            }
            double u = random.nextDouble() * total;
            TreeSize last = TreeSize.MEDIUM;
            for (Map.Entry<TreeSize, Double> e : weights.entrySet()) {
                last = e.getKey();
                u -= e.getValue();
                if (u < 0) {
                    return last;
                }
            }
            return last;
        }

        /** Whether the conifer rules of world generation apply to this tree (see ProceduralTreeFeature). */
        private boolean stand(TreeBlueprint bp) {
            return !strict && bp.species().isConifer();
        }

        /**
         * Whether one of the eight cells around blueprint cell {@code (x, y, z)} of a tree at
         * {@code (ox, oz)} holds an upright log, skipping the tree's own stem at that layer.
         */
        private boolean besideUpright(TreeBlueprint bp, int ox, int oz, int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 || bp.isTrunkBase(x + dx, z + dz) && TreeBlueprint.isWoody(bp.get(x + dx, y, z + dz))) {
                        continue;
                    }
                    if (get(ox + x + dx, y, oz + z + dz) == TreeBlueprint.LOG_Y) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * A neighbour's limb the stem may grow through, as the feature tells it (see {@link #limbWood}):
         * above the root zone, not resting on the ground, with no upright log beside it.
         */
        private boolean limb(TreeBlueprint bp, int ox, int oz, int x, int y, int z, byte e) {
            return y >= ROOT_ZONE && limbWood(e) && get(ox + x, y - 1, oz + z) != SOIL && !besideUpright(bp, ox, oz, x, y, z);
        }

        /** Whether an upright log stands next to the stem's footprint in the root zone. */
        private boolean againstTrunk(TreeBlueprint bp, int ox, int oz) {
            boolean[] hit = {false};
            bp.forEachTrunkBase((x, z, unused) -> {
                for (int dx = -1; dx <= 1 && !hit[0]; dx++) {
                    for (int dz = -1; dz <= 1 && !hit[0]; dz++) {
                        for (int y = 0; y < ROOT_ZONE && !hit[0]; y++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (bp.isTrunkBase(nx, nz) && TreeBlueprint.isWoody(bp.get(nx, y, nz))) {
                                continue;
                            }
                            hit[0] = get(ox + nx, y, oz + nz) == TreeBlueprint.LOG_Y;
                        }
                    }
                }
            });
            return hit[0];
        }

        /**
         * The feature's fit test against the trees already standing: -1 when the tree does not fit,
         * otherwise the number of limb cells its trunk grows through.
         */
        private int fits(TreeBlueprint bp, int ox, int oz) {
            if (stand(bp) && againstTrunk(bp, ox, oz)) {
                stats.againstTrunk++;
                return -1;
            }
            int trunkLimit = Math.max(2, (int) (bp.height() * 0.6));
            int[] counts = new int[4]; // checked, blocked, sampled leaves, displaced
            boolean[] ok = {true};
            bp.forEachCell((x, y, z, code) -> {
                if (!ok[0] || y < 0) {
                    return;
                }
                byte e = get(ox + x, y, oz + z);
                boolean log = TreeBlueprint.isWoody(e);
                if (TreeBlueprint.isLeaf(code)) {
                    if ((counts[2]++ & 3) != 0) {
                        return;
                    }
                    counts[0]++;
                    if (log) {
                        counts[1]++;
                    }
                    return;
                }
                boolean trunk = bp.isTrunkBase(x, z) && y < trunkLimit;
                boolean through = trunk && log && stand(bp) && limb(bp, ox, oz, x, y, z, e);
                boolean free = !log || !trunk || through;
                if (!free) {
                    ok[0] = false;
                    stats.trunkHits++;
                    return;
                }
                counts[3] += through ? 1 : 0;
                counts[0]++;
            });
            if (!ok[0]) {
                return -1;
            }
            if (counts[0] > 0 && counts[1] > counts[0] * 0.15) {
                stats.crowded++;
                return -1;
            }
            return counts[3];
        }

        /** Places a blueprint like the feature does; returns the number of blocks written. */
        private long place(TreeBlueprint bp, int ox, int oz) {
            bp.finish();
            int trunkLimit = Math.max(2, (int) (bp.height() * 0.6));
            // the limbs the stem grows through, chosen before the tree's own wood stands beside them
            java.util.Set<Long> limbs = new java.util.HashSet<>();
            if (stand(bp)) {
                bp.forEachTrunkBase((x, z, unused) -> {
                    for (int y = ROOT_ZONE; y < trunkLimit; y++) {
                        if (TreeBlueprint.isWoody(bp.get(x, y, z)) && limb(bp, ox, oz, x, y, z, get(ox + x, y, oz + z))) {
                            limbs.add(((long) x << 40) ^ ((long) y << 20) ^ (z & 0xfffffL));
                        }
                    }
                });
            }
            boolean[] grew = new boolean[5];
            long[] n = {0};
            // the ground under the roots is read before anything is written
            List<double[]> roots = new ArrayList<>();
            bp.forEachGroundRoot((x, z, rise) -> {
                for (int y = 6; y >= -1; y--) {
                    if (TreeBlueprint.isWoody(get(ox + x, y, oz + z))) {
                        return;
                    }
                }
                roots.add(new double[]{x, z, rise});
            });
            bp.forEachCell((x, y, z, code) -> {
                if (y < 0 || TreeBlueprint.isLeaf(code) || !TreeBlueprint.isWoody(code)) {
                    return;
                }
                byte e = get(ox + x, y, oz + z);
                boolean displace = limbs.contains(((long) x << 40) ^ ((long) y << 20) ^ (z & 0xfffffL));
                if (displace) {
                    grew[partAt(ox + x, y, oz + z)] = true;
                }
                if (e == AIR || TreeBlueprint.isLeaf(e) || displace) {
                    set(ox + x, y, oz + z, (byte) code);
                    n[0]++;
                    setPart(ox + x, y, oz + z, bp.isTrunkBase(x, z) ? PART_TRUNK : PART_LIMB);
                }
            });
            for (double[] r : roots) {
                int x = ox + (int) r[0];
                int z = oz + (int) r[1];
                if (r[2] < 0) {
                    continue;
                }
                int top = -1 + (int) Math.floor(r[2]);
                for (int y = -1; y <= top; y++) {
                    byte e = get(x, y, z);
                    if (y == -1 || e == AIR || TreeBlueprint.isLeaf(e)) {
                        set(x, y, z, TreeBlueprint.WOOD);
                        n[0]++;
                        setPart(x, y, z, PART_ROOT);
                    }
                }
            }
            bp.forEachCell((x, y, z, code) -> {
                if (y < 0 || !TreeBlueprint.isLeaf(code)) {
                    return;
                }
                byte e = get(ox + x, y, oz + z);
                if (e == AIR || TreeBlueprint.isLeaf(e)) {
                    set(ox + x, y, oz + z, TreeBlueprint.LEAF);
                    n[0]++;
                    setPart(ox + x, y, oz + z, (byte) 0);
                }
            });
            if (inner((ox - MARGIN) >> 4, (oz - MARGIN) >> 4)) {
                for (int k = 1; k < grew.length; k++) {
                    stats.through[k] += grew[k] ? 1 : 0;
                }
            }
            return n[0];
        }

        /**
         * Pairs of canopy trees whose trunk feet touch (their footprints are next to each other,
         * diagonally included) and pairs whose stems touch six blocks up; each pair is counted once,
         * when one of its trees stands in the inner chunks.
         */
        private String touching(int innerChunks) {
            int feet = 0;
            int stems = 0;
            int apart = 0;
            int counted = 0;
            int touchingTrees = 0;
            boolean[] touches = new boolean[canopies.size()];
            for (int i = 0; i < canopies.size(); i++) {
                Canopy a = canopies.get(i);
                counted += a.counted ? 1 : 0;
                for (int j = i + 1; j < canopies.size(); j++) {
                    Canopy b = canopies.get(j);
                    if (!a.counted && !b.counted || Math.abs(a.ox - b.ox) > 16 || Math.abs(a.oz - b.oz) > 16) {
                        continue;
                    }
                    int d = Canopy.gap(a.foot, b.foot);
                    if (d <= 1) {
                        feet++;
                        touches[i] = true;
                        touches[j] = true;
                    } else if (d == 2) {
                        apart++;
                    }
                    if (Canopy.gap(a.stem, b.stem) <= 1) {
                        stems++;
                    }
                }
            }
            for (int i = 0; i < canopies.size(); i++) {
                touchingTrees += canopies.get(i).counted && touches[i] ? 1 : 0;
            }
            return String.format(Locale.ROOT,
                "  canopy trees touching: feet of %.1f%% touch another canopy tree's (pairs per chunk %.3f), stems touching six blocks up"
                    + " %.3f pairs per chunk, feet one block apart %.3f pairs per chunk",
                counted == 0 ? 0.0 : 100.0 * touchingTrees / counted, feet / (double) innerChunks, stems / (double) innerChunks,
                apart / (double) innerChunks);
        }

        /**
         * Vanilla-like sky light at the floor of the inner chunks: returns the mean, the percentages
         * at 7 or less and at 4 or less, and the number of open floor cells.
         */
        private double[] floorLight() {
            byte[] light = new byte[vox.length];
            // straight down from the sky: full light through air, one level less per block from the first leaf on
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    int level = 15;
                    for (int iy = HEIGHT - 1; iy >= 1; iy--) {
                        byte v = vox[idx(x, iy, z)];
                        if (TreeBlueprint.isWoody(v) || v == SOIL) {
                            break;
                        }
                        if (v != AIR || level < 15) {
                            level--;
                        }
                        if (level <= 0) {
                            break;
                        }
                        light[idx(x, iy, z)] = (byte) level;
                    }
                }
            }
            // then sideways and down, one level less per block (leaves too: their light dampening is one),
            // brightest first; only cells next to a darker open cell need to start spreading
            int[] steps = {size * size, -size * size, size, -size, 1, -1};
            IntList[] buckets = new IntList[16];
            for (int l = 0; l < 16; l++) {
                buckets[l] = new IntList();
            }
            for (int iy = 1; iy < HEIGHT; iy++) {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        int i = idx(x, iy, z);
                        int l = light[i];
                        if (l <= 1) {
                            continue;
                        }
                        boolean edge = false;
                        for (int k = 0; k < 6 && !edge; k++) {
                            int j = i + steps[k];
                            if (k < 2 ? iy + (k == 0 ? 1 : -1) >= 1 && iy + (k == 0 ? 1 : -1) < HEIGHT
                                : k < 4 ? x + (k == 2 ? 1 : -1) >= 0 && x + (k == 2 ? 1 : -1) < size
                                : z + (k == 4 ? 1 : -1) >= 0 && z + (k == 4 ? 1 : -1) < size) {
                                byte v = vox[j];
                                edge = !TreeBlueprint.isWoody(v) && v != SOIL && light[j] < l - 1;
                            }
                        }
                        if (edge) {
                            buckets[l].add(i);
                        }
                    }
                }
            }
            for (int l = 15; l >= 2; l--) {
                IntList b = buckets[l];
                for (int n = 0; n < b.size; n++) {
                    int i = b.data[n];
                    if (light[i] != l) {
                        continue;
                    }
                    int iy = i / (size * size);
                    int x = i / size % size;
                    int z = i % size;
                    for (int k = 0; k < 6; k++) {
                        int ny = iy + (k == 0 ? 1 : k == 1 ? -1 : 0);
                        int nx = x + (k == 2 ? 1 : k == 3 ? -1 : 0);
                        int nz = z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                        if (ny < 1 || ny >= HEIGHT || nx < 0 || nx >= size || nz < 0 || nz >= size) {
                            continue;
                        }
                        int j = i + steps[k];
                        byte v = vox[j];
                        if (TreeBlueprint.isWoody(v) || v == SOIL || light[j] >= l - 1) {
                            continue;
                        }
                        light[j] = (byte) (l - 1);
                        buckets[l - 1].add(j);
                    }
                }
            }
            this.floor = new byte[size * size];
            long sum = 0;
            int n = 0;
            int dark7 = 0;
            int dark4 = 0;
            int columns = 0;
            int blocked = 0;
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    byte v = vox[idx(x, 1, z)];
                    int l = v == AIR ? light[idx(x, 1, z)] : -1;
                    floor[x * size + z] = (byte) l;
                    int cx = (x - MARGIN) >> 4;
                    int cz = (z - MARGIN) >> 4;
                    if (x < MARGIN || z < MARGIN || !inner(cx, cz)) {
                        continue;
                    }
                    columns++;
                    if (v != AIR || vox[idx(x, 2, z)] != AIR) {
                        blocked++;
                    }
                    if (l < 0) {
                        continue;
                    }
                    n++;
                    sum += l;
                    dark7 += l <= 7 ? 1 : 0;
                    dark4 += l <= 4 ? 1 : 0;
                }
            }
            return new double[]{n == 0 ? 0 : (double) sum / n, n == 0 ? 0 : 100.0 * dark7 / n, n == 0 ? 0 : 100.0 * dark4 / n, n,
                columns == 0 ? 0 : 100.0 * blocked / columns};
        }

        private byte[] floor;

        /**
         * The stand as a walker sees it: eyes 1.6 blocks above the most open spot of the inner chunks,
         * looking east and a little up. Blocks are shaded by the face a ray enters through (light from
         * the upper left) and fade into haze with distance.
         */
        private BufferedImage view() {
            int w = 960;
            int h = 540;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            // the spot of the inner chunks furthest from any wood or leaves at the height of a walker
            // (up to six blocks), then with the longest free view east
            int cx = size / 2;
            int cz = size / 2;
            int best = -1;
            int lo = MARGIN + 2 * 16;
            int hi = MARGIN + (chunks - 2) * 16;
            for (int x = lo; x < hi; x++) {
                for (int z = lo; z < hi; z++) {
                    int clear = 0;
                    grow:
                    while (clear < 6) {
                        int r = clear + 1;
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                for (int y = 0; y < 3; y++) {
                                    if (dx * dx + dz * dz <= r * r && get(x + dx, y, z + dz) != AIR) {
                                        break grow;
                                    }
                                }
                            }
                        }
                        clear = r;
                    }
                    int run = 0;
                    while (run < 40 && get(x + run, 1, z) == AIR && get(x + run, 2, z) == AIR) {
                        run++;
                    }
                    int score = clear * 100 + run;
                    if (score > best) {
                        best = score;
                        cx = x;
                        cz = z;
                    }
                }
            }
            double ex = cx + 0.5;
            double ey = 2.62; // voxel layers: the soil is layer 0, the first tree layer 1
            double ez = cz + 0.5;
            double pitch = Math.toRadians(12.0);
            double fov = Math.tan(Math.toRadians(80.0) / 2.0);
            double[] sun = {-0.35, 0.8, -0.48};
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    double sx = (2.0 * (px + 0.5) / w - 1.0) * fov;
                    double sy = (1.0 - 2.0 * (py + 0.5) / h) * fov * h / w;
                    // forward is +x tilted up by the pitch; right is +z
                    double dx = Math.cos(pitch) - sy * Math.sin(pitch);
                    double dy = Math.sin(pitch) + sy * Math.cos(pitch);
                    double dz = sx;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    img.setRGB(px, py, trace(ex, ey, ez, dx / len, dy / len, dz / len, sun));
                }
            }
            return img;
        }

        /** Marches a ray through the voxels (one cell at a time) and returns the colour it sees. */
        private int trace(double ox, double oy, double oz, double dx, double dy, double dz, double[] sun) {
            int x = (int) Math.floor(ox);
            int y = (int) Math.floor(oy);
            int z = (int) Math.floor(oz);
            int stepX = dx > 0 ? 1 : -1;
            int stepY = dy > 0 ? 1 : -1;
            int stepZ = dz > 0 ? 1 : -1;
            double tdx = Math.abs(1.0 / dx);
            double tdy = Math.abs(1.0 / dy);
            double tdz = Math.abs(1.0 / dz);
            double tx = (dx > 0 ? x + 1 - ox : ox - x) * tdx;
            double ty = (dy > 0 ? y + 1 - oy : oy - y) * tdy;
            double tz = (dz > 0 ? z + 1 - oz : oz - z) * tdz;
            double t = 0.0;
            int face = 0;
            while (t < 220.0) {
                if (tx < ty && tx < tz) {
                    x += stepX;
                    t = tx;
                    tx += tdx;
                    face = 0;
                } else if (ty < tz) {
                    y += stepY;
                    t = ty;
                    ty += tdy;
                    face = 1;
                } else {
                    z += stepZ;
                    t = tz;
                    tz += tdz;
                    face = 2;
                }
                if (x < 0 || z < 0 || x >= size || z >= size || y >= HEIGHT) {
                    break;
                }
                if (y < 0) {
                    break;
                }
                byte v = vox[idx(x, y, z)];
                if (v == AIR) {
                    continue;
                }
                double[] n = {face == 0 ? -stepX : 0, face == 1 ? -stepY : 0, face == 2 ? -stepZ : 0};
                double lit = 0.45 + 0.55 * Math.max(0.0, (n[0] * sun[0] + n[1] * sun[1] + n[2] * sun[2]) / 1.05);
                // a little per-block variation, so leaf masses show their blocks
                double jitter = 0.9 + 0.2 * ((x * 73_856_093 ^ y * 19_349_663 ^ z * 83_492_791) & 255) / 255.0;
                double r;
                double g;
                double b;
                if (v == SOIL) {
                    r = 104;
                    g = 128;
                    b = 64;
                } else if (TreeBlueprint.isLeaf(v)) {
                    r = 52 * jitter;
                    g = 92 * jitter;
                    b = 58 * jitter;
                } else {
                    r = 86 * jitter;
                    g = 60 * jitter;
                    b = 38 * jitter;
                }
                double fog = Math.min(1.0, t / 220.0);
                fog = fog * fog;
                return rgb(r * lit * (1 - fog) + 178 * fog, g * lit * (1 - fog) + 202 * fog, b * lit * (1 - fog) + 228 * fog);
            }
            double up = Math.max(0.0, dy);
            return rgb(150 - 40 * up, 190 - 30 * up, 235);
        }

        private static int rgb(double r, double g, double b) {
            int ir = (int) Math.max(0, Math.min(255, r));
            int ig = (int) Math.max(0, Math.min(255, g));
            int ib = (int) Math.max(0, Math.min(255, b));
            return ir << 16 | ig << 8 | ib;
        }

        /** Plan view (left: canopy shaded by height, trunks brown; dots placed trees, crosses rejected ones) and floor light map (right). */
        private BufferedImage picture(String biome) {
            int px = 2;
            int w = chunks * 16;
            BufferedImage img = new BufferedImage(2 * w * px + 12, w * px + 20, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(40, 40, 40));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < w; z++) {
                    int wx = x + MARGIN;
                    int wz = z + MARGIN;
                    int top = 0;
                    byte kind = AIR;
                    for (int iy = HEIGHT - 1; iy >= 1; iy--) {
                        byte v = vox[idx(wx, iy, wz)];
                        if (v != AIR) {
                            top = iy;
                            kind = v;
                            break;
                        }
                    }
                    Color c;
                    if (kind == AIR) {
                        c = new Color(132, 160, 92);
                    } else if (TreeBlueprint.isWoody(kind)) {
                        c = new Color(108, 74, 44);
                    } else {
                        float b = Math.min(1f, 0.25f + top / 110f);
                        c = Color.getHSBColor(0.38f, 0.55f, b);
                    }
                    g.setColor(c);
                    g.fillRect(x * px, 20 + z * px, px, px);
                    int l = floor[wx * size + wz];
                    Color f;
                    if (l < 0) {
                        f = new Color(70, 50, 30);
                    } else if (l <= 7) {
                        int v = 30 + l * 14;
                        f = new Color(v + 40, v / 2, v / 2);
                    } else {
                        int v = 40 + l * 13;
                        f = new Color(v, v, v);
                    }
                    g.setColor(f);
                    g.fillRect(w * px + 12 + x * px, 20 + z * px, px, px);
                }
            }
            for (int[] m : marks) {
                int x = (m[0] - MARGIN) * px;
                int z = 20 + (m[1] - MARGIN) * px;
                if (m[2] == 1) {
                    g.setColor(Color.BLACK);
                    g.fillOval(x - 2, z - 2, 5, 5);
                } else {
                    g.setColor(Color.RED);
                    g.drawLine(x - 3, z - 3, x + 3, z + 3);
                    g.drawLine(x - 3, z + 3, x + 3, z - 3);
                }
            }
            g.setColor(new Color(255, 255, 255, 90));
            int lo = 2 * 16 * px;
            int hi = (chunks - 2) * 16 * px;
            g.drawRect(lo, 20 + lo, hi - lo, hi - lo);
            g.drawRect(w * px + 12 + lo, 20 + lo, hi - lo, hi - lo);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString(biome + (strict ? " (strict)" : "") + ": plan view", 4, 14);
            g.drawString("floor sky light (red: 7 or less)", w * px + 16, 14);
            g.dispose();
            return img;
        }
    }

    /**
     * Wood a conifer's stem may grow through, as {@code ProceduralTreeFeature} tells it in the world:
     * logs lying on their side and blocks with bark on all sides, when they lie above the root zone
     * and no upright log (which may be a trunk) stands beside them.
     */
    private static boolean limbWood(int code) {
        return code == TreeBlueprint.LOG_X || code == TreeBlueprint.LOG_Z || code == TreeBlueprint.WOOD;
    }

    /** A placed canopy tree: its trunk footprint, its stem six blocks up (world columns) and whether it is counted. */
    private static final class Canopy {
        final int ox;
        final int oz;
        final boolean counted;
        final List<int[]> foot = new ArrayList<>();
        final List<int[]> stem = new ArrayList<>();

        Canopy(TreeBlueprint bp, int ox, int oz, boolean counted) {
            this.ox = ox;
            this.oz = oz;
            this.counted = counted;
            bp.forEachTrunkBase((x, z, unused) -> {
                foot.add(new int[]{ox + x, oz + z});
                if (TreeBlueprint.isWoody(bp.get(x, 6, z))) {
                    stem.add(new int[]{ox + x, oz + z});
                }
            });
        }

        /** The smallest Chebyshev distance between two sets of columns. */
        static int gap(List<int[]> a, List<int[]> b) {
            int best = Integer.MAX_VALUE;
            for (int[] p : a) {
                for (int[] q : b) {
                    best = Math.min(best, Math.max(Math.abs(p[0] - q[0]), Math.abs(p[1] - q[1])));
                }
            }
            return best;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reading the data pack
    // ------------------------------------------------------------------------------------------

    /** The tree placements the data pack adds to a biome, in file order. */
    @SuppressWarnings("unchecked")
    private static List<Placement> placements(Path data, String biome) throws IOException {
        List<Placement> list = new ArrayList<>();
        Path modifiers = data.resolve("neoforge/biome_modifier/trees");
        List<Path> files;
        try (var s = Files.list(modifiers)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path f : files) {
            Map<String, Object> m = (Map<String, Object>) Json.parse(Files.readString(f));
            if (!"neoforge:add_features".equals(m.get("type")) || !(m.get("biomes") instanceof List<?> biomes)
                || !biomes.contains("minecraft:" + biome)) {
                continue;
            }
            Object features = m.get("features");
            for (Object id : features instanceof List<?> l ? l : List.of(features)) {
                Placement p = placement(data, (String) id);
                if (p != null) {
                    list.add(p);
                }
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("no tree placements for biome " + biome);
        }
        return list;
    }

    /** Fallen logs per chunk the data pack's {@code details/} biome modifiers give a biome. */
    @SuppressWarnings("unchecked")
    private static double fallenLogs(Path data, String biome) throws IOException {
        Path modifiers = data.resolve("neoforge/biome_modifier/details");
        if (!Files.isDirectory(modifiers)) {
            return 0.0;
        }
        List<Path> files;
        try (var s = Files.list(modifiers)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        double total = 0.0;
        for (Path f : files) {
            Map<String, Object> m = (Map<String, Object>) Json.parse(Files.readString(f));
            if (!"neoforge:add_features".equals(m.get("type")) || !(m.get("biomes") instanceof List<?> biomes)
                || !biomes.contains("minecraft:" + biome)) {
                continue;
            }
            Object features = m.get("features");
            for (Object id : features instanceof List<?> l ? l : List.of(features)) {
                String path = ((String) id).substring(((String) id).indexOf(':') + 1);
                Map<String, Object> placed = (Map<String, Object>) Json.parse(Files.readString(data.resolve("worldgen/placed_feature/" + path + ".json")));
                if (!"terrahaul:fallen_log".equals(configured(data, (String) placed.get("feature")).get("type"))) {
                    continue;
                }
                for (Object o : (List<Object>) placed.get("placement")) {
                    Map<String, Object> mod = (Map<String, Object>) o;
                    if ("terrahaul:density_count".equals(mod.get("type")) && mod.get("count") instanceof Double c) {
                        total += c;
                    }
                }
            }
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static Placement placement(Path data, String id) throws IOException {
        String path = id.substring(id.indexOf(':') + 1);
        Map<String, Object> placed = (Map<String, Object>) Json.parse(Files.readString(data.resolve("worldgen/placed_feature/" + path + ".json")));
        double count = 1.0;
        for (Object o : (List<Object>) placed.get("placement")) {
            Map<String, Object> mod = (Map<String, Object>) o;
            if ("terrahaul:density_count".equals(mod.get("type")) && mod.get("count") instanceof Double c) {
                count = c;
            }
        }
        String feature = (String) placed.get("feature");
        Map<String, Object> configured = configured(data, feature);
        List<Double> chances = new ArrayList<>();
        List<TreeConfig> features = new ArrayList<>();
        TreeConfig fallback;
        if ("minecraft:random_selector".equals(configured.get("type"))) {
            Map<String, Object> config = (Map<String, Object>) configured.get("config");
            for (Object o : (List<Object>) config.get("features")) {
                Map<String, Object> entry = (Map<String, Object>) o;
                chances.add((Double) entry.get("chance"));
                features.add(tree(data, (String) ((Map<String, Object>) entry.get("feature")).get("feature")));
            }
            fallback = tree(data, (String) ((Map<String, Object>) config.get("default")).get("feature"));
        } else {
            fallback = tree(configured);
        }
        if (fallback == null && features.stream().allMatch(t -> t == null)) {
            return null;
        }
        return new Placement(path.substring(path.lastIndexOf('/') + 1), count, chances, features, fallback);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> configured(Path data, String id) throws IOException {
        String path = id.substring(id.indexOf(':') + 1);
        return (Map<String, Object>) Json.parse(Files.readString(data.resolve("worldgen/configured_feature/" + path + ".json")));
    }

    private static TreeConfig tree(Path data, String id) throws IOException {
        return id.startsWith("terrahaul:") ? tree(configured(data, id)) : null;
    }

    /** A procedural tree configuration, or null for any other feature (huge mushrooms, ...). */
    @SuppressWarnings("unchecked")
    private static TreeConfig tree(Map<String, Object> configured) {
        if (!"terrahaul:procedural_tree".equals(configured.get("type"))) {
            return null;
        }
        Map<String, Object> config = (Map<String, Object>) configured.get("config");
        Map<TreeSize, Double> sizes = new LinkedHashMap<>();
        for (Object o : (List<Object>) config.get("sizes")) {
            Map<String, Object> e = (Map<String, Object>) o;
            sizes.put(TreeSize.byName((String) e.get("data")), (Double) e.get("weight"));
        }
        return new TreeConfig(TreeSpecies.byName((String) config.get("species")), sizes);
    }

    /** A growable list of ints (the light queue holds millions of cells). */
    private static final class IntList {
        int[] data = new int[1024];
        int size;

        void add(int v) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, size * 2);
            }
            data[size++] = v;
        }
    }

    /** A small JSON reader for the data pack: objects become maps, arrays lists and numbers doubles. */
    private static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String s) {
            return new Json(s).value();
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object value() {
            skip();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                skip();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    skip();
                    String k = string();
                    skip();
                    i++; // ':'
                    m.put(k, value());
                    skip();
                    if (s.charAt(i++) == '}') {
                        return m;
                    }
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                skip();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    skip();
                    if (s.charAt(i++) == ']') {
                        return l;
                    }
                }
            }
            if (c == '"') {
                return string();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char e = s.charAt(i++);
                    sb.append(switch (e) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'u' -> {
                            char u = (char) Integer.parseInt(s.substring(i, i + 4), 16);
                            i += 4;
                            yield u;
                        }
                        default -> e;
                    });
                } else {
                    sb.append(c);
                }
            }
            i++;
            return sb.toString();
        }
    }
}
