package com.terrahaul.devtools;

import com.terrahaul.worldgen.ruins.HousePlanner;
import com.terrahaul.worldgen.ruins.LampPostPlanner;
import com.terrahaul.worldgen.ruins.LightStretch;
import com.terrahaul.worldgen.ruins.RuinCell;
import com.terrahaul.worldgen.ruins.RuinDemos;
import com.terrahaul.worldgen.ruins.RuinMaterials;
import com.terrahaul.worldgen.ruins.RuinPart;
import com.terrahaul.worldgen.ruins.RuinPlan;
import com.terrahaul.worldgen.ruins.RuinSite;
import com.terrahaul.worldgen.ruins.RuinSites;
import com.terrahaul.worldgen.ruins.Signpost;
import com.terrahaul.worldgen.ruins.SignpostPlanner;
import com.terrahaul.worldgen.ruins.SunkenRuin;
import com.terrahaul.worldgen.ruins.TrailRuin;
import com.terrahaul.worldgen.ruins.TrailSurvey;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainFinder;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.terrain.TerrainSettings;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import javax.imageio.ImageIO;

/**
 * Development tool for the trail ruins, signposts, lamp posts and sunken ruins: statistics over a
 * region, maps of where everything stands, and isometric renders of the plans, all from the pure
 * planners without starting Minecraft.
 * <pre>
 *   RuinStudy stats &lt;seed&gt; &lt;half&gt;                          counts, rates, timings, floating cells, determinism
 *   RuinStudy map &lt;seed&gt; &lt;cx&gt; &lt;cz&gt; &lt;size&gt; &lt;bpp&gt; &lt;out.png&gt;   relief with trails and every site
 *   RuinStudy sheet &lt;outDir&gt; [seed]                         every type at three decays, two views each
 *   RuinStudy find &lt;seed&gt; &lt;outDir&gt; &lt;target&gt; &lt;count&gt;          finder results, checked and rendered in place
 * </pre>
 * Without the game there is no world filter: biomes are estimated from the climate and no site
 * is excluded for vanilla structures.
 */
public final class RuinStudy {
    private RuinStudy() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        switch (mode) {
            case "stats" -> stats(Long.parseLong(args[1]), Integer.parseInt(args[2]));
            case "map" -> map(Long.parseLong(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                Integer.parseInt(args[5]), new File(args[6]));
            case "sheet" -> sheet(new File(args[1]), args.length > 2 ? Long.parseLong(args[2]) : 7L);
            case "find" -> find(Long.parseLong(args[1]), new File(args[2]), args[3], Integer.parseInt(args[4]));
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    // =========================================================================================
    // stats
    // =========================================================================================

    private static void stats(long seed, int half) throws Exception {
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        int tc = RuinSites.TRAIL_CELL;
        int c0 = Math.floorDiv(-half, tc);
        int c1 = Math.floorDiv(half - 1, tc);
        int[] byTemplate = new int[HousePlanner.NAMES.length];
        int[] byClimate = new int[RuinSites.CLIMATE_NAMES.length];
        int junctionSigns = 0;
        int endSigns = 0;
        int stretches = 0;
        int posts = 0;
        int[] byStyle = new int[LightStretch.STYLE_NAMES.length];
        int[] byState = new int[LampPostPlanner.STATE_NAMES.length];
        List<TrailRuin> ruins = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = c0; cz <= c1; cz++) {
                TrailSurvey.Cell cell = sites.trailCell(cx, cz);
                if (cell.ruin() != null) {
                    ruins.add(cell.ruin());
                    byTemplate[cell.ruin().template]++;
                    byClimate[cell.ruin().climate]++;
                }
                for (Signpost s : cell.signposts()) {
                    if (s.kind == Signpost.JUNCTION) {
                        junctionSigns++;
                    } else {
                        endSigns++;
                    }
                }
                if (cell.lights() != null) {
                    stretches++;
                    posts += cell.lights().posts.length;
                    byStyle[cell.lights().style]++;
                    for (int i = 0; i < cell.lights().posts.length; i++) {
                        byState[LampPostPlanner.state(cell.lights(), i)]++;
                    }
                }
            }
        }
        long t1 = System.nanoTime();
        double km2 = Math.pow((c1 - c0 + 1) * tc / 1000.0, 2);
        long laidPoints = sites.stats.get(19);
        double trailKm = laidPoints * 16.0 / 1000.0;
        System.out.printf(Locale.ROOT, "seed %d: %d trail cells (%.1f km2), %d with trails, laid trail ~%.1f km, survey %.1f s%n", seed,
            sites.stats.get(0), km2, sites.stats.get(1), trailKm, (t1 - t0) / 1e9);
        System.out.printf(Locale.ROOT, "survey time per cell: mean %.2f ms, max %.1f ms%n", sites.stats.get(17) / 1e6 / Math.max(1, sites.stats.get(0)),
            sites.stats.get(18) / 1e6);
        System.out.printf(Locale.ROOT, "trail ruins %d = %.2f per km of laid trail (%.2f per km2)%n", ruins.size(), ruins.size() / trailKm, ruins.size() / km2);
        StringBuilder sb = new StringBuilder("  by type:");
        for (int i = 0; i < byTemplate.length; i++) {
            sb.append(' ').append(HousePlanner.NAMES[i]).append('=').append(byTemplate[i]);
        }
        System.out.println(sb);
        sb = new StringBuilder("  by climate:");
        for (int i = 0; i < byClimate.length; i++) {
            sb.append(' ').append(RuinSites.CLIMATE_NAMES[i]).append('=').append(byClimate[i]);
        }
        System.out.println(sb);
        System.out.printf(Locale.ROOT, "  attempts %d; rejected: no tangent %d, off-axis %d, biome %d, on trail %d, slope %d, land %d, border %d, feature %d%n",
            sites.stats.get(2), sites.stats.get(3), sites.stats.get(4), sites.stats.get(5), sites.stats.get(6), sites.stats.get(7),
            sites.stats.get(8), sites.stats.get(9), sites.stats.get(10));
        System.out.printf(Locale.ROOT, "junctions %d, trail ends %d (validated, in cells); signposts %d junction + %d end = %.2f per km%n",
            sites.stats.get(12), sites.stats.get(13), junctionSigns, endSigns, (junctionSigns + endSigns) / trailKm);
        System.out.printf(Locale.ROOT, "lamp stretches %d (%.2f per km), %d posts, %.1f per stretch%n", stretches, stretches / trailKm, posts,
            posts / Math.max(1.0, stretches));
        sb = new StringBuilder("  styles:");
        for (int i = 0; i < byStyle.length; i++) {
            sb.append(' ').append(LightStretch.STYLE_NAMES[i]).append('=').append(byStyle[i]);
        }
        sb.append("  states:");
        for (int i = 0; i < byState.length; i++) {
            sb.append(' ').append(LampPostPlanner.STATE_NAMES[i]).append('=').append(byState[i]);
        }
        System.out.println(sb);
        // sunken ruins and the sea floor they can stand on
        int sc = RuinSites.SUNKEN_CELL;
        int s0 = Math.floorDiv(-half, sc);
        int s1 = Math.floorDiv(half - 1, sc);
        int sunken = 0;
        int[] byKind = new int[SunkenRuin.KIND_NAMES.length];
        int warm = 0;
        List<SunkenRuin> sunkenList = new ArrayList<>();
        long t2 = System.nanoTime();
        for (int cx = s0; cx <= s1; cx++) {
            for (int cz = s0; cz <= s1; cz++) {
                SunkenRuin s = sites.sunken(cx, cz);
                if (s != null) {
                    sunken++;
                    byKind[s.kind]++;
                    warm += s.warm ? 1 : 0;
                    sunkenList.add(s);
                }
            }
        }
        long t3 = System.nanoTime();
        int floorSamples = 0;
        ColumnSample cs = new ColumnSample();
        for (int x = s0 * sc; x < (s1 + 1) * sc; x += 32) {
            for (int z = s0 * sc; z < (s1 + 1) * sc; z += 32) {
                model.sampleInto(x, z, cs);
                double depth = model.seaLevel() - cs.height;
                if (cs.isSea(model.seaLevel()) && depth >= 10 && depth <= 48) {
                    floorSamples++;
                }
            }
        }
        double floorKm2 = floorSamples * 1024.0 / 1e6;
        System.out.printf(Locale.ROOT, "sunken ruins %d (%d warm) over %.1f km2 of 10-48 deep floor = %.2f per km2, %.0f ms%n", sunken, warm,
            floorKm2, sunken / Math.max(0.01, floorKm2), (t3 - t2) / 1e6);
        sb = new StringBuilder("  by kind:");
        for (int i = 0; i < byKind.length; i++) {
            sb.append(' ').append(SunkenRuin.KIND_NAMES[i]).append('=').append(byKind[i]);
        }
        System.out.println(sb);
        // plans: time, size and the floating-cell guarantee
        long pt = 0;
        long ptMax = 0;
        int floating = 0;
        int cells = 0;
        int chests = 0;
        for (RuinSite r : concat(ruins, sunkenList)) {
            long a = System.nanoTime();
            RuinPlan p = sites.plan(r);
            long dt = System.nanoTime() - a;
            pt += dt;
            ptMax = Math.max(ptMax, dt);
            floating += p.floatingCells();
            cells += p.countCells();
            for (RuinPlan.Extra e : p.extras) {
                chests += e.lootTable() != null && e.lootTable().contains("chests") ? 1 : 0;
            }
        }
        int planCount = ruins.size() + sunkenList.size();
        System.out.printf(Locale.ROOT, "plans %d: mean %.2f ms, max %.1f ms, mean %d cells, %d chests, floating cells after prune: %d%n", planCount,
            pt / 1e6 / Math.max(1, planCount), ptMax / 1e6, cells / Math.max(1, planCount), chests, floating);
        // signpost text for a few posts
        int shown = 0;
        long st = 0;
        long stMax = 0;
        int textCount = 0;
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = c0; cz <= c1; cz++) {
                for (Signpost s : sites.trailCell(cx, cz).signposts()) {
                    long a = System.nanoTime();
                    SignpostPlanner.Board[] boards = SignpostPlanner.boards(sites, s);
                    long dt = System.nanoTime() - a;
                    st += dt;
                    stMax = Math.max(stMax, dt);
                    textCount++;
                    if (shown < 6) {
                        shown++;
                        StringBuilder t = new StringBuilder(String.format(Locale.ROOT, "  %s post at %d %d:", s.kind == Signpost.JUNCTION ? "junction" : "end",
                            s.x, s.z));
                        for (SignpostPlanner.Board b : boards) {
                            t.append(" [").append("NESW".charAt(b.dir())).append(": ").append(String.join(" / ", b.text().front()).trim()).append(']');
                        }
                        System.out.println(t);
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "sign text: %d posts, mean %.1f ms, max %.1f ms%n", textCount, st / 1e6 / Math.max(1, textCount), stMax / 1e6);
        determinism(seed, c0, c1, sites);
    }

    private static List<RuinSite> concat(List<? extends RuinSite> a, List<? extends RuinSite> b) {
        List<RuinSite> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** Recomputes a sample of cells with a fresh model on another thread, in reverse order, and compares. */
    private static void determinism(long seed, int c0, int c1, RuinSites sites) throws Exception {
        TerrainModel other = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites fresh = RuinSites.of(other);
        List<int[]> cells = new ArrayList<>();
        for (int cx = c0; cx <= c1; cx += 3) {
            for (int cz = c0; cz <= c1; cz += 2) {
                cells.add(new int[]{cx, cz});
            }
        }
        long[] a = new long[cells.size()];
        long[] b = new long[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            a[i] = cellHash(sites, cells.get(i));
        }
        Thread t = new Thread(() -> {
            for (int i = cells.size() - 1; i >= 0; i--) {
                b[i] = cellHash(fresh, cells.get(i));
            }
        });
        t.start();
        t.join();
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                diff++;
                int[] c = cells.get(i);
                System.out.println("  differs: cell " + c[0] + " " + c[1] + ": " + describeCell(sites, c) + " vs " + describeCell(fresh, c));
            }
        }
        System.out.printf(Locale.ROOT, "determinism: %d cells recomputed in reverse on another thread, %d differ%n", cells.size(), diff);
    }

    private static String describeCell(RuinSites sites, int[] c) {
        TrailSurvey.Cell cell = sites.trailCell(c[0], c[1]);
        StringBuilder b = new StringBuilder();
        if (cell.ruin() != null) {
            b.append("ruin ").append(cell.ruin().x).append(',').append(cell.ruin().z).append(" fp ").append(sites.plan(cell.ruin()).fingerprint());
        }
        for (Signpost s : cell.signposts()) {
            b.append(" sign ").append(s.x).append(',').append(s.z);
        }
        if (cell.lights() != null) {
            b.append(" lights ").append(cell.lights().posts.length).append(" first ").append(cell.lights().posts[0][0]).append(',')
                .append(cell.lights().posts[0][1]);
        }
        SunkenRuin s = sites.sunken(c[0], c[1]);
        if (s != null) {
            b.append(" sunken ").append(s.x).append(',').append(s.z).append(" fp ").append(sites.plan(s).fingerprint());
        }
        return b.toString();
    }

    private static long cellHash(RuinSites sites, int[] c) {
        TrailSurvey.Cell cell = sites.trailCell(c[0], c[1]);
        long h = 17;
        if (cell.ruin() != null) {
            h = h * 31 + cell.ruin().seed + cell.ruin().x * 7L + cell.ruin().z * 13L + sites.plan(cell.ruin()).fingerprint();
        }
        for (Signpost s : cell.signposts()) {
            h = h * 31 + s.x * 7L + s.z * 13L + s.kind;
        }
        if (cell.lights() != null) {
            for (int[] p : cell.lights().posts) {
                h = h * 31 + p[0] * 7L + p[1] * 13L + p[2];
            }
        }
        SunkenRuin s = sites.sunken(c[0], c[1]);
        if (s != null) {
            h = h * 31 + s.seed + sites.plan(s).fingerprint();
        }
        return h;
    }

    // =========================================================================================
    // map
    // =========================================================================================

    private static void map(long seed, int cx, int cz, int size, int bpp, File out) throws Exception {
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        int px = size / bpp;
        int minX = cx - size / 2;
        int minZ = cz - size / 2;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        int sl = model.seaLevel();
        for (int j = 0; j < px; j++) {
            for (int i = 0; i < px; i++) {
                int x = minX + i * bpp;
                int z = minZ + j * bpp;
                img.setRGB(i, j, terrainColour(model, x, z, sl, bpp <= 2));
            }
        }
        Graphics2D g = img.createGraphics();
        int tc = RuinSites.TRAIL_CELL;
        for (int tx = Math.floorDiv(minX, tc); tx <= Math.floorDiv(minX + size, tc); tx++) {
            for (int tz = Math.floorDiv(minZ, tc); tz <= Math.floorDiv(minZ + size, tc); tz++) {
                TrailSurvey.Cell cell = sites.trailCell(tx, tz);
                if (cell.ruin() != null) {
                    TrailRuin r = cell.ruin();
                    RuinPlan p = sites.plan(r);
                    g.setColor(new Color(255, 40, 40));
                    for (int x = p.minX; x <= p.maxX(); x++) {
                        for (int z = p.minZ; z <= p.maxZ(); z++) {
                            boolean any = false;
                            for (int y = p.baseY; y <= p.maxY() && !any; y++) {
                                int c = p.get(x, y, z);
                                any = c != 0 && RuinCell.part(c).structural;
                            }
                            if (any) {
                                g.fillRect((x - minX) / bpp, (z - minZ) / bpp, 1, 1);
                            }
                        }
                    }
                    g.setColor(Color.WHITE);
                    for (RuinPlan.Decal d : p.decals) {
                        if (RuinCell.part(d.cell()) == RuinPart.DIRT_PATH || RuinCell.part(d.cell()) == RuinPart.COARSE_DIRT) {
                            g.fillRect((d.x() - minX) / bpp, (d.z() - minZ) / bpp, 1, 1);
                        }
                    }
                    g.setColor(Color.RED);
                    g.drawRect((r.minX - minX) / bpp, (r.minZ - minZ) / bpp, (r.maxX - r.minX) / bpp, (r.maxZ - r.minZ) / bpp);
                    g.drawString(HousePlanner.NAMES[r.template], (r.maxX - minX) / bpp + 2, (r.minZ - minZ) / bpp + 8);
                }
                for (Signpost s : cell.signposts()) {
                    int sx = (s.x - minX) / bpp;
                    int sz = (s.z - minZ) / bpp;
                    g.setColor(s.kind == Signpost.JUNCTION ? Color.MAGENTA : Color.BLUE);
                    g.fillOval(sx - 3, sz - 3, 7, 7);
                    for (double a : s.arms) {
                        g.drawLine((s.nodeX - minX) / bpp, (s.nodeZ - minZ) / bpp, (int) ((s.nodeX + Math.cos(a) * 24 - minX) / bpp),
                            (int) ((s.nodeZ + Math.sin(a) * 24 - minZ) / bpp));
                    }
                }
                if (cell.lights() != null) {
                    g.setColor(Color.YELLOW);
                    for (int[] p : cell.lights().posts) {
                        int sx = (p[0] - minX) / bpp;
                        int sz = (p[1] - minZ) / bpp;
                        g.fillRect(sx - 1, sz - 1, 3, 3);
                        g.drawLine(sx, sz, sx + RuinCell.DX[p[2]] * 3, sz + RuinCell.DZ[p[2]] * 3);
                    }
                }
            }
        }
        int sc = RuinSites.SUNKEN_CELL;
        for (int tx = Math.floorDiv(minX, sc); tx <= Math.floorDiv(minX + size, sc); tx++) {
            for (int tz = Math.floorDiv(minZ, sc); tz <= Math.floorDiv(minZ + size, sc); tz++) {
                SunkenRuin s = sites.sunken(tx, tz);
                if (s != null) {
                    g.setColor(Color.CYAN);
                    g.drawRect((s.minX - minX) / bpp, (s.minZ - minZ) / bpp, (s.maxX - s.minX) / bpp, (s.maxZ - s.minZ) / bpp);
                    g.drawString(SunkenRuin.KIND_NAMES[s.kind], (s.maxX - minX) / bpp + 2, (s.minZ - minZ) / bpp + 8);
                }
            }
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    private static int terrainColour(TerrainModel model, int x, int z, int sl, boolean trails) {
        ColumnSample s = model.sample(x & ~3, z & ~3);
        double h = model.interpolatedHeight(x, z);
        double hx = model.interpolatedHeight(x + 2, z);
        double hz = model.interpolatedHeight(x, z + 2);
        int shade = (int) Math.max(-40, Math.min(40, (hx - h + hz - h) * 10));
        int rgb;
        if (s.waterLevel != ColumnSample.NO_WATER || h < sl - 0.5) {
            int d = (int) Math.max(0, Math.min(120, (sl - h) * 2));
            rgb = rgb(40, 90 - d / 3, 170 - d / 2);
        } else {
            int g = (int) Math.max(40, Math.min(200, 80 + (h - sl) * 0.5));
            rgb = rgb(g / 2 + 30 + shade, g + shade, g / 3 + shade / 2);
        }
        double d = model.trailDistance(x, z);
        if (trails && d < 2.0) {
            rgb = TerrainModel.trailGround(s, sl) ? rgb(200, 160, 96) : rgb(120, 90, 60);
        } else if (!trails && d < 3.0 && TerrainModel.trailGround(s, sl)) {
            rgb = rgb(200, 160, 96);
        }
        return rgb;
    }

    private static int rgb(int r, int g, int b) {
        return Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
    }

    // =========================================================================================
    // sheet: every type at three decays
    // =========================================================================================

    private static void sheet(File dir, long seed) throws Exception {
        dir.mkdirs();
        double[] decays = {0.3, 0.55, 0.85};
        RuinMaterials[] mats = {
            new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.OAK, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD, RuinMaterials.AZALEA),
            new RuinMaterials(RuinMaterials.SLATE, RuinMaterials.SPRUCE, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_TILE, RuinMaterials.SPRUCE),
            new RuinMaterials(RuinMaterials.GRANITE, RuinMaterials.SPRUCE, RuinMaterials.SPRUCE, RuinMaterials.ROOF_STONE, RuinMaterials.SPRUCE),
            new RuinMaterials(RuinMaterials.LIMESTONE, RuinMaterials.BIRCH, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_WOOD, RuinMaterials.BIRCH),
            new RuinMaterials(RuinMaterials.MUD, RuinMaterials.ACACIA, RuinMaterials.ACACIA, RuinMaterials.ROOF_WOOD, RuinMaterials.ACACIA),
            new RuinMaterials(RuinMaterials.MOSSY, RuinMaterials.BAMBOO, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_WOOD, RuinMaterials.JUNGLE),
            new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.DARK_OAK, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_WOOD, RuinMaterials.DARK_OAK),
            new RuinMaterials(RuinMaterials.ASHLAR, RuinMaterials.SPRUCE, RuinMaterials.SPRUCE, RuinMaterials.ROOF_STONE, RuinMaterials.SPRUCE),
            new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.OAK, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD, RuinMaterials.OAK)};
        int[] climates = {RuinSites.TEMPERATE, RuinSites.COLD, RuinSites.UPLAND, RuinSites.TEMPERATE, RuinSites.HOT_DRY, RuinSites.HOT_WET,
            RuinSites.TEMPERATE, RuinSites.UPLAND, RuinSites.TEMPERATE};
        int[] templates = {HousePlanner.COTTAGE, HousePlanner.LOG_CABIN, HousePlanner.BOTHY, HousePlanner.FARMSTEAD, HousePlanner.ADOBE,
            HousePlanner.STILT_HUT, HousePlanner.CHIMNEY_RUIN, HousePlanner.WATCHTOWER, HousePlanner.LEAN_TO};
        int floating = 0;
        for (int t = 0; t < templates.length; t++) {
            List<BufferedImage> row = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (double d : decays) {
                double wet = climates[t] == RuinSites.HOT_DRY ? 0.2 : climates[t] == RuinSites.HOT_WET ? 0.8 : 0.55;
                RuinPlan p = RuinDemos.house(templates[t], climates[t], mats[t], d, wet, seed + t * 101L + (long) (d * 1000));
                floating += p.floatingCells();
                Voxels v = Voxels.stamp(p, (x, z) -> 64, -1000);
                row.add(v.render(14, false));
                row.add(v.render(14, true));
                labels.add(String.format(Locale.ROOT, "%s decay %.2f, %s, seen from SE", HousePlanner.NAMES[templates[t]], d, mats[t]));
                labels.add("seen from NW (front, trail side)");
            }
            write(tile(row, labels, 2), new File(dir, "house_" + HousePlanner.NAMES[templates[t]] + ".png"));
        }
        int[] stones = {RuinMaterials.SEA_STONE, RuinMaterials.MARBLE, RuinMaterials.SEA_TUFF, RuinMaterials.SEA_SANDSTONE};
        for (int k = 0; k < SunkenRuin.KIND_NAMES.length; k++) {
            List<BufferedImage> row = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < decays.length; i++) {
                boolean warm = i == 1;
                RuinPlan p = RuinDemos.sunken(k, warm, stones[(k + i) % stones.length], decays[i], seed + k * 31L + i);
                floating += p.floatingCells();
                Voxels v = Voxels.stamp(p, (x, z) -> 30, 63);
                row.add(v.render(11, false));
                row.add(v.render(11, true));
                labels.add(String.format(Locale.ROOT, "%s %s decay %.2f", SunkenRuin.KIND_NAMES[k], warm ? "warm" : "cold", decays[i]));
                labels.add("back");
            }
            write(tile(row, labels, 2), new File(dir, "sunken_" + SunkenRuin.KIND_NAMES[k] + ".png"));
        }
        List<BufferedImage> row = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (boolean junction : new boolean[]{true, false}) {
            RuinPlan p = RuinDemos.signpost(junction, mats[1], seed);
            Voxels v = Voxels.stamp(p, (x, z) -> 64, -1000);
            row.add(v.render(14, false));
            labels.add(junction ? "fingerpost" : "trail end");
        }
        for (int style = 0; style < LightStretch.STYLE_NAMES.length; style++) {
            List<RuinPlan> lamps = RuinDemos.lampPosts(style, 0.75, 0.7, seed + style, 8);
            for (int i = 0; i < lamps.size(); i++) {
                Voxels v = Voxels.stamp(lamps.get(i), (x, z) -> 64, -1000);
                row.add(v.render(14, false));
                labels.add(LightStretch.STYLE_NAMES[style] + " " + RuinDemos.lampState(style, 0.75, seed + style, 8, i));
            }
        }
        write(tile(row, labels, 8), new File(dir, "posts.png"));
        System.out.println("floating cells over all demo plans: " + floating);
    }

    private static void write(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }

    private static BufferedImage tile(List<BufferedImage> imgs, List<String> labels, int perRow) {
        int cw = 0;
        int ch = 0;
        for (BufferedImage i : imgs) {
            cw = Math.max(cw, i.getWidth());
            ch = Math.max(ch, i.getHeight());
        }
        ch += 16;
        int rows = (imgs.size() + perRow - 1) / perRow;
        int cols = Math.min(perRow, imgs.size());
        BufferedImage out = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(182, 206, 232));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < imgs.size(); i++) {
            int x = (i % perRow) * cw;
            int y = (i / perRow) * ch;
            g.drawImage(imgs.get(i), x, y + 16, null);
            g.setColor(Color.BLACK);
            g.drawString(labels.get(i), x + 4, y + 12);
        }
        g.dispose();
        return out;
    }

    // =========================================================================================
    // find: the finder's answers, checked against the sites and rendered in their terrain
    // =========================================================================================

    private static void find(long seed, File dir, String target, int count) throws Exception {
        dir.mkdirs();
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        TerrainFinder.Target t = TerrainFinder.targets().get(target);
        if (t == null) {
            throw new IllegalArgumentException("no finder target " + target);
        }
        int ox = 0;
        int oz = 0;
        for (int i = 0; i < count; i++) {
            long a = System.nanoTime();
            TerrainFinder.Result r = TerrainFinder.find(model, t, ox, oz, 6000, () -> false);
            long dt = System.nanoTime() - a;
            if (r == null) {
                System.out.println("nothing found from " + ox + "," + oz);
                break;
            }
            int type = Arrays.asList(RuinSites.TYPE_NAMES).indexOf(target);
            int cellSize = type == RuinSites.SUNKEN_RUIN ? RuinSites.SUNKEN_CELL : RuinSites.TRAIL_CELL;
            RuinSite match = null;
            for (RuinSite s : sites.sitesInCell(type, Math.floorDiv(r.x(), cellSize), Math.floorDiv(r.z(), cellSize))) {
                if (s.x == r.x() && s.z == r.z()) {
                    match = s;
                }
            }
            System.out.printf(Locale.ROOT, "%s %d at %d %d %d (%.0f ms) %s%n", target, i, r.x(), r.y(), r.z(), dt / 1e6,
                match == null ? "NO MATCHING SITE" : describe(sites, match));
            if (match != null) {
                render(model, sites, match, new File(dir, target + "_" + i + ".png"));
            }
            ox = r.x() + 900;
            oz = r.z() + 700;
        }
    }

    private static String describe(RuinSites sites, RuinSite s) {
        if (s instanceof TrailRuin r) {
            return String.format(Locale.ROOT, "%s (%s, %s) decay %.2f wet %.2f '%s' floor %d", HousePlanner.NAMES[r.template],
                RuinSites.CLIMATE_NAMES[r.climate], r.materials, r.decay, r.wetness, r.name, r.floorY);
        }
        if (s instanceof Signpost p) {
            StringBuilder b = new StringBuilder(p.kind == Signpost.JUNCTION ? "junction" : "trail end");
            for (SignpostPlanner.Board board : SignpostPlanner.boards(sites, p)) {
                b.append(" [").append("NESW".charAt(board.dir())).append(": ").append(String.join(" / ", board.text().front()).trim()).append(']');
            }
            return b.toString();
        }
        if (s instanceof LightStretch l) {
            return String.format(Locale.ROOT, "%d %s posts, decay %.2f", l.posts.length, LightStretch.STYLE_NAMES[l.style], l.decay);
        }
        SunkenRuin u = (SunkenRuin) s;
        return String.format(Locale.ROOT, "%s %s %s decay %.2f floor %d depth %d", SunkenRuin.KIND_NAMES[u.kind], u.warm ? "warm" : "cold",
            RuinMaterials.STONE_NAMES[u.materials.stone], u.decay, u.floorY, u.depth);
    }

    /** A top-down map around the site (4 px per block) beside an isometric view of it in its terrain. */
    private static void render(TerrainModel model, RuinSites sites, RuinSite s, File out) throws Exception {
        int size = 96;
        int bpp = 1;
        int scale = 4;
        int minX = s.centreX() - size / 2;
        int minZ = s.centreZ() - size / 2;
        BufferedImage top = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int c = terrainColour(model, minX + i * bpp, minZ + j * bpp, model.seaLevel(), true);
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        top.setRGB(i * scale + a, j * scale + b, c);
                    }
                }
            }
        }
        IntBinaryOperator ground = (x, z) -> (int) Math.floor(model.interpolatedHeight(x, z));
        Voxels v = new Voxels();
        List<RuinPlan> plans = new ArrayList<>();
        if (s instanceof TrailRuin || s instanceof SunkenRuin) {
            plans.add(sites.plan(s));
        } else if (s instanceof Signpost p) {
            plans.add(SignpostPlanner.plan(sites, p));
        } else if (s instanceof LightStretch l) {
            for (int i = 0; i < l.posts.length; i++) {
                plans.add(LampPostPlanner.plan(l, i));
            }
        }
        for (RuinPlan p : plans) {
            v.add(p, ground, s instanceof SunkenRuin ? model.seaLevel() : -1000);
        }
        // the plan's highest cells over the map
        for (Map.Entry<Long, int[]> e : v.topDown().entrySet()) {
            int x = (int) (e.getKey() >> 32);
            int z = (int) (long) e.getKey().intValue();
            int i = x - minX;
            int j = z - minZ;
            if (i >= 0 && j >= 0 && i < size && j < size) {
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        top.setRGB(i * scale + a, j * scale + b, e.getValue()[0]);
                    }
                }
            }
        }
        // the terrain around the site in the isometric view
        Voxels scene = new Voxels();
        int r = s instanceof LightStretch ? 4 : 6;
        for (int x = s.minX - r; x <= s.maxX + r; x++) {
            for (int z = s.minZ - r; z <= s.maxZ + r; z++) {
                int g = ground.applyAsInt(x, z);
                boolean sea = s instanceof SunkenRuin;
                for (int y = g - 2; y <= g; y++) {
                    scene.put(x, y, z, y == g ? (sea ? 0xD8CC9A : 0x6A9A3C) : (sea ? 0xC8BC8A : 0x86603F), 0);
                }
                double td = model.trailDistance(x, z);
                if (!sea && td < 2.0 && TerrainModel.trailGround(model.sample((x + 2) & ~3, (z + 2) & ~3), model.seaLevel())) {
                    scene.put(x, g, z, 0x947A41, 0);
                }
            }
        }
        for (RuinPlan p : plans) {
            scene.add(p, ground, s instanceof SunkenRuin ? model.seaLevel() : -1000);
        }
        BufferedImage iso = scene.render(s instanceof LightStretch ? 4 : 8, false);
        BufferedImage combined = new BufferedImage(top.getWidth() + iso.getWidth(), Math.max(top.getHeight(), iso.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(new Color(182, 206, 232));
        g.fillRect(0, 0, combined.getWidth(), combined.getHeight());
        g.drawImage(top, 0, 0, null);
        g.drawImage(iso, top.getWidth(), 0, null);
        g.dispose();
        write(combined, out);
    }

    // =========================================================================================
    // A tiny voxel world: plans stamped like the feature stamps them, rendered isometrically
    // =========================================================================================

    /** Coloured voxels; a shape code says how much of the cell a block fills. */
    static final class Voxels {
        static final int FULL = 0;
        static final int BOTTOM = 1;
        static final int TOP = 2;
        static final int POST = 3;
        static final int SMALL = 4;
        static final int FLAT = 5;
        static final int WATER = 6;
        final Map<Long, int[]> map = new HashMap<>();

        static long key(int x, int y, int z) {
            return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
        }

        static int kx(long k) {
            return (int) ((k >>> 42) & 0x3FFFFF) << 10 >> 10;
        }

        static int ky(long k) {
            return (int) ((k >>> 22) & 0xFFFFF) << 12 >> 12;
        }

        static int kz(long k) {
            return (int) (k & 0x3FFFFF) << 10 >> 10;
        }

        void put(int x, int y, int z, int rgb, int shape) {
            map.put(key(x, y, z), new int[]{rgb, shape});
        }

        boolean occupied(int x, int y, int z) {
            int[] v = map.get(key(x, y, z));
            return v != null && v[1] != WATER;
        }

        static Voxels stamp(RuinPlan p, IntBinaryOperator ground, int seaLevel) {
            Voxels v = new Voxels();
            // ground only around what the plan places, so renders stay tight
            int x0 = Integer.MAX_VALUE;
            int z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int z1 = Integer.MIN_VALUE;
            for (int x = p.minX; x <= p.maxX(); x++) {
                for (int z = p.minZ; z <= p.maxZ(); z++) {
                    boolean any = p.fill(x, z) != 0;
                    for (int y = p.minY; y <= p.maxY() && !any; y++) {
                        any = p.get(x, y, z) != 0;
                    }
                    if (any) {
                        x0 = Math.min(x0, x);
                        z0 = Math.min(z0, z);
                        x1 = Math.max(x1, x);
                        z1 = Math.max(z1, z);
                    }
                }
            }
            for (RuinPlan.Decal d : p.decals) {
                x0 = Math.min(x0, d.x());
                z0 = Math.min(z0, d.z());
                x1 = Math.max(x1, d.x());
                z1 = Math.max(z1, d.z());
            }
            for (int x = x0 - 2; x <= x1 + 2; x++) {
                for (int z = z0 - 2; z <= z1 + 2; z++) {
                    int g = ground.applyAsInt(x, z);
                    v.put(x, g, z, seaLevel > g ? 0xD8CC9A : 0x6A9A3C, FULL);
                }
            }
            v.add(p, ground, seaLevel);
            return v;
        }

        /** Stamps a plan the way the feature does: foundations, cells, then ground-hugging decals. */
        void add(RuinPlan p, IntBinaryOperator ground, int seaLevel) {
            int anchor = p.mode == RuinPlan.GROUPED ? ground.applyAsInt(p.anchorX, p.anchorZ) : 0;
            for (int x = p.minX; x <= p.maxX(); x++) {
                for (int z = p.minZ; z <= p.maxZ(); z++) {
                    int g = ground.applyAsInt(x, z);
                    if (p.mode == RuinPlan.ABSOLUTE) {
                        int fill = p.fill(x, z);
                        if (fill != 0) {
                            for (int y = Math.max(g + 1, p.baseY - RuinPlan.MAX_FILL); y < p.baseY; y++) {
                                put(x, y, z, colour(fill, p.materials), FULL);
                            }
                        }
                    }
                    for (int y = p.minY; y <= p.maxY(); y++) {
                        int c = p.get(x, y, z);
                        if (c == 0) {
                            continue;
                        }
                        int wy = p.mode == RuinPlan.GROUPED ? anchor + y : y;
                        RuinPart part = RuinCell.part(c);
                        if (part == RuinPart.AIR) {
                            if (!RuinCell.soft(c)) {
                                map.remove(key(x, wy, z));
                            }
                            continue;
                        }
                        if (RuinCell.soft(c) && occupied(x, wy, z)) {
                            continue;
                        }
                        put(x, wy, z, colour(c, p.materials), shape(c));
                    }
                }
            }
            for (RuinPlan.Decal d : p.decals) {
                int g = ground.applyAsInt(d.x(), d.z());
                int y = g + d.dy();
                if (d.dy() > 0 && occupied(d.x(), y, d.z())) {
                    continue;
                }
                put(d.x(), y, d.z(), colour(d.cell(), p.materials), shape(d.cell()));
            }
        }

        /** Highest voxel per column: {colour}. */
        Map<Long, int[]> topDown() {
            Map<Long, int[]> top = new HashMap<>();
            Map<Long, Integer> topY = new HashMap<>();
            for (Map.Entry<Long, int[]> e : map.entrySet()) {
                long k = e.getKey();
                long col = (long) kx(k) << 32 | kz(k) & 0xFFFFFFFFL;
                int y = ky(k);
                Integer prev = topY.get(col);
                if (prev == null || y > prev) {
                    topY.put(col, y);
                    top.put(col, e.getValue());
                }
            }
            return top;
        }

        BufferedImage render(int s, boolean flip) {
            if (map.isEmpty()) {
                return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            }
            List<long[]> list = new ArrayList<>();
            int minSx = Integer.MAX_VALUE;
            int maxSx = Integer.MIN_VALUE;
            int minSy = Integer.MAX_VALUE;
            int maxSy = Integer.MIN_VALUE;
            for (long k : map.keySet()) {
                int x = kx(k);
                int y = ky(k);
                int z = kz(k);
                if (flip) {
                    x = -x;
                    z = -z;
                }
                list.add(new long[]{x, y, z, k});
                int sx = (x - z) * s;
                int sy = (x + z) * s / 2 - y * s;
                minSx = Math.min(minSx, sx - s);
                maxSx = Math.max(maxSx, sx + s);
                minSy = Math.min(minSy, sy - s);
                maxSy = Math.max(maxSy, sy + 2 * s);
            }
            list.sort((a, b) -> {
                long da = a[0] + a[2] + a[1];
                long db = b[0] + b[2] + b[1];
                return da != db ? Long.compare(da, db) : Long.compare(a[1], b[1]);
            });
            int w = maxSx - minSx + 4;
            int h = maxSy - minSy + 4;
            BufferedImage img = new BufferedImage(Math.min(w, 4000), Math.min(h, 4000), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(182, 206, 232));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            int ox = -minSx + 2;
            int oy = -minSy + 2;
            for (long[] e : list) {
                int[] v = map.get(e[3]);
                if (v[1] == WATER) {
                    continue;
                }
                double x0 = e[0];
                double z0 = e[2];
                double y0 = e[1];
                double x1 = x0 + 1;
                double z1 = z0 + 1;
                double y1 = y0 + 1;
                switch (v[1]) {
                    case BOTTOM -> y1 = y0 + 0.5;
                    case TOP -> y0 = y0 + 0.5;
                    case POST -> {
                        x0 += 0.35;
                        x1 -= 0.35;
                        z0 += 0.35;
                        z1 -= 0.35;
                    }
                    case SMALL -> {
                        x0 += 0.25;
                        x1 -= 0.25;
                        z0 += 0.25;
                        z1 -= 0.25;
                        y1 = y0 + 0.6;
                    }
                    case FLAT -> y1 = y0 + 0.1;
                    default -> {
                    }
                }
                cube(g, ox, oy, s, x0, y0, z0, x1, y1, z1, v[0]);
            }
            g.dispose();
            return img;
        }

        private static void cube(Graphics2D g, int ox, int oy, int s, double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
            int[][] top = {p(ox, oy, s, x0, y1, z0), p(ox, oy, s, x1, y1, z0), p(ox, oy, s, x1, y1, z1), p(ox, oy, s, x0, y1, z1)};
            int[][] right = {p(ox, oy, s, x1, y0, z0), p(ox, oy, s, x1, y1, z0), p(ox, oy, s, x1, y1, z1), p(ox, oy, s, x1, y0, z1)};
            int[][] left = {p(ox, oy, s, x0, y0, z1), p(ox, oy, s, x1, y0, z1), p(ox, oy, s, x1, y1, z1), p(ox, oy, s, x0, y1, z1)};
            fill(g, right, shade(rgb, 0.62));
            fill(g, left, shade(rgb, 0.8));
            fill(g, top, rgb);
        }

        private static int[] p(int ox, int oy, int s, double x, double y, double z) {
            return new int[]{ox + (int) Math.round((x - z) * s), oy + (int) Math.round((x + z) * s / 2.0 - y * s)};
        }

        private static void fill(Graphics2D g, int[][] pts, int rgb) {
            int[] xs = new int[pts.length];
            int[] ys = new int[pts.length];
            for (int i = 0; i < pts.length; i++) {
                xs[i] = pts[i][0];
                ys[i] = pts[i][1];
            }
            g.setColor(new Color(rgb));
            g.fillPolygon(xs, ys, pts.length);
        }

        private static int shade(int rgb, double f) {
            return rgb((int) ((rgb >> 16 & 255) * f), (int) ((rgb >> 8 & 255) * f), (int) ((rgb & 255) * f));
        }
    }

    // =========================================================================================
    // Approximate block colours
    // =========================================================================================

    static int shape(int c) {
        RuinPart p = RuinCell.part(c);
        return switch (p) {
            case MASONRY_SLAB, PLANK_SLAB, ROOF_SLAB, TRIM_SLAB -> RuinCell.top(c) ? Voxels.TOP : Voxels.BOTTOM;
            case FENCE, MASONRY_WALL, CHAIN, IRON_BARS, GLASS_PANE, LADDER, DOOR, STANDING_SIGN -> Voxels.POST;
            case LANTERN, FLOWER, SHORT_GRASS, FERN, TALL_GRASS, DEAD_BUSH, MUSHROOM, SWEET_BERRY_BUSH, CAMPFIRE, FLOWER_POT, ROCK, SEAGRASS,
                TALL_SEAGRASS, KELP, KELP_PLANT, SEA_PICKLE, CORAL_FAN, COBWEB, WALL_HANGING_SIGN, DECORATED_POT, VINE, GLOW_LICHEN -> Voxels.SMALL;
            case MOSS_CARPET, CARPET, LITTER -> Voxels.FLAT;
            case TRAPDOOR -> RuinCell.flag(c) ? Voxels.POST : RuinCell.top(c) ? Voxels.TOP : Voxels.FLAT;
            case WATER -> Voxels.WATER;
            default -> Voxels.FULL;
        };
    }

    private static final int[] PLANK = {0xA2834F, 0x72543A, 0xC0AF79, 0x43301A, 0xA85A32, 0xA0734D, 0x763631, 0xE3B3AD, 0xC4B048, 0x5E8A2E};
    private static final int[] LOG = {0x6D5532, 0x3B2711, 0xD8D7D2, 0x3C2E1A, 0x676157, 0x564419, 0x544030, 0x361925, 0x8A9A30, 0x5E8A2E};
    private static final int[] STRIPPED = {0xB1904F, 0x735A36, 0xC4B07B, 0x60482E, 0xAE5C3B, 0xAB8550, 0x773936, 0xD9A4A9, 0xC4B048, 0x5E8A2E};
    private static final int[] LEAVES = {0x4A7A2A, 0x3A5A3A, 0x6A8A3A, 0x3A6A2A, 0x5A8A2A, 0x3A8A1A, 0x4A7A2A, 0xE6A6C9, 0x3A8A1A, 0x6E9A3E};
    // main, quoin, roof-stone colours of the stone families
    private static final int[][] STONE = {
        {0x7F7F7F, 0x7B7B7B, 0x6E6E6E}, {0x7B7B7B, 0x8A8A8A, 0x6E6E6E}, {0x7F7F7F, 0x9A6A5A, 0x6E6E6E}, {0x4D4D50, 0x474749, 0x363639},
        {0xDFE0DC, 0xC4C4C6, 0xB0B0B2}, {0xD9CF9C, 0xE0D6A4, 0xC8BE8A}, {0xB95F21, 0xC0662A, 0xA8541C}, {0x8C6A4E, 0x8E6B4F, 0x7C5A40},
        {0x6E7D5F, 0x737E64, 0x5E6D4F}, {0x5F6159, 0x626660, 0x55574F}, {0x7B7B7B, 0x8A8A8A, 0x707070}, {0x5F6159, 0x6A6E68, 0x55574F},
        {0xEAE4DC, 0xF2EEE8, 0xDAD4CC}, {0xD9CF9C, 0xE0D6A4, 0xC8BE8A}};

    static int colour(int c, RuinMaterials m) {
        RuinPart p = RuinCell.part(c);
        int weathering = RuinCell.weathering(c);
        int[] stone = STONE[m.stone];
        int col = switch (p) {
            case FOUNDATION, MASONRY, CHIMNEY, MASONRY_STAIR, MASONRY_SLAB, MASONRY_WALL, PAVING -> stone[0];
            case QUOIN, TRIM, TRIM_STAIR, TRIM_SLAB, COLUMN_BASE -> stone[1];
            case PILLAR -> m.stone == RuinMaterials.MARBLE ? 0xEFEAE2 : stone[1] + 0x080808;
            case CAPITAL -> stone[2];
            case PLANKS, FLOOR, PLANK_STAIR, PLANK_SLAB, FENCE, DOOR, TRAPDOOR -> PLANK[m.wood];
            case LOG -> RuinCell.aux(c) == 1 ? 0x2A2420 : LOG[m.wood];
            case STRIPPED_LOG -> RuinCell.aux(c) == 1 ? 0x3A3020 : STRIPPED[m.wood];
            case ROOF, ROOF_STAIR, ROOF_SLAB -> m.roofCover == RuinMaterials.ROOF_TILE ? 0x363639
                : m.roofCover == RuinMaterials.ROOF_STONE ? stone[2] : PLANK[m.roofWood];
            case THATCH, HAY -> 0xA68A1D;
            case PACKED_MUD -> 0x8E6B4F;
            case SOIL -> switch (RuinCell.aux(c)) {
                case 1 -> 0x77553A;
                case 2 -> 0x5A3F1F;
                case 3 -> 0x86603F;
                default -> 0x6A9A3C;
            };
            case SEDIMENT, SUSPICIOUS_SAND -> 0xDBCFA3;
            case DIRT_PATH -> 0x947A41;
            case COARSE_DIRT -> 0x77553A;
            case GRAVEL, SUSPICIOUS_GRAVEL -> 0x83807E;
            case MOSS_BLOCK, MOSS_CARPET -> 0x5A7C2C;
            case WATER -> 0x3F76E4;
            case GLASS_PANE -> 0xC0E0F0;
            case IRON_BARS, CHAIN, ANVIL -> 0x5A5A5A;
            case LADDER -> 0x8A6A3A;
            case LEAVES -> LEAVES[m.leaves];
            case VINE -> 0x2F6B1E;
            case COBWEB -> 0xE8E8E8;
            case SHORT_GRASS, FERN, TALL_GRASS, SWEET_BERRY_BUSH -> 0x5F9F3A;
            case FLOWER -> 0xE04040;
            case DEAD_BUSH -> 0x8A6A3A;
            case MUSHROOM -> 0xB08060;
            case LITTER -> 0x9A7A3A;
            case ROCK -> RuinCell.aux(c) == 1 ? 0x6E7D5F : 0x8A8A8A;
            case CAMPFIRE -> RuinCell.flag(c) ? 0xFF9020 : 0x5A4A3A;
            case CRAFTING_TABLE, BARREL, COMPOSTER -> 0x8A6A3A;
            case CHEST -> 0xB07A2A;
            case FURNACE, CAULDRON -> 0x606060;
            case BOOKSHELF -> 0x7A5A3A;
            case FLOWER_POT, DECORATED_POT -> 0x9A5A3A;
            case BED -> 0xA02020;
            case CARPET -> 0xC0C0C0;
            case LANTERN -> 0xF0C050;
            case STANDING_SIGN, WALL_HANGING_SIGN -> 0xC8A070;
            case SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT -> 0x3A8A2A;
            case SEA_PICKLE -> 0x7A9A3A;
            case CORAL_FAN -> 0xD060A0;
            case WET_SPONGE -> 0xBFB54A;
            case GLOW_LICHEN -> 0x80A090;
            case SEA_LANTERN -> 0xD0E8E0;
            default -> 0xFF00FF;
        };
        if (weathering == RuinCell.MOSSY) {
            col = mix(col, 0x5A7A3A, 0.4);
        } else if (weathering == RuinCell.CRACKED) {
            col = mix(col, 0x404040, 0.15);
        } else if (weathering == RuinCell.ENCRUSTED) {
            col = 0x5FA59A;
        }
        return col;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return rgb(r, g, bl);
    }
}
