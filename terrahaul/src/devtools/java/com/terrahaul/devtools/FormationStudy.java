package com.terrahaul.devtools;

import com.terrahaul.worldgen.features.FormationShapes;
import com.terrahaul.worldgen.surface.Fades;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.Geology;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Development tool: plans the rock formations of real formation sites with the very shape code
 * the game uses ({@link FormationShapes}), voxelises them on the model terrain and
 * <ul>
 *   <li>renders an isometric view, a cross-section, a top view and two side elevations of each,
 *       coloured with the blocks' average texture colours, into {@code <outDir>/<type>_<i>.png};</li>
 *   <li>checks that no block floats: a 6-connected flood fill from the terrain must reach every
 *       formation block;</li>
 *   <li>measures the tops ({@link #summits}): how many end in a point, how many sit as a wide cap
 *       on a narrow neck, and how long their single-block tips are;</li>
 *   <li>with mode {@code check}, does the flood-fill check and the top measurements (no images)
 *       for many sites of every type and prints the totals.</li>
 * </ul>
 * Usage: {@code FormationStudy <seed> <outDir> [arch|hoodoo|spire|sea_stack|check] [count=4] [scale=5]},
 * where {@code scale} is the size of a block in pixels in the views.
 * The terrain is the model's heightmap (no 3D detail, caves or water), so this judges shapes and
 * connectivity, not their exact fit into the generated ground.
 */
public final class FormationStudy {
    private static final int EMPTY = 0;
    private static final int TERRAIN = 1;
    /** Grid codes from here on are formation materials: code - FORMATION. */
    private static final int FORMATION = 2;
    /** Grid code of formation blocks found floating (drawn red). */
    private static final int FLOATING = 60;

    private FormationStudy() {
    }

    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File dir = new File(args[1]);
        dir.mkdirs();
        String mode = args.length > 2 ? args[2] : "arch";
        int count = args.length > 3 ? Integer.parseInt(args[3]) : 4;
        int scale = args.length > 4 ? Integer.parseInt(args[4]) : 5;
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        if (mode.equals("check")) {
            for (int type : new int[]{TerrainModel.ARCH, TerrainModel.HOODOO, TerrainModel.SPIRE, TerrainModel.SEA_STACK}) {
                check(model, type, count);
            }
            return;
        }
        int type = switch (mode) {
            case "hoodoo" -> TerrainModel.HOODOO;
            case "spire" -> TerrainModel.SPIRE;
            case "sea_stack" -> TerrainModel.SEA_STACK;
            default -> TerrainModel.ARCH;
        };
        List<TerrainModel.FormationSite> sites = sites(model, type, count);
        List<BufferedImage> tiles = new ArrayList<>();
        for (int i = 0; i < sites.size(); i++) {
            TerrainModel.FormationSite site = sites.get(i);
            long t0 = System.nanoTime();
            Voxels v = voxelise(model, site);
            long t1 = System.nanoTime();
            int floating = floating(v);
            Summits tops = summits(v);
            System.out.printf(Locale.ROOT, "%s %d at %d %d (ground %.0f, %s, climate %d): %s; %d formation blocks, %d floating, planned in %.0f ms, voxelised in %.0f ms%n",
                TerrainModel.formationName(type), i, site.x, site.z, site.ground, Geology.name(site.geology), site.climate,
                v.description, v.formationBlocks, floating, v.planMillis, (t1 - t0) / 1e6 - v.planMillis);
            System.out.println("    tops: " + tops);
            for (String neck : tops.necks) {
                System.out.println("    top on a neck at " + neck);
            }
            for (String blunt : tops.blunt) {
                System.out.println("    blunt top at " + blunt);
            }
            BufferedImage iso = isometric(v, scale);
            BufferedImage section = section(v, scale);
            BufferedImage top = topView(v, Math.max(1, scale - 1));
            BufferedImage sideX = elevation(v, Math.max(1, scale - 1), false);
            BufferedImage sideZ = elevation(v, Math.max(1, scale - 1), true);
            int upper = Math.max(iso.getHeight(), section.getHeight() + top.getHeight());
            int w = Math.max(iso.getWidth() + Math.max(section.getWidth(), top.getWidth()), sideX.getWidth() + sideZ.getWidth() + 8);
            int h = upper + Math.max(sideX.getHeight(), sideZ.getHeight());
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(200, 215, 230));
            g.fillRect(0, 0, w, h);
            g.drawImage(iso, 0, 0, null);
            g.drawImage(section, iso.getWidth(), 0, null);
            g.drawImage(top, iso.getWidth(), section.getHeight(), null);
            g.drawImage(sideX, 0, upper, null);
            g.drawImage(sideZ, sideX.getWidth() + 8, upper, null);
            g.setColor(Color.BLACK);
            g.drawString(String.format(Locale.ROOT, "%s %d  %s  floating=%d", TerrainModel.formationName(type), i, v.description, floating), 6, 14);
            g.drawString("tops: " + tops, 6, 28);
            g.dispose();
            ImageIO.write(out, "png", new File(dir, TerrainModel.formationName(type) + "_" + i + ".png"));
            tiles.add(iso);
        }
        ImageIO.write(sheet(tiles), "png", new File(dir, TerrainModel.formationName(type) + "_sheet.png"));
        System.out.println("wrote " + dir);
    }

    /** Formation sites of one type, nearest to the origin first (rings of formation cells). */
    static List<TerrainModel.FormationSite> sites(TerrainModel model, int type, int count) {
        List<TerrainModel.FormationSite> out = new ArrayList<>();
        for (int r = 0; r < 400 && out.size() < count; r++) {
            for (int cx = -r; cx <= r && out.size() < count; cx++) {
                for (int cz = -r; cz <= r && out.size() < count; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r) {
                        continue;
                    }
                    TerrainModel.FormationSite f = model.formationSite(cx, cz);
                    // the model's site cache can hand out the site of the point-mirrored cell
                    // (see SeedMixer.hash2); count each site once, from its own cell
                    if (f != null && f.type == type && Math.floorDiv(f.x, TerrainModel.FORMATION_CELL) == cx
                        && Math.floorDiv(f.z, TerrainModel.FORMATION_CELL) == cz) {
                        out.add(f);
                    }
                }
            }
        }
        return out;
    }

    private static void check(TerrainModel model, int type, int count) {
        List<TerrainModel.FormationSite> sites = sites(model, type, count);
        long blocks = 0;
        long floating = 0;
        long dropped = 0;
        int bad = 0;
        int empty = 0;
        Summits tops = new Summits();
        long t0 = System.nanoTime();
        for (TerrainModel.FormationSite site : sites) {
            Voxels v = voxelise(model, site);
            int f = floating(v);
            blocks += v.formationBlocks;
            floating += f;
            dropped += v.dropped;
            Summits s = summits(v);
            tops.add(s);
            if (f > 0) {
                bad++;
                System.out.printf(Locale.ROOT, "  %s at %d %d: %d of %d blocks float (%s)%n", TerrainModel.formationName(type), site.x, site.z, f, v.formationBlocks, v.description);
            }
            if (s.capped > 0) {
                System.out.printf(Locale.ROOT, "  %s at %d %d: %d of %d tops sit on a neck (%s)%n", TerrainModel.formationName(type), site.x, site.z, s.capped, s.count, v.description);
                for (String neck : s.necks) {
                    System.out.println("    top on a neck at " + neck);
                }
            }
            if (v.formationBlocks == 0) {
                empty++;
            }
        }
        System.out.printf(Locale.ROOT, "%s: %d sites, %d blocks, %d floating blocks in %d formations, %d empty, %d blocks dropped by the plans, %.1f s%n",
            TerrainModel.formationName(type), sites.size(), blocks, floating, bad, empty, dropped, (System.nanoTime() - t0) / 1e9);
        System.out.println("    tops: " + tops);
    }

    // =========================================================================================
    // tops
    // =========================================================================================

    /** Tops at least this many blocks above the ground are measured; lower ones are bumps on a fin or the talus. */
    private static final int SUMMIT_MIN = 6;

    /** Statistics of the tops of one or more formations (see {@link #summits}). */
    static final class Summits {
        int count;
        int pointed;
        int capped;
        int widestTop;
        int needleSum;
        int needleMax;
        /** Tops whose single-block tip is 4 or more layers long: a rod rather than a point. */
        int rods;
        /** Position and area profile (top layer first) of the first few tops on a neck. */
        final List<String> necks = new ArrayList<>();
        /** The same for the first few tops that are not pointed. */
        final List<String> blunt = new ArrayList<>();

        void add(Summits o) {
            count += o.count;
            pointed += o.pointed;
            capped += o.capped;
            widestTop = Math.max(widestTop, o.widestTop);
            needleSum += o.needleSum;
            needleMax = Math.max(needleMax, o.needleMax);
            rods += o.rods;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d tops, %d pointed, %d on a neck (cap), widest top layer %d blocks, single-block tip %.1f layers on average (max %d, %d of 4 or more)",
                count, pointed, capped, widestTop, count == 0 ? 0.0 : needleSum / (double) count, needleMax, rods);
        }
    }

    /**
     * Measures the tops of a voxelised formation. Every horizontal patch of formation blocks
     * (4-connected within its layer) with no formation block above it is a top; from each top at
     * least {@link #SUMMIT_MIN} blocks above the ground the patches under it are followed down
     * layer by layer, which gives the area profile of the top:
     * <ul>
     *   <li>pointed: the top layer has at most 2 blocks and the one under it at most 6;</li>
     *   <li>on a neck: within the upper 40% of the top's height (at most 10 layers), a layer is
     *       under 60% of, and at least 5 blocks narrower than, a layer above it: a wide cap on a
     *       narrow neck, the mushroom shape;</li>
     *   <li>single-block tip: how many layers at the very top hold one block.</li>
     * </ul>
     */
    static Summits summits(Voxels v) {
        int plane = v.sx * v.sz;
        int[] label = new int[v.grid.length];
        int[] area = new int[1024];
        int[] layer = new int[1024];
        int[] stack = new int[plane];
        int next = 1;
        for (int j = 0; j < v.sy; j++) {
            for (int c = 0; c < plane; c++) {
                int idx = j * plane + c;
                if (v.grid[idx] < FORMATION || label[idx] != 0) {
                    continue;
                }
                if (next == area.length) {
                    area = Arrays.copyOf(area, next * 2);
                    layer = Arrays.copyOf(layer, next * 2);
                }
                int sp = 0;
                stack[sp++] = c;
                label[idx] = next;
                int a = 0;
                while (sp > 0) {
                    int cc = stack[--sp];
                    a++;
                    int i = cc % v.sx;
                    int k = cc / v.sx;
                    int[] around = {i > 0 ? cc - 1 : -1, i + 1 < v.sx ? cc + 1 : -1, k > 0 ? cc - v.sx : -1, k + 1 < v.sz ? cc + v.sx : -1};
                    for (int n : around) {
                        if (n >= 0 && v.grid[j * plane + n] >= FORMATION && label[j * plane + n] == 0) {
                            label[j * plane + n] = next;
                            stack[sp++] = n;
                        }
                    }
                }
                area[next] = a;
                layer[next] = j;
                next++;
            }
        }
        // the cells of every patch, grouped by label
        boolean[] covered = new boolean[next];
        int[] start = new int[next + 1];
        for (int idx = 0; idx < label.length; idx++) {
            if (label[idx] != 0) {
                start[label[idx] + 1]++;
                if (idx + plane < label.length && v.grid[idx + plane] >= FORMATION) {
                    covered[label[idx]] = true;
                }
            }
        }
        for (int l = 0; l < next; l++) {
            start[l + 1] += start[l];
        }
        int[] fill = Arrays.copyOf(start, next);
        int[] cells = new int[start[next]];
        for (int idx = 0; idx < label.length; idx++) {
            if (label[idx] != 0) {
                cells[fill[label[idx]]++] = idx % plane;
            }
        }
        Summits out = new Summits();
        int[] mark = new int[next];
        int stamp = 0;
        for (int l = 1; l < next; l++) {
            if (covered[l]) {
                continue;
            }
            int j = layer[l];
            int c0 = cells[start[l]];
            int above = v.minY + j - v.ground[c0];
            if (above < SUMMIT_MIN) {
                continue;
            }
            // follow the patches under this top down
            List<Integer> profile = new ArrayList<>();
            profile.add(area[l]);
            List<Integer> current = List.of(l);
            for (int jj = j - 1; jj >= 0 && profile.size() <= 12; jj--) {
                stamp++;
                List<Integer> below = new ArrayList<>();
                int a = 0;
                for (int cl : current) {
                    for (int p = start[cl]; p < start[cl + 1]; p++) {
                        int under = label[jj * plane + cells[p]];
                        if (under != 0 && mark[under] != stamp) {
                            mark[under] = stamp;
                            below.add(under);
                            a += area[under];
                        }
                    }
                }
                if (below.isEmpty() || a > 400) {
                    break;
                }
                profile.add(a);
                current = below;
            }
            out.count++;
            out.widestTop = Math.max(out.widestTop, profile.get(0));
            String where = String.format(Locale.ROOT, "%d %d %d (%d above the ground) %s", v.minX + c0 % v.sx, v.minY + j, v.minZ + c0 / v.sx, above, profile);
            if (profile.get(0) <= 2 && (profile.size() < 2 || profile.get(1) <= 6)) {
                out.pointed++;
            } else if (out.blunt.size() < 3) {
                out.blunt.add(where);
            }
            int needle = 0;
            while (needle < profile.size() && profile.get(needle) == 1) {
                needle++;
            }
            out.needleSum += needle;
            out.needleMax = Math.max(out.needleMax, needle);
            out.rods += needle >= 4 ? 1 : 0;
            int limit = Math.max(2, Math.min(10, (int) (0.4 * above)));
            int widest = profile.get(0);
            for (int k = 1; k < profile.size() && k <= limit; k++) {
                int a = profile.get(k);
                if (widest >= 6 && a <= 0.6 * widest && widest - a >= 5) {
                    out.capped++;
                    if (out.necks.size() < 3) {
                        out.necks.add(where);
                    }
                    break;
                }
                widest = Math.max(widest, a);
            }
        }
        return out;
    }

    // =========================================================================================
    // voxelisation
    // =========================================================================================

    /** A formation and the terrain under it on a block grid. */
    static final class Voxels {
        int minX;
        int minY;
        int minZ;
        int sx;
        int sy;
        int sz;
        byte[] grid;
        int[] ground;
        int[][] palette;
        int formationBlocks;
        /** Blocks the plan dropped because they would have floated. */
        int dropped;
        double planMillis;
        String description = "";
        /** Section line: start point and direction in the xz plane. */
        double sectionX;
        double sectionZ;
        double sectionDx = 1.0;
        double sectionDz = 0.0;
        double sectionHalf;

        int get(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                return EMPTY;
            }
            return grid[(y * sz + z) * sx + x];
        }

        void set(int x, int y, int z, int code) {
            grid[(y * sz + z) * sx + x] = (byte) code;
        }
    }

    interface Shape {
        int material(int x, int y, int z, int ground);
    }

    static Voxels voxelise(TerrainModel model, TerrainModel.FormationSite site) {
        Voxels v = new Voxels();
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        int maxY;
        int baseLow;
        Shape shape;
        ColumnSample s = model.sample(site.x & ~3, site.z & ~3);
        boolean mountainous = site.climate == 0 && (s.mountain > 0.3 || s.alpine > 0.3);
        switch (site.type) {
            case TerrainModel.ARCH -> {
                long t0 = System.nanoTime();
                FormationShapes.Arch a = FormationShapes.Arch.plan(model, site);
                v.planMillis = (System.nanoTime() - t0) / 1e6;
                minX = a.minX;
                maxX = a.maxX;
                minZ = a.minZ;
                maxZ = a.maxZ;
                maxY = a.maxY;
                baseLow = a.baseY;
                shape = (x, y, z, g) -> a.material(model, x, y, z, g, Fades.strataWarp(model, x, z));
                v.palette = family(site.geology, site.climate, mountainous);
                v.description = String.format(Locale.ROOT, "%s, base %d, %dx%d blocks, %d dropped", a.fin ? "fin window" : "free arch", a.baseY,
                    maxX - minX + 1, maxZ - minZ + 1, FormationShapes.dropped(a));
                v.dropped = FormationShapes.dropped(a);
            }
            case TerrainModel.HOODOO -> {
                long t0 = System.nanoTime();
                FormationShapes.HoodooField f = FormationShapes.HoodooField.plan(model, site);
                v.planMillis = (System.nanoTime() - t0) / 1e6;
                minX = f.minX;
                maxX = f.maxX;
                minZ = f.minZ;
                maxZ = f.maxZ;
                maxY = f.maxY;
                baseLow = Integer.MAX_VALUE;
                double tallest = 0.0;
                for (FormationShapes.Hoodoo p : f.pillars) {
                    baseLow = Math.min(baseLow, p.base);
                    tallest = Math.max(tallest, p.height);
                }
                FormationShapes.Column[] column = new FormationShapes.Column[1];
                int[] at = {Integer.MIN_VALUE, Integer.MIN_VALUE};
                shape = (x, y, z, g) -> {
                    if (x != at[0] || z != at[1]) {
                        column[0] = f.column(x, z);
                        at[0] = x;
                        at[1] = z;
                    }
                    return f.material(model, x, y, z, g, column[0]);
                };
                int climate = Math.max(1, site.climate);
                v.palette = withSoil(family(site.geology, climate, false));
                v.description = String.format(Locale.ROOT, "%d pillars, tallest %.0f, %dx%d blocks, %d dropped", f.pillars.size(), tallest,
                    maxX - minX + 1, maxZ - minZ + 1, FormationShapes.dropped(f));
                v.dropped = FormationShapes.dropped(f);
            }
            default -> {
                boolean stack = site.type == TerrainModel.SEA_STACK;
                int g = (int) Math.floor(model.interpolatedHeight(site.x, site.z));
                FormationShapes.Spire p = FormationShapes.Spire.plan(site, g, model.seaLevel(), stack)
                    .settle(model, (x, z) -> (int) Math.floor(model.interpolatedHeight(x, z)));
                v.dropped = FormationShapes.dropped(p);
                int r = FormationShapes.Spire.MAX_RADIUS;
                minX = site.x - r;
                maxX = site.x + r;
                minZ = site.z - r;
                maxZ = site.z + r;
                maxY = p.topY + 1;
                baseLow = p.baseY;
                shape = (x, y, z, gr) -> p.material(model, x, y, z, gr, Fades.strataWarp(model, x, z));
                v.palette = withSoil(family(site.geology, site.climate, mountainous));
                v.description = String.format(Locale.ROOT, "%s from %d to %d", stack ? "sea stack" : "spire", p.baseY, p.topY);
            }
        }
        v.minX = minX - 2;
        v.minZ = minZ - 2;
        v.sx = maxX - minX + 5;
        v.sz = maxZ - minZ + 5;
        v.ground = new int[v.sx * v.sz];
        int lowest = baseLow;
        for (int i = 0; i < v.sx; i++) {
            for (int k = 0; k < v.sz; k++) {
                int g = (int) Math.floor(model.interpolatedHeight(v.minX + i, v.minZ + k));
                v.ground[k * v.sx + i] = g;
                lowest = Math.min(lowest, g);
            }
        }
        v.minY = lowest - 4;
        v.sy = maxY - v.minY + 3;
        v.grid = new byte[v.sx * v.sy * v.sz];
        for (int i = 0; i < v.sx; i++) {
            for (int k = 0; k < v.sz; k++) {
                int g = v.ground[k * v.sx + i];
                for (int j = 0; j < v.sy; j++) {
                    int y = v.minY + j;
                    int m = shape.material(v.minX + i, y, v.minZ + k, g);
                    if (m != FormationShapes.AIR) {
                        v.set(i, j, k, FORMATION + m);
                        v.formationBlocks++;
                    } else if (y <= g) {
                        v.set(i, j, k, TERRAIN);
                    }
                }
            }
        }
        // section through the site along the longer axis of the formation
        v.sectionX = site.x;
        v.sectionZ = site.z;
        if (site.type == TerrainModel.ARCH) {
            v.sectionDx = Math.cos(site.angle);
            v.sectionDz = Math.sin(site.angle);
            // the arch picks its own orientation: find it from the blocks (longest extent)
            double best = -1.0;
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 16.0;
                double dx = Math.cos(ang);
                double dz = Math.sin(ang);
                int n = 0;
                for (int t = -60; t <= 60; t++) {
                    int i = (int) Math.round(site.x + dx * t) - v.minX;
                    int k = (int) Math.round(site.z + dz * t) - v.minZ;
                    for (int j = 0; j < v.sy; j++) {
                        if (v.get(i, j, k) >= FORMATION) {
                            n++;
                        }
                    }
                }
                if (n > best) {
                    best = n;
                    v.sectionDx = dx;
                    v.sectionDz = dz;
                }
            }
        } else {
            v.sectionDx = Math.cos(site.angle);
            v.sectionDz = Math.sin(site.angle);
        }
        v.sectionHalf = Math.max(v.sx, v.sz) * 0.75;
        return v;
    }

    /** Formation blocks that no 6-connected path through solid blocks joins to the terrain. */
    static int floating(Voxels v) {
        boolean[] seen = new boolean[v.grid.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < v.sx; i++) {
            for (int k = 0; k < v.sz; k++) {
                for (int j = 0; j < v.sy; j++) {
                    if (v.get(i, j, k) == TERRAIN) {
                        int idx = (j * v.sz + k) * v.sx + i;
                        seen[idx] = true;
                        queue.add(new int[]{i, j, k});
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : dirs) {
                int i = p[0] + d[0];
                int j = p[1] + d[1];
                int k = p[2] + d[2];
                if (v.get(i, j, k) == EMPTY) {
                    continue;
                }
                int idx = (j * v.sz + k) * v.sx + i;
                if (!seen[idx]) {
                    seen[idx] = true;
                    queue.add(new int[]{i, j, k});
                }
            }
        }
        int floating = 0;
        for (int idx = 0; idx < v.grid.length; idx++) {
            if (v.grid[idx] >= FORMATION && v.grid[idx] != FLOATING && !seen[idx]) {
                floating++;
                v.grid[idx] = FLOATING;
                if (floating <= 3) {
                    int i = idx % v.sx;
                    int k = (idx / v.sx) % v.sz;
                    int j = idx / (v.sx * v.sz);
                    System.out.printf(Locale.ROOT, "    floating block at %d %d %d%n", v.minX + i, v.minY + j, v.minZ + k);
                }
            }
        }
        return floating;
    }

    // =========================================================================================
    // colours (average texture colours of the blocks GeologyPalette uses)
    // =========================================================================================

    static int[][] family(int geology, int climate, boolean mountainous) {
        int[] tuff = {108, 109, 102};
        int[] andesite = {136, 136, 137};
        int[] stone = {125, 125, 125};
        if (geology == Geology.VOLCANIC) {
            return new int[][]{{42, 35, 40}, {80, 81, 86}, {72, 72, 78}};
        }
        if (mountainous) {
            return geology == Geology.DARK ? new int[][]{tuff, {72, 72, 78}, {80, 80, 82}}
                : geology == Geology.LIMESTONE ? new int[][]{{223, 224, 220}, {188, 188, 188}, stone} : new int[][]{tuff, andesite, stone};
        }
        if (climate == 2) {
            return new int[][]{{181, 98, 31}, {186, 99, 29}, {161, 83, 37}};
        }
        if (climate == 1 || geology == Geology.SEDIMENTARY) {
            return new int[][]{{223, 214, 170}, {216, 203, 155}, {216, 203, 155}};
        }
        return switch (geology) {
            case Geology.DARK -> new int[][]{tuff, {72, 72, 78}, {80, 80, 82}};
            case Geology.LIMESTONE -> new int[][]{{223, 224, 220}, {188, 188, 188}, stone};
            default -> new int[][]{tuff, andesite, stone};
        };
    }

    /** The family's three stops followed by grass and soil (material codes 3 and 4). */
    private static int[][] withSoil(int[][] family) {
        int[][] out = Arrays.copyOf(family, 5);
        out[FormationShapes.GRASS] = new int[]{95, 160, 60};
        out[FormationShapes.DIRT] = new int[]{134, 96, 67};
        return out;
    }

    private static int[] colour(Voxels v, int code) {
        if (code == TERRAIN) {
            return new int[]{120, 150, 90};
        }
        if (code == FLOATING) {
            return new int[]{255, 0, 0};
        }
        int m = code - FORMATION;
        int[][] p = v.palette.length > m ? v.palette : withSoil(v.palette);
        // an unknown material code shows up magenta
        return m >= p.length || p[m] == null ? new int[]{255, 0, 255} : p[m];
    }

    private static Color shade(int[] c, double f) {
        return new Color(clamp((int) (c[0] * f)), clamp((int) (c[1] * f)), clamp((int) (c[2] * f)));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // =========================================================================================
    // views
    // =========================================================================================

    /**
     * Isometric view from the south-east and above, painted back to front. Terrain is drawn only
     * in its top layers so the formation's base stays visible.
     */
    static BufferedImage isometric(Voxels v, int s) {
        double cx = 0.866 * s;
        double cy = 0.5 * s;
        int w = (int) Math.ceil((v.sx + v.sz) * cx) + 4;
        int h = (int) Math.ceil((v.sx + v.sz) * cy + v.sy * s) + 3 * s + 4;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(200, 215, 230));
        g.fillRect(0, 0, w, h);
        double ox = v.sz * cx + 2;
        double oy = v.sy * s + 2;
        // look across the formation's main axis: mirror z when the axis runs towards the viewer
        boolean mirror = Math.abs(v.sectionDx - v.sectionDz) < Math.abs(v.sectionDx + v.sectionDz);
        // back to front: increasing x + y + z is closer to a viewer at +x, +y, +z
        for (int sum = 0; sum <= v.sx + v.sy + v.sz; sum++) {
            for (int j = 0; j < v.sy; j++) {
                for (int i = Math.max(0, sum - j - v.sz + 1); i < v.sx && i <= sum - j; i++) {
                    int kk = sum - j - i;
                    if (kk < 0 || kk >= v.sz) {
                        continue;
                    }
                    int k = mirror ? v.sz - 1 - kk : kk;
                    int dk = mirror ? -1 : 1;
                    int code = v.get(i, j, k);
                    if (code == EMPTY) {
                        continue;
                    }
                    if (code == TERRAIN && v.minY + j < v.ground[k * v.sx + i] - 2) {
                        continue;
                    }
                    int[] c = colour(v, code);
                    double px = ox + (i - kk) * cx;
                    double py = oy + (i + kk) * cy - j * s;
                    if (v.get(i, j + 1, k) == EMPTY) {
                        face(g, shade(c, 1.1), px, py - s, cx, cy, 0);
                    }
                    if (v.get(i + 1, j, k) == EMPTY) {
                        face(g, shade(c, 0.85), px, py - s, cx, cy, 1);
                    }
                    if (v.get(i, j, k + dk) == EMPTY) {
                        face(g, shade(c, 0.68), px, py - s, cx, cy, 2);
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    /** One face of an isometric cube whose top corner (towards -x, -z) projects to (px, py). */
    private static void face(Graphics2D g, Color c, double px, double py, double cx, double cy, int which) {
        double s = cy * 2.0;
        Polygon p = new Polygon();
        switch (which) {
            case 0 -> {
                // top: diamond
                p.addPoint((int) Math.round(px), (int) Math.round(py));
                p.addPoint((int) Math.round(px + cx), (int) Math.round(py + cy));
                p.addPoint((int) Math.round(px), (int) Math.round(py + 2 * cy));
                p.addPoint((int) Math.round(px - cx), (int) Math.round(py + cy));
            }
            case 1 -> {
                // +x face: right lower parallelogram
                p.addPoint((int) Math.round(px + cx), (int) Math.round(py + cy));
                p.addPoint((int) Math.round(px), (int) Math.round(py + 2 * cy));
                p.addPoint((int) Math.round(px), (int) Math.round(py + 2 * cy + s));
                p.addPoint((int) Math.round(px + cx), (int) Math.round(py + cy + s));
            }
            default -> {
                // +z face: left lower parallelogram
                p.addPoint((int) Math.round(px - cx), (int) Math.round(py + cy));
                p.addPoint((int) Math.round(px), (int) Math.round(py + 2 * cy));
                p.addPoint((int) Math.round(px), (int) Math.round(py + 2 * cy + s));
                p.addPoint((int) Math.round(px - cx), (int) Math.round(py + cy + s));
            }
        }
        g.setColor(c);
        g.fillPolygon(p);
    }

    /** Vertical section along the formation's main axis, through the site. */
    static BufferedImage section(Voxels v, int s) {
        int half = (int) v.sectionHalf;
        int w = (2 * half + 1) * s;
        int h = v.sy * s;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int t = -half; t <= half; t++) {
            int i = (int) Math.round(v.sectionX + v.sectionDx * t) - v.minX;
            int k = (int) Math.round(v.sectionZ + v.sectionDz * t) - v.minZ;
            for (int j = 0; j < v.sy; j++) {
                int code = v.get(i, j, k);
                int rgb = code == EMPTY ? 0xC8D7E6 : rgb(colour(v, code));
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        img.setRGB((t + half) * s + a, (v.sy - 1 - j) * s + b, rgb);
                    }
                }
            }
        }
        return img;
    }

    /**
     * Side view along a horizontal axis ({@code alongX}: looking along +x, else along +z), as
     * seen from the ground far away: every pixel shows the nearest block, darker with distance,
     * lit where its top is open and outlined where the depth jumps, so the silhouettes of the
     * pillars and the shapes of their tops read clearly. Only the top two layers of the ground
     * are drawn, terrain or formation (the anchoring under a foot is buried in the game), so a
     * near hill does not wall off the view.
     */
    static BufferedImage elevation(Voxels v, int s, boolean alongX) {
        int w = alongX ? v.sz : v.sx;
        int deep = alongX ? v.sx : v.sz;
        int[] depth = new int[w * v.sy];
        int[] rgb = new int[w * v.sy];
        Arrays.fill(depth, -1);
        for (int u = 0; u < w; u++) {
            for (int j = 0; j < v.sy; j++) {
                for (int dd = 0; dd < deep; dd++) {
                    int i = alongX ? dd : u;
                    int k = alongX ? u : dd;
                    int code = v.get(i, j, k);
                    if (code == EMPTY || v.minY + j < v.ground[k * v.sx + i] - 1) {
                        continue;
                    }
                    double f = 1.0 - 0.45 * dd / (double) deep;
                    if (v.get(i, j + 1, k) == EMPTY) {
                        f *= 1.12;
                    }
                    depth[j * w + u] = dd;
                    rgb[j * w + u] = shade(colour(v, code), f).getRGB() & 0xFFFFFF;
                    break;
                }
            }
        }
        BufferedImage img = new BufferedImage(w * s, v.sy * s, BufferedImage.TYPE_INT_RGB);
        for (int u = 0; u < w; u++) {
            for (int j = 0; j < v.sy; j++) {
                int d = depth[j * w + u];
                int c = 0xC8D7E6;
                if (d >= 0) {
                    c = rgb[j * w + u];
                    // outline: a neighbour much further back, or sky
                    boolean edge = false;
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int uu = u + o[0];
                        int jj = j + o[1];
                        if (uu < 0 || jj < 0 || uu >= w || jj >= v.sy) {
                            continue;
                        }
                        int dn = depth[jj * w + uu];
                        edge |= dn < 0 || dn > d + 3;
                    }
                    if (edge) {
                        c = shade(new int[]{c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF}, 0.72).getRGB() & 0xFFFFFF;
                    }
                }
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        img.setRGB(u * s + a, (v.sy - 1 - j) * s + b, c);
                    }
                }
            }
        }
        return img;
    }

    /** Top view: the material of the highest block of every column, shaded by its height. */
    static BufferedImage topView(Voxels v, int s) {
        BufferedImage img = new BufferedImage(v.sx * s, v.sz * s, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < v.sx; i++) {
            for (int k = 0; k < v.sz; k++) {
                int rgb = 0xC8D7E6;
                for (int j = v.sy - 1; j >= 0; j--) {
                    int code = v.get(i, j, k);
                    if (code != EMPTY) {
                        double f = 0.6 + 0.5 * j / (double) v.sy;
                        rgb = shade(colour(v, code), f).getRGB() & 0xFFFFFF;
                        break;
                    }
                }
                for (int a = 0; a < s; a++) {
                    for (int b = 0; b < s; b++) {
                        img.setRGB(i * s + a, k * s + b, rgb);
                    }
                }
            }
        }
        return img;
    }

    private static int rgb(int[] c) {
        return (c[0] << 16) | (c[1] << 8) | c[2];
    }

    static BufferedImage sheet(List<BufferedImage> tiles) {
        int cols = Math.min(3, Math.max(1, tiles.size()));
        int rows = (tiles.size() + cols - 1) / cols;
        int tw = 0;
        int th = 0;
        for (BufferedImage t : tiles) {
            tw = Math.max(tw, t.getWidth());
            th = Math.max(th, t.getHeight());
        }
        BufferedImage out = new BufferedImage(Math.max(1, cols * tw), Math.max(1, rows * th), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(200, 215, 230));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int i = 0; i < tiles.size(); i++) {
            g.drawImage(tiles.get(i), (i % cols) * tw, (i / cols) * th, null);
        }
        g.dispose();
        return out;
    }
}
