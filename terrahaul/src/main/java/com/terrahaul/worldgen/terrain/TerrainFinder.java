package com.terrahaul.worldgen.terrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Locates landforms by querying the terrain model directly, without generating any chunks. Used by
 * {@code /terrahaul find}. Searches walk outward in square rings so the first match found is (close
 * to) the nearest one, then refine the hit on a finer grid to land on the feature itself.
 * <p>
 * A column predicate alone cannot tell whether a landform is really there to see: a river
 * column may be dry, a "wetland" frozen, a "great river" narrower than its nominal width. Grid
 * targets can therefore add a site check that looks at the neighbourhood (water width, pools
 * around, the drop to the sea, walls of a fjord, ...); the refinement returns the nearest
 * candidate that passes it. Summit targets (mountains, peaks) climb from the match to the
 * summit, so the player arrives on top of the peak rather than somewhere on its flank.
 * {@code FinderAudit} (devtools) checks every target this way from several origins and seeds.
 */
public final class TerrainFinder {
    /**
     * A search target and how to look for it. For {@link Kind#GRID} and {@link Kind#HIGHEST} the
     * step is the sampling distance in blocks; for {@link Kind#LAKE_SITE} it holds the
     * {@link LakeType} to look for (0 = any lake) and for {@link Kind#FORMATION} the formation type.
     */
    public record Target(String name, String description, int step, Predicate<ColumnSample> coarse, Predicate<ColumnSample> exact, Kind kind) {
    }

    public enum Kind {
        GRID,
        HIGHEST,
        VOLCANO,
        WATERFALL,
        LAKE_SITE,
        FORMATION,
        OVERHANG
    }

    /** Result of a search: block position of the match and a short label. */
    public record Result(int x, int y, int z, String label) {
    }

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();
    /** Neighbourhood checks of grid targets (see {@link SiteCheck}). */
    private static final Map<String, SiteCheck> SITES = new HashMap<>();
    /** Targets that climb from the match to the summit of the peak. */
    private static final Set<String> SUMMITS = Set.of("mountain", "alpine_peaks", "ancient_mountains", "mountain_wall", "colossal_peak");

    static {
        add("river", "a river", 24, s -> s.riverValley > 0.4 && s.riverWidth >= 2.5,
            s -> s.river > 0.2 && s.riverWidth >= 2.5 && !s.dryRiver && wet(s), Kind.GRID);
        // near the centre line, where the water is as wide as the river
        add("great_river", "a great river (100-200 blocks wide)", 64, s -> s.riverValley > 0.3 && s.riverWidth >= 50,
            s -> s.river > 0.6 && s.riverWidth >= 50 && wet(s), Kind.GRID);
        add("dry_river", "a dry riverbed", 24, s -> s.riverValley > 0.4 && s.dryRiver,
            s -> s.river > 0.2 && s.dryRiver && !wet(s) && s.height > 64.0, Kind.GRID);
        add("stream", "a mountain stream or forest creek", 12, s -> s.stream > 0.3, s -> s.stream > 0.5 && !wet(s), Kind.GRID);
        add("lake", "a lake", 0, null, null, Kind.LAKE_SITE);
        for (int type = LakeType.FOREST; type <= LakeType.GLACIAL; type++) {
            final int t = type;
            String name = LakeType.name(t);
            if (t == LakeType.CRATER) {
                add(name, "a crater lake", 32, s -> s.lakeType == LakeType.CRATER && s.lake > 0.0,
                    s -> s.lakeType == LakeType.CRATER && s.lake > 0.2 && s.waterLevel != ColumnSample.NO_WATER, Kind.GRID);
            } else {
                add(name, "a " + name.replace('_', ' '), t, null, null, Kind.LAKE_SITE);
            }
        }
        add("ocean", "the ocean", 64, s -> s.isSea(63) && s.continentalness < -0.19, s -> s.isSea(63) && s.height < 50, Kind.GRID);
        add("deep_ocean", "a deep ocean basin", 96, s -> s.continentalness < -0.47 && s.height < -30, s -> s.height < -60, Kind.GRID);
        add("trench", "a deep-sea trench with magma vents", 64, s -> s.trench > 0.2,
            s -> s.trench > 0.9 && s.height < s.trenchFloor + 20.0 && s.height < -150.0, Kind.GRID);
        add("trail", "a dirt trail", 16, s -> s.trailDistance < 20.0 && TerrainModel.trailGround(s, 63),
            s -> s.trailDistance < 1.2 && TerrainModel.trailGround(s, 63), Kind.GRID);
        add("beach", "a sandy beach", 32, s -> s.beach > 0.4, s -> s.beach > 0.6 && Math.abs(s.height - 64.0) < 3.0, Kind.GRID);
        add("sea_cliff", "rocky sea cliffs", 32, s -> s.coastCliff > 0.3 && s.height > 70, s -> s.coastCliff > 0.3 && s.height > 76, Kind.GRID);
        // archipelago islands fade out towards the continental shore, where the last of them join
        // the mainland
        add("island", "an island", 64, s -> s.island > 0.3 && s.height > 64,
            s -> s.island > 0.4 && s.height > 66 && s.rawContinentalness < -0.22, Kind.GRID);
        add("mushroom_island", "a mushroom island", 96, s -> s.continentalness < -1.05 && s.height > 63, s -> s.continentalness < -1.05 && s.height > 66, Kind.GRID);
        add("fjord", "a fjord", 48, s -> s.fjord > 0.4, s -> s.fjord > 0.6 && s.height < 57.0, Kind.GRID);
        add("mountain", "a mountain range", 64, s -> s.mountain > 0.6 && s.height > 250, s -> s.mountain > 0.6 && s.peakness > 0.6, Kind.GRID);
        add("alpine_peaks", "jagged alpine peaks", 64, s -> s.alpine > 0.5 && s.height > 350, s -> s.alpine > 0.5 && s.peakness > 0.7, Kind.GRID);
        add("ancient_mountains", "rounded ancient mountains", 64, s -> s.ancient > 0.5 && s.height > 200, s -> s.ancient > 0.5 && s.peakness > 0.6, Kind.GRID);
        add("mountain_wall", "a massive mountain wall", 64, s -> s.wall > 0.4 && s.height > 300, s -> s.wall > 0.5 && s.peakness > 0.6, Kind.GRID);
        add("colossal_peak", "a colossal peak (over 1200 blocks high)", 96, s -> s.height > 1000, s -> s.height > 1200, Kind.GRID);
        add("highest_peak", "the highest point in range", 48, s -> s.height > 150, s -> true, Kind.HIGHEST);
        add("meadow", "a high mountain meadow", 48, s -> s.meadow > 0.5 && s.height > 150, s -> s.meadow > 0.6 && s.height > 150 && !wet(s), Kind.GRID);
        add("plateau", "a plateau", 48, s -> s.plateau > 0.6, s -> s.plateau > 0.85 && !wet(s), Kind.GRID);
        add("canyon", "a canyon", 32, s -> s.canyon > 0.3, s -> s.canyon > 0.7, Kind.GRID);
        add("valley", "a mountain valley", 48, s -> s.mountain > 0.4 && s.peakness < 0.25 && s.height > 120,
            s -> s.mountain > 0.5 && s.peakness < 0.2 && !wet(s), Kind.GRID);
        add("hills", "rolling hills", 48, s -> s.erosion > -0.4 && s.erosion < 0.0 && s.mountain < 0.1 && s.height > 90, s -> s.peakness > 0.35, Kind.GRID);
        add("plains", "wide flat plains", 64, s -> s.erosion > 0.2 && s.erosion < 0.55 && s.mountain < 0.05 && s.height > 64 && s.height < 110,
            s -> s.river <= 0 && s.lake <= 0 && s.riverValley < 0.2 && s.swamp < 0.2 && s.windswept < 0.2 && !wet(s), Kind.GRID);
        add("windswept_hills", "windswept uplands", 48, s -> s.windswept > 0.6, s -> s.windswept > 0.8, Kind.GRID);
        // a hummock or mud flat at the edge of the pools of a marsh that is not frozen
        add("wetland", "a wetland or swamp", 48, s -> s.swamp > 0.6 && s.temperature > -0.4,
            s -> s.swamp > 0.85 && s.temperature > -0.4 && s.height >= 62.5 && s.height < 65.0, Kind.GRID);
        add("volcano", "a volcano", 0, null, null, Kind.VOLCANO);
        add("waterfall", "a waterfall", 0, null, null, Kind.WATERFALL);
        add("arch", "a natural stone arch", TerrainModel.ARCH, null, null, Kind.FORMATION);
        add("spire", "a rock spire", TerrainModel.SPIRE, null, null, Kind.FORMATION);
        add("hoodoo", "a hoodoo cluster", TerrainModel.HOODOO, null, null, Kind.FORMATION);
        add("sea_stack", "a sea stack", TerrainModel.SEA_STACK, null, null, Kind.FORMATION);
        add("overhang", "a big curved cliff overhang", 16, s -> s.cliff > 0.1 || s.canyon > 0.2 || s.coastCliff > 0.3 || s.plateau > 0.1,
            null, Kind.OVERHANG);

        // what must be visible around a matching column
        site("great_river", (m, s, x, z) -> {
            int w = waterWidth(m, x, z);
            return w >= 96 && w <= 230;
        });
        site("dry_river", (m, s, x, z) -> waterWithin(m, x, z, 16) == 0);
        site("stream", (m, s, x, z) -> {
            ColumnSample c = m.sample((x + 2) & ~3, (z + 2) & ~3);
            return c.stream > 0.0 && c.waterLevel == ColumnSample.NO_WATER && m.interpolatedHeight(x, z) > m.seaLevel() + 1.0
                && m.streamDistance(x, z) < 0.6 + 1.6 * c.stream;
        });
        site("beach", (m, s, x, z) -> seaWithin(m, x, z, 24));
        site("sea_cliff", (m, s, x, z) -> seaWithin(m, x, z, 14));
        site("fjord", (m, s, x, z) -> maxWithin(m, x, z, 160) > m.seaLevel() + 70.0);
        site("meadow", (m, s, x, z) -> slope(m, x, z, 8) < 0.8);
        site("plateau", (m, s, x, z) -> slope(m, x, z, 12) < 1.0 && s.height - minWithin(m, x, z, 320) > 35.0);
        site("valley", (m, s, x, z) -> maxWithin(m, x, z, 240) > s.height + 80.0);
        site("plains", (m, s, x, z) -> maxWithin(m, x, z, 48) - minWithin(m, x, z, 48) < 8.0);
        site("wetland", (m, s, x, z) -> waterWithin(m, x, z, 20) >= 20);
        // walls on both sides: the canyon flag alone was also set on shallow ends of canyons
        // whose floor rises to the land around
        site("canyon", (m, s, x, z) -> wallSides(m, x, z, s.height, 112, 28.0) >= 4);
    }

    /** A check of a candidate's neighbourhood. */
    @FunctionalInterface
    private interface SiteCheck {
        boolean test(TerrainModel model, ColumnSample s, int x, int z);
    }

    private static void site(String name, SiteCheck check) {
        SITES.put(name, check);
    }

    /** Water stands over the column (sea level water, an elevated river or a lake). */
    private static boolean wet(ColumnSample s) {
        return s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > s.height || s.height < 62.5;
    }

    private TerrainFinder() {
    }

    private static void add(String name, String description, int step, Predicate<ColumnSample> coarse, Predicate<ColumnSample> exact, Kind kind) {
        TARGETS.put(name, new Target(name, description, step, coarse, exact, kind));
    }

    public static Map<String, Target> targets() {
        return Collections.unmodifiableMap(TARGETS);
    }

    /**
     * Finds the nearest occurrence of {@code target} within {@code radius} blocks of {@code (x, z)}.
     *
     * @param cancelled polled between rings so long searches can be abandoned
     * @return the match, or {@code null} if none was found in range
     */
    public static Result find(TerrainModel model, Target target, int x, int z, int radius, BooleanSupplier cancelled) {
        return switch (target.kind()) {
            case VOLCANO -> findVolcano(model, x, z, radius);
            case WATERFALL -> findWaterfall(model, x, z, radius, cancelled);
            case LAKE_SITE -> findLake(model, target.step(), x, z, radius);
            case HIGHEST -> findHighest(model, target, x, z, radius, cancelled);
            case GRID -> findGrid(model, target, x, z, radius, cancelled);
            case FORMATION -> findFormation(model, target.step(), x, z, radius, cancelled);
            case OVERHANG -> findOverhang(model, target, x, z, radius, cancelled);
        };
    }

    private static Result findGrid(TerrainModel model, Target target, int x, int z, int radius, BooleanSupplier cancelled) {
        ColumnSample s = new ColumnSample();
        int step = target.step();
        int rings = radius / step;
        for (int r = 0; r <= rings; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            Result best = null;
            double bestD = Double.MAX_VALUE;
            for (int[] p : ring(r)) {
                int px = x + p[0] * step;
                int pz = z + p[1] * step;
                model.sampleInto(px, pz, s);
                if (target.coarse().test(s)) {
                    Result refined = refine(model, target, px, pz, step, s);
                    if (refined != null) {
                        double dd = sq(refined.x - x) + sq(refined.z - z);
                        if (dd < bestD) {
                            bestD = dd;
                            best = refined;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * Nearest column under a big curved overhang with a radius of at least 9 blocks. Reports the
     * point halfway up the overhang's underside, at the column 4 blocks out from the cliff face.
     */
    private static Result findOverhang(TerrainModel model, Target target, int x, int z, int radius, BooleanSupplier cancelled) {
        ColumnSample s = new ColumnSample();
        int step = target.step();
        int rings = radius / step;
        for (int r = 0; r <= rings; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            for (int[] p : ring(r)) {
                int px = (x + p[0] * step) & ~3;
                int pz = (z + p[1] * step) & ~3;
                model.sampleInto(px, pz, s);
                if (!target.coarse().test(s)) {
                    continue;
                }
                for (int dx = -12; dx <= 12; dx += 4) {
                    for (int dz = -12; dz <= 12; dz += 4) {
                        double[] o = com.terrahaul.worldgen.density.TerrainDetail.curvedOverhang(model, px + dx, pz + dz);
                        if (o != null && o[1] >= 9.0 && o[2] >= 2.0 && o[2] <= 6.0) {
                            return new Result(px + dx, (int) Math.round(o[0] - o[1] * 0.5), pz + dz, target.name());
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Result refine(TerrainModel model, Target target, int cx, int cz, int step, ColumnSample s) {
        int fine = Math.max(2, step / 8);
        int reach = step + step / 2;
        List<long[]> candidates = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx += fine) {
            for (int dz = -reach; dz <= reach; dz += fine) {
                model.sampleInto(cx + dx, cz + dz, s);
                if (target.exact().test(s)) {
                    candidates.add(new long[]{(long) dx * dx + (long) dz * dz, dx, dz});
                }
            }
        }
        candidates.sort((a, b) -> Long.compare(a[0], b[0]));
        SiteCheck site = SITES.get(target.name());
        int checks = 0;
        int climbs = 0;
        for (long[] c : candidates) {
            int x = cx + (int) c[1];
            int z = cz + (int) c[2];
            model.sampleInto(x, z, s);
            if (site != null) {
                // neighbourhood checks are costly: test the nearest candidates only
                if (++checks > 12) {
                    break;
                }
                if (!site.test(model, s, x, z)) {
                    continue;
                }
            }
            if (SUMMITS.contains(target.name())) {
                // the summit; if the climb left the landform (a higher peak of another style) this
                // candidate has no summit of its own, and its flank is no answer
                int[] top = climb(model, x, z);
                ColumnSample peak = new ColumnSample();
                model.sampleInto(top[0], top[1], peak);
                if (target.coarse().test(peak)) {
                    return new Result(top[0], surfaceY(peak), top[1], target.name());
                }
                if (++climbs > 6) {
                    break;
                }
                continue;
            }
            return new Result(x, surfaceY(s), z, target.name());
        }
        return null;
    }

    /** Radius of the neighbourhood searched for a higher top after each ascent, in blocks. */
    private static final int SUMMIT_REACH = 48;

    /**
     * Climbs from (x, z) to the summit on the 4-block grid the generated terrain is interpolated
     * from (between grid columns the ground never rises above them): steepest ascent with steps
     * halving from 32 to 4 blocks, then a look around for anything higher within
     * {@value #SUMMIT_REACH} blocks and a new ascent from there. Ridged peaks have shoulders and
     * lesser tops a few dozen blocks from the summit, on which a plain ascent stopped up to 100
     * blocks below it.
     */
    private static int[] climb(TerrainModel model, int x, int z) {
        ColumnSample s = new ColumnSample();
        x &= ~3;
        z &= ~3;
        model.sampleInto(x, z, s);
        double best = s.height;
        for (int round = 0; round < 16; round++) {
            for (int step = 32; step >= 4; step /= 2) {
                boolean moved = true;
                while (moved) {
                    moved = false;
                    int bx = x;
                    int bz = z;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                        model.sampleInto(x + d[0] * step, z + d[1] * step, s);
                        if (s.height > best) {
                            best = s.height;
                            bx = x + d[0] * step;
                            bz = z + d[1] * step;
                        }
                    }
                    if (bx != x || bz != z) {
                        x = bx;
                        z = bz;
                        moved = true;
                    }
                }
            }
            int bx = x;
            int bz = z;
            for (int dx = -SUMMIT_REACH; dx <= SUMMIT_REACH; dx += 4) {
                for (int dz = -SUMMIT_REACH; dz <= SUMMIT_REACH; dz += 4) {
                    if (dx * dx + dz * dz <= SUMMIT_REACH * SUMMIT_REACH) {
                        model.sampleInto(x + dx, z + dz, s);
                        if (s.height > best) {
                            best = s.height;
                            bx = x + dx;
                            bz = z + dz;
                        }
                    }
                }
            }
            if (bx == x && bz == z) {
                break;
            }
            x = bx;
            z = bz;
        }
        return new int[]{x, z};
    }

    /** Narrowest run of water through (x, z) over four line directions, in blocks. */
    private static int waterWidth(TerrainModel model, int x, int z) {
        int best = Integer.MAX_VALUE;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        ColumnSample s = new ColumnSample();
        for (int[] d : dirs) {
            double unit = d[0] != 0 && d[1] != 0 ? Math.sqrt(2.0) : 1.0;
            int run = 1;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int k = 1; k < 160; k++) {
                    model.sampleInto(x + sign * d[0] * k * 2, z + sign * d[1] * k * 2, s);
                    if (!wet(s)) {
                        break;
                    }
                    run++;
                }
            }
            best = Math.min(best, (int) Math.round(run * 2 * unit));
        }
        return best;
    }

    /**
     * Number of the eight compass directions in which the ground rises at least {@code rise}
     * blocks above {@code h} within {@code r} blocks of (x, z).
     */
    private static int wallSides(TerrainModel model, int x, int z, double h, int r, double rise) {
        int sides = 0;
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4.0;
            for (int d = 8; d <= r; d += 8) {
                if (model.sample((int) Math.round(x + d * Math.cos(ang)) & ~3, (int) Math.round(z + d * Math.sin(ang)) & ~3).height >= h + rise) {
                    sides++;
                    break;
                }
            }
        }
        return sides;
    }

    /** Number of 4-block grid columns within r blocks that are under water. */
    private static int waterWithin(TerrainModel model, int x, int z, int r) {
        int n = 0;
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                if (wet(model.sample((x + dx) & ~3, (z + dz) & ~3))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean seaWithin(TerrainModel model, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                if (dx * dx + dz * dz <= r * r && model.sample((x + dx) & ~3, (z + dz) & ~3).isSea(model.seaLevel())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double maxWithin(TerrainModel model, int x, int z, int r) {
        double max = -Double.MAX_VALUE;
        int step = Math.max(4, (r / 12) & ~3);
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                max = Math.max(max, model.sample((x + dx) & ~3, (z + dz) & ~3).height);
            }
        }
        return max;
    }

    private static double minWithin(TerrainModel model, int x, int z, int r) {
        double min = Double.MAX_VALUE;
        int step = Math.max(4, (r / 12) & ~3);
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                min = Math.min(min, model.sample((x + dx) & ~3, (z + dz) & ~3).height);
            }
        }
        return min;
    }

    /** Steepest slope (blocks per block) from (x, z) to the points r blocks away in eight directions. */
    private static double slope(TerrainModel model, int x, int z, int r) {
        double h = model.interpolatedHeight(x, z);
        double best = 0.0;
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4.0;
            int px = (int) Math.round(x + r * Math.cos(ang));
            int pz = (int) Math.round(z + r * Math.sin(ang));
            best = Math.max(best, Math.abs(model.interpolatedHeight(px, pz) - h) / r);
        }
        return best;
    }

    private static Result findHighest(TerrainModel model, Target target, int x, int z, int radius, BooleanSupplier cancelled) {
        ColumnSample s = new ColumnSample();
        int step = target.step();
        double bestH = -Double.MAX_VALUE;
        int bx = x;
        int bz = z;
        for (int dx = -radius; dx <= radius; dx += step) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            for (int dz = -radius; dz <= radius; dz += step) {
                model.sampleInto(x + dx, z + dz, s);
                if (s.height > bestH) {
                    bestH = s.height;
                    bx = x + dx;
                    bz = z + dz;
                }
            }
        }
        // hill-climb to the actual summit
        int stepSize = step / 2;
        while (stepSize >= 1) {
            boolean moved = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                model.sampleInto(bx + d[0] * stepSize, bz + d[1] * stepSize, s);
                if (s.height > bestH) {
                    bestH = s.height;
                    bx += d[0] * stepSize;
                    bz += d[1] * stepSize;
                    moved = true;
                }
            }
            if (!moved) {
                stepSize /= 2;
            }
        }
        return new Result(bx, (int) Math.ceil(bestH) + 1, bz, "highest_peak");
    }

    private static Result findVolcano(TerrainModel model, int x, int z, int radius) {
        TerrainModel.VolcanoSite v = model.nearestVolcano(x, z, radius);
        if (v == null) {
            return null;
        }
        ColumnSample s = new ColumnSample();
        model.sampleInto((int) v.x, (int) v.z, s);
        return new Result((int) v.x, surfaceY(s), (int) v.z, "volcano");
    }

    private static Result findLake(TerrainModel model, int type, int x, int z, int radius) {
        Result best = null;
        double bestD = Double.MAX_VALUE;
        for (boolean great : new boolean[]{false, true}) {
            if (type != 0 && (type == LakeType.GREAT) != great) {
                continue;
            }
            TerrainModel.LakeSite lake = nearestLakeOfType(model, x, z, radius, great, type);
            if (lake != null) {
                double dd = sq(lake.x - x) + sq(lake.z - z);
                if (dd < bestD) {
                    bestD = dd;
                    best = new Result((int) lake.x, (int) lake.level + 1, (int) lake.z, LakeType.name(lake.type));
                }
            }
        }
        return best;
    }

    private static TerrainModel.LakeSite nearestLakeOfType(TerrainModel model, int x, int z, int radius, boolean great, int type) {
        // walk outward; nearestLake returns the nearest active lake around a point, so probe a ring of
        // points when a type filter is used
        TerrainModel.LakeSite direct = model.nearestLake(x, z, radius, great);
        if (direct == null || type == 0 || direct.type == type) {
            return direct;
        }
        int probe = great ? 6000 : 700;
        TerrainModel.LakeSite best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 1; r * probe <= radius + probe; r++) {
            for (int[] p : ring(r)) {
                TerrainModel.LakeSite lake = model.nearestLake(x + p[0] * probe, z + p[1] * probe, probe, great);
                if (lake != null && lake.type == type) {
                    double dd = sq(lake.x - x) + sq(lake.z - z);
                    if (dd < bestD && dd <= sq(radius)) {
                        bestD = dd;
                        best = lake;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static Result findWaterfall(TerrainModel model, int x, int z, int radius, BooleanSupplier cancelled) {
        int cell = TerrainModel.WATERFALL_CELL;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        int rings = radius / cell + 1;
        for (int r = 0; r <= rings; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            Result best = null;
            double bestD = Double.MAX_VALUE;
            for (int[] p : ring(r)) {
                TerrainModel.WaterfallSite w = model.waterfallSite(cx + p[0], cz + p[1]);
                if (w != null) {
                    double dd = sq(w.x - x) + sq(w.z - z);
                    if (dd < bestD) {
                        bestD = dd;
                        best = new Result(w.x, (int) w.top + 1, w.z, "waterfall");
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static Result findFormation(TerrainModel model, int type, int x, int z, int radius, BooleanSupplier cancelled) {
        int cell = TerrainModel.FORMATION_CELL;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        int rings = radius / cell + 1;
        for (int r = 0; r <= rings; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            Result best = null;
            double bestD = Double.MAX_VALUE;
            for (int[] p : ring(r)) {
                TerrainModel.FormationSite f = model.formationSite(cx + p[0], cz + p[1]);
                if (f != null && f.type == type) {
                    double dd = sq(f.x - x) + sq(f.z - z);
                    if (dd < bestD) {
                        bestD = dd;
                        best = new Result(f.x, (int) Math.ceil(Math.max(f.ground, 63)) + 2, f.z, TerrainModel.formationName(type));
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static int surfaceY(ColumnSample s) {
        double top = Math.max(s.height, s.waterLevel == ColumnSample.NO_WATER ? s.height : s.waterLevel);
        if (s.height < 63) {
            top = 63;
        }
        return (int) Math.ceil(top) + 1;
    }

    private static List<int[]> ring(int r) {
        List<int[]> points = new ArrayList<>();
        if (r == 0) {
            points.add(new int[]{0, 0});
            return points;
        }
        for (int i = -r; i <= r; i++) {
            points.add(new int[]{i, -r});
            points.add(new int[]{i, r});
        }
        for (int i = -r + 1; i <= r - 1; i++) {
            points.add(new int[]{-r, i});
            points.add(new int[]{r, i});
        }
        return points;
    }

    private static double sq(double v) {
        return v * v;
    }
}
