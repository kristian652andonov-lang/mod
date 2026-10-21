package com.terrahaul.devtools;

import com.terrahaul.worldgen.ruins.HousePlanner;
import com.terrahaul.worldgen.ruins.LampPostPlanner;
import com.terrahaul.worldgen.ruins.LandmarkPlanner;
import com.terrahaul.worldgen.ruins.LightStretch;
import com.terrahaul.worldgen.ruins.RuinBiomes;
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
import com.terrahaul.worldgen.ruins.WaysidePlanner;
import com.terrahaul.worldgen.noise.SeedMixer;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import javax.imageio.ImageIO;

/**
 * Development tool for the trail ruins (broken houses and the other ruins beside trails),
 * signposts, lamp posts and sunken ruins: statistics over a region, maps of where everything
 * stands, isometric renders of the plans, and checks of the finder, all from the pure planners
 * without starting Minecraft.
 * <pre>
 *   RuinStudy stats &lt;seed&gt; &lt;half&gt;                         counts per 1000 blocks of trail, coverage, placement audit, determinism
 *   RuinStudy map &lt;seed&gt; &lt;cx&gt; &lt;cz&gt; &lt;size&gt; &lt;bpp&gt; &lt;out.png&gt;  relief with trails and every site
 *   RuinStudy sheet &lt;outDir&gt; [seed] [zoom]                 every house layout, style and state, every other kind and variant, posts
 *   RuinStudy find &lt;seed&gt; &lt;outDir&gt; &lt;target&gt; &lt;count&gt; [x z]   finder results from a point (default 0 0), checked and rendered in place
 *   RuinStudy findall &lt;seed&gt; &lt;outDir&gt; [count]               every ruin target of the finder from three origins, checked; renders
 *   RuinStudy filtercheck &lt;seed&gt;                              with a synthetic world filter: the finder reports only sites that are built
 * </pre>
 * Without the game there is no world filter: biomes are estimated from the climate and no site
 * is excluded for vanilla structures (filtercheck installs a synthetic one).
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
            case "sheet" -> sheet(new File(args[1]), args.length > 2 ? Long.parseLong(args[2]) : 7L, args.length > 3 ? Integer.parseInt(args[3]) : 1);
            case "find" -> find(Long.parseLong(args[1]), new File(args[2]), args[3], Integer.parseInt(args[4]),
                args.length > 6 ? Integer.parseInt(args[5]) : 0, args.length > 6 ? Integer.parseInt(args[6]) : 0);
            case "findall" -> findAll(Long.parseLong(args[1]), new File(args[2]), args.length > 3 ? Integer.parseInt(args[3]) : 3);
            case "filtercheck" -> filterCheck(Long.parseLong(args[1]));
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    // =========================================================================================
    // stats
    // =========================================================================================

    /** What a survey of a square found; {@code km} counts the trail drawn on the ground, {@code laidKm} all laid trail. */
    private record Survey(List<TrailRuin> ruins, List<Signpost> signs, List<LightStretch> lights, double km, double laidKm, double km2,
                          double seconds, double cpuMeanMs, double cpuMaxMs) {
    }

    private static Survey survey(RuinSites sites, int half) {
        int tc = RuinSites.TRAIL_CELL;
        int c0 = Math.floorDiv(-half, tc);
        int c1 = Math.floorDiv(half - 1, tc);
        List<TrailRuin> ruins = new ArrayList<>();
        List<Signpost> signs = new ArrayList<>();
        List<LightStretch> lights = new ArrayList<>();
        // thread CPU time per cell, which other work on the machine does not inflate
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        long cpuSum = 0;
        long cpuMax = 0;
        long t0 = System.nanoTime();
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = c0; cz <= c1; cz++) {
                long a = bean.getCurrentThreadCpuTime();
                TrailSurvey.Cell cell = sites.trailCell(cx, cz);
                long dt = bean.getCurrentThreadCpuTime() - a;
                cpuSum += dt;
                cpuMax = Math.max(cpuMax, dt);
                ruins.addAll(cell.ruins());
                signs.addAll(cell.signposts());
                lights.addAll(cell.lights());
            }
        }
        long t1 = System.nanoTime();
        // a lattice point every 8 blocks on laid trail stands for about 16 blocks of trail; only
        // trail in biomes that show it counts (deserts and beaches have nothing beside their trails)
        double km = sites.stats.get(54) * 16.0 / 1000.0;
        double laidKm = sites.stats.get(19) * 16.0 / 1000.0;
        double km2 = Math.pow((c1 - c0 + 1) * tc / 1000.0, 2);
        int cells = (c1 - c0 + 1) * (c1 - c0 + 1);
        return new Survey(ruins, signs, lights, km, laidKm, km2, (t1 - t0) / 1e9, cpuSum / 1e6 / cells, cpuMax / 1e6);
    }

    private static void stats(long seed, int half) throws Exception {
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        Survey sv = survey(sites, half);
        double km = sv.km;
        System.out.printf(Locale.ROOT, "seed %d: %d trail cells (%.1f km2), %d with trails, drawn trail ~%.1f km (laid ~%.1f km, the rest in deserts "
            + "and on beaches), survey %.1f s%n", seed, sites.stats.get(0), sv.km2, sites.stats.get(1), km, sv.laidKm, sv.seconds);
        System.out.printf(Locale.ROOT, "survey time per cell: mean %.2f ms, max %.1f ms (thread CPU time: mean %.2f ms, max %.1f ms)%n",
            sites.stats.get(17) / 1e6 / Math.max(1, sites.stats.get(0)), sites.stats.get(18) / 1e6, sv.cpuMeanMs, sv.cpuMaxMs);
        // ruins by kind
        int[] byKind = new int[TrailRuin.KIND_NAMES.length];
        List<TrailRuin> houses = new ArrayList<>();
        for (TrailRuin r : sv.ruins) {
            byKind[r.kind]++;
            if (r.kind == TrailRuin.HOUSE) {
                houses.add(r);
            }
        }
        System.out.printf(Locale.ROOT, "PER 1000 BLOCKS OF TRAIL: broken houses %.2f, other path ruins %.2f, signposts %.2f, lamp posts %.2f "
                + "(%.2f stretches)%n", houses.size() / km, (sv.ruins.size() - houses.size()) / km, sv.signs.size() / km,
            countPosts(sv.lights) / km, sv.lights.size() / km);
        System.out.printf(Locale.ROOT, "path ruins %d = %.2f per 1000 blocks of trail; broken houses %d = %.2f%n", sv.ruins.size(),
            sv.ruins.size() / km, houses.size(), houses.size() / km);
        StringBuilder sb = new StringBuilder("  by kind:");
        for (int k = 0; k < byKind.length; k++) {
            sb.append(String.format(Locale.ROOT, " %s=%d (%.2f)", TrailRuin.KIND_NAMES[k], byKind[k], byKind[k] / km));
        }
        System.out.println(sb);
        int[] layout = new int[HousePlanner.LAYOUT_NAMES.length];
        int[] style = new int[HousePlanner.STYLE_NAMES.length];
        int[] state = new int[HousePlanner.STATE_NAMES.length];
        int[] climate = new int[RuinSites.CLIMATE_NAMES.length];
        for (TrailRuin h : houses) {
            layout[h.variant]++;
            style[h.style]++;
            state[h.state]++;
            climate[h.climate]++;
        }
        int[] chosen = new int[HousePlanner.LAYOUT_NAMES.length];
        for (int k = 0; k < chosen.length; k++) {
            chosen[k] = (int) sites.stats.get(48 + k);
        }
        System.out.println("  houses by layout: " + histogram(HousePlanner.LAYOUT_NAMES, layout) + "  (first drawn for their slots: "
            + histogram(HousePlanner.LAYOUT_NAMES, chosen) + "; a house with no room falls back a size)");
        System.out.println("  houses by style:  " + histogram(HousePlanner.STYLE_NAMES, style));
        System.out.println("  houses by state:  " + histogram(HousePlanner.STATE_NAMES, state));
        System.out.println("  houses by climate: " + histogram(RuinSites.CLIMATE_NAMES, climate));
        for (int k = 1; k < TrailRuin.KIND_NAMES.length; k++) {
            Map<String, Integer> v = new HashMap<>();
            for (TrailRuin r : sv.ruins) {
                if (r.kind == k) {
                    v.merge(RuinSites.variantName(k, r.variant), 1, Integer::sum);
                }
            }
            System.out.println("  " + TrailRuin.KIND_NAMES[k] + " variants: " + v);
        }
        System.out.printf(Locale.ROOT, "  slots %d, attempts %d; rejected: no tangent %d, off-axis %d, biome %d, on trail %d, slope %d, land %d, "
                + "border %d, feature %d, overlap %d; stream crossings %d%n", sites.stats.get(24), sites.stats.get(2), sites.stats.get(3),
            sites.stats.get(4), sites.stats.get(5), sites.stats.get(6), sites.stats.get(7), sites.stats.get(8), sites.stats.get(9),
            sites.stats.get(10), sites.stats.get(25), sites.stats.get(27));
        System.out.printf(Locale.ROOT, "  stream crossings without abutments: roll %d, no water in the lane %d, span %d, banks %d, ground %d, deck %d, "
                + "room %d%n", sites.stats.get(38), sites.stats.get(39), sites.stats.get(40), sites.stats.get(41), sites.stats.get(42),
            sites.stats.get(43), sites.stats.get(44));
        // signposts
        int[] signKind = new int[Signpost.KIND_NAMES.length];
        int[] signStyle = new int[Signpost.STYLE_NAMES.length];
        for (Signpost s : sv.signs) {
            signKind[s.kind]++;
            signStyle[s.style]++;
        }
        System.out.printf(Locale.ROOT, "junctions %d, trail ends %d (validated, in cells); signposts %d = %.2f per 1000 blocks: %s; posts %s; "
                + "marker crossings %d%n", sites.stats.get(12), sites.stats.get(13), sv.signs.size(), sv.signs.size() / km,
            histogram(Signpost.KIND_NAMES, signKind), histogram(Signpost.STYLE_NAMES, signStyle), sites.stats.get(28));
        signText(sites, sv.signs);
        markerSpacing(sv.signs);
        // lamp posts
        int posts = countPosts(sv.lights);
        int maxRun = 0;
        int[] lampStyle = new int[LightStretch.STYLE_NAMES.length];
        int[] lampState = new int[LampPostPlanner.STATE_NAMES.length];
        List<int[]> postXz = new ArrayList<>();
        for (LightStretch l : sv.lights) {
            maxRun = Math.max(maxRun, l.posts.length);
            for (LightStretch.Post p : l.posts) {
                lampStyle[p.style()]++;
                lampState[LampPostPlanner.state(p)]++;
                postXz.add(new int[]{p.x(), p.z()});
            }
        }
        System.out.printf(Locale.ROOT, "lamp stretches %d (%.2f per 1000 blocks), %d posts (%.2f per 1000 blocks), %.1f per stretch in a cell, "
                + "longest %d; lamp-line crossings %d%n", sv.lights.size(), sv.lights.size() / km, posts, posts / km,
            posts / Math.max(1.0, sv.lights.size()), maxRun, sites.stats.get(29));
        System.out.printf(Locale.ROOT, "  lit crossings in cells %d; refused: near a junction or end %d, no spot beside the path %d, chunk shift %d, "
                + "ground %d, near a ruin %d, near a sign %d, biome %d%n", sites.stats.get(30), sites.stats.get(31), sites.stats.get(32),
            sites.stats.get(33), sites.stats.get(34), sites.stats.get(35), sites.stats.get(36), sites.stats.get(37));
        System.out.println("  styles: " + histogram(LightStretch.STYLE_NAMES, lampStyle) + "  states: " + histogram(LampPostPlanner.STATE_NAMES, lampState));
        List<Double> nearestPost = nearestDistances(postXz);
        int close = 0;
        double closest = Double.MAX_VALUE;
        for (double d : nearestPost) {
            close += d < TrailSurvey.LAMP_MIN_GAP ? 1 : 0;
            closest = Math.min(closest, d);
        }
        System.out.printf(Locale.ROOT, "  spacing to the nearest other post: %s; posts closer than %d blocks to another: %d (closest %.1f)%n",
            percentiles(nearestPost), TrailSurvey.LAMP_MIN_GAP, close, closest);
        System.out.println("  lit runs (posts chained across cell borders within 66 blocks): " + runs(postXz));
        // coverage
        coverage(sites, half, houses, "broken house");
        coverage(sites, half, new ArrayList<>(sv.ruins), "path ruin (any kind)");
        // plans: time, size, floating cells, and the placement audit
        audit(model, sites, sv.ruins);
        postAudit(sites, sv.signs, sv.lights);
        standingAudit(model, sites, sv);
        // sunken ruins
        sunken(model, sites, half);
        // signpost text for a few posts of each kind
        int[] shown = new int[Signpost.KIND_NAMES.length];
        for (Signpost s : sv.signs) {
            if (shown[s.kind]++ < 2) {
                System.out.println("  " + describe(sites, s) + " at " + s.x + " " + s.z);
            }
        }
        int tc = RuinSites.TRAIL_CELL;
        determinism(seed, Math.floorDiv(-half, tc), Math.floorDiv(half - 1, tc), sites);
    }

    /**
     * The cost of working out the text of every signpost (the destinations each arm names), cold:
     * run right after the survey, before anything else asks for the boards and caches them. The
     * cells around are already surveyed, as they mostly are in a running game.
     */
    private static void signText(RuinSites sites, List<Signpost> signs) {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        long sum = 0;
        long max = 0;
        int empty = 0;
        for (Signpost s : signs) {
            long a = bean.getCurrentThreadCpuTime();
            SignpostPlanner.Board[] boards = SignpostPlanner.boards(sites, s);
            long dt = bean.getCurrentThreadCpuTime() - a;
            sum += dt;
            max = Math.max(max, dt);
            if (boards.length == 0) {
                empty++;
                System.out.println("  NO BOARDS on " + s.x + " " + s.z);
            }
        }
        System.out.printf(Locale.ROOT, "  sign text (cold, thread CPU time): %d posts, mean %.2f ms, max %.1f ms, total %.2f s; posts without boards %d%n",
            signs.size(), sum / 1e6 / Math.max(1, signs.size()), max / 1e6, sum / 1e9, empty);
    }

    /** How far direction posts and milestones stand from each other and from junction and end signs. */
    private static void markerSpacing(List<Signpost> signs) {
        List<Double> toMarker = new ArrayList<>();
        List<Double> toNode = new ArrayList<>();
        int closeMarkers = 0;
        for (Signpost a : signs) {
            if (a.kind != Signpost.WAYPOST && a.kind != Signpost.MILESTONE) {
                continue;
            }
            double m = Double.MAX_VALUE;
            double n = Double.MAX_VALUE;
            for (Signpost b : signs) {
                if (b == a) {
                    continue;
                }
                double d = Math.hypot(a.x - b.x, a.z - b.z);
                if (b.kind == Signpost.WAYPOST || b.kind == Signpost.MILESTONE) {
                    m = Math.min(m, d);
                } else {
                    n = Math.min(n, d);
                }
            }
            if (m < 400) {
                toMarker.add(m);
            }
            if (n < 400) {
                toNode.add(n);
            }
            closeMarkers += m < TrailSurvey.MARKER_SPACING ? 1 : 0;
        }
        System.out.printf(Locale.ROOT, "  markers: nearest other marker %s; closer than %d: %d; nearest junction or end sign %s%n", percentiles(toMarker),
            TrailSurvey.MARKER_SPACING, closeMarkers, percentiles(toNode));
    }

    private static int countPosts(List<LightStretch> lights) {
        int n = 0;
        for (LightStretch l : lights) {
            n += l.posts.length;
        }
        return n;
    }

    private static String histogram(String[] names, int[] counts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            b.append(i == 0 ? "" : " ").append(names[i]).append('=').append(counts[i]);
        }
        return b.toString();
    }

    private static List<Double> nearestDistances(List<int[]> pts) {
        Map<Long, List<int[]>> buckets = new HashMap<>();
        for (int[] p : pts) {
            buckets.computeIfAbsent(((long) (p[0] >> 6) << 32) ^ (p[1] >> 6 & 0xFFFFFFFFL), k -> new ArrayList<>()).add(p);
        }
        List<Double> out = new ArrayList<>();
        for (int[] p : pts) {
            double best = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<int[]> b = buckets.get(((long) ((p[0] >> 6) + dx) << 32) ^ ((p[1] >> 6) + dz & 0xFFFFFFFFL));
                    if (b == null) {
                        continue;
                    }
                    for (int[] q : b) {
                        if (q != p) {
                            best = Math.min(best, Math.hypot(q[0] - p[0], q[1] - p[1]));
                        }
                    }
                }
            }
            if (best < 64) {
                out.add(best);
            }
        }
        return out;
    }

    /** Lengths (in posts) of chains of posts no more than three lamp-line spacings apart, across cell borders. */
    private static String runs(List<int[]> pts) {
        int n = pts.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        Map<Long, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int[] p = pts.get(i);
            buckets.computeIfAbsent(((long) (p[0] >> 6) << 32) ^ (p[1] >> 6 & 0xFFFFFFFFL), k -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < n; i++) {
            int[] p = pts.get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Integer> b = buckets.get(((long) ((p[0] >> 6) + dx) << 32) ^ ((p[1] >> 6) + dz & 0xFFFFFFFFL));
                    if (b == null) {
                        continue;
                    }
                    for (int j : b) {
                        int[] q = pts.get(j);
                        if (j > i && Math.hypot(q[0] - p[0], q[1] - p[1]) <= 66) {
                            parent[root(parent, i)] = root(parent, j);
                        }
                    }
                }
            }
        }
        Map<Integer, Integer> size = new HashMap<>();
        for (int i = 0; i < n; i++) {
            size.merge(root(parent, i), 1, Integer::sum);
        }
        List<Double> sizes = new ArrayList<>();
        int in5 = 0;
        int in10 = 0;
        for (int s : size.values()) {
            sizes.add((double) s);
            in5 += s >= 5 ? s : 0;
            in10 += s >= 10 ? s : 0;
        }
        return String.format(Locale.ROOT, "%d runs, %.0f%% of the posts in runs of 5 or more, %.0f%% in runs of 10 or more, posts per run %s",
            sizes.size(), 100.0 * in5 / Math.max(1, n), 100.0 * in10 / Math.max(1, n), percentiles(sizes));
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static String percentiles(List<Double> v) {
        if (v.isEmpty()) {
            return "none";
        }
        Collections.sort(v);
        int n = v.size();
        return String.format(Locale.ROOT, "n %d, p10 %.0f, median %.0f, p90 %.0f, max %.0f", n, v.get(n / 10), v.get(n / 2), v.get(n * 9 / 10),
            v.get(n - 1));
    }

    /**
     * Distance from the drawn trail to the nearest site (the outer 600 blocks left out, where
     * sites beyond the surveyed square would count). Trail in deserts and on beaches is left out:
     * the surface rule does not draw it, and nothing stands beside it.
     */
    private static void coverage(RuinSites sites, int half, List<? extends RuinSite> sitesList, String what) {
        Map<Long, List<int[]>> buckets = new HashMap<>();
        for (RuinSite s : sitesList) {
            buckets.computeIfAbsent(((long) (s.x >> 8) << 32) ^ (s.z >> 8 & 0xFFFFFFFFL), k -> new ArrayList<>()).add(new int[]{s.x, s.z});
        }
        List<Double> dist = new ArrayList<>();
        int tc = RuinSites.TRAIL_CELL;
        int a = Math.floorDiv(-half, tc) * tc;
        int b = (Math.floorDiv(half - 1, tc) + 1) * tc;
        for (int x = a + 4; x < b; x += 8) {
            for (int z = a + 4; z < b; z += 8) {
                if (Math.abs(x) > half - 600 || Math.abs(z) > half - 600 || !sites.trailShown(x, z)) {
                    continue;
                }
                double best = 4000;
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        List<int[]> l = buckets.get(((long) ((x >> 8) + dx) << 32) ^ ((z >> 8) + dz & 0xFFFFFFFFL));
                        if (l != null) {
                            for (int[] p : l) {
                                best = Math.min(best, Math.hypot(p[0] - x, p[1] - z));
                            }
                        }
                    }
                }
                dist.add(best);
            }
        }
        Collections.sort(dist);
        int n = dist.size();
        int far = 0;
        int farther = 0;
        for (double d : dist) {
            far += d > 250 ? 1 : 0;
            farther += d > 400 ? 1 : 0;
        }
        System.out.printf(Locale.ROOT, "distance from drawn trail to nearest %s: median %.0f, p90 %.0f, p99 %.0f, %.1f%% farther than 250, "
            + "%.1f%% farther than 400%n", what, dist.get(n / 2), dist.get(n * 9 / 10), dist.get(n * 99 / 100), 100.0 * far / n, 100.0 * farther / n);
    }

    /**
     * Plans of every ruin: time, size, chests, floating cells; and where they stand: no placed
     * block in a water column (bridge stubs reach over the water above its surface), none on a
     * cliff, none on the trail itself except footpaths and decals.
     */
    private static void audit(TerrainModel m, RuinSites sites, List<TrailRuin> ruins) {
        long pt = 0;
        long ptMax = 0;
        int floating = 0;
        int cells = 0;
        int chests = 0;
        int wetColumns = 0;
        int bridgeOverWater = 0;
        int cliffColumns = 0;
        int onTrail = 0;
        int outsideBox = 0;
        int[] wetByKind = new int[TrailRuin.KIND_NAMES.length];
        int decalsOnPath = 0;
        int houses = 0;
        int burnedHouses = 0;
        int beds = 0;
        int frames = 0;
        ColumnSample cs = new ColumnSample();
        for (TrailRuin r : ruins) {
            long a = System.nanoTime();
            RuinPlan p = sites.plan(r);
            long dt = System.nanoTime() - a;
            pt += dt;
            ptMax = Math.max(ptMax, dt);
            floating += p.floatingCells();
            cells += p.countCells();
            // the feature builds a ruin only in chunks its site box touches, and the box must stay in its cell
            int tc = RuinSites.TRAIL_CELL;
            if (p.minX < r.minX || p.maxX() > r.maxX || p.minZ < r.minZ || p.maxZ() > r.maxZ || Math.floorDiv(r.minX, tc) != Math.floorDiv(r.maxX, tc)
                || Math.floorDiv(r.minZ, tc) != Math.floorDiv(r.maxZ, tc)) {
                outsideBox++;
            }
            for (RuinPlan.Extra e : p.extras) {
                chests += e.lootTable() != null && e.lootTable().contains("chests") ? 1 : 0;
            }
            for (RuinPlan.Decal d : p.decals) {
                RuinPart part = RuinCell.part(d.cell());
                boolean plant = switch (part) {
                    case SHORT_GRASS, FERN, TALL_GRASS, FLOWER, DEAD_BUSH, MUSHROOM, MOSS_CARPET, LITTER -> true;
                    default -> false;
                };
                if (d.dy() > 0 && !plant && m.trailDistance(d.x(), d.z()) < 2.6) {
                    decalsOnPath++;
                }
            }
            if (r.kind == TrailRuin.HOUSE) {
                houses++;
                burnedHouses += r.state == HousePlanner.BURNED || r.state == HousePlanner.FOUNDATIONS ? 1 : 0;
                boolean bed = false;
                boolean frame = false;
                for (int x = p.minX; x <= p.maxX(); x++) {
                    for (int z = p.minZ; z <= p.maxZ(); z++) {
                        for (int y = p.minY; y <= p.maxY(); y++) {
                            int c = p.get(x, y, z);
                            bed |= c != 0 && RuinCell.part(c) == RuinPart.BED;
                            // the rotten frame: a plank stair with a slab at its foot
                            frame |= c != 0 && RuinCell.part(c) == RuinPart.PLANK_STAIR && !RuinCell.top(c)
                                && RuinCell.part(p.get(x - RuinCell.DX[RuinCell.dir(c)], y, z - RuinCell.DZ[RuinCell.dir(c)])) == RuinPart.PLANK_SLAB;
                        }
                    }
                }
                beds += bed ? 1 : 0;
                frames += frame && !bed ? 1 : 0;
            }
            for (int x = p.minX; x <= p.maxX(); x++) {
                for (int z = p.minZ; z <= p.maxZ(); z++) {
                    boolean solid = p.fill(x, z) != 0;
                    for (int y = p.minY; y <= p.maxY() && !solid; y++) {
                        int c = p.get(x, y, z);
                        solid = c != 0 && RuinCell.part(c) != RuinPart.AIR && !RuinCell.soft(c);
                    }
                    if (!solid) {
                        continue;
                    }
                    if (RuinSites.water(m, x, z)) {
                        if (r.kind == TrailRuin.BRIDGE && p.fill(x, z) == 0) {
                            bridgeOverWater++;
                        } else {
                            wetColumns++;
                            wetByKind[r.kind]++;
                        }
                    }
                    m.sampleInto(x & ~3, z & ~3, cs);
                    if (cs.cliff >= 0.25) {
                        cliffColumns++;
                    }
                    if (m.trailDistance(x, z) < 2.0 && r.kind != TrailRuin.BRIDGE) {
                        onTrail++;
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "plans %d: mean %.2f ms, max %.1f ms, mean %d cells, %d chests with loot, floating cells after prune: %d%n",
            ruins.size(), pt / 1e6 / Math.max(1, ruins.size()), ptMax / 1e6, cells / Math.max(1, ruins.size()), chests, floating);
        System.out.printf(Locale.ROOT, "beds: %d of %d houses keep a real bed (%d of them burned or down to foundations, which keep none), %d more "
            + "the rotten frame of one%n", beds, houses, burnedHouses, frames);
        System.out.printf(Locale.ROOT, "placement audit: built columns in water %d %s, bridge stubs over water %d, on cliffs %d, on the path %d, "
            + "things on the ground within 2.6 blocks of the trail's centre (boards, rubble, loads; plants aside) %d, plans outside their site box "
            + "or cell %d%n", wetColumns, histogram(TrailRuin.KIND_NAMES, wetByKind), bridgeOverWater, cliffColumns, onTrail, decalsOnPath, outsideBox);
    }

    /**
     * Signposts and lamp posts: floating cells, and anything a post places outside its own chunk
     * (which builds it alone, so the rest would be lost).
     */
    private static void postAudit(RuinSites sites, List<Signpost> signs, List<LightStretch> lights) {
        int floating = 0;
        int outside = 0;
        int inWater = 0;
        int plans = 0;
        List<RuinPlan> all = new ArrayList<>();
        List<int[]> anchors = new ArrayList<>();
        for (Signpost s : signs) {
            all.add(SignpostPlanner.plan(sites, s));
            anchors.add(new int[]{s.x, s.z});
        }
        for (LightStretch l : lights) {
            for (LightStretch.Post p : l.posts) {
                all.add(LampPostPlanner.plan(p));
                anchors.add(new int[]{p.x(), p.z()});
            }
        }
        for (int i = 0; i < all.size(); i++) {
            RuinPlan p = all.get(i);
            int cx = anchors.get(i)[0] >> 4;
            int cz = anchors.get(i)[1] >> 4;
            plans++;
            floating += p.floatingCells();
            boolean out = false;
            for (int x = p.minX; x <= p.maxX(); x++) {
                for (int z = p.minZ; z <= p.maxZ(); z++) {
                    for (int y = p.minY; y <= p.maxY(); y++) {
                        out |= p.get(x, y, z) != 0 && (x >> 4 != cx || z >> 4 != cz);
                    }
                }
            }
            for (RuinPlan.Decal d : p.decals) {
                out |= d.x() >> 4 != cx || d.z() >> 4 != cz;
            }
            outside += out ? 1 : 0;
            // anything of the post over a stream channel, a pond or the sea
            boolean wet = false;
            for (int x = p.minX; x <= p.maxX() && !wet; x++) {
                for (int z = p.minZ; z <= p.maxZ() && !wet; z++) {
                    boolean any = false;
                    for (int y = p.minY; y <= p.maxY(); y++) {
                        any |= p.get(x, y, z) != 0;
                    }
                    for (RuinPlan.Decal d : p.decals) {
                        any |= d.x() == x && d.z() == z;
                    }
                    wet = any && RuinSites.water(sites.model(), x, z);
                }
            }
            if (wet) {
                inWater++;
                if (inWater <= 5) {
                    System.out.println("  POST OVER WATER at " + anchors.get(i)[0] + " " + anchors.get(i)[1]);
                }
            }
        }
        System.out.printf(Locale.ROOT, "posts: %d signpost and lamp-post plans, floating cells %d, posts reaching out of their chunk %d, "
            + "posts with a part over water %d%n", plans, floating, outside, inWater);
    }

    /**
     * The finder's standing point ({@link RuinSites#visit}) of every site, checked with
     * {@link #standCheck}; beside it the same check on the site's own position (the body of a
     * ruin, the post itself), which is what the finder used to report.
     */
    private static void standingAudit(TerrainModel m, RuinSites sites, Survey sv) {
        int[] bad = new int[TrailRuin.KIND_NAMES.length + 2];
        int[] oldBad = new int[bad.length];
        int[] n = new int[bad.length];
        List<RuinSite> all = new ArrayList<>(sv.ruins);
        all.addAll(sv.signs);
        all.addAll(sv.lights);
        int shown = 0;
        for (RuinSite s : all) {
            int k = s instanceof TrailRuin t ? t.kind : s instanceof Signpost ? bad.length - 2 : bad.length - 1;
            n[k]++;
            int[] v = sites.visit(s);
            String why = standCheck(m, sites, v[0], v[1], v[2], false);
            if (why != null) {
                bad[k]++;
                if (shown++ < 12) {
                    System.out.println("  BAD STANDING POINT " + v[0] + " " + v[1] + " " + v[2] + " (" + describe(sites, s) + "): " + why);
                }
            }
            if (standCheck(m, sites, s.x, s.y, s.z, false) != null) {
                oldBad[k]++;
            }
        }
        StringBuilder b = new StringBuilder("finder standing points that fail (feet, head, ground, water below): ");
        int total = 0;
        for (int k = 0; k < bad.length; k++) {
            String name = k < TrailRuin.KIND_NAMES.length ? TrailRuin.KIND_NAMES[k] : k == bad.length - 2 ? "signpost" : "lamp_stretch";
            b.append(String.format(Locale.ROOT, "%s %d/%d (site position %d) ", name, bad[k], n[k], oldBad[k]));
            total += bad[k];
        }
        System.out.println(b.append("; total ").append(total));
    }

    private static void sunken(TerrainModel model, RuinSites sites, int half) {
        int sc = RuinSites.SUNKEN_CELL;
        int s0 = Math.floorDiv(-half, sc);
        int s1 = Math.floorDiv(half - 1, sc);
        int sunken = 0;
        int floating = 0;
        for (int cx = s0; cx <= s1; cx++) {
            for (int cz = s0; cz <= s1; cz++) {
                SunkenRuin s = sites.sunken(cx, cz);
                if (s != null) {
                    sunken++;
                    floating += sites.plan(s).floatingCells();
                }
            }
        }
        System.out.printf(Locale.ROOT, "sunken ruins %d, floating cells %d%n", sunken, floating);
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
                System.out.println("  differs: cell " + c[0] + " " + c[1]);
            }
        }
        System.out.printf(Locale.ROOT, "determinism: %d cells recomputed in reverse on another thread, %d differ%n", cells.size(), diff);
    }

    private static long cellHash(RuinSites sites, int[] c) {
        TrailSurvey.Cell cell = sites.trailCell(c[0], c[1]);
        long h = 17;
        for (TrailRuin r : cell.ruins()) {
            h = h * 31 + r.seed + r.x * 7L + r.z * 13L + r.kind * 101L + sites.plan(r).fingerprint();
        }
        for (Signpost s : cell.signposts()) {
            h = h * 31 + s.x * 7L + s.z * 13L + s.kind;
        }
        for (LightStretch l : cell.lights()) {
            for (LightStretch.Post p : l.posts) {
                h = h * 31 + p.x() * 7L + p.z() * 13L + p.toTrail() + p.style() * 3L + LampPostPlanner.plan(p).fingerprint();
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

    private static final String[] SHORT = {"H", "T", "W", "Wg", "S", "C", "F", "B", "Ch"};

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
                img.setRGB(i, j, terrainColour(model, minX + i * bpp, minZ + j * bpp, sl, bpp <= 2));
            }
        }
        Graphics2D g = img.createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        int tc = RuinSites.TRAIL_CELL;
        for (int tx = Math.floorDiv(minX, tc); tx <= Math.floorDiv(minX + size, tc); tx++) {
            for (int tz = Math.floorDiv(minZ, tc); tz <= Math.floorDiv(minZ + size, tc); tz++) {
                TrailSurvey.Cell cell = sites.trailCell(tx, tz);
                for (LightStretch l : cell.lights()) {
                    g.setColor(Color.YELLOW);
                    for (LightStretch.Post p : l.posts) {
                        int sx = (p.x() - minX) / bpp;
                        int sz = (p.z() - minZ) / bpp;
                        g.fillRect(sx - 1, sz - 1, 3, 3);
                    }
                }
                for (TrailRuin r : cell.ruins()) {
                    g.setColor(r.kind == TrailRuin.HOUSE ? new Color(255, 40, 40) : new Color(255, 140, 0));
                    g.fillRect((r.minX - minX) / bpp, (r.minZ - minZ) / bpp, Math.max(2, (r.maxX - r.minX) / bpp), Math.max(2, (r.maxZ - r.minZ) / bpp));
                    g.setColor(Color.WHITE);
                    g.drawString(SHORT[r.kind], (r.maxX - minX) / bpp + 2, (r.minZ - minZ) / bpp + 8);
                }
                for (Signpost s : cell.signposts()) {
                    int sx = (s.x - minX) / bpp;
                    int sz = (s.z - minZ) / bpp;
                    g.setColor(switch (s.kind) {
                        case Signpost.JUNCTION -> Color.MAGENTA;
                        case Signpost.END -> Color.BLUE;
                        default -> Color.CYAN;
                    });
                    g.fillOval(sx - 3, sz - 3, 7, 7);
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
        if (s.stream > 0.0 && model.streamDistance(x, z) < 1.25 + 1.5 * s.stream) {
            rgb = rgb(60, 110, 200);
        }
        // drawn trail light; laid trail where the biome shows none (deserts, beaches) dark
        double d = model.trailDistance(x, z);
        if (trails && d < 2.0) {
            rgb = RuinSites.of(model).trailShown(x, z) ? rgb(200, 160, 96) : rgb(120, 90, 60);
        } else if (!trails && d < 3.0 && TerrainModel.trailGround(s, sl)) {
            rgb = RuinSites.of(model).trailShown(x, z) || d >= 2.0 ? rgb(200, 160, 96) : rgb(120, 90, 60);
        }
        return rgb;
    }

    private static int rgb(int r, int g, int b) {
        return Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
    }

    // =========================================================================================
    // sheet: every house layout, style and state; every other kind and variant; posts
    // =========================================================================================

    /** Materials and climate of a demo in each house style. */
    private static RuinMaterials styleMaterials(int style) {
        return switch (style) {
            case HousePlanner.LOG_CABIN -> new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.SPRUCE, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD,
                RuinMaterials.SPRUCE);
            case HousePlanner.STONE -> new RuinMaterials(RuinMaterials.GRANITE, RuinMaterials.SPRUCE, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_STONE,
                RuinMaterials.SPRUCE);
            case HousePlanner.ADOBE -> new RuinMaterials(RuinMaterials.MUD, RuinMaterials.ACACIA, RuinMaterials.ACACIA, RuinMaterials.ROOF_WOOD,
                RuinMaterials.ACACIA);
            case HousePlanner.RAISED -> new RuinMaterials(RuinMaterials.MOSSY, RuinMaterials.BAMBOO, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_WOOD,
                RuinMaterials.JUNGLE);
            default -> new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.OAK, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD, RuinMaterials.AZALEA);
        };
    }

    private static int styleClimate(int style) {
        return switch (style) {
            case HousePlanner.LOG_CABIN -> RuinSites.COLD;
            case HousePlanner.STONE -> RuinSites.UPLAND;
            case HousePlanner.ADOBE -> RuinSites.HOT_DRY;
            case HousePlanner.RAISED -> RuinSites.HOT_WET;
            default -> RuinSites.TEMPERATE;
        };
    }

    private static double styleWet(int style) {
        return style == HousePlanner.ADOBE ? 0.2 : style == HousePlanner.RAISED ? 0.8 : 0.55;
    }

    private static void sheet(File dir, long seed, int zoom) throws Exception {
        dir.mkdirs();
        int floating = 0;
        int plans = 0;
        // every layout in every style (the state that shows the building best: the roof fallen in)
        for (int style = 0; style < HousePlanner.STYLE_NAMES.length; style++) {
            List<BufferedImage> row = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int layout = 0; layout < HousePlanner.LAYOUT_NAMES.length; layout++) {
                if (!HousePlanner.fits(layout, style)) {
                    continue;
                }
                RuinPlan p = RuinDemos.ruin(TrailRuin.HOUSE, layout, style, HousePlanner.ROOF_FALLEN, styleClimate(style), styleMaterials(style), 0.45,
                    styleWet(style), seed + layout * 101L + style * 7L);
                floating += p.floatingCells();
                plans++;
                Voxels v = Voxels.stamp(p, (x, z) -> 64, -1000);
                row.add(v.render(zoom * 10, false));
                row.add(v.render(zoom * 10, true));
                labels.add(HousePlanner.LAYOUT_NAMES[layout] + ", " + HousePlanner.STYLE_NAMES[style] + ", from SE");
                labels.add("from NW (front, trail side)");
            }
            write(tile(row, labels, 4), new File(dir, "house_style_" + HousePlanner.STYLE_NAMES[style] + ".png"));
        }
        // every state of decay, for a cottage and a two-room house of each style
        for (int style = 0; style < HousePlanner.STYLE_NAMES.length; style++) {
            List<BufferedImage> row = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int state = 0; state < HousePlanner.STATE_NAMES.length; state++) {
                if (!HousePlanner.fitsState(style, state)) {
                    continue;
                }
                for (int layout : new int[]{HousePlanner.COTTAGE, HousePlanner.TWO_ROOM}) {
                    RuinPlan p = RuinDemos.ruin(TrailRuin.HOUSE, layout, style, state, styleClimate(style), styleMaterials(style), 0.6, styleWet(style),
                        seed * 3 + state * 13L + layout);
                    floating += p.floatingCells();
                    plans++;
                    Voxels v = Voxels.stamp(p, (x, z) -> 64, -1000);
                    row.add(v.render(zoom * 10, layout == HousePlanner.TWO_ROOM));
                    labels.add(HousePlanner.STATE_NAMES[state] + ", " + HousePlanner.LAYOUT_NAMES[layout] + " " + HousePlanner.STYLE_NAMES[style]);
                }
            }
            write(tile(row, labels, 4), new File(dir, "house_states_" + HousePlanner.STYLE_NAMES[style] + ".png"));
        }
        // the other kinds, every variant at two decays
        RuinMaterials stone = new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.SPRUCE, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD,
            RuinMaterials.SPRUCE);
        RuinMaterials ashlar = new RuinMaterials(RuinMaterials.ASHLAR, RuinMaterials.DARK_OAK, RuinMaterials.DARK_OAK, RuinMaterials.ROOF_TILE,
            RuinMaterials.OAK);
        RuinMaterials sand = new RuinMaterials(RuinMaterials.SANDSTONE, RuinMaterials.ACACIA, RuinMaterials.ACACIA, RuinMaterials.ROOF_WOOD,
            RuinMaterials.ACACIA);
        RuinMaterials oak = new RuinMaterials(RuinMaterials.COBBLE, RuinMaterials.OAK, RuinMaterials.SPRUCE, RuinMaterials.ROOF_WOOD,
            RuinMaterials.OAK);
        for (int kind = 1; kind < TrailRuin.KIND_NAMES.length; kind++) {
            List<BufferedImage> row = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            int variants = variantCount(kind);
            for (int variant = 0; variant < variants; variant++) {
                for (double decay : new double[]{0.35, 0.8}) {
                    RuinMaterials mat = switch (kind) {
                        case TrailRuin.CHAPEL -> ashlar;
                        case TrailRuin.WELL -> variant == WaysidePlanner.TROUGH ? sand : stone;
                        case TrailRuin.WAGON, TrailRuin.CAMPSITE -> oak;
                        default -> stone;
                    };
                    int climate = kind == TrailRuin.WELL && variant == WaysidePlanner.TROUGH ? RuinSites.HOT_DRY : RuinSites.TEMPERATE;
                    RuinPlan p = RuinDemos.ruin(kind, variant, 0, 0, climate, mat, decay, 0.6, seed + kind * 1000L + variant * 17L + (long) (decay * 100));
                    floating += p.floatingCells();
                    plans++;
                    Voxels v;
                    if (kind == TrailRuin.BRIDGE) {
                        int span = RuinDemos.BRIDGE_SPAN;
                        IntBinaryOperator bank = (x, z) -> z >= 0 && z < span ? 61 : 64;
                        v = Voxels.stamp(p, bank, -1000);
                        for (int x = p.minX - 2; x <= p.maxX() + 2; x++) {
                            for (int z = 0; z < span; z++) {
                                v.put(x, 62, z, 0x3F76E4, Voxels.FULL);
                                v.put(x, 63, z, 0x3F76E4, Voxels.FLAT);
                            }
                        }
                    } else {
                        v = Voxels.stamp(p, (x, z) -> 64, -1000);
                    }
                    row.add(v.render(zoom * (kind == TrailRuin.FIELD ? 7 : 11), decay > 0.5));
                    labels.add(String.format(Locale.ROOT, "%s %s, decay %.2f%s", TrailRuin.KIND_NAMES[kind], RuinSites.variantName(kind, variant), decay,
                        decay > 0.5 ? ", from NW" : ""));
                }
            }
            write(tile(row, labels, 4), new File(dir, "kind_" + TrailRuin.KIND_NAMES[kind] + ".png"));
        }
        // signposts: every kind in every post style
        List<BufferedImage> row = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int kind = 0; kind < Signpost.KIND_NAMES.length; kind++) {
            for (int style = 0; style < Signpost.STYLE_NAMES.length; style++) {
                RuinPlan p = RuinDemos.signpost(kind, style, styleMaterials(HousePlanner.LOG_CABIN), seed + kind * 4L + style);
                floating += p.floatingCells();
                plans++;
                Voxels v = Voxels.stamp(p, (x, z) -> 64, -1000);
                row.add(v.render(zoom * 16, false));
                labels.add(Signpost.KIND_NAMES[kind] + ", " + Signpost.STYLE_NAMES[style] + " post");
            }
        }
        write(tile(row, labels, 4), new File(dir, "signposts.png"));
        // lamp posts: every style, a row of posts in their various states
        row = new ArrayList<>();
        labels = new ArrayList<>();
        for (int style = 0; style < LightStretch.STYLE_NAMES.length; style++) {
            for (LightStretch.Post post : RuinDemos.lampPosts(style, 0.75, 0.7, seed + style, 6)) {
                RuinPlan p = LampPostPlanner.plan(post);
                floating += p.floatingCells();
                plans++;
                Voxels v = Voxels.stamp(p, (x, z) -> 64, -1000);
                row.add(v.render(zoom * 14, false));
                labels.add(LightStretch.STYLE_NAMES[style] + " " + RuinDemos.lampState(post));
            }
        }
        write(tile(row, labels, 6), new File(dir, "lamp_posts.png"));
        System.out.println("floating cells over all " + plans + " demo plans: " + floating);
    }

    private static int variantCount(int kind) {
        return switch (kind) {
            case TrailRuin.WATCHTOWER -> LandmarkPlanner.TOWER_NAMES.length;
            case TrailRuin.CHAPEL -> LandmarkPlanner.CHAPEL_NAMES.length;
            case TrailRuin.BRIDGE -> LandmarkPlanner.BRIDGE_NAMES.length;
            case TrailRuin.WELL -> WaysidePlanner.WELL_NAMES.length;
            case TrailRuin.WAGON -> WaysidePlanner.WAGON_NAMES.length;
            case TrailRuin.SHRINE -> WaysidePlanner.SHRINE_NAMES.length;
            case TrailRuin.CAMPSITE -> WaysidePlanner.CAMP_NAMES.length;
            case TrailRuin.FIELD -> WaysidePlanner.FIELD_NAMES.length;
            default -> 1;
        };
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
    // find, findall, filtercheck: the finder's answers, checked against the sites
    // =========================================================================================

    /**
     * The site a finder result leads to: a built site of the target's selector whose standing
     * point ({@link RuinSites#visit}) is the reported position, or {@code null}.
     */
    private static RuinSite match(RuinSites sites, int selector, TerrainFinder.Result r) {
        int type = selector & 15;
        int cellSize = type == RuinSites.SUNKEN_RUIN ? RuinSites.SUNKEN_CELL : RuinSites.TRAIL_CELL;
        // the standing point lies within a few blocks of its site, which may be in the next cell
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (RuinSite s : sites.sitesInCell(type, Math.floorDiv(r.x(), cellSize) + dx, Math.floorDiv(r.z(), cellSize) + dz)) {
                    if (!RuinSites.matches(s, selector) || !sites.allowed(s)) {
                        continue;
                    }
                    int[] v = sites.visit(s);
                    if (v[0] == r.x() && v[1] == r.y() && v[2] == r.z()) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    /** The plans a site builds (a stretch builds one per post). */
    private static List<RuinPlan> plansOf(RuinSites sites, RuinSite s) {
        List<RuinPlan> plans = new ArrayList<>();
        if (s instanceof TrailRuin || s instanceof SunkenRuin) {
            plans.add(sites.plan(s));
        } else if (s instanceof Signpost p) {
            plans.add(SignpostPlanner.plan(sites, p));
        } else if (s instanceof LightStretch l) {
            for (LightStretch.Post p : l.posts) {
                plans.add(LampPostPlanner.plan(p));
            }
        }
        return plans;
    }

    /**
     * Checks a finder result the way a player meets it after the suggested teleport. The plans of
     * every site within 24 blocks are stamped onto the model ground as the feature stamps them
     * (an emulation independent of the finder's own search), then the reported cell and the one
     * above must be free (air, or a plant one walks through), the cell below must carry a player,
     * and no water may lie within three blocks below. Returns {@code null} if all is well, else
     * what is wrong.
     */
    private static String standCheck(TerrainModel m, RuinSites sites, int x, int y, int z, boolean underwater) {
        IntBinaryOperator ground = (a, b) -> (int) Math.floor(m.interpolatedHeight(a, b));
        Voxels v = new Voxels();
        for (int a = x - 3; a <= x + 3; a++) {
            for (int b = z - 3; b <= z + 3; b++) {
                int g = ground.applyAsInt(a, b);
                for (int k = 0; k <= 6; k++) {
                    v.put(a, g - k, b, 0x6A9A3C, Voxels.FULL);
                }
            }
        }
        int tc = RuinSites.TRAIL_CELL;
        for (int type : new int[]{RuinSites.TRAIL_RUIN, RuinSites.SIGNPOST, RuinSites.PATH_LIGHTS, RuinSites.SUNKEN_RUIN}) {
            int size = type == RuinSites.SUNKEN_RUIN ? RuinSites.SUNKEN_CELL : tc;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (RuinSite s : sites.sitesInCell(type, Math.floorDiv(x, size) + dx, Math.floorDiv(z, size) + dz)) {
                        if (s.overlaps(x - 24, z - 24, x + 24, z + 24) && sites.allowed(s)) {
                            for (RuinPlan p : plansOf(sites, s)) {
                                v.add(p, ground, underwater ? m.seaLevel() : -1000);
                            }
                        }
                    }
                }
            }
        }
        if (!underwater && RuinSites.water(m, x, z)) {
            return "in a water column";
        }
        for (int k = 0; k <= 1; k++) {
            int[] c = v.at(x, y + k, z);
            if (c != null && !(c[2] >= 0 && walkThrough(RuinCell.part(c[2]), underwater))) {
                return (k == 0 ? "feet in " : "head in ") + (c[2] < 0 ? "terrain" : RuinCell.part(c[2]).name());
            }
        }
        int[] below = v.at(x, y - 1, z);
        if (below == null || below[2] >= 0 && !carries(RuinCell.part(below[2]))) {
            return "nothing to stand on (" + (below == null ? "air" : RuinCell.part(below[2]).name()) + ")";
        }
        for (int k = 1; k <= 3 && !underwater; k++) {
            int[] c = v.at(x, y - k, z);
            if (c != null && c[1] == Voxels.WATER) {
                return "water " + k + " below";
            }
        }
        return null;
    }

    private static boolean walkThrough(RuinPart p, boolean underwater) {
        return switch (p) {
            case SHORT_GRASS, FERN, TALL_GRASS, FLOWER, DEAD_BUSH, MUSHROOM, MOSS_CARPET, CARPET, LITTER, VINE, GLOW_LICHEN -> true;
            case WATER, SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT -> underwater;
            default -> false;
        };
    }

    private static boolean carries(RuinPart p) {
        return p.fullCube() || p.structural && p != RuinPart.FENCE && p != RuinPart.MASONRY_WALL;
    }

    /**
     * Structural cells of a plan that do not connect to the model ground. The plan's own pruning
     * connects everything to its floor course; this checks that the floor course meets the world:
     * cells at or below the ground of their column, and floor-course cells on a foundation fill
     * that reaches the ground, are grounded, and the rest must connect to them through shared
     * faces (roof cells also step diagonally, like the plan's pruning allows).
     */
    private static int offGround(TerrainModel m, RuinPlan p) {
        int anchor = p.mode == RuinPlan.GROUPED ? (int) Math.floor(m.interpolatedHeight(p.anchorX, p.anchorZ)) : 0;
        Map<Long, Boolean> reached = new HashMap<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        int total = 0;
        for (int x = p.minX; x <= p.maxX(); x++) {
            for (int z = p.minZ; z <= p.maxZ(); z++) {
                int g = (int) Math.floor(m.interpolatedHeight(x, z));
                boolean filled = p.mode == RuinPlan.ABSOLUTE && p.fill(x, z) != 0 && p.baseY - RuinPlan.MAX_FILL <= g + 1;
                for (int y = p.minY; y <= p.maxY(); y++) {
                    int c = p.get(x, y, z);
                    if (c == 0 || !RuinCell.part(c).structural) {
                        continue;
                    }
                    total++;
                    int wy = p.mode == RuinPlan.GROUPED ? anchor + y : y;
                    if (wy <= g + 1 || filled && y <= p.baseY) {
                        reached.put(Voxels.key(x, y, z), true);
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};
        while (!queue.isEmpty()) {
            int[] q = queue.poll();
            boolean roof = roofPart(p.get(q[0], q[1], q[2]));
            // a standing lantern carries the cap on it
            if (RuinCell.part(p.get(q[0], q[1] + 1, q[2])) == RuinPart.LANTERN) {
                int c = p.get(q[0], q[1] + 2, q[2]);
                if (c != 0 && RuinCell.part(c).structural && reached.putIfAbsent(Voxels.key(q[0], q[1] + 2, q[2]), true) == null) {
                    queue.add(new int[]{q[0], q[1] + 2, q[2]});
                }
            }
            for (int k = 0; k < faces.length; k++) {
                int x = q[0] + faces[k][0];
                int y = q[1] + faces[k][1];
                int z = q[2] + faces[k][2];
                int c = p.get(x, y, z);
                if (c == 0 || !RuinCell.part(c).structural || k >= 6 && !(roof && roofPart(c))) {
                    continue;
                }
                if (reached.putIfAbsent(Voxels.key(x, y, z), true) == null) {
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        return total - reached.size();
    }

    private static boolean roofPart(int c) {
        RuinPart p = RuinCell.part(c);
        return c != 0 && (p == RuinPart.ROOF_STAIR || p == RuinPart.ROOF_SLAB || p == RuinPart.THATCH || p == RuinPart.ROOF);
    }

    /** Cells a site's plans place within {@code r} blocks of a column. */
    private static int cellsNear(List<RuinPlan> plans, int x, int z, int r) {
        int n = 0;
        for (RuinPlan p : plans) {
            for (int a = Math.max(p.minX, x - r); a <= Math.min(p.maxX(), x + r); a++) {
                for (int b = Math.max(p.minZ, z - r); b <= Math.min(p.maxZ(), z + r); b++) {
                    for (int y = p.minY; y <= p.maxY(); y++) {
                        int c = p.get(a, y, b);
                        n += c != 0 && RuinCell.part(c) != RuinPart.AIR ? 1 : 0;
                    }
                }
            }
            for (RuinPlan.Decal d : p.decals) {
                n += Math.abs(d.x() - x) <= r && Math.abs(d.z() - z) <= r ? 1 : 0;
            }
        }
        return n;
    }

    private static void find(long seed, File dir, String target, int count, int x, int z) throws Exception {
        dir.mkdirs();
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        TerrainFinder.Target t = TerrainFinder.targets().get(target);
        if (t == null || t.kind() != TerrainFinder.Kind.RUIN) {
            throw new IllegalArgumentException("no ruin finder target " + target);
        }
        int ox = x;
        int oz = z;
        for (int i = 0; i < count; i++) {
            long a = System.nanoTime();
            TerrainFinder.Result r = TerrainFinder.find(model, t, ox, oz, 6000, () -> false);
            long dt = System.nanoTime() - a;
            if (r == null) {
                System.out.println("nothing found from " + ox + "," + oz);
                break;
            }
            RuinSite match = match(sites, t.step(), r);
            String stand = standCheck(model, sites, r.x(), r.y(), r.z(), match instanceof SunkenRuin);
            System.out.printf(Locale.ROOT, "%s %d at %d %d %d (%.0f ms) %s; site at %s; standing point %s%n", target, i, r.x(), r.y(), r.z(),
                dt / 1e6, match == null ? "NO MATCHING SITE" : describe(sites, match), match == null ? "-" : match.x + " " + match.z,
                stand == null ? "safe" : "BAD: " + stand);
            if (match != null) {
                render(model, sites, match, new File(dir, target + "_" + i + ".png"));
            }
            ox = r.x() + 900;
            oz = r.z() + 700;
        }
    }

    /**
     * Every ruin target of the finder, from three origins. Each result must lead to a built site of
     * the right kind ({@link #match}); the site's plans are built and must place cells within 16
     * blocks of the point and rest on the model ground ({@link #offGround}); and the point must
     * be a safe place to stand ({@link #standCheck}). The first result also gets the nearest built
     * site of the selector found by brute force over the cells around the origin, which the finder
     * must equal.
     */
    private static void findAll(long seed, File dir, int count) throws Exception {
        dir.mkdirs();
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        int[][] origins = {{0, 0}, {3100, -2300}, {-4200, 1700}};
        int failures = 0;
        for (TerrainFinder.Target t : TerrainFinder.targets().values()) {
            if (t.kind() != TerrainFinder.Kind.RUIN) {
                continue;
            }
            boolean sea = t.name().equals("sunken_ruin");
            int found = 0;
            int matched = 0;
            int standing = 0;
            int built = 0;
            int notNearest = 0;
            double ms = 0;
            double nearest = 0;
            StringBuilder what = new StringBuilder();
            for (int[] o : origins) {
                int ox = o[0];
                int oz = o[1];
                for (int i = 0; i < count; i++) {
                    long a = System.nanoTime();
                    TerrainFinder.Result r = TerrainFinder.find(model, t, ox, oz, 6000, () -> false);
                    ms += (System.nanoTime() - a) / 1e6;
                    if (r == null) {
                        break;
                    }
                    found++;
                    RuinSite s = match(sites, t.step(), r);
                    if (s == null) {
                        failures++;
                        System.out.println("  NO MATCHING SITE: " + t.name() + " at " + r.x() + " " + r.y() + " " + r.z());
                    } else {
                        matched++;
                        if (i == 0) {
                            nearest += Math.hypot(s.x - o[0], s.z - o[1]);
                            RuinSite brute = bruteNearest(sites, t.step(), ox, oz, sea);
                            if (brute != s && brute != null && Math.hypot(brute.x - ox, brute.z - oz) < Math.hypot(s.x - ox, s.z - oz)) {
                                notNearest++;
                                failures++;
                                System.out.printf(Locale.ROOT, "  NOT THE NEAREST: %s from %d %d gave %d %d, but %d %d is nearer%n", t.name(), ox, oz,
                                    s.x, s.z, brute.x, brute.z);
                            }
                        }
                        List<RuinPlan> plans = plansOf(sites, s);
                        int roots = 0;
                        for (RuinPlan p : plans) {
                            roots += offGround(model, p);
                        }
                        int near = cellsNear(plans, r.x(), r.z(), 16);
                        if (near > 0 && roots == 0) {
                            built++;
                        } else {
                            failures++;
                            System.out.printf(Locale.ROOT, "  NOT BUILT RIGHT: %s at %d %d: %d cells within 16 blocks, %d cells off the ground%n", t.name(),
                                r.x(), r.z(), near, roots);
                        }
                        String why = standCheck(model, sites, r.x(), r.y(), r.z(), sea);
                        if (why == null) {
                            standing++;
                        } else {
                            failures++;
                            System.out.printf(Locale.ROOT, "  BAD STANDING POINT: %s at %d %d %d (%s): %s%n", t.name(), r.x(), r.y(), r.z(), describe(sites, s),
                                why);
                        }
                        if (found == 1) {
                            render(model, sites, s, new File(dir, "find_" + seed + "_" + t.name() + ".png"));
                            what.append(describe(sites, s)).append(" at ").append(r.x()).append(' ').append(r.y()).append(' ').append(r.z());
                        }
                    }
                    ox = r.x() + 900;
                    oz = r.z() + 700;
                }
            }
            System.out.printf(Locale.ROOT, "%-14s found %2d, built sites %2d, built right %2d, safe to stand %2d, farther than the nearest %d; "
                    + "mean distance to the nearest %5.0f, %6.1f ms per search; first: %s%n", t.name(), found, matched, built, standing, notNearest,
                nearest / origins.length, ms / Math.max(1, found), what);
        }
        System.out.println("finder check seed " + seed + ": " + failures + " failed checks");
    }

    /** The nearest built site of a selector to a point, by brute force over the cells within 2500 blocks. */
    private static RuinSite bruteNearest(RuinSites sites, int selector, int x, int z, boolean sea) {
        int cs = sea ? RuinSites.SUNKEN_CELL : RuinSites.TRAIL_CELL;
        int reach = 2500 / cs + 1;
        RuinSite best = null;
        double bestD = Double.MAX_VALUE;
        for (int cx = Math.floorDiv(x, cs) - reach; cx <= Math.floorDiv(x, cs) + reach; cx++) {
            for (int cz = Math.floorDiv(z, cs) - reach; cz <= Math.floorDiv(z, cs) + reach; cz++) {
                for (RuinSite s : sites.sitesInCell(selector & 15, cx, cz)) {
                    double d = Math.hypot(s.x - x, s.z - z);
                    if (d < bestD && d <= 2500 && RuinSites.matches(s, selector) && sites.allowed(s)) {
                        bestD = d;
                        best = s;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Installs a synthetic world filter (biomes estimated from the climate, a vanilla structure
     * predicted in a third of all 640-block squares) and checks that the finder reports only
     * sites the feature then builds: every result passes the same {@link RuinSites#allowed} test
     * that the feature applies, and no result lies where a structure keeps ruins away.
     */
    private static void filterCheck(long seed) {
        TerrainModel model = new TerrainModel(seed, TerrainSettings.DEFAULT);
        RuinSites sites = RuinSites.of(model);
        sites.install(new RuinSites.WorldFilter() {
            @Override
            public int biome(int x, int y, int z) {
                return estimate(model.sample(x & ~3, z & ~3));
            }

            @Override
            public boolean structureNear(int x, int z, int radius) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int qx = Math.floorDiv(x + dx * radius, 640);
                        int qz = Math.floorDiv(z + dz * radius, 640);
                        if (SeedMixer.unit(SeedMixer.hash2(0x5778C7L, qx, qz)) < 0.33) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public boolean trails() {
                return true;
            }

            @Override
            public boolean formations() {
                return true;
            }
        });
        int checked = 0;
        int bad = 0;
        int refusedNearby = 0;
        for (TerrainFinder.Target t : TerrainFinder.targets().values()) {
            if (t.kind() != TerrainFinder.Kind.RUIN) {
                continue;
            }
            int ox = 0;
            int oz = 0;
            for (int i = 0; i < 4; i++) {
                TerrainFinder.Result r = TerrainFinder.find(model, t, ox, oz, 6000, () -> false);
                if (r == null) {
                    break;
                }
                checked++;
                RuinSite s = match(sites, t.step(), r);
                if (s == null || sites.filter().structureNear(s.centreX(), s.centreZ(), s.radius() + 8)) {
                    bad++;
                    System.out.println("  REFUSED BY THE FILTER: " + t.name() + " at " + r.x() + " " + r.z());
                }
                ox = r.x() + 900;
                oz = r.z() + 700;
            }
        }
        // how many sites in the area the filter refused (the finder skipped them)
        int tc = RuinSites.TRAIL_CELL;
        for (int cx = -8; cx < 8; cx++) {
            for (int cz = -8; cz < 8; cz++) {
                for (RuinSite s : sites.sitesInCell(RuinSites.TRAIL_RUIN, cx, cz)) {
                    refusedNearby += sites.allowed(s) ? 0 : 1;
                }
            }
        }
        System.out.println("filter check seed " + seed + ": " + checked + " finder results, " + bad + " refused by the world filter; "
            + refusedNearby + " ruins in the central " + (16 * tc) + " blocks refused by it (skipped by finder and feature alike)");
    }

    /** The biome estimate the sites use without a world (the same climate bands). */
    private static int estimate(ColumnSample s) {
        double t = s.temperature;
        double h = s.humidity;
        if (s.isSea(63)) {
            return RuinBiomes.OCEAN;
        }
        if (t >= 0.55) {
            return RuinBiomes.DESERT;
        }
        if (t >= 0.2) {
            return h < -0.1 ? RuinBiomes.SAVANNA : h < 0.1 ? RuinBiomes.FOREST : RuinBiomes.JUNGLE;
        }
        if (t < -0.45) {
            return RuinBiomes.SNOWY;
        }
        return h < -0.1 ? RuinBiomes.PLAINS : h < 0.1 ? RuinBiomes.FOREST : RuinBiomes.TAIGA;
    }

    private static String describe(RuinSites sites, RuinSite s) {
        if (s instanceof TrailRuin r) {
            return String.format(Locale.ROOT, "%s (%s, %s) decay %.2f wet %.2f '%s' floor %d", r.describe(), RuinSites.CLIMATE_NAMES[r.climate],
                r.materials, r.decay, r.wetness, r.name, r.floorY);
        }
        if (s instanceof Signpost p) {
            StringBuilder b = new StringBuilder(Signpost.KIND_NAMES[p.kind] + " (" + Signpost.STYLE_NAMES[p.style] + ")");
            for (SignpostPlanner.Board board : SignpostPlanner.boards(sites, p)) {
                b.append(" [").append("NESW".charAt(board.dir())).append(": ").append(String.join(" / ", board.text().front()).trim()).append(']');
            }
            return b.toString();
        }
        if (s instanceof LightStretch l) {
            return String.format(Locale.ROOT, "%d %s posts, decay %.2f", l.posts.length, LightStretch.STYLE_NAMES[l.style()], l.decay());
        }
        SunkenRuin u = (SunkenRuin) s;
        return String.format(Locale.ROOT, "%s %s %s decay %.2f floor %d depth %d", SunkenRuin.KIND_NAMES[u.kind], u.warm ? "warm" : "cold",
            RuinMaterials.STONE_NAMES[u.materials.stone], u.decay, u.floorY, u.depth);
    }

    /** A top-down map around the site (4 px per block) beside an isometric view of it in its terrain. */
    private static void render(TerrainModel model, RuinSites sites, RuinSite s, File out) throws Exception {
        int size = s instanceof LightStretch ? 160 : 96;
        int scale = s instanceof LightStretch ? 3 : 4;
        int minX = s.centreX() - size / 2;
        int minZ = s.centreZ() - size / 2;
        BufferedImage top = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int c = terrainColour(model, minX + i, minZ + j, model.seaLevel(), true);
                for (int a = 0; a < scale; a++) {
                    for (int b = 0; b < scale; b++) {
                        top.setRGB(i * scale + a, j * scale + b, c);
                    }
                }
            }
        }
        IntBinaryOperator ground = (x, z) -> (int) Math.floor(model.interpolatedHeight(x, z));
        List<RuinPlan> plans = new ArrayList<>();
        if (s instanceof TrailRuin || s instanceof SunkenRuin) {
            plans.add(sites.plan(s));
        } else if (s instanceof Signpost p) {
            plans.add(SignpostPlanner.plan(sites, p));
        } else if (s instanceof LightStretch l) {
            for (LightStretch.Post p : l.posts) {
                plans.add(LampPostPlanner.plan(p));
            }
        }
        Voxels v = new Voxels();
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
        // the terrain around the site in the isometric view, streams and the trail drawn on it
        Voxels scene = new Voxels();
        int r = s instanceof LightStretch ? 4 : 6;
        for (int x = s.minX - r; x <= s.maxX + r; x++) {
            for (int z = s.minZ - r; z <= s.maxZ + r; z++) {
                int g = ground.applyAsInt(x, z);
                boolean sea = s instanceof SunkenRuin;
                for (int y = g - 2; y <= g; y++) {
                    scene.put(x, y, z, y == g ? (sea ? 0xD8CC9A : 0x6A9A3C) : (sea ? 0xC8BC8A : 0x86603F), 0);
                }
                if (!sea && RuinSites.water(model, x, z)) {
                    scene.put(x, g, z, 0x3F76E4, 0);
                }
                if (!sea && sites.trailShown(x, z)) {
                    scene.put(x, g, z, 0x947A41, 0);
                }
            }
        }
        for (RuinPlan p : plans) {
            scene.add(p, ground, s instanceof SunkenRuin ? model.seaLevel() : -1000);
        }
        // where the finder sends the player: a magenta figure two blocks tall, and a ring on the map
        int[] stand = sites.visit(s);
        scene.put(stand[0], stand[1], stand[2], 0xE020E0, Voxels.POST);
        scene.put(stand[0], stand[1] + 1, stand[2], 0xE020E0, Voxels.POST);
        // view from the side the ground falls towards, or a slope rising towards the viewer hides the site
        boolean flip = ground.applyAsInt(s.maxX + r, s.maxZ + r) > ground.applyAsInt(s.minX - r, s.minZ - r) + 2;
        BufferedImage iso = scene.render(s instanceof LightStretch ? 3 : 8, flip);
        BufferedImage combined = new BufferedImage(top.getWidth() + iso.getWidth(), Math.max(top.getHeight(), iso.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D tg = top.createGraphics();
        tg.setColor(new Color(224, 32, 224));
        tg.drawOval((stand[0] - minX) * scale - 2 * scale, (stand[2] - minZ) * scale - 2 * scale, 5 * scale, 5 * scale);
        tg.dispose();
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

        /** A voxel of terrain (not from a plan): {colour, shape, -1}. */
        void put(int x, int y, int z, int rgb, int shape) {
            map.put(key(x, y, z), new int[]{rgb, shape, -1});
        }

        /** A voxel of a plan: {colour, shape, cell code}. */
        void putCell(int x, int y, int z, int rgb, int cell) {
            map.put(key(x, y, z), new int[]{rgb, shape(cell), cell});
        }

        boolean occupied(int x, int y, int z) {
            int[] v = map.get(key(x, y, z));
            return v != null && v[1] != WATER;
        }

        /** The voxel at a cell, or {@code null}. */
        int[] at(int x, int y, int z) {
            return map.get(key(x, y, z));
        }

        /**
         * Whether a block counts as solid for the feature's decal test ({@code BlockState.isSolid},
         * which is true for full blocks, leaves, slabs, stairs, fences and the like, not for plants,
         * carpets, lanterns or signs). Terrain voxels are solid.
         */
        boolean solid(int x, int y, int z) {
            int[] v = map.get(key(x, y, z));
            if (v == null || v[1] == WATER) {
                return false;
            }
            if (v[2] < 0) {
                return true;
            }
            RuinPart part = RuinCell.part(v[2]);
            return part.structural || part.fullCube() || switch (part) {
                case CHEST, CAMPFIRE, TRAPDOOR, ANVIL, CAULDRON, COMPOSTER, LECTERN, BED, DOOR, LEAVES -> true;
                default -> false;
            };
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
                    v.put(x, g - 1, z, 0x86603F, FULL);
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
                        // soft cells (and grouped posts above their footing) never replace what is there
                        boolean soft = RuinCell.soft(c) || p.mode == RuinPlan.GROUPED && y > 0;
                        if (soft && occupied(x, wy, z)) {
                            continue;
                        }
                        putCell(x, wy, z, colour(c, p.materials), c);
                    }
                }
            }
            // decals as the feature lays them: on the ground only over soil, above it only onto
            // something solid that is not leaves (leaves may carry the next course of a hedge)
            List<RuinPlan.Decal> decals = new ArrayList<>(p.decals);
            decals.sort((a, b) -> Integer.compare(a.dy(), b.dy()));
            for (RuinPlan.Decal d : decals) {
                int g = ground.applyAsInt(d.x(), d.z());
                int y = g + d.dy();
                if (d.dy() <= 0) {
                    int[] top = at(d.x(), y, d.z());
                    if (top != null && top[2] >= 0 && !soilLike(RuinCell.part(top[2]))) {
                        continue;
                    }
                } else {
                    int[] below = at(d.x(), y - 1, d.z());
                    boolean leaves = below != null && below[2] >= 0 && RuinCell.part(below[2]) == RuinPart.LEAVES;
                    if (occupied(d.x(), y, d.z()) || !solid(d.x(), y - 1, d.z())
                        || leaves && RuinCell.part(d.cell()) != RuinPart.LEAVES) {
                        continue;
                    }
                }
                putCell(d.x(), y, d.z(), colour(d.cell(), p.materials), d.cell());
            }
        }

        private static boolean soilLike(RuinPart p) {
            return p == RuinPart.SOIL || p == RuinPart.DIRT_PATH || p == RuinPart.COARSE_DIRT || p == RuinPart.GRAVEL || p == RuinPart.SEDIMENT
                || p == RuinPart.MOSS_BLOCK;
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
            case FENCE, FENCE_GATE, MASONRY_WALL, CHAIN, IRON_BARS, GLASS_PANE, LADDER, DOOR, STANDING_SIGN -> Voxels.POST;
            case LANTERN, FLOWER, SHORT_GRASS, FERN, TALL_GRASS, DEAD_BUSH, MUSHROOM, SWEET_BERRY_BUSH, CAMPFIRE, FLOWER_POT, ROCK, SEAGRASS,
                TALL_SEAGRASS, KELP, KELP_PLANT, SEA_PICKLE, CORAL_FAN, COBWEB, WALL_HANGING_SIGN, WALL_SIGN, DECORATED_POT, VINE, GLOW_LICHEN, CANDLE,
                BELL -> Voxels.SMALL;
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
    private static final int[] WOOL = {0xE9ECEC, 0x8E8E86, 0x724728, 0x546D1B};
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
            case PLANKS, FLOOR, PLANK_STAIR, PLANK_SLAB, FENCE, FENCE_GATE, DOOR, TRAPDOOR -> RuinCell.aux(c) == 1 && p == RuinPart.PLANKS ? 0x43301A
                : PLANK[m.wood];
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
                case 4 -> 0x5A7C2C;
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
            case CARPET -> RuinCell.aux(c) == 1 ? 0x724728 : RuinCell.aux(c) == 2 ? 0xA02020 : 0xC0C0C0;
            case LANTERN -> 0xF0C050;
            case STANDING_SIGN, WALL_HANGING_SIGN, WALL_SIGN -> 0xC8A070;
            case WOOL -> WOOL[RuinCell.aux(c) % WOOL.length];
            case CANDLE -> RuinCell.flag(c) ? 0xFFD060 : 0xE8D8A0;
            case BELL -> 0xE0B030;
            case LECTERN -> 0x9A7A4A;
            case CARVED_PUMPKIN -> 0xE07820;
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
