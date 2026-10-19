package com.terrahaul.worldgen.features.marine;

import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.function.BooleanSupplier;

/**
 * Finds sea-floor features for {@code /terrahaul find} with the same planners the features use,
 * so a reported spot really has what it promises. The reported position is in open water in
 * front of the feature, a few blocks above the floor, so a teleport lands the player looking at it
 * (underwater: bring something to breathe).
 * <ul>
 *   <li>{@code coral_reef}: in front of the reef front, the wall of coral rising from the sand to
 *       two blocks below the surface (the reef lies about 8 blocks up-slope);</li>
 *   <li>{@code patch_reef}: above the middle of an oval patch reef;</li>
 *   <li>{@code kelp_forest}: inside a dense kelp forest;</li>
 *   <li>{@code seagrass_meadow}: above a seagrass meadow;</li>
 *   <li>{@code rocky_seabed}: next to rock ledges with boulders around;</li>
 *   <li>{@code sea_grotto}: in front of the mouth of an underwater grotto (label
 *       {@code hidden_grotto} for one with an air dome and beach inside, {@code swim_through} for
 *       an arch through a ridge that opens on its far side).</li>
 * </ul>
 */
public final class MarineFinder {
    private MarineFinder() {
    }

    public static TerrainFinder.Result find(TerrainModel model, String target, int x, int z, int radius, BooleanSupplier cancelled) {
        MarinePlanner p = MarinePlanner.of(model);
        TerrainFinder.Result r = search(p, target, x, z, radius, cancelled);
        if (r == null || target.equals("sea_grotto") || target.equals("patch_reef")) {
            return r;
        }
        // the spot must be open water, not inside a coral head or a boulder planned there
        return new TerrainFinder.Result(r.x(), openWater(p, r.x(), r.y(), r.z(), target.equals("coral_reef")), r.z(), r.label());
    }

    /** The lowest y from {@code y} up where the planned reef or rocks leave two blocks of water. */
    private static int openWater(MarinePlanner p, int x, int y, int z, boolean reef) {
        MarineVolume v = new MarineVolume(p, x & ~15, z & ~15, 16, 16, reef ? ReefRaster.MARGIN : RockRaster.MARGIN);
        if (reef) {
            ReefRaster.build(v);
        } else {
            RockRaster.build(v, 1.0, 1.0);
        }
        while (y < p.surf - 1 && (MarineVolume.isSolid(v.get(x, y, z)) || MarineVolume.isSolid(v.get(x, y + 1, z)))) {
            y++;
        }
        return y;
    }

    private static TerrainFinder.Result search(MarinePlanner p, String target, int x, int z, int radius, BooleanSupplier cancelled) {
        return switch (target) {
            case "coral_reef" -> rings(p, x, z, radius, 48, cancelled, s -> s.temperature > 0.42 && s.height < p.seaLevel() - 1.0 && s.height > p.seaLevel() - 18.0,
                (c, px, pz) -> reefFront(p, c, px, pz), target);
            case "kelp_forest" -> rings(p, x, z, radius, 32, cancelled, s -> s.temperature > -0.62 && s.temperature < 0.34 && s.height < p.seaLevel() - 7.0
                && s.height > p.seaLevel() - 30.0, (c, px, pz) -> c.sea && c.kelp > 0.7 && c.depth >= 8 && c.depth <= 28
                ? new int[]{px, c.floor + Math.max(3, (int) (c.depth * 0.4)), pz} : null, target);
            case "seagrass_meadow" -> rings(p, x, z, radius, 32, cancelled, s -> s.temperature > -0.5 && s.height < p.seaLevel() - 2.0
                && s.height > p.seaLevel() - 14.0, (c, px, pz) -> c.sea && c.meadow > 0.7 && c.depth >= 3 && c.depth <= 12 && !SeaLifeRaster.reefColumn(c)
                && c.kelp < 0.3 && c.rocky < 0.3 && SeaLifeRaster.naturalFloor(c) == SeaLifeRaster.SOFT ? new int[]{px, c.floor + 3, pz} : null, target);
            case "rocky_seabed" -> rings(p, x, z, radius, 32, cancelled, s -> s.height < p.seaLevel() - 4.0 && s.height > p.seaLevel() - 40.0
                && s.lake <= 0.0 && s.river <= 0.0, (c, px, pz) -> c.sea && c.outcrop >= 2 && c.rocky >= 0.5
                ? new int[]{px, c.floor + c.outcrop + 3, pz} : null, target);
            case "patch_reef" -> patchReef(p, x, z, radius, cancelled);
            case "sea_grotto" -> grotto(p, x, z, radius, cancelled);
            default -> null;
        };
    }

    @FunctionalInterface
    private interface Coarse {
        boolean test(ColumnSample s);
    }

    @FunctionalInterface
    private interface Exact {
        int[] test(MarineColumn c, int x, int z);
    }

    /** Ring search on a coarse grid of model samples, refined on a 4-block grid around each hit. */
    private static TerrainFinder.Result rings(MarinePlanner p, int x, int z, int radius, int step, BooleanSupplier cancelled, Coarse coarse,
                                              Exact exact, String label) {
        TerrainModel model = p.model();
        MarineColumn c = new MarineColumn();
        for (int r = 0; r <= radius / step; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            TerrainFinder.Result best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = -r; i <= r; i++) {
                for (int[] q : r == 0 ? new int[][]{{0, 0}} : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                    int px = (x + q[0] * step) & ~3;
                    int pz = (z + q[1] * step) & ~3;
                    if (!coarse.test(model.sample(px, pz))) {
                        continue;
                    }
                    for (int dx = -step; dx <= step; dx += 4) {
                        for (int dz = -step; dz <= step; dz += 4) {
                            p.column(px + dx, pz + dz, c);
                            int[] hit = exact.test(c, px + dx, pz + dz);
                            if (hit != null) {
                                double d = (double) (hit[0] - x) * (hit[0] - x) + (double) (hit[2] - z) * (hit[2] - z);
                                if (d < bestD) {
                                    bestD = d;
                                    best = new TerrainFinder.Result(hit[0], hit[1], hit[2], label);
                                }
                            }
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
     * A column on the outer edge of a reef flat; returns the open-water point 8 blocks downslope
     * from it, in front of the reef front, if that point is sand without reef.
     */
    private static int[] reefFront(MarinePlanner p, MarineColumn c, int x, int z) {
        double d = p.seaLevel() - c.height;
        if (!c.sea || !c.hasPlatform() || c.reef < 0.6 || d < c.front - 2.0 || d > c.front || c.slope < 0.03) {
            return null;
        }
        int fx = (int) Math.round(x - c.gradX / c.slope * 8.0);
        int fz = (int) Math.round(z - c.gradZ / c.slope * 8.0);
        MarineColumn f = new MarineColumn();
        p.column(fx, fz, f);
        if (!f.sea || f.hasPlatform() || f.depth < 6) {
            return null;
        }
        // level with the middle of the reef wall, even where the floor in front drops away
        return new int[]{fx, Math.max(f.floor + 3, p.crest - 6), fz};
    }

    private static TerrainFinder.Result patchReef(MarinePlanner p, int x, int z, int radius, BooleanSupplier cancelled) {
        int cell = MarinePlanner.PATCH_CELL;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        for (int r = 0; r <= radius / cell + 1; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            TerrainFinder.Result best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = -r; i <= r; i++) {
                for (int[] q : r == 0 ? new int[][]{{0, 0}} : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                    MarinePlanner.PatchReef pr = p.patchReef(cx + q[0], cz + q[1]);
                    if (pr != null) {
                        double d = (double) (pr.x - x) * (pr.x - x) + (double) (pr.z - z) * (pr.z - z);
                        if (d < bestD) {
                            bestD = d;
                            best = new TerrainFinder.Result(pr.x, p.crest + 2, pr.z, "patch_reef");
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

    private static TerrainFinder.Result grotto(MarinePlanner p, int x, int z, int radius, BooleanSupplier cancelled) {
        GrottoPlan plan = GrottoPlan.of(p);
        int cell = GrottoPlan.CELL;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        for (int r = 0; r <= radius / cell + 1; r++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            TerrainFinder.Result best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = -r; i <= r; i++) {
                for (int[] q : r == 0 ? new int[][]{{0, 0}} : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                    GrottoPlan.Grotto g = plan.grotto(cx + q[0], cz + q[1]);
                    if (g != null) {
                        double d = (g.mx - x) * (g.mx - x) + (g.mz - z) * (g.mz - z);
                        if (d < bestD) {
                            bestD = d;
                            int[] f = g.front();
                            best = new TerrainFinder.Result(f[0], f[1], f[2], g.dome ? "hidden_grotto" : g.swim ? "swim_through" : "sea_grotto");
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
}
