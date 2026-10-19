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
 *   <li>renders an isometric view, a cross-section and a top view of each, coloured with the
 *       blocks' average texture colours, into {@code <outDir>/<type>_<i>.png};</li>
 *   <li>checks that no block floats: a 6-connected flood fill from the terrain must reach every
 *       formation block;</li>
 *   <li>with mode {@code check}, does the flood-fill check (no images) for many sites of every
 *       type and prints the totals.</li>
 * </ul>
 * Usage: {@code FormationStudy <seed> <outDir> [arch|hoodoo|spire|sea_stack|check] [count=4]}.
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
            System.out.printf(Locale.ROOT, "%s %d at %d %d (ground %.0f, %s, climate %d): %s; %d formation blocks, %d floating, planned in %.0f ms, voxelised in %.0f ms%n",
                TerrainModel.formationName(type), i, site.x, site.z, site.ground, Geology.name(site.geology), site.climate,
                v.description, v.formationBlocks, floating, v.planMillis, (t1 - t0) / 1e6 - v.planMillis);
            BufferedImage iso = isometric(v, 5);
            BufferedImage section = section(v, 5);
            BufferedImage top = topView(v, 4);
            int w = iso.getWidth() + Math.max(section.getWidth(), top.getWidth());
            int h = Math.max(iso.getHeight(), section.getHeight() + top.getHeight());
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(200, 215, 230));
            g.fillRect(0, 0, w, h);
            g.drawImage(iso, 0, 0, null);
            g.drawImage(section, iso.getWidth(), 0, null);
            g.drawImage(top, iso.getWidth(), section.getHeight(), null);
            g.setColor(Color.BLACK);
            g.drawString(String.format(Locale.ROOT, "%s %d  %s  floating=%d", TerrainModel.formationName(type), i, v.description, floating), 6, 14);
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
        long t0 = System.nanoTime();
        for (TerrainModel.FormationSite site : sites) {
            Voxels v = voxelise(model, site);
            int f = floating(v);
            blocks += v.formationBlocks;
            floating += f;
            dropped += v.dropped;
            if (f > 0) {
                bad++;
                System.out.printf(Locale.ROOT, "  %s at %d %d: %d of %d blocks float (%s)%n", TerrainModel.formationName(type), site.x, site.z, f, v.formationBlocks, v.description);
            }
            if (v.formationBlocks == 0) {
                empty++;
            }
        }
        System.out.printf(Locale.ROOT, "%s: %d sites, %d blocks, %d floating blocks in %d formations, %d empty, %d blocks dropped by the plans, %.1f s%n",
            TerrainModel.formationName(type), sites.size(), blocks, floating, bad, empty, dropped, (System.nanoTime() - t0) / 1e9);
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
                int capped = 0;
                double tallest = 0.0;
                for (FormationShapes.Hoodoo p : f.pillars) {
                    baseLow = Math.min(baseLow, p.base);
                    capped += p.capped ? 1 : 0;
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
                v.palette = withCap(family(site.geology, climate, false), new int[]{209, 178, 161});
                v.description = String.format(Locale.ROOT, "%d pillars (%d capped), tallest %.0f, %dx%d blocks, %d dropped", f.pillars.size(), capped, tallest,
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

    private static int[][] withCap(int[][] family, int[] cap) {
        int[][] out = Arrays.copyOf(family, 6);
        out[3] = cap;
        out[4] = new int[]{95, 160, 60};
        out[5] = new int[]{134, 96, 67};
        return out;
    }

    private static int[][] withSoil(int[][] family) {
        return withCap(family, new int[]{255, 0, 255});
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
        return p[m] == null ? new int[]{255, 0, 255} : p[m];
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
