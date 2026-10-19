package com.terrahaul.devtools;

import com.terrahaul.worldgen.density.TerrainDetail;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Development tool: runs {@code /terrahaul find} targets from several origins and seeds and checks,
 * with the terrain model, that the landform is really present and visible at the reported place.
 * The checks are deliberately independent of the finder's own predicates and look at the
 * neighbourhood (water width across a river, the drop of a sea cliff, the walls of a canyon or
 * fjord, pools in a wetland, ...). Usage: {@code FinderAudit <seed,seed,...> [target ...]}; with
 * no targets, every grid, summit and overhang target is audited.
 */
public final class FinderAudit {
    private static final int[][] ORIGINS = {{0, 0}, {6000, -4000}, {-9000, 7000}};
    private static final int RADIUS = 20000;

    private FinderAudit() {
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            names.add(args[i]);
        }
        if (names.isEmpty()) {
            for (TerrainFinder.Target t : TerrainFinder.targets().values()) {
                if (t.kind() == TerrainFinder.Kind.GRID || t.kind() == TerrainFinder.Kind.HIGHEST || t.kind() == TerrainFinder.Kind.OVERHANG) {
                    names.add(t.name());
                }
            }
        }
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (String seedText : args[0].split(",")) {
            long seed = Long.parseLong(seedText);
            TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
            for (String name : names) {
                TerrainFinder.Target target = TerrainFinder.targets().get(name);
                for (int[] o : ORIGINS) {
                    long t0 = System.nanoTime();
                    TerrainFinder.Result r = TerrainFinder.find(model, target, o[0], o[1], RADIUS, () -> false);
                    double ms = (System.nanoTime() - t0) / 1e6;
                    int[] tot = totals.computeIfAbsent(name, k -> new int[3]);
                    if (r == null) {
                        tot[2]++;
                        System.out.printf(Locale.ROOT, "seed %d %-18s from %6d %6d: none within %d (%.0f ms)%n", seed, name, o[0], o[1], RADIUS, ms);
                        continue;
                    }
                    String problem = check(model, name, r);
                    if (problem == null) {
                        tot[0]++;
                    } else {
                        tot[1]++;
                    }
                    System.out.printf(Locale.ROOT, "seed %d %-18s from %6d %6d: %6d %4d %6d (%5.0f away, %.0f ms) %s%n", seed, name, o[0], o[1],
                        r.x(), r.y(), r.z(), Math.hypot(r.x() - o[0], r.z() - o[1]), ms, problem == null ? "OK" : "FAIL " + problem);
                }
            }
        }
        System.out.println("summary (ok / fail / not found):");
        totals.forEach((k, v) -> System.out.printf(Locale.ROOT, "  %-18s %2d / %2d / %2d%n", k, v[0], v[1], v[2]));
    }

    /** The reason the result does not show the landform, or {@code null} if it does. */
    static String check(TerrainModel model, String name, TerrainFinder.Result r) {
        int sl = model.seaLevel();
        ColumnSample s = new ColumnSample();
        model.sampleInto(r.x(), r.z(), s);
        double h = s.height;
        boolean wet = s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > h || h < sl - 0.5;
        switch (name) {
            case "river" -> {
                return s.river <= 0.0 ? "not in a river channel" : s.dryRiver ? "dry river" : !wet ? "no water" : null;
            }
            case "great_river" -> {
                if (s.river <= 0.0 || !wet) {
                    return "no river water";
                }
                int w = waterWidth(model, r.x(), r.z());
                return w < 96 || w > 220 ? "water " + w + " blocks wide" : null;
            }
            case "dry_river" -> {
                if (s.river <= 0.0 || !s.dryRiver) {
                    return "not a dry river bed";
                }
                // the generated ground interpolates the 4-block grid columns, so those decide
                for (int dx = -8; dx <= 12; dx += 4) {
                    for (int dz = -8; dz <= 12; dz += 4) {
                        model.sampleInto((r.x() + dx) & ~3, (r.z() + dz) & ~3, s);
                        if (s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > s.height || s.height < sl - 0.5) {
                            return "water within 8 blocks";
                        }
                    }
                }
                return null;
            }
            case "stream" -> {
                ColumnSample c = new ColumnSample();
                model.sampleInto((r.x() + 2) & ~3, (r.z() + 2) & ~3, c);
                double half = 0.8 + 1.6 * c.stream;
                return c.stream <= 0.0 ? "no stream at the corner" : model.streamDistance(r.x(), r.z()) >= half ? "outside the stream bed"
                    : h <= sl ? "below sea level" : c.waterLevel != ColumnSample.NO_WATER ? "inside other water" : null;
            }
            case "ocean" -> {
                return !s.isSea(sl) || h > sl - 8 ? "not open sea" : null;
            }
            case "deep_ocean" -> {
                return h > -60 ? "only " + (int) (sl - h) + " deep" : null;
            }
            case "trench" -> {
                return s.trench < 0.6 ? "trench " + fmt(s.trench) : h > -150 || h > s.trenchFloor + 40 ? "not on the trench floor (y " + (int) h + ")" : null;
            }
            case "trail" -> {
                return s.trailDistance > 1.6 ? "off the trail" : !TerrainModel.trailGround(s, sl) ? "no trail ground" : null;
            }
            case "beach" -> {
                if (s.beach < 0.4 || Math.abs(h - sl) > 3.5) {
                    return "not a beach (h " + (int) h + ")";
                }
                return seaWithin(model, r.x(), r.z(), 24) ? null : "no sea within 24 blocks";
            }
            case "sea_cliff" -> {
                if (h < sl + 12) {
                    return "low ground (h " + (int) h + ")";
                }
                double drop = maxDropToSea(model, r.x(), r.z(), 16);
                return drop < 12 ? "no drop to the sea within 16 blocks" : null;
            }
            case "island" -> {
                return s.island < 0.3 || h < sl + 1 ? "not island land" : s.rawContinentalness > -0.2 ? "part of the mainland" : null;
            }
            case "mushroom_island" -> {
                return s.continentalness > -1.05 || h < sl ? "not a mushroom island" : null;
            }
            case "fjord" -> {
                if (s.fjord < 0.4 || h > sl - 4) {
                    return "not fjord water (h " + (int) h + ")";
                }
                double max = maxWithin(model, r.x(), r.z(), 160);
                return max < sl + 60 ? "no high walls (max " + (int) max + ")" : null;
            }
            case "mountain", "alpine_peaks", "ancient_mountains", "mountain_wall" -> {
                double max = maxWithin(model, r.x(), r.z(), 32);
                double min = h;
                for (int a = 0; a < 8; a++) {
                    double ang = a * Math.PI / 4;
                    min = Math.min(min, model.sample((int) (r.x() + 160 * Math.cos(ang)) & ~3, (int) (r.z() + 160 * Math.sin(ang)) & ~3).height);
                }
                String kind = name.equals("alpine_peaks") ? (s.alpine < 0.4 ? "alpine " + fmt(s.alpine) : null)
                    : name.equals("ancient_mountains") ? (s.ancient < 0.4 ? "ancient " + fmt(s.ancient) : null)
                    : name.equals("mountain_wall") ? (s.wall < 0.3 ? "wall " + fmt(s.wall) : null) : null;
                return kind != null ? kind : max > h + 25 ? "not near a summit (" + (int) (max - h) + " higher within 32)"
                    : h - min < 80 ? "no relief (" + (int) (h - min) + " in 160 blocks)" : null;
            }
            case "colossal_peak" -> {
                return h < 1200 ? "only " + (int) h + " high" : maxWithin(model, r.x(), r.z(), 48) > h + 40 ? "not near the summit" : null;
            }
            case "highest_peak" -> {
                return maxWithin(model, r.x(), r.z(), 16) > h + 2 ? "not the summit" : null;
            }
            case "meadow" -> {
                return s.meadow < 0.5 || h < 150 ? "not a meadow" : wet ? "under water" : slopeAround(model, r.x(), r.z(), 8) > 1.0 ? "steep ground" : null;
            }
            case "plateau" -> {
                if (s.plateau < 0.8 || wet) {
                    return "not on a plateau";
                }
                if (slopeAround(model, r.x(), r.z(), 12) > 1.2) {
                    return "steep top";
                }
                double min = h;
                for (int a = 0; a < 16; a++) {
                    double ang = a * Math.PI / 8;
                    min = Math.min(min, model.sample((int) (r.x() + 320 * Math.cos(ang)) & ~3, (int) (r.z() + 320 * Math.sin(ang)) & ~3).height);
                }
                return h - min < 35 ? "does not stand out (" + (int) (h - min) + " above the land around)" : null;
            }
            case "canyon" -> {
                if (s.canyon < 0.5) {
                    return "canyon " + fmt(s.canyon);
                }
                double max = maxWithin(model, r.x(), r.z(), 120);
                return max - h < 30 ? "shallow (" + (int) (max - h) + ")" : null;
            }
            case "valley" -> {
                double max = maxWithin(model, r.x(), r.z(), 240);
                return s.mountain < 0.3 ? "not in mountains" : wet ? "under water" : max - h < 80 ? "no mountains around (" + (int) (max - h) + ")" : null;
            }
            case "hills" -> {
                double max = maxWithin(model, r.x(), r.z(), 200);
                double min = minWithin(model, r.x(), r.z(), 200);
                return s.mountain > 0.2 ? "mountains" : wet ? "under water" : max - min < 20 ? "flat (" + (int) (max - min) + ")" : null;
            }
            case "plains" -> {
                double max = maxWithin(model, r.x(), r.z(), 48);
                double min = minWithin(model, r.x(), r.z(), 48);
                return wet ? "under water" : max - min > 10 ? "not flat (" + (int) (max - min) + ")" : h < sl + 1 ? "at sea level" : null;
            }
            case "windswept_hills" -> {
                return s.windswept < 0.6 ? "windswept " + fmt(s.windswept) : wet ? "under water" : null;
            }
            case "wetland" -> {
                if (s.swamp < 0.6) {
                    return "swamp " + fmt(s.swamp);
                }
                if (s.temperature < -0.45) {
                    return "frozen (temperature " + fmt(s.temperature) + ")";
                }
                if (Math.abs(h - sl) > 3) {
                    return "dry ground (h " + (int) h + ")";
                }
                double frac = waterFraction(model, r.x(), r.z(), 24);
                return frac < 0.15 ? "few pools (" + (int) (frac * 100) + "% water)" : null;
            }
            case "crater_lake" -> {
                return s.lakeType != LakeType.CRATER || s.waterLevel == ColumnSample.NO_WATER ? "no crater lake water" : null;
            }
            case "overhang" -> {
                double[] o = TerrainDetail.curvedOverhang(model, r.x() & ~3, r.z() & ~3);
                return o == null ? "no overhang at the column" : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Narrowest run of water through (x, z) over four line directions, in blocks. */
    static int waterWidth(TerrainModel model, int x, int z) {
        int best = Integer.MAX_VALUE;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        ColumnSample s = new ColumnSample();
        for (int[] d : dirs) {
            double step = d[0] != 0 && d[1] != 0 ? Math.sqrt(2.0) : 1.0;
            int run = 0;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int k = sign < 0 ? 1 : 0; k < 400; k++) {
                    model.sampleInto(x + sign * d[0] * k * 2, z + sign * d[1] * k * 2, s);
                    boolean water = s.waterLevel != ColumnSample.NO_WATER && s.waterLevel > s.height || s.height < model.seaLevel() - 0.5;
                    if (!water) {
                        break;
                    }
                    run++;
                }
            }
            best = Math.min(best, (int) Math.round(run * 2 * step));
        }
        return best;
    }

    static boolean seaWithin(TerrainModel model, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                if (model.sample((x + dx) & ~3, (z + dz) & ~3).isSea(model.seaLevel())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Largest drop from (x, z) to a sea column within r blocks. */
    static double maxDropToSea(TerrainModel model, int x, int z, int r) {
        double h = model.interpolatedHeight(x, z);
        double best = 0.0;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                ColumnSample c = model.sample((x + dx + 2) & ~3, (z + dz + 2) & ~3);
                if (c.isSea(model.seaLevel())) {
                    best = Math.max(best, h - model.seaLevel());
                }
            }
        }
        return best;
    }

    static double maxWithin(TerrainModel model, int x, int z, int r) {
        double max = -1e9;
        int step = Math.max(4, (r / 12) & ~3);
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                max = Math.max(max, model.sample((x + dx) & ~3, (z + dz) & ~3).height);
            }
        }
        return max;
    }

    static double minWithin(TerrainModel model, int x, int z, int r) {
        double min = 1e9;
        int step = Math.max(4, (r / 12) & ~3);
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                min = Math.min(min, model.sample((x + dx) & ~3, (z + dz) & ~3).height);
            }
        }
        return min;
    }

    /** Steepest slope (blocks per block) between (x, z) and points r blocks away. */
    static double slopeAround(TerrainModel model, int x, int z, int r) {
        double h = model.interpolatedHeight(x, z);
        double best = 0.0;
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            best = Math.max(best, Math.abs(model.interpolatedHeight((int) Math.round(x + r * Math.cos(ang)), (int) Math.round(z + r * Math.sin(ang))) - h) / r);
        }
        return best;
    }

    /** Fraction of columns within r blocks covered by water. */
    static double waterFraction(TerrainModel model, int x, int z, int r) {
        int n = 0;
        int w = 0;
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                ColumnSample c = model.sample((x + dx) & ~3, (z + dz) & ~3);
                n++;
                if (c.waterLevel != ColumnSample.NO_WATER && c.waterLevel > c.height || c.height < model.seaLevel() - 0.5) {
                    w++;
                }
            }
        }
        return w / (double) n;
    }
}
