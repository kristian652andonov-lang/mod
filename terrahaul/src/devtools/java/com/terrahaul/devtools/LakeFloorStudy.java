package com.terrahaul.devtools;

import com.terrahaul.worldgen.features.lake.LakeColumn;
import com.terrahaul.worldgen.features.lake.LakeLifeRaster;
import com.terrahaul.worldgen.features.lake.LakePlanner;
import com.terrahaul.worldgen.features.lake.LakeRaster;
import com.terrahaul.worldgen.features.lake.LakeVolume;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.LakeType;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Development tool: renders and checks Terrahaul lake floors (meadows, weed beds, lily pads,
 * reeds, shelves and patches, rocks, sunken logs and discoveries) without Minecraft, using exactly
 * the rasters the lake features run, applied chunk by chunk. Run through {@code SeaFloorStudy}:
 * <pre>
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; lakes [types=all] [count=1] [before|after|both]
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; lakeat &lt;x&gt; &lt;z&gt; [size=160]
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; lakeview &lt;x&gt; &lt;z&gt; [&lt;x&gt; &lt;z&gt; ...]
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; lakesurvey [types=all] [count=2]
 * SeaFloorStudy &lt;seed&gt; &lt;outDir&gt; lakeperf
 * </pre>
 * {@code types} is a comma-separated list of {@code great, forest, mountain, glacial, plateau,
 * marsh, crater}. For each lake it writes a top view, a close-up, a diver's view, a view from a
 * boat just above the surface, a section and a sheet of them, before ({@code before_}, the lake
 * as the terrain and the water leave it) and after ({@code after_}) the lake features, from the
 * same cameras; for a great lake also the open water around its nearest shoal ({@code _open}) and
 * the open water halfway out to its centre ({@code _far}). The diver of the sheet looks at the
 * most interesting spot; besides it, a diver and a boat at a fixed point that nobody chose
 * ({@code _fix_sheet}: halfway from the centre to the shore, or the middle of the area for a
 * great lake) show what the lake is like on average.
 * It checks: planned blocks resting on nothing, plants without their support, blocks in the
 * water that would not hold it (not solid and not waterloggable), floor blocks replaced by
 * anything but solid ground, and seams (chunk-by-chunk assembly against one raster of the whole
 * area). {@code lakeview} renders the same fixed views at given points. {@code lakesurvey} prints,
 * per lake, the share of lake columns with plants and with objects (rocks, logs, ruins) in four
 * depth bands, and how many columns have nothing within eight blocks. {@code lakeperf} measures the
 * planning cost per chunk and counts the blocks a chunk writes (the world writes themselves, two
 * reads and one write per planned block, are not timed here).
 */
final class LakeFloorStudy {
    private static final String[] TYPE_KEYS = {"none", "forest", "mountain", "great", "marsh", "crater", "plateau", "glacial"};

    private LakeFloorStudy() {
    }

    /** A lake found for the study: centre, radius, the radius of its shaped surroundings, surface. */
    record Lake(int type, int x, int z, double radius, double ring, int level) {
    }

    static void main(TerrainModel model, File dir, String mode, String[] args) throws Exception {
        LakePlanner planner = LakePlanner.of(model);
        switch (mode) {
            case "lakes" -> {
                String which = args.length > 3 ? args[3] : "all";
                int count = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                String phase = args.length > 5 ? args[5] : "both";
                List<Integer> types = new ArrayList<>();
                for (int t = 1; t < TYPE_KEYS.length; t++) {
                    if (which.equals("all") || List.of(which.split(",")).contains(TYPE_KEYS[t])) {
                        types.add(t);
                    }
                }
                for (int type : types) {
                    List<Lake> lakes = find(model, type, count);
                    if (lakes.isEmpty()) {
                        System.out.println("no " + LakeType.name(type) + " found");
                    }
                    for (int i = 0; i < lakes.size(); i++) {
                        Lake l = lakes.get(i);
                        String name = TYPE_KEYS[type] + "_" + i;
                        LakeColumn centre = new LakeColumn();
                        planner.column(l.x, l.z, centre);
                        System.out.printf(Locale.ROOT, "== %s at %d %d, radius %.0f, surface %d, temperature %.2f, humidity %.2f%n", LakeType.name(type), l.x, l.z,
                            l.radius, l.level - 1, centre.temperature, centre.humidity);
                        study(planner, dir, name, l, phase, 0, false);
                        if (type == LakeType.GREAT) {
                            // the open lake far from its shores, around a shoal
                            LakePlanner.Shoal sh = nearestShoal(planner, l);
                            if (sh != null) {
                                Lake at = new Lake(type, (int) sh.x(), (int) sh.z(), sh.radius(), sh.radius(), l.level);
                                System.out.printf(Locale.ROOT, "   shoal at %.0f %.0f, radius %.0f, crest %d blocks under the surface%n", sh.x(), sh.z(), sh.radius(),
                                    l.level - 1 - sh.top());
                                study(planner, dir, name + "_open", at, phase, 192, true);
                            }
                            // and the open lake halfway out to its centre, wherever that falls
                            int[] far = fixedPoint(planner, l);
                            System.out.printf(Locale.ROOT, "   open water halfway to the centre at %d %d%n", far[0], far[1]);
                            study(planner, dir, name + "_far", new Lake(type, far[0], far[1], 80, 80, l.level), phase, 192, true);
                        }
                    }
                }
            }
            case "lakeat" -> {
                int x = Integer.parseInt(args[3]);
                int z = Integer.parseInt(args[4]);
                int size = args.length > 5 ? Integer.parseInt(args[5]) : 160;
                LakeColumn c = new LakeColumn();
                planner.column(x, z, c);
                Lake l = new Lake(c.type, x, z, size / 2.0 - 8, size / 2.0 - 8, c.surf + 1);
                study(planner, dir, "lake_" + x + "_" + z, l, "after", size, false);
            }
            case "lakeview" -> {
                for (int i = 3; i + 1 < args.length; i += 2) {
                    int x = Integer.parseInt(args[i]);
                    int z = Integer.parseInt(args[i + 1]);
                    LakeColumn c = new LakeColumn();
                    planner.column(x, z, c);
                    System.out.printf(Locale.ROOT, "%d %d: lake %b type %s surface %d floor %d temperature %.2f%n", x, z, c.wet, LakeType.name(c.type), c.surf, c.floor,
                        c.temperature);
                    if (!c.wet) {
                        continue;
                    }
                    int size = 192;
                    int minX = Math.floorDiv(x - size / 2, 16) * 16;
                    int minZ = Math.floorDiv(z - size / 2, 16) * 16;
                    LakeFloorArea after = new LakeFloorArea(planner, minX, minZ, size);
                    after.applyFloor(1.0, 1.0);
                    after.applyLife();
                    String name = "view_" + x + "_" + z;
                    LakeFloorArea before = new LakeFloorArea(planner, minX, minZ, size);
                    fixedViews(before, dir, "before_" + name, x, z);
                    fixedViews(after, dir, "after_" + name, x, z);
                    ImageIO.write(before.top(minX, minZ, size, 2), "png", new File(dir, "before_" + name + "_top.png"));
                    ImageIO.write(after.top(minX, minZ, size, 2), "png", new File(dir, "after_" + name + "_top.png"));
                }
            }
            case "lakesurvey" -> survey(model, planner, args.length > 3 ? args[3] : "all", args.length > 4 ? Integer.parseInt(args[4]) : 2);
            case "lakeperf" -> perf(model, planner);
            default -> throw new IllegalArgumentException("unknown lake mode " + mode);
        }
    }

    // =========================================================================================
    // Finding lakes
    // =========================================================================================

    /** The {@code count} active lakes of a type nearest the origin (crater lakes: of volcanoes with water). */
    static List<Lake> find(TerrainModel model, int type, int count) {
        List<Lake> out = new ArrayList<>();
        if (type == LakeType.CRATER) {
            Set<Long> seen = new HashSet<>();
            for (int r = 0; r < 12 && out.size() < count; r++) {
                for (int a = -r; a <= r; a++) {
                    for (int b = -r; b <= r; b++) {
                        if (Math.max(Math.abs(a), Math.abs(b)) != r || out.size() >= count) {
                            continue;
                        }
                        TerrainModel.VolcanoSite v = model.nearestVolcano(a * 4200, b * 4200, 4200);
                        if (v == null || v.lava || v.waterLevel == ColumnSample.NO_WATER || !seen.add((long) v.x * 31 + (long) v.z)) {
                            continue;
                        }
                        out.add(new Lake(type, (int) v.x, (int) v.z, v.craterRadius, v.craterRadius + 12, (int) v.waterLevel));
                    }
                }
            }
            return out;
        }
        boolean great = type == LakeType.GREAT;
        int cell = great ? 6000 : 700;
        for (int r = 0; r < (great ? 6 : 30) && out.size() < count; r++) {
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != r || out.size() >= count) {
                        continue;
                    }
                    TerrainModel.LakeSite s = model.lakeSiteRaw(a, b, great);
                    if (s.active && s.type == type) {
                        out.add(new Lake(type, (int) s.x, (int) s.z, s.radius, s.ringRadius, (int) Math.max(model.seaLevel(), s.level)));
                    }
                }
            }
        }
        return out;
    }

    // =========================================================================================
    // Study of one lake
    // =========================================================================================

    /** Where the renders look from: diver and boat cameras, the section's row, the close-up's corner. */
    record Views(double[] dive, double[] boat, int sectionZ, int zoomX, int zoomZ, int lowest) {
    }

    /**
     * Studies one lake: the decorated area is built chunk by chunk, measured and checked, and its
     * views are chosen; then the undecorated lake ({@code before}) and the decorated one
     * ({@code after}) are rendered from the same cameras, so the pairs compare directly.
     *
     * @param open true for a stretch of open water (a great lake's shoal): the boat looks at the
     *     centre from the water instead of from the shore
     */
    static void study(LakePlanner planner, File dir, String name, Lake lake, String phase, int fixedSize, boolean open) throws Exception {
        // small lakes are shown whole; of a great lake, a stretch of shore and the water off it
        int size;
        int cx = lake.x;
        int cz = lake.z;
        if (fixedSize > 0) {
            size = (fixedSize + 15) / 16 * 16;
        } else if (lake.type != LakeType.GREAT) {
            // the warped shoreline reaches out to 1.42 radii
            size = Math.min(320, (int) Math.ceil((lake.radius * 2.9 + 32) / 16.0) * 16);
        } else {
            size = 256;
            int[] shore = shore(planner, lake);
            double dx = lake.x - shore[0];
            double dz = lake.z - shore[1];
            double l = Math.hypot(dx, dz);
            cx = (int) Math.round(shore[0] + dx / l * 72.0);
            cz = (int) Math.round(shore[1] + dz / l * 72.0);
        }
        int minX = Math.floorDiv(cx - size / 2, 16) * 16;
        int minZ = Math.floorDiv(cz - size / 2, 16) * 16;
        long t0 = System.nanoTime();
        LakeFloorArea after = new LakeFloorArea(planner, minX, minZ, size);
        long t1 = System.nanoTime();
        after.applyFloor(1.0, 1.0);
        long t2 = System.nanoTime();
        after.applyLife();
        long t3 = System.nanoTime();
        int chunks = (size / 16) * (size / 16);
        System.out.printf(Locale.ROOT, "ms per chunk: column plans %.2f, floor raster %.2f, plants %.2f%n", (t1 - t0) / 1.0e6 / chunks,
            (t2 - t1) / 1.0e6 / chunks, (t3 - t2) / 1.0e6 / chunks);
        stats(after);
        checks(after);
        seams(planner, after);
        Views views = views(planner, after, lake, open);
        // the fixed point: halfway from the centre to the shore, or the middle of a great lake's area
        int[] fix = lake.type == LakeType.GREAT || fixedSize > 0 ? new int[]{cx, cz} : fixedPoint(planner, lake);
        if (!phase.equals("after")) {
            LakeFloorArea before = new LakeFloorArea(planner, minX, minZ, size);
            render(before, dir, "before_" + name, lake, views, false);
            fixedViews(before, dir, "before_" + name, fix[0], fix[1]);
        }
        if (!phase.equals("before")) {
            render(after, dir, "after_" + name, lake, views, true);
            fixedViews(after, dir, "after_" + name, fix[0], fix[1]);
        }
        System.out.println("wrote " + name + " images (area " + minX + "," + minZ + " size " + size + ", diver at "
            + (int) views.dive[0] + " " + (int) views.dive[1] + " " + (int) views.dive[2] + ")");
    }

    /** Whether a boat at {@code (x, z)} floats on lake water whose top block is {@code surf}. */
    static boolean floats(LakePlanner planner, double x, double z, int surf) {
        LakeColumn c = new LakeColumn();
        planner.column((int) Math.floor(x), (int) Math.floor(z), c);
        return c.wet && c.surf == surf && c.depth >= 2;
    }

    /**
     * A point nobody chose for being interesting: halfway from the lake's centre to its radius,
     * on the first of eight bearings where the lake has water.
     */
    static int[] fixedPoint(LakePlanner planner, Lake lake) {
        LakeColumn c = new LakeColumn();
        for (int a = 0; a < 8; a++) {
            double angle = 0.3 + a * Math.PI / 4.0;
            int x = (int) Math.floor(lake.x + Math.cos(angle) * lake.radius * 0.5);
            int z = (int) Math.floor(lake.z + Math.sin(angle) * lake.radius * 0.5);
            planner.column(x, z, c);
            if (c.wet && c.depth >= 2) {
                return new int[]{x, z};
            }
        }
        return new int[]{lake.x, lake.z};
    }

    /**
     * The views from a fixed point: a diver five blocks over the floor (or in the top water block
     * of shallow water) looking east and looking south, and a boat just over the surface looking
     * east, written with a sheet of the three ({@code _fix_sheet}).
     */
    static void fixedViews(LakeFloorArea area, File dir, String name, int x, int z) throws Exception {
        LakeColumn c = area.column(x, z);
        if (!c.wet) {
            return;
        }
        int t = top(area, x, z);
        double camY = Math.min(c.surf + 0.5, t + 5.5);
        BufferedImage east = area.perspective(x + 0.5, camY, z + 0.5, 1.0, 0.0, -12.0, 640, 360);
        BufferedImage south = area.perspective(x + 0.5, camY, z + 0.5, 0.0, 1.0, -12.0, 640, 360);
        BufferedImage boat = area.surfaceView(x + 0.5, c.surf + 2.6, z + 0.5, 1.0, 0.0, -10.0, 640, 360);
        ImageIO.write(east, "png", new File(dir, name + "_fixdive.png"));
        ImageIO.write(boat, "png", new File(dir, name + "_fixboat.png"));
        sheet(new File(dir, name + "_fix_sheet.png"), east, south, boat);
    }

    /** Chooses the cameras on the decorated area. */
    static Views views(LakePlanner planner, LakeFloorArea area, Lake lake, boolean open) {
        int[] spot = viewSpot(area, lake, !open);
        double[] dive = diver(area, spot);
        double[] boat;
        if (open) {
            // from the water thirty blocks off the shoal's centre, looking at it
            double a = 0.7;
            double bx = lake.x + 0.5 - Math.cos(a) * 30.0;
            double bz = lake.z + 0.5 - Math.sin(a) * 30.0;
            boat = new double[]{bx, lake.level + 2.6, bz, Math.cos(a), Math.sin(a), -14.0};
        } else {
            // from a boat a few blocks off the shore, looking out over the lake and along its shore
            int[] shore = shore(planner, lake);
            double ox = lake.x - shore[0];
            double oz = lake.z - shore[1];
            double ol = Math.max(1e-6, Math.hypot(ox, oz));
            ox /= ol;
            oz /= ol;
            double lx = ox * Math.cos(1.0) - oz * Math.sin(1.0);
            double lz = ox * Math.sin(1.0) + oz * Math.cos(1.0);
            // seven blocks out from the shore and six back along the view, then farther out along
            // the lake-ward ray until the boat floats on the lake
            double bx = shore[0] + 0.5 + ox * 7.0 - lx * 6.0;
            double bz = shore[1] + 0.5 + oz * 7.0 - lz * 6.0;
            for (int i = 0; i < 200 && !floats(planner, bx, bz, lake.level - 1); i++) {
                bx += ox;
                bz += oz;
            }
            boat = new double[]{bx, lake.level + 2.6, bz, lx, lz, -16.0};
        }
        int lowest = Integer.MAX_VALUE;
        for (int x = area.minX; x < area.minX + area.size; x++) {
            lowest = Math.min(lowest, area.column(x, spot[1]).floor);
        }
        int zx = Math.max(area.minX, Math.min(area.minX + area.size - 90, spot[0] - 45));
        int zz = Math.max(area.minZ, Math.min(area.minZ + area.size - 90, spot[1] - 45));
        return new Views(dive, boat, spot[1], zx, zz, lowest);
    }

    /**
     * The diver's camera, as {x, y, z, dirX, dirZ, pitch}: in open water eight to fourteen blocks
     * back from the view spot, a few blocks above the floor, looking down at the spot.
     */
    static double[] diver(LakeFloorArea area, int[] spot) {
        double dl = Math.hypot(spot[2], spot[3]);
        int targetTop = top(area, spot[0], spot[1]);
        double[] best = null;
        // the spot's own direction first, then the others around it
        for (int turn = 0; turn < 8 && best == null; turn++) {
            double a = Math.atan2(spot[3] / dl, spot[2] / dl) + (turn % 2 == 0 ? 1 : -1) * ((turn + 1) / 2) * Math.PI / 4.0;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (double back = 14.0; back >= 8.0; back -= 1.0) {
                double camX = spot[0] + 0.5 - dx * back;
                double camZ = spot[1] + 0.5 - dz * back;
                int ix = (int) Math.floor(camX);
                int iz = (int) Math.floor(camZ);
                LakeColumn c = area.column(ix, iz);
                if (!c.wet || !area.in(ix, iz)) {
                    continue;
                }
                double camY = Math.min(c.surf - 0.4, Math.max(targetTop + 4.5, top(area, ix, iz) + 3.0));
                if (area.get(ix, (int) Math.floor(camY), iz) != LakeVolume.NAT_WATER
                    || !clearSight(area, camX, camY, camZ, spot[0] + 0.5, targetTop + 1.5, spot[1] + 0.5)) {
                    continue;
                }
                double pitch = Math.toDegrees(Math.atan2(targetTop + 1.0 - camY, back));
                best = new double[]{camX, camY, camZ, dx, dz, Math.max(-35.0, pitch)};
                break;
            }
        }
        if (best == null) {
            // over the spot itself, in the water: at most in the top water block (surf is that
            // block's index, so surf + 0.5 is its middle)
            LakeColumn sc = area.column(spot[0], spot[1]);
            double camY = Math.min(sc.surf + 0.5, targetTop + 2.6);
            while (camY < sc.surf + 0.5 && area.get(spot[0], (int) Math.floor(camY), spot[1]) != LakeVolume.NAT_WATER) {
                camY += 1.0;
            }
            best = new double[]{spot[0] + 0.5, camY, spot[1] + 0.5, spot[2], spot[3], -10.0};
        }
        return best;
    }

    /** True if nothing opaque stands between two points (plants do not count). */
    static boolean clearSight(LakeFloorArea area, double ax, double ay, double az, double bx, double by, double bz) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int steps = (int) Math.ceil(len * 2.0);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            byte code = area.get((int) Math.floor(ax + (bx - ax) * t), (int) Math.floor(ay + (by - ay) * t), (int) Math.floor(az + (bz - az) * t));
            if (LakeFloorArea.opaque(code)) {
                return false;
            }
        }
        return true;
    }

    /** Writes the top view, the views from the cameras, the section, the close-up and a sheet of them. */
    static void render(LakeFloorArea area, File dir, String name, Lake lake, Views views, boolean decorated) throws Exception {
        int minX = area.minX;
        int minZ = area.minZ;
        int size = area.size;
        ImageIO.write(area.top(minX, minZ, size, size <= 128 ? 4 : size <= 192 ? 3 : 2), "png", new File(dir, name + "_top.png"));
        double[] d = views.dive;
        BufferedImage dive = area.perspective(d[0], d[1], d[2], d[3], d[4], d[5], 640, 360);
        ImageIO.write(dive, "png", new File(dir, name + "_dive.png"));
        double[] b = views.boat;
        BufferedImage surface = area.surfaceView(b[0], b[1], b[2], b[3], b[4], b[5], 640, 360);
        ImageIO.write(surface, "png", new File(dir, name + "_surface.png"));
        ImageIO.write(area.section(minX, size, views.sectionZ, Math.max(views.lowest - 3, lake.level - 60), lake.level + 8, size <= 160 ? 4 : 3),
            "png", new File(dir, name + "_sec.png"));
        BufferedImage zoom = area.top(views.zoomX, views.zoomZ, Math.min(90, size), 4);
        ImageIO.write(zoom, "png", new File(dir, name + "_zoom.png"));
        sheet(new File(dir, name + "_sheet.png"), scaled(area.top(minX, minZ, size, 2), 360), zoom, dive, surface);
        if (decorated) {
            // a close-up of every discovery in the area
            int cell = LakePlanner.DISCOVERY_CELL;
            int k = 0;
            for (int dcx = Math.floorDiv(minX, cell); dcx <= Math.floorDiv(minX + size, cell); dcx++) {
                for (int dcz = Math.floorDiv(minZ, cell); dcz <= Math.floorDiv(minZ + size, cell); dcz++) {
                    LakePlanner.Discovery dd = area.planner.discovery(dcx, dcz);
                    if (dd == null || !area.in(dd.x(), dd.z())) {
                        continue;
                    }
                    int half = dd.reach() + 3;
                    BufferedImage iso = area.iso(dd.x() - half, dd.z() - half, 2 * half + 1, 9, dd.y() - 3, Math.max(dd.surf() + 3, dd.y() + 10));
                    ImageIO.write(iso, "png", new File(dir, name + "_" + LakePlanner.DISCOVERY_NAMES[dd.kind()] + "_" + k++ + ".png"));
                }
            }
        }
    }

    /** An image scaled to {@code n} pixels square, nearest neighbour. */
    static BufferedImage scaled(BufferedImage src, int n) {
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                out.setRGB(i, j, src.getRGB(i * src.getWidth() / n, j * src.getHeight() / n));
            }
        }
        return out;
    }

    /** The shoal of a great lake nearest its centre, or null. */
    static LakePlanner.Shoal nearestShoal(LakePlanner planner, Lake lake) {
        LakePlanner.Shoal best = null;
        double bestD = Double.MAX_VALUE;
        int cell = LakePlanner.SHOAL_CELL;
        int r = (int) Math.ceil(lake.radius * 1.4 / cell) + 1;
        for (int cx = Math.floorDiv(lake.x, cell) - r; cx <= Math.floorDiv(lake.x, cell) + r; cx++) {
            for (int cz = Math.floorDiv(lake.z, cell) - r; cz <= Math.floorDiv(lake.z, cell) + r; cz++) {
                LakePlanner.Shoal sh = planner.shoal(cx, cz);
                if (sh != null) {
                    double d = Math.hypot(sh.x() - lake.x, sh.z() - lake.z);
                    if (d < bestD) {
                        bestD = d;
                        best = sh;
                    }
                }
            }
        }
        return best;
    }

    /** A dry spot on the lake's shore, looking at the water: the first column out from the centre that is not lake water, on the ray with the most to see. */
    static int[] shore(LakePlanner planner, Lake lake) {
        LakeColumn c = new LakeColumn();
        int[] best = null;
        double bestR = Double.MAX_VALUE;
        for (int a = 0; a < 8; a++) {
            double dx = Math.cos(a * Math.PI / 4.0 + 0.3);
            double dz = Math.sin(a * Math.PI / 4.0 + 0.3);
            for (double r = lake.radius * 0.3; r < lake.ring + 40; r += 2.0) {
                int x = (int) Math.floor(lake.x + dx * r);
                int z = (int) Math.floor(lake.z + dz * r);
                planner.column(x, z, c);
                if (!c.wet) {
                    if (r < bestR) {
                        bestR = r;
                        best = new int[]{x, z};
                    }
                    break;
                }
            }
        }
        return best != null ? best : new int[]{lake.x, lake.z};
    }

    /** Top of the solid blocks of a column in the assembled area. */
    static int top(LakeFloorArea area, int x, int z) {
        int top = area.column(x, z).floor;
        while (LakeVolume.isSolid(area.get(x, top + 1, z))) {
            top++;
        }
        return top;
    }

    private static boolean interesting(byte code) {
        return LakeVolume.isPlanned(code) && code != LakeVolume.FILL && code != LakeVolume.SAND && code != LakeVolume.GRAVEL && code != LakeVolume.CLAY
            && code != LakeVolume.MUD && code != LakeVolume.LILY_PAD;
    }

    /**
     * What the diver looks at, as {x, z, dirX, dirZ}: a discovery if the area holds one, seen from
     * the open water on one side of it, else the spot of open water three to fourteen blocks deep
     * with the most to see around it, looking towards the bulk of it.
     */
    static int[] viewSpot(LakeFloorArea area, Lake lake, boolean discoveries) {
        int cell = LakePlanner.DISCOVERY_CELL;
        for (int cx = Math.floorDiv(area.minX, cell); cx <= Math.floorDiv(area.minX + area.size, cell) && discoveries; cx++) {
            for (int cz = Math.floorDiv(area.minZ, cell); cz <= Math.floorDiv(area.minZ + area.size, cell); cz++) {
                LakePlanner.Discovery d = area.planner.discovery(cx, cz);
                if (d == null || !area.in(d.x(), d.z())) {
                    continue;
                }
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4.0;
                    int x = (int) Math.round(d.x() + Math.cos(a) * (d.reach() + 2));
                    int z = (int) Math.round(d.z() + Math.sin(a) * (d.reach() + 2));
                    LakeColumn c = area.column(x, z);
                    if (c.wet && area.in(x, z) && c.surf - top(area, x, z) >= 3) {
                        // look at the discovery from the open water on this side of it
                        return new int[]{d.x(), d.z(), (int) Math.round(-Math.cos(a) * 100), (int) Math.round(-Math.sin(a) * 100)};
                    }
                }
            }
        }
        double bestScore = -1;
        int[] best = {lake.x, lake.z, 100, 0};
        for (int x = area.minX + 10; x < area.minX + area.size - 10; x += 4) {
            for (int z = area.minZ + 10; z < area.minZ + area.size - 10; z += 4) {
                LakeColumn c = area.column(x, z);
                int t = top(area, x, z);
                int water = c.surf - t;
                if (!c.wet || water < 3 || water > 14 || area.get(x, t + 1, z) != LakeVolume.NAT_WATER || area.get(x, t + 2, z) != LakeVolume.NAT_WATER) {
                    continue;
                }
                double score = 0.0;
                double sx = 0.0;
                double sz = 0.0;
                for (int dx = -9; dx <= 9; dx += 2) {
                    for (int dz = -9; dz <= 9; dz += 2) {
                        int n = 0;
                        for (int y = t - 2; y <= t + 5; y++) {
                            if (interesting(area.get(x + dx, y, z + dz))) {
                                n++;
                            }
                        }
                        score += n;
                        sx += n * dx;
                        sz += n * dz;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    double l = Math.hypot(sx, sz);
                    best = l < 1e-6 ? new int[]{x, z, 100, 0} : new int[]{x, z, (int) Math.round(sx / l * 100), (int) Math.round(sz / l * 100)};
                }
            }
        }
        if (best[2] == 0 && best[3] == 0) {
            best[2] = 100;
        }
        return best;
    }

    private static void sheet(File out, BufferedImage... images) throws Exception {
        int w = 0;
        int h = 0;
        for (BufferedImage i : images) {
            w += i.getWidth();
            h = Math.max(h, i.getHeight());
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int x = 0;
        for (BufferedImage i : images) {
            g.drawImage(i, x, 0, null);
            x += i.getWidth();
        }
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    // =========================================================================================
    // Statistics and checks
    // =========================================================================================

    static void stats(LakeFloorArea area) {
        int[] counts = new int[256];
        for (Map.Entry<Long, Byte> e : area.cells().entrySet()) {
            counts[e.getValue() & 0xFF]++;
        }
        int wet = 0;
        int shelf = 0;
        int aboveWater = 0;
        for (int x = area.minX; x < area.minX + area.size; x++) {
            for (int z = area.minZ; z < area.minZ + area.size; z++) {
                LakeColumn c = area.column(x, z);
                if (c.wet) {
                    wet++;
                    if (c.bed > c.floor && c.fill == LakeVolume.FILL) {
                        shelf++;
                    }
                    if (LakeVolume.isSolid(area.get(x, c.surf + 1, z))) {
                        aboveWater++;
                    }
                }
            }
        }
        int rock = 0;
        for (int i = 0; i < 15; i++) {
            rock += counts[LakeVolume.ROCK + i];
        }
        int logs = 0;
        for (int code = LakeVolume.LOG_X; code <= LakeVolume.STRIPPED_Y; code++) {
            logs += counts[code];
        }
        int boat = counts[LakeVolume.PLANKS] + counts[LakeVolume.SLAB] + counts[LakeVolume.SLAB_TOP] + counts[LakeVolume.FENCE];
        for (int i = 0; i < 8; i++) {
            boat += counts[LakeVolume.STAIRS + i];
        }
        int stone = counts[LakeVolume.STONE_BRICKS] + counts[LakeVolume.MOSSY_BRICKS] + counts[LakeVolume.CRACKED_BRICKS] + counts[LakeVolume.BRICK_SLAB];
        System.out.printf(Locale.ROOT, "lake columns %d (%d on shelves), columns rising out of the water %d%n", wet, shelf, aboveWater);
        System.out.printf(Locale.ROOT, "bed patches: sand %d gravel %d clay %d mud %d moss %d; fill %d; rock %d, mossy cobble %d, erratics %d, pebbles/rocks %d%n",
            counts[LakeVolume.SAND], counts[LakeVolume.GRAVEL], counts[LakeVolume.CLAY], counts[LakeVolume.MUD], counts[LakeVolume.MOSS],
            counts[LakeVolume.FILL], rock, counts[LakeVolume.MOSSY_COBBLE], counts[LakeVolume.GRANITE] + counts[LakeVolume.DIORITE],
            sum(counts, LakeVolume.PEBBLES, 20));
        System.out.printf(Locale.ROOT, "plants: seagrass %d, tall seagrass %d, weed (kelp) %d, lily pads %d, reeds %d, dripleaf %d small %d big, moss carpets %d%n",
            counts[LakeVolume.SEAGRASS], counts[LakeVolume.TALL_LOWER], counts[LakeVolume.KELP_PLANT] + counts[LakeVolume.KELP_TOP] + counts[LakeVolume.KELP_GROWING],
            counts[LakeVolume.LILY_PAD], counts[LakeVolume.REED], counts[LakeVolume.SMALL_DRIP_LOWER], counts[LakeVolume.BIG_DRIP_HEAD], counts[LakeVolume.MOSS_CARPET]);
        System.out.printf(Locale.ROOT, "wood: log blocks %d, boat/pier boards %d, chests %d; stonework %d%n", logs, boat, sum(counts, LakeVolume.CHEST, 4), stone);
        // the discoveries in the area
        LakePlanner p = area.planner;
        int cell = LakePlanner.DISCOVERY_CELL;
        for (int cx = Math.floorDiv(area.minX, cell); cx <= Math.floorDiv(area.minX + area.size, cell); cx++) {
            for (int cz = Math.floorDiv(area.minZ, cell); cz <= Math.floorDiv(area.minZ + area.size, cell); cz++) {
                LakePlanner.Discovery d = p.discovery(cx, cz);
                if (d != null && area.in(d.x(), d.z())) {
                    System.out.printf(Locale.ROOT, "  discovery: %s at %d %d %d (water %d)%n", LakePlanner.DISCOVERY_NAMES[d.kind()], d.x(), d.y(), d.z(), d.surf() - d.y());
                }
            }
        }
    }

    private static int sum(int[] counts, int from, int n) {
        int s = 0;
        for (int i = 0; i < n; i++) {
            s += counts[from + i];
        }
        return s;
    }

    /** Waterloggable thin codes: they keep the water they stand in. */
    private static boolean waterloggable(byte code) {
        return code >= LakeVolume.PEBBLES && code < LakeVolume.ROCK_DECOR + 10 || code >= LakeVolume.SEAGRASS && code <= LakeVolume.KELP_GROWING
            || code >= LakeVolume.SMALL_DRIP_LOWER && code <= LakeVolume.BIG_DRIP_HEAD || code == LakeVolume.FENCE;
    }

    /**
     * Invariants: every planned solid rests on a solid block; every plant on what it needs; in a
     * lake's water only solid blocks and waterloggable ones; lily pads on water, reeds and moss
     * carpets above it; the floor only replaced by solid blocks.
     */
    static void checks(LakeFloorArea area) {
        int floating = 0;
        int unsupported = 0;
        int leaks = 0;
        int floorBad = 0;
        for (Map.Entry<Long, Byte> e : area.cells().entrySet()) {
            int[] p = LakeFloorArea.unkey(e.getKey());
            int x = p[0];
            int y = p[1];
            int z = p[2];
            byte code = e.getValue();
            if (!LakeVolume.isPlanned(code)) {
                continue;
            }
            LakeColumn c = area.column(x, z);
            byte below = area.get(x, y - 1, z);
            if (y <= c.floor) {
                if (!LakeVolume.isSolid(code)) {
                    floorBad++;
                    if (floorBad <= 5) {
                        System.out.println("  floor replaced by non-solid code " + code + " at " + x + " " + y + " " + z);
                    }
                }
                continue;
            }
            if (LakeVolume.isSolid(code)) {
                if (!LakeVolume.isSolid(below)) {
                    if (floating++ < 5) {
                        System.out.println("  floating code " + code + " at " + x + " " + y + " " + z + " on " + below);
                    }
                }
                continue;
            }
            boolean held = switch (code) {
                case LakeVolume.TALL_UPPER -> below == LakeVolume.TALL_LOWER;
                case LakeVolume.KELP_PLANT, LakeVolume.KELP_TOP, LakeVolume.KELP_GROWING -> below == LakeVolume.KELP_PLANT || LakeVolume.isSolid(below);
                case LakeVolume.SMALL_DRIP_UPPER -> below == LakeVolume.SMALL_DRIP_LOWER;
                case LakeVolume.BIG_DRIP_STEM -> below == LakeVolume.BIG_DRIP_STEM || LakeVolume.isSolid(below);
                case LakeVolume.BIG_DRIP_HEAD -> below == LakeVolume.BIG_DRIP_STEM;
                case LakeVolume.REED -> below == LakeVolume.REED || LakeVolume.isSolid(below);
                case LakeVolume.LILY_PAD -> below == LakeVolume.NAT_WATER || waterloggable(below) && below != LakeVolume.FENCE && y - 1 <= c.surf;
                default -> LakeVolume.isSolid(below);
            };
            if (!held && unsupported++ < 5) {
                System.out.println("  unsupported code " + code + " at " + x + " " + y + " " + z + " on " + below);
            }
            boolean inWater = c.wet && y <= c.surf;
            if (inWater && !waterloggable(code)) {
                if (leaks++ < 5) {
                    System.out.println("  code " + code + " in the water at " + x + " " + y + " " + z + " would not hold it");
                }
            }
            if ((code == LakeVolume.REED || code == LakeVolume.MOSS_CARPET || code == LakeVolume.LILY_PAD)) {
                // nothing that cannot hold water beside lake water either
                for (int d = 0; d < 4; d++) {
                    int nx = x + LakeVolume.DIR_X[d];
                    int nz = z + LakeVolume.DIR_Z[d];
                    if (area.get(nx, y, nz) == LakeVolume.NAT_WATER) {
                        if (leaks++ < 5) {
                            System.out.println("  code " + code + " beside lake water at " + x + " " + y + " " + z);
                        }
                        break;
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "CHECK floating blocks %d, unsupported plants %d, blocks in or beside the water that do not hold it %d, floor replaced by non-solids %d, refused by the world rules %d -> %s%n",
            floating, unsupported, leaks, floorBad, area.refused, floating == 0 && unsupported == 0 && leaks == 0 && floorBad == 0 ? "PASS" : "FAIL");
    }

    /** Builds the whole area as one volume and compares it with the chunk-by-chunk assembly. */
    static void seams(LakePlanner planner, LakeFloorArea area) {
        LakeFloorArea ref = new LakeFloorArea(planner, area.minX, area.minZ, area.size);
        ref.whole = true;
        ref.applyFloor(1.0, 1.0);
        ref.applyLife();
        int diff = 0;
        Set<Long> keys = new HashSet<>(area.cells().keySet());
        keys.addAll(ref.cells().keySet());
        for (long k : keys) {
            int[] p = LakeFloorArea.unkey(k);
            if (area.get(p[0], p[1], p[2]) != ref.get(p[0], p[1], p[2])) {
                if (diff++ < 5) {
                    System.out.println("  seam: " + p[0] + " " + p[1] + " " + p[2] + " chunks " + area.get(p[0], p[1], p[2]) + " whole " + ref.get(p[0], p[1], p[2]));
                }
            }
        }
        System.out.printf(Locale.ROOT, "CHECK seams: %d cells differ between chunk-by-chunk and whole-area rasters -> %s%n", diff, diff == 0 ? "PASS" : "FAIL");
    }

    // =========================================================================================
    // Survey
    // =========================================================================================

    /**
     * How full the lake floors are: for the {@code count} lakes of each type nearest the origin
     * (the deep middle of a great lake, 384 blocks across; smaller lakes whole), the share of lake
     * columns, in four bands of depth, that carry a plant, and that carry an object (rock, log,
     * ruin); the share with nothing within eight blocks; and the plant counts.
     */
    static void survey(TerrainModel model, LakePlanner planner, String which, int count) {
        for (int type = 1; type < TYPE_KEYS.length; type++) {
            if (!which.equals("all") && !List.of(which.split(",")).contains(TYPE_KEYS[type])) {
                continue;
            }
            for (Lake l : find(model, type, count)) {
                int size = type == LakeType.GREAT ? 384 : Math.min(320, (int) Math.ceil((l.radius * 2.9 + 32) / 16.0) * 16);
                int minX = Math.floorDiv(l.x - size / 2, 16) * 16;
                int minZ = Math.floorDiv(l.z - size / 2, 16) * 16;
                LakeFloorArea a = new LakeFloorArea(planner, minX, minZ, size);
                a.applyFloor(1.0, 1.0);
                a.applyLife();
                surveyArea(planner, TYPE_KEYS[type], l, a);
            }
        }
    }

    /** Plants, and the moss growing on the bed (moss blocks: aquatic moss in deep clear water, moss in humid shallows). */
    private static boolean plant(byte b) {
        return b >= LakeVolume.SEAGRASS && b <= LakeVolume.MOSS_CARPET && b != LakeVolume.REED || b == LakeVolume.MOSS;
    }

    private static void surveyArea(LakePlanner planner, String type, Lake l, LakeFloorArea a) {
        int n = a.size;
        // per column: 0 nothing, 1 the bed's own top changed, 2 an object on it, 3 a plant
        int[] what = new int[n * n];
        int[] species = new int[4];
        for (Map.Entry<Long, Byte> e : a.cells().entrySet()) {
            int[] p = LakeFloorArea.unkey(e.getKey());
            if (!a.in(p[0], p[2])) {
                continue;
            }
            int i = (p[0] - a.minX) * n + (p[2] - a.minZ);
            byte b = e.getValue();
            int k = plant(b) ? 3 : p[1] > a.column(p[0], p[2]).floor ? 2 : 1;
            what[i] = Math.max(what[i], k);
            if (b == LakeVolume.SEAGRASS || b == LakeVolume.TALL_LOWER) {
                species[0]++;
            } else if (b == LakeVolume.KELP_TOP || b == LakeVolume.KELP_GROWING) {
                species[1]++;
            } else if (b == LakeVolume.LILY_PAD) {
                species[2]++;
            } else if (b == LakeVolume.REED) {
                species[3]++;
            }
        }
        int[] wet = new int[4];
        int[] plants = new int[4];
        int[] objects = new int[4];
        int[] ps = new int[(n + 1) * (n + 1)];
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                ps[(x + 1) * (n + 1) + z + 1] = (what[x * n + z] >= 2 ? 1 : 0) + ps[x * (n + 1) + z + 1] + ps[(x + 1) * (n + 1) + z] - ps[x * (n + 1) + z];
            }
        }
        int total = 0;
        int empty = 0;
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                LakeColumn c = a.column(a.minX + x, a.minZ + z);
                if (!c.wet) {
                    continue;
                }
                total++;
                int d = c.surf - c.floor;
                int band = d <= 3 ? 0 : d <= 9 ? 1 : d <= 20 ? 2 : 3;
                wet[band]++;
                int w = what[x * n + z];
                plants[band] += w == 3 ? 1 : 0;
                objects[band] += w >= 2 ? 1 : 0;
                int x0 = Math.max(0, x - 8);
                int x1 = Math.min(n, x + 9);
                int z0 = Math.max(0, z - 8);
                int z1 = Math.min(n, z + 9);
                if (ps[x1 * (n + 1) + z1] - ps[x0 * (n + 1) + z1] - ps[x1 * (n + 1) + z0] + ps[x0 * (n + 1) + z0] == 0) {
                    empty++;
                }
            }
        }
        LakeColumn centre = new LakeColumn();
        planner.column(l.x, l.z, centre);
        StringBuilder sb = new StringBuilder();
        String[] bands = {"1-3", "4-9", "10-20", "21+"};
        for (int b = 0; b < 4; b++) {
            sb.append(String.format(Locale.ROOT, " %s: %s/%s", bands[b], pct(plants[b], wet[b]), pct(objects[b], wet[b])));
        }
        System.out.printf(Locale.ROOT, "%-8s at %6d %6d r %3.0f T %5.2f | wet %6d | plants/objects %%%s | empty %4.1f%% | seagrass %d, weed %d, lily pads %d, reeds %d%n",
            type, l.x, l.z, l.radius, centre.temperature, total, sb, 100.0 * empty / Math.max(1, total), species[0], species[1], species[2], species[3]);
    }

    private static String pct(int a, int b) {
        return b == 0 ? "-" : String.format(Locale.ROOT, "%.1f", 100.0 * a / b);
    }

    // =========================================================================================
    // Cost
    // =========================================================================================

    /**
     * Cost per chunk. In the game the surface water feature has sampled the model corners of every
     * chunk and its margin on the same worker thread before the lake features run, so the corners
     * are warmed the same way here first (untimed). Then: the quick test every chunk runs, on random
     * chunks; and on chunks inside lakes of every type, the column plans, the floor raster and the
     * plant decisions, once with the planner's caches cold (each chunk planned for the first time,
     * as in the game) and once warm.
     */
    static void perf(TerrainModel model, LakePlanner planner) {
        List<int[]> chunks = new ArrayList<>();
        for (int type = 1; type < TYPE_KEYS.length; type++) {
            for (Lake l : find(model, type, 3)) {
                for (int i = 0; i < 40; i++) {
                    double a = i * 2.39996;
                    double r = Math.sqrt((i + 0.5) / 40.0) * l.radius * 0.9;
                    chunks.add(new int[]{Math.floorDiv((int) (l.x + Math.cos(a) * r), 16) * 16, Math.floorDiv((int) (l.z + Math.sin(a) * r), 16) * 16});
                }
            }
        }
        java.util.Random random = new java.util.Random(11);
        double quickCold = 0.0;
        double quickWarm = 0.0;
        int hits = 0;
        int tests = 2000;
        for (int i = 0; i < tests; i++) {
            int cx = (random.nextInt(4000) - 2000) * 16;
            int cz = (random.nextInt(4000) - 2000) * 16;
            long t0 = System.nanoTime();
            planner.mayHaveLake(cx, cz, 1);
            long t1 = System.nanoTime();
            warm(model, cx, cz);
            long t2 = System.nanoTime();
            hits += planner.mayHaveLake(cx, cz, 0) ? 1 : 0;
            hits += planner.mayHaveLake(cx, cz, 1) ? 1 : 0;
            quickCold += t1 - t0;
            quickWarm += System.nanoTime() - t2;
        }
        LakeLifeRaster.Plant plant = new LakeLifeRaster.Plant();
        for (int pass = 0; pass < 2; pass++) {
            double plans = 0.0;
            double floor = 0.0;
            double life = 0.0;
            long[] blocks = new long[TYPE_KEYS.length];
            int[] most = new int[TYPE_KEYS.length];
            int[] chunksOf = new int[TYPE_KEYS.length];
            for (int[] ch : chunks) {
                warm(model, ch[0], ch[1]);
                long t0 = System.nanoTime();
                LakeVolume v = new LakeVolume(planner, ch[0], ch[1], 16, 16);
                for (int x = ch[0]; x < ch[0] + 16; x++) {
                    for (int z = ch[1]; z < ch[1] + 16; z++) {
                        v.column(x, z);
                    }
                }
                long t1 = System.nanoTime();
                LakeRaster.build(v, 1.0, 1.0);
                long t2 = System.nanoTime();
                LakeColumn c = new LakeColumn();
                for (int x = ch[0]; x < ch[0] + 16; x++) {
                    for (int z = ch[1]; z < ch[1] + 16; z++) {
                        planner.column(x, z, c);
                        if (c.wet) {
                            LakeLifeRaster.decide(planner, c, c.bed, LakeLifeRaster.naturalBed(planner, c), plant);
                        }
                    }
                }
                long t3 = System.nanoTime();
                plans += t1 - t0;
                floor += t2 - t1;
                life += t3 - t2;
                if (pass == 0) {
                    // the blocks the floor feature writes in this chunk (each costs two reads and a write in the world)
                    int written = 0;
                    for (int x = ch[0]; x < ch[0] + 16; x++) {
                        for (int z = ch[1]; z < ch[1] + 16; z++) {
                            for (int y = v.storedMin(x, z); y < v.storedMax(x, z); y++) {
                                written += LakeVolume.isPlanned(v.get(x, y, z)) ? 1 : 0;
                            }
                        }
                    }
                    planner.column(ch[0] + 8, ch[1] + 8, c);
                    int type = c.type;
                    blocks[type] += written;
                    most[type] = Math.max(most[type], written);
                    chunksOf[type]++;
                }
            }
            int n = chunks.size();
            System.out.printf(Locale.ROOT, "%d lake chunks, planner caches %s: column plans %.2f ms, floor raster %.2f ms, plant decisions %.2f ms, total %.2f ms per chunk (planning only)%n",
                n, pass == 0 ? "cold" : "warm", plans / n / 1.0e6, floor / n / 1.0e6, life / n / 1.0e6, (plans + floor + life) / n / 1.0e6);
            if (pass == 0) {
                for (int type = 0; type < TYPE_KEYS.length; type++) {
                    if (chunksOf[type] > 0) {
                        System.out.printf(Locale.ROOT, "  floor blocks written per %s chunk: mean %d, most %d (%d chunks)%n", TYPE_KEYS[type], blocks[type] / chunksOf[type],
                            most[type], chunksOf[type]);
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "quick lake test (both features) on %d random chunks: %.3f ms per chunk with the model corners warm as in the game, %.3f ms cold; %d of %d tests pass%n",
            tests, quickWarm / tests / 1.0e6, quickCold / tests / 1.0e6, hits, 2 * tests);
    }

    /** Samples the model corners the surface water solve samples for a chunk (its window of five columns around it). */
    private static void warm(TerrainModel model, int minX, int minZ) {
        for (int x = (minX - 5) & ~3; x <= minX + 20 + 4; x += 4) {
            for (int z = (minZ - 5) & ~3; z <= minZ + 20 + 4; z += 4) {
                model.sample(x, z);
            }
        }
    }
}
