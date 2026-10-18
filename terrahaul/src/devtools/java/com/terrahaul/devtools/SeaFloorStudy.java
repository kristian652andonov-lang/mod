package com.terrahaul.devtools;

import com.terrahaul.worldgen.features.marine.GrottoPlan;
import com.terrahaul.worldgen.features.marine.MarineColumn;
import com.terrahaul.worldgen.features.marine.MarinePlanner;
import com.terrahaul.worldgen.features.marine.MarineVolume;
import com.terrahaul.worldgen.features.marine.ReefRaster;
import com.terrahaul.worldgen.features.marine.RockRaster;
import com.terrahaul.worldgen.features.marine.SeaLifeRaster;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Development tool: renders and checks Terrahaul sea floors (coral reefs, kelp, seagrass, rocks,
 * grottos) without Minecraft, using exactly the rasters the features run, applied chunk by chunk in
 * feature step order (rocks, grottos, reefs, kelp and seagrass).
 * <pre>
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; area &lt;x&gt; &lt;z&gt; [size=96]        render and check an area
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; find &lt;target&gt; [count=2] [size]   find targets with the finder, then as area
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; stats                        finder distances of every sea target
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; perf                         raster cost per chunk on sea chunks
 * </pre>
 * Checks printed for each area: floating planned blocks, coral blocks without water next to them,
 * plants without support, seams (chunk-by-chunk assembly against one raster of the whole area),
 * grotto roof thickness, and whether the finder's spot shows what it promises.
 */
public final class SeaFloorStudy {
    static final String[] TARGETS = {"coral_reef", "patch_reef", "kelp_forest", "seagrass_meadow", "rocky_seabed", "sea_grotto"};

    private SeaFloorStudy() {
    }

    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File dir = new File(args[1]);
        dir.mkdirs();
        String mode = args[2];
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        MarinePlanner planner = MarinePlanner.of(model);
        switch (mode) {
            case "area" -> {
                int x = Integer.parseInt(args[3]);
                int z = Integer.parseInt(args[4]);
                int size = args.length > 5 ? Integer.parseInt(args[5]) : 96;
                study(planner, dir, "area", x, z, size, null);
            }
            case "find" -> {
                String target = args[3];
                int count = args.length > 4 ? Integer.parseInt(args[4]) : 2;
                int size = args.length > 5 ? Integer.parseInt(args[5]) : 96;
                int ox = 0;
                int oz = 0;
                for (int i = 0; i < count; i++) {
                    TerrainFinder.Result r = TerrainFinder.find(model, TerrainFinder.targets().get(target), ox, oz, 30000, () -> false);
                    if (r == null) {
                        System.out.println("no " + target + " found from " + ox + "," + oz);
                        break;
                    }
                    System.out.println("== " + r.label() + " at " + r.x() + " " + r.y() + " " + r.z());
                    study(planner, dir, target + "_" + i, r.x(), r.z(), size, r);
                    // look for the next one farther out
                    ox = r.x() + (r.x() >= 0 ? 1500 : -1500);
                    oz = r.z() + (r.z() >= 0 ? 1500 : -1500);
                }
            }
            case "stats" -> stats(model);
            case "grottos" -> {
                // census of grotto sites in a square around the origin; renders the nearest hidden one and swim-through
                int half = args.length > 3 ? Integer.parseInt(args[3]) : 5000;
                GrottoPlan plan = GrottoPlan.of(planner);
                int sites = 0;
                int domes = 0;
                double minMargin = Double.MAX_VALUE;
                java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
                GrottoPlan.Grotto firstDome = null;
                GrottoPlan.Grotto firstSwim = null;
                long t0 = System.nanoTime();
                int cells = 0;
                for (int cx = -half / GrottoPlan.CELL; cx < half / GrottoPlan.CELL; cx++) {
                    for (int cz = -half / GrottoPlan.CELL; cz < half / GrottoPlan.CELL; cz++) {
                        cells++;
                        GrottoPlan.Grotto g = plan.grotto(cx, cz);
                        if (g == null) {
                            continue;
                        }
                        sites++;
                        kinds.merge(g.kind, 1, Integer::sum);
                        minMargin = Math.min(minMargin, g.margin);
                        if (g.swim) {
                            kinds.merge("swim-through", 1, Integer::sum);
                            if (firstSwim == null || Math.hypot(g.mx, g.mz) < Math.hypot(firstSwim.mx, firstSwim.mz)) {
                                firstSwim = g;
                            }
                        }
                        if (g.dome) {
                            domes++;
                            if (firstDome == null || Math.hypot(g.mx, g.mz) < Math.hypot(firstDome.mx, firstDome.mz)) {
                                firstDome = g;
                            }
                        }
                    }
                }
                double area = 4.0 * half * half / 1.0e8;
                System.out.printf(Locale.ROOT, "%d grottos (%.1f per 10000 x 10000 blocks), %d hidden with an air dome, kinds %s, smallest roof margin %.2f, planning %.2f ms per cell%n",
                    sites, sites / area, domes, kinds, minMargin, (System.nanoTime() - t0) / 1.0e6 / cells);
                if (firstDome != null) {
                    int[] f = firstDome.front();
                    study(planner, dir, "hidden_grotto", f[0], f[2], 96, new TerrainFinder.Result(f[0], f[1], f[2], "hidden_grotto"));
                }
                if (firstSwim != null) {
                    int[] f = firstSwim.front();
                    study(planner, dir, "swim_through", f[0], f[2], 96, new TerrainFinder.Result(f[0], f[1], f[2], "swim_through"));
                }
            }
            case "perf" -> perf(planner);
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    static boolean isGrotto(String label) {
        return label.endsWith("grotto") || label.equals("swim_through");
    }

    static void stats(TerrainModel model) {
        for (String target : TARGETS) {
            long t0 = System.nanoTime();
            TerrainFinder.Result r = TerrainFinder.find(model, TerrainFinder.targets().get(target), 0, 0, 30000, () -> false);
            long ms = (System.nanoTime() - t0) / 1000000;
            System.out.printf(Locale.ROOT, "%-16s %s (%d ms)%n", target, r == null ? "not found within 30000"
                : String.format(Locale.ROOT, "%s at %d %d %d, %.0f blocks from the origin", r.label(), r.x(), r.y(), r.z(), Math.hypot(r.x(), r.z())), ms);
        }
    }

    /** Cost of each raster per chunk on random sea chunks (warm caches, like a running server). */
    static void perf(MarinePlanner planner) {
        java.util.Random random = new java.util.Random(7);
        double[] total = new double[4];
        int[] count = new int[4];
        GrottoPlan grottos = GrottoPlan.of(planner);
        int sea = 0;
        for (int i = 0; sea < 400 && i < 20000; i++) {
            int cx = (random.nextInt(4000) - 2000) * 16;
            int cz = (random.nextInt(4000) - 2000) * 16;
            if (!planner.mayHaveSea(cx, cz, 0)) {
                continue;
            }
            sea++;
            long t0 = System.nanoTime();
            MarineVolume rocks = new MarineVolume(planner, cx, cz, 16, 16, RockRaster.MARGIN);
            RockRaster.build(rocks, 1.0, 1.0);
            long t1 = System.nanoTime();
            total[0] += t1 - t0;
            count[0]++;
            if (planner.mayHaveReef(cx, cz, ReefRaster.MARGIN + 2)) {
                MarineVolume reef = new MarineVolume(planner, cx, cz, 16, 16, ReefRaster.MARGIN);
                ReefRaster.build(reef);
                total[1] += System.nanoTime() - t1;
                count[1]++;
            }
            long t2 = System.nanoTime();
            if (planner.mayHaveShelf(cx, cz)) {
                MarineColumn c = new MarineColumn();
                SeaLifeRaster.Plant plant = new SeaLifeRaster.Plant();
                for (int x = cx; x < cx + 16; x++) {
                    for (int z = cz; z < cz + 16; z++) {
                        double h = planner.model().interpolatedHeight(x, z);
                        if (h < planner.seaLevel() - 0.5 && h >= planner.seaLevel() - 44.0) {
                            planner.column(x, z, c);
                            SeaLifeRaster.decide(planner, c, c.floor, SeaLifeRaster.naturalFloor(c), plant);
                        }
                    }
                }
                total[2] += System.nanoTime() - t2;
                count[2]++;
            }
            long t3 = System.nanoTime();
            if (!grottos.near(cx, cz).isEmpty()) {
                MarineVolume g = new MarineVolume(planner, cx, cz, 16, 16, GrottoPlan.MARGIN);
                grottos.build(g);
                total[3] += System.nanoTime() - t3;
                count[3]++;
            }
        }
        // the same rock chunks again without boulders, and with every column's plan cached (warm)
        random = new java.util.Random(7);
        double noBoulders = 0.0;
        double plansOnly = 0.0;
        int n = 0;
        for (int i = 0; n < 400 && i < 20000; i++) {
            int cx = (random.nextInt(4000) - 2000) * 16;
            int cz = (random.nextInt(4000) - 2000) * 16;
            if (!planner.mayHaveSea(cx, cz, 0)) {
                continue;
            }
            n++;
            long t0 = System.nanoTime();
            MarineVolume rocks = new MarineVolume(planner, cx, cz, 16, 16, RockRaster.MARGIN);
            RockRaster.build(rocks, 0.0, 1.0);
            long t1 = System.nanoTime();
            noBoulders += t1 - t0;
            MarineVolume plan = new MarineVolume(planner, cx, cz, 16, 16, 0);
            for (int x = cx; x < cx + 16; x++) {
                for (int z = cz; z < cz + 16; z++) {
                    plan.column(x, z);
                }
            }
            plansOnly += System.nanoTime() - t1;
        }
        System.out.printf(Locale.ROOT, "rocks without boulders %.2f ms, 256 column plans alone %.2f ms per chunk%n", noBoulders / n / 1.0e6, plansOnly / n / 1.0e6);
        String[] names = {"rocks", "reef", "vegetation", "grottos"};
        System.out.printf(Locale.ROOT, "%d sea chunks sampled%n", sea);
        for (int k = 0; k < 4; k++) {
            System.out.printf(Locale.ROOT, "%-10s %4d chunks, %.2f ms per chunk that runs it%n", names[k], count[k], count[k] == 0 ? 0 : total[k] / count[k] / 1.0e6);
        }
    }

    static SeaFloorArea build(MarinePlanner planner, int minX, int minZ, int size, boolean whole) {
        SeaFloorArea area = new SeaFloorArea(planner, minX, minZ, size);
        area.whole = whole;
        int chunks = (size / 16) * (size / 16);
        long t0 = System.nanoTime();
        area.apply(RockRaster.MARGIN, v -> RockRaster.build(v, 1.0, 1.0));
        long t1 = System.nanoTime();
        GrottoPlan grottos = GrottoPlan.of(planner);
        area.applyDug(GrottoPlan.MARGIN, grottos::build);
        long t2 = System.nanoTime();
        area.guarded = true;
        area.apply(ReefRaster.MARGIN, ReefRaster::build);
        area.guarded = false;
        long t3 = System.nanoTime();
        area.applyVegetation();
        long t4 = System.nanoTime();
        System.out.printf(Locale.ROOT, "ms per chunk: rocks %.2f, grottos %.2f, reef %.2f, vegetation %.2f%n", (t1 - t0) / 1.0e6 / chunks,
            (t2 - t1) / 1.0e6 / chunks, (t3 - t2) / 1.0e6 / chunks, (t4 - t3) / 1.0e6 / chunks);
        return area;
    }

    static void study(MarinePlanner planner, File dir, String name, int cx, int cz, int size, TerrainFinder.Result found) throws Exception {
        int minX = Math.floorDiv(cx - size / 2, 16) * 16;
        int minZ = Math.floorDiv(cz - size / 2, 16) * 16;
        size = (size + 15) / 16 * 16;
        SeaFloorArea area = build(planner, minX, minZ, size, false);
        stats(area);
        checks(area);
        seams(planner, area);
        grottoChecks(planner, area);
        if (found != null) {
            truth(area, found);
        }
        ImageIO.write(area.top(minX, minZ, size, size <= 128 ? 4 : size <= 256 ? 2 : 1), "png", new File(dir, name + "_top.png"));
        int isoN = Math.min(size, 96);
        int ax = minX + (size - isoN) / 2;
        int az = minZ + (size - isoN) / 2;
        // vertical range: from the lowest floor of the view up to 24 above its highest floor
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int x = ax; x < ax + isoN; x++) {
            for (int z = az; z < az + isoN; z++) {
                lowest = Math.min(lowest, area.column(x, z).floor);
                highest = Math.max(highest, area.column(x, z).floor);
            }
        }
        int yHigh = Math.min(planner.surf + 6, highest + 24);
        int yLow = Math.max(lowest - 3, yHigh - 70);
        ImageIO.write(area.iso(ax, az, isoN, isoN > 64 ? 6 : 8, yLow, yHigh), "png", new File(dir, name + "_iso.png"));
        int secZ = found != null ? found.z() : minZ + size / 2;
        ImageIO.write(area.section(minX, size, secZ, yLow, yHigh, 5), "png", new File(dir, name + "_sec.png"));
        if (found != null) {
            // a diver's view: at the finder's spot (reef, grotto) looking at the feature, or from
            // 12 blocks back for spots in the middle of a field (kelp, meadow, rocks)
            MarineColumn c = area.column(found.x(), found.z());
            double dx = c.slope > 0.02 ? c.gradX : 0.0;
            double dz = c.slope > 0.02 ? c.gradZ : -1.0;
            double l = Math.hypot(dx, dz);
            dx /= l;
            dz /= l;
            boolean facing = found.label().equals("coral_reef") || isGrotto(found.label());
            if (isGrotto(found.label())) {
                for (GrottoPlan.Grotto g : GrottoPlan.of(planner).near(found.x() & ~15, found.z() & ~15)) {
                    if (g.front()[0] == found.x() && g.front()[2] == found.z()) {
                        dx = g.ux;
                        dz = g.uz;
                    }
                }
            }
            double back = facing ? 2.0 : 12.0;
            double camY = facing ? found.y() + 0.5 : found.y() + 2.5;
            ImageIO.write(area.perspective(found.x() + 0.5 - dx * back, camY, found.z() + 0.5 - dz * back, dx, dz, facing ? 2.0 : -14.0, 640, 360),
                "png", new File(dir, name + "_view.png"));
        }
        if (found != null && isGrotto(found.label())) {
            GrottoPlan plan = GrottoPlan.of(planner);
            for (GrottoPlan.Grotto g : plan.near(found.x() & ~15, found.z() & ~15)) {
                int[] f = g.front();
                if (f[0] != found.x() || f[2] != found.z()) {
                    continue;
                }
                System.out.printf(Locale.ROOT, "grotto %s%s: mouth %.0f %.0f dir %.2f %.2f floor %d opening %dx%.1f mouth plane %d tunnel %d chamber %.1f x %.1f x %.1f (floor %d) roof margin %.1f%n",
                    g.kind, g.dome ? " (hidden, air dome)" : g.swim ? " (swim-through, far mouth at " + g.farMouth + ")" : "", g.mx, g.mz, g.ux, g.uz, g.floor, g.ho, 2 * g.hw, g.um, g.lt, 2 * g.ra, 2 * g.rv, 2 * g.ry, g.chamberFloor, g.margin);
                int uMax = (int) Math.ceil(g.chamberU() + g.ra) + 8;
                ImageIO.write(area.axis(g.mx, g.mz, g.ux, g.uz, -12, uMax, g.floor - 6, Math.max(g.floor + 34, planner.surf + 10), 6), "png",
                    new File(dir, name + "_axis.png"));
                ImageIO.write(area.slice(minX, minZ, size, g.floor + 2, 4), "png", new File(dir, name + "_slice.png"));
            }
        }
        System.out.println("wrote " + name + " images centred at " + cx + " " + cz + " (area " + minX + "," + minZ + " size " + size + ")");
    }

    /** The finder's spot must show what it promises, in the assembled area. */
    static void truth(SeaFloorArea area, TerrainFinder.Result r) {
        int live = 0;
        int kelp = 0;
        int grass = 0;
        int rock = 0;
        int dug = 0;
        int columns = 0;
        for (int x = r.x() - 10; x <= r.x() + 10; x++) {
            for (int z = r.z() - 10; z <= r.z() + 10; z++) {
                columns++;
                boolean g = false;
                boolean k = false;
                for (int y = r.y() - 40; y <= r.y() + 20; y++) {
                    byte c = area.get(x, y, z);
                    if (MarineVolume.isLive(c)) {
                        live++;
                    }
                    if (c >= MarineVolume.KELP_PLANT && c <= MarineVolume.KELP_GROWING) {
                        k = true;
                    }
                    if (c >= MarineVolume.SEAGRASS && c <= MarineVolume.TALL_UPPER) {
                        g = true;
                    }
                    if (c >= MarineVolume.ROCK && c < MarineVolume.ROCK + 15 || c == MarineVolume.MOSSY_COBBLE) {
                        rock++;
                    }
                    if (c == MarineVolume.WATER || c == MarineVolume.AIR) {
                        dug++;
                    }
                }
                kelp += k ? 1 : 0;
                grass += g ? 1 : 0;
            }
        }
        System.out.printf(Locale.ROOT, "TRUTH within 10 blocks of the finder's spot: live coral %d, kelp columns %d, seagrass columns %d of %d, rock blocks %d, dug-out blocks %d%n",
            live, kelp, grass, columns, rock, dug);
    }

    /** Thinnest rock above each grotto column of the area (planned floor above the highest dug block). */
    static void grottoChecks(MarinePlanner planner, SeaFloorArea area) {
        int columns = 0;
        int thinnest = Integer.MAX_VALUE;
        int dome = 0;
        for (int x = area.minX; x < area.minX + area.size; x++) {
            for (int z = area.minZ; z < area.minZ + area.size; z++) {
                MarineColumn c = area.column(x, z);
                int top = Integer.MIN_VALUE;
                for (int y = c.floor - 80; y <= c.floor; y++) {
                    byte code = area.get(x, y, z);
                    if (code == MarineVolume.WATER || code == MarineVolume.AIR) {
                        top = y;
                    }
                    if (code == MarineVolume.AIR) {
                        dome++;
                    }
                }
                if (top != Integer.MIN_VALUE && c.floor > top) {
                    columns++;
                    thinnest = Math.min(thinnest, c.floor - top);
                }
            }
        }
        if (columns > 0) {
            System.out.printf(Locale.ROOT, "GROTTO columns under rock %d, thinnest planned roof %d blocks, air-dome blocks %d%n", columns, thinnest, dome);
        }
    }

    static void stats(SeaFloorArea area) {
        int[] counts = new int[128];
        for (Map.Entry<Long, Byte> e : area.cells().entrySet()) {
            counts[e.getValue()]++;
        }
        int live = 0;
        int dead = 0;
        int plants = 0;
        int fans = 0;
        int wallFans = 0;
        int pickles = 0;
        int[] species = new int[5];
        for (int i = 0; i < 5; i++) {
            live += counts[MarineVolume.LIVE + i];
            species[i] = counts[MarineVolume.LIVE + i];
            dead += counts[MarineVolume.DEAD + i];
            plants += counts[MarineVolume.PLANT + i];
            fans += counts[MarineVolume.FAN + i];
        }
        for (int i = 0; i < 20; i++) {
            wallFans += counts[MarineVolume.WALL_FAN + i];
        }
        for (int i = 0; i < 4; i++) {
            pickles += counts[MarineVolume.PICKLE + i];
        }
        int reefColumns = 0;
        for (int x = area.minX; x < area.minX + area.size; x++) {
            for (int z = area.minZ; z < area.minZ + area.size; z++) {
                MarineColumn c = area.column(x, z);
                for (int y = c.floor + 1; y <= area.planner.surf; y++) {
                    if (MarineVolume.isReef(area.get(x, y, z))) {
                        reefColumns++;
                        break;
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "reef columns %.1f%%, live %d (tube %d brain %d bubble %d fire %d horn %d), dead %d, calcite %d, sand %d%n",
            100.0 * reefColumns / (area.size * area.size), live, species[0], species[1], species[2], species[3], species[4], dead,
            counts[MarineVolume.CALCITE], counts[MarineVolume.SAND]);
        System.out.printf(Locale.ROOT, "plants %d, fans %d, wall fans %d, pickles %d, seagrass %d, tall seagrass %d, kelp %d%n",
            plants, fans, wallFans, pickles, counts[MarineVolume.SEAGRASS], counts[MarineVolume.TALL_LOWER],
            counts[MarineVolume.KELP_PLANT] + counts[MarineVolume.KELP_TOP] + counts[MarineVolume.KELP_GROWING]);
    }

    /** Invariants: nothing floats, coral blocks touch water, plants stand on something solid. */
    static void checks(SeaFloorArea area) {
        int edge = 10;
        int floating = 0;
        int dry = 0;
        int unsupported = 0;
        Set<Long> anchored = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        // anchor: planned solids resting on natural ground
        for (Map.Entry<Long, Byte> e : area.cells().entrySet()) {
            if (!MarineVolume.isSolid(e.getValue())) {
                continue;
            }
            int[] p = unkey(e.getKey());
            byte below = area.get(p[0], p[1] - 1, p[2]);
            if (below == MarineVolume.NAT_SOLID || neighbourNatural(area, p)) {
                anchored.add(e.getKey());
                queue.add(p);
            }
        }
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int x = p[0] + d[0];
                int y = p[1] + d[1];
                int z = p[2] + d[2];
                long k = key(x, y, z);
                if (!anchored.contains(k) && area.cells().containsKey(k) && MarineVolume.isSolid(area.get(x, y, z))) {
                    anchored.add(k);
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        for (Map.Entry<Long, Byte> e : area.cells().entrySet()) {
            int[] p = unkey(e.getKey());
            if (p[0] < area.minX + edge || p[0] >= area.minX + area.size - edge || p[2] < area.minZ + edge || p[2] >= area.minZ + area.size - edge) {
                continue;
            }
            byte code = e.getValue();
            if (MarineVolume.isSolid(code) && !anchored.contains(e.getKey())) {
                if (floating++ < 5) {
                    System.out.println("  floating code " + code + " at " + p[0] + " " + p[1] + " " + p[2]);
                }
            }
            if (MarineVolume.isLive(code)) {
                boolean wet = false;
                for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    byte n = area.get(p[0] + d[0], p[1] + d[1], p[2] + d[2]);
                    if (!MarineVolume.isSolid(n) && n != MarineVolume.AIR && (p[1] + d[1] <= area.planner.surf)) {
                        wet = true;
                    }
                }
                if (!wet) {
                    if (dry++ < 5) {
                        StringBuilder sb = new StringBuilder();
                        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                            sb.append(' ').append(area.get(p[0] + d[0], p[1] + d[1], p[2] + d[2]));
                        }
                        System.out.println("  dry coral " + code + " at " + p[0] + " " + p[1] + " " + p[2] + ", neighbours" + sb);
                    }
                }
            }
            if (needsFloor(code) && !MarineVolume.isSolid(area.get(p[0], p[1] - 1, p[2])) && !(code == MarineVolume.TALL_UPPER
                && area.get(p[0], p[1] - 1, p[2]) == MarineVolume.TALL_LOWER) && !isKelpChain(area, p, code)) {
                if (unsupported++ < 5) {
                    System.out.println("  unsupported code " + code + " at " + p[0] + " " + p[1] + " " + p[2] + " on " + area.get(p[0], p[1] - 1, p[2]));
                }
            }
            if (code >= MarineVolume.WALL_FAN && code < MarineVolume.PICKLE) {
                int k = (code - MarineVolume.WALL_FAN) % 4;
                if (!MarineVolume.isSolid(area.get(p[0] - MarineVolume.DIR_X[k], p[1], p[2] - MarineVolume.DIR_Z[k]))) {
                    if (unsupported++ < 5) {
                        System.out.println("  unsupported wall fan at " + p[0] + " " + p[1] + " " + p[2]);
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "CHECK floating solids %d, coral blocks without water %d, unsupported plants %d -> %s%n",
            floating, dry, unsupported, floating == 0 && dry == 0 && unsupported == 0 ? "PASS" : "FAIL");
    }

    private static boolean neighbourNatural(SeaFloorArea area, int[] p) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
            if (area.get(p[0] + d[0], p[1], p[2] + d[2]) == MarineVolume.NAT_SOLID) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKelpChain(SeaFloorArea area, int[] p, byte code) {
        if (code < MarineVolume.KELP_PLANT || code > MarineVolume.KELP_GROWING) {
            return false;
        }
        return area.get(p[0], p[1] - 1, p[2]) == MarineVolume.KELP_PLANT;
    }

    private static boolean needsFloor(byte code) {
        return code >= MarineVolume.PLANT && code < MarineVolume.WALL_FAN || code >= MarineVolume.PICKLE && code <= MarineVolume.KELP_GROWING
            || code >= MarineVolume.PEBBLES && code < MarineVolume.GLOW_LICHEN;
    }

    /** Builds the whole area as one volume and compares it with the chunk-by-chunk assembly. */
    static void seams(MarinePlanner planner, SeaFloorArea area) {
        SeaFloorArea ref = build(planner, area.minX, area.minZ, area.size, true);
        int diff = 0;
        Set<Long> keys = new HashSet<>(area.cells().keySet());
        keys.addAll(ref.cells().keySet());
        for (long k : keys) {
            int[] p = unkey(k);
            if (area.get(p[0], p[1], p[2]) != ref.get(p[0], p[1], p[2])) {
                diff++;
            }
        }
        System.out.printf(Locale.ROOT, "CHECK seams: %d cells differ between chunk-by-chunk and whole-area rasters -> %s%n", diff, diff == 0 ? "PASS" : "FAIL");
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048 & 0xFFF);
    }

    static int[] unkey(long k) {
        int x = (int) (k >> 38);
        x = x << 6 >> 6;
        int z = (int) (k >> 12 & 0x3FFFFFF);
        z = z << 6 >> 6;
        int y = (int) (k & 0xFFF) - 2048;
        return new int[]{x, y, z};
    }
}
