package com.terrahaul.worldgen.terrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Locates landforms by querying the terrain model directly, without generating any chunks. Used by
 * {@code /terrahaul find}. Searches walk outward in square rings so the first match found is (close
 * to) the nearest one, then refine the hit on a finer grid to land on the feature itself.
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
        FORMATION
    }

    /** Result of a search: block position of the match and a short label. */
    public record Result(int x, int y, int z, String label) {
    }

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    static {
        add("river", "a river", 24, s -> s.riverValley > 0.4 && s.riverWidth >= 2.5, s -> s.river > 0.2 && s.riverWidth >= 2.5, Kind.GRID);
        add("great_river", "a great river (over 60 blocks wide)", 48, s -> s.riverValley > 0.3 && s.riverWidth >= 30, s -> s.river > 0.3 && s.riverWidth >= 30, Kind.GRID);
        add("dry_river", "a dry riverbed", 24, s -> s.riverValley > 0.4 && s.dryRiver, s -> s.river > 0.2 && s.dryRiver, Kind.GRID);
        add("stream", "a mountain stream or forest creek", 12, s -> s.stream > 0.3, s -> s.stream > 0.5, Kind.GRID);
        add("lake", "a lake", 0, null, null, Kind.LAKE_SITE);
        for (int type = LakeType.FOREST; type <= LakeType.GLACIAL; type++) {
            final int t = type;
            String name = LakeType.name(t);
            if (t == LakeType.CRATER) {
                add(name, "a crater lake", 32, s -> s.lakeType == LakeType.CRATER && s.lake > 0.0, s -> s.lakeType == LakeType.CRATER && s.lake > 0.2, Kind.GRID);
            } else {
                add(name, "a " + name.replace('_', ' '), t, null, null, Kind.LAKE_SITE);
            }
        }
        add("ocean", "the ocean", 64, s -> s.isSea(63) && s.continentalness < -0.19, s -> s.isSea(63) && s.height < 50, Kind.GRID);
        add("deep_ocean", "a deep ocean basin", 96, s -> s.continentalness < -0.47 && s.height < -30, s -> s.height < -60, Kind.GRID);
        add("trench", "a deep-sea trench with magma vents", 64, s -> s.trench > 0.2, s -> s.trench > 0.9, Kind.GRID);
        add("trail", "a dirt trail", 16, s -> s.trailDistance < 20.0 && TerrainModel.trailGround(s, 63),
            s -> s.trailDistance < 1.2 && TerrainModel.trailGround(s, 63), Kind.GRID);
        add("beach", "a sandy beach", 32, s -> s.beach > 0.4, s -> s.beach > 0.6, Kind.GRID);
        add("sea_cliff", "rocky sea cliffs", 32, s -> s.coastCliff > 0.4 && s.height > 70, s -> s.coastCliff > 0.5 && s.height > 74, Kind.GRID);
        add("island", "an island", 64, s -> s.island > 0.3 && s.height > 64, s -> s.island > 0.4 && s.height > 66, Kind.GRID);
        add("mushroom_island", "a mushroom island", 96, s -> s.continentalness < -1.05 && s.height > 63, s -> s.continentalness < -1.05 && s.height > 66, Kind.GRID);
        add("fjord", "a fjord", 48, s -> s.fjord > 0.4, s -> s.fjord > 0.7, Kind.GRID);
        add("mountain", "a mountain range", 64, s -> s.mountain > 0.6 && s.height > 250, s -> s.mountain > 0.6 && s.peakness > 0.6, Kind.GRID);
        add("alpine_peaks", "jagged alpine peaks", 64, s -> s.alpine > 0.5 && s.height > 350, s -> s.alpine > 0.5 && s.peakness > 0.7, Kind.GRID);
        add("ancient_mountains", "rounded ancient mountains", 64, s -> s.ancient > 0.5 && s.height > 200, s -> s.ancient > 0.5 && s.peakness > 0.6, Kind.GRID);
        add("mountain_wall", "a massive mountain wall", 64, s -> s.wall > 0.4 && s.height > 300, s -> s.wall > 0.5 && s.peakness > 0.6, Kind.GRID);
        add("colossal_peak", "a colossal peak (over 1200 blocks high)", 96, s -> s.height > 1000, s -> s.height > 1200, Kind.GRID);
        add("highest_peak", "the highest point in range", 48, s -> s.height > 150, s -> true, Kind.HIGHEST);
        add("meadow", "a high mountain meadow", 48, s -> s.meadow > 0.5 && s.height > 150, s -> s.meadow > 0.6, Kind.GRID);
        add("plateau", "a plateau", 48, s -> s.plateau > 0.6, s -> s.plateau > 0.85, Kind.GRID);
        add("canyon", "a canyon", 32, s -> s.canyon > 0.3, s -> s.canyon > 0.7, Kind.GRID);
        add("valley", "a mountain valley", 48, s -> s.mountain > 0.4 && s.peakness < 0.25 && s.height > 120, s -> s.mountain > 0.5 && s.peakness < 0.2, Kind.GRID);
        add("hills", "rolling hills", 48, s -> s.erosion > -0.4 && s.erosion < 0.0 && s.mountain < 0.1 && s.height > 90, s -> s.peakness > 0.35, Kind.GRID);
        add("plains", "wide flat plains", 64, s -> s.erosion > 0.2 && s.erosion < 0.5 && s.mountain < 0.05 && s.height > 64 && s.height < 110, s -> s.river <= 0 && s.lake <= 0, Kind.GRID);
        add("windswept_hills", "windswept uplands", 48, s -> s.windswept > 0.6, s -> s.windswept > 0.8, Kind.GRID);
        add("wetland", "a wetland or swamp", 48, s -> s.swamp > 0.6, s -> s.swamp > 0.8, Kind.GRID);
        add("volcano", "a volcano", 0, null, null, Kind.VOLCANO);
        add("waterfall", "a waterfall", 0, null, null, Kind.WATERFALL);
        add("arch", "a natural stone arch", TerrainModel.ARCH, null, null, Kind.FORMATION);
        add("spire", "a rock spire", TerrainModel.SPIRE, null, null, Kind.FORMATION);
        add("hoodoo", "a hoodoo cluster", TerrainModel.HOODOO, null, null, Kind.FORMATION);
        add("sea_stack", "a sea stack", TerrainModel.SEA_STACK, null, null, Kind.FORMATION);
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

    private static Result refine(TerrainModel model, Target target, int cx, int cz, int step, ColumnSample s) {
        int fine = Math.max(2, step / 8);
        int reach = step + step / 2;
        Result best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx += fine) {
            for (int dz = -reach; dz <= reach; dz += fine) {
                model.sampleInto(cx + dx, cz + dz, s);
                if (target.exact().test(s)) {
                    double score = -(sq(dx) + sq(dz));
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Result(cx + dx, surfaceY(s), cz + dz, target.name());
                    }
                }
            }
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
                        best = new Result(w.x, (int) Math.ceil(w.top) + 1, w.z, "waterfall");
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
