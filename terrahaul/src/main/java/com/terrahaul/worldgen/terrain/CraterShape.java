package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.clamp01;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;
import java.util.Locale;

/**
 * The craters of {@link TerrainModel}'s volcanoes: bowls with a blobby outline, a rim of uneven
 * height and a floor of blobs and hollows, so that crater lakes get blobby shores, headlands,
 * islets and often a second pool instead of being perfect discs of water in perfect funnels.
 * <p>
 * The crater is laid out in polar coordinates around the volcano's centre, after a gentle domain
 * warp that bends the outline a little further than any curve of radius-by-bearing could. When the
 * volcano site is made, the crater's shape is worked out once for every quarter degree of bearing
 * and kept in tables, which the surface reads back between neighbouring bearings:
 * <ul>
 *   <li>the <b>outline</b>, the radius of the rim's crest, is the crater's radius varied by noise
 *       read on circles (a few broad lobes, finer scallops and a roughness of a few blocks), widened
 *       by rounded bays where part of the wall slumped outward, and sometimes by the lobe of a pit
 *       crater that cut into the rim. The noise is turned down as far as needed to keep the outline
 *       within {@link #MAX_TURN} of the circle at every bearing, measured on the table itself;</li>
 *   <li>the <b>crest</b> of the rim is the cone's height at the outline plus a raised lip whose
 *       height wanders: notches and breaches cut it down, broad shoulders stand higher. Bays lie
 *       further down the cone, so their rims are lower, as collapse scars are;</li>
 *   <li>the <b>inner wall</b> falls from the crest to its foot, steepest under the crest and
 *       flattening into scree, ribbed by gullies on its upper part. The power of its profile
 *       changes around the crater (concave scree slopes in places, straighter walls in others),
 *       and two or three ledges break it, flatter benches with steeper steps between them, at
 *       heights that wander up and down around the crater and fade out where it is an even slope.
 *       Its foot follows the outline
 *       smoothed over some twenty degrees either way, at a share of the radius that wanders with
 *       the bearing on noise of its own, so the rim's scallops and roughness are dents in the top
 *       of the wall rather than creases running to the floor, and the foot has bays and
 *       headlands the rim does not have. The crest's highs and lows fade toward its mean on the
 *       way down for the same reason. Where the wall would be steeper than its slope budget (the
 *       actual gradient, worked out from the tables at every height of the wall, with the
 *       outline's turns, the gullies, the floor's relief and the warp included), the share widens
 *       there, up to {@link #MAX_SHARE}; a crater too narrow for its depth gets a higher floor
 *       instead, so every crater keeps a floor, and its wall is then fitted again to that floor;</li>
 *   <li>the <b>floor</b> has broad blobs and hollows several blocks high, the same relief that
 *       runs on up the wall and fades toward the crest. Lakes and lava pools are filled to within
 *       that relief (some lakes a little way further up the wall), so their shores wander over
 *       it, apart from the rim. Some craters hold a younger cone standing as an island, a pit
 *       crater whose rim is a ridge curving across the floor around the pit, cutting a second,
 *       smaller basin off the main one, and mounds of slumped rock that stand as islets, or as
 *       headlands joined to the wall where the liquid is too deep for them. A lava lake carries
 *       plates of cooled crust flush with its surface, their sides sloping away under the lava;</li>
 *   <li>outside the outline the lip falls back to the cone over a band a fifth of the crater's
 *       radius wide, and the cone's relief ({@link ConeShape}) fades in across it.</li>
 * </ul>
 * Whatever the floor does, the surface along the outline is the crest at that bearing, and every
 * path out of the crater crosses the outline, so the crater holds anything below its lowest
 * crest. That lowest point is the lowest entry of the crest table (the surface between two
 * bearings lies on the straight line between their crests), and lakes and lava pools are filled
 * to a few blocks below it at most: they stay enclosed however chaotic the rim gets, with no water
 * walls and nothing for the water solver to dam.
 * <p>
 * The per-volcano parameters and tables live in a {@link Crater}, made once when the volcano site
 * is made; everything is a pure function of the position and the world seed. Heights are relative
 * to the volcano's base height.
 */
final class CraterShape {
    private static final double TAU = Math.PI * 2.0;
    /** Bearings in a crater's tables: a quarter of a degree apart, under two blocks along the largest rims. */
    static final int RIM_SAMPLES = 1440;
    /**
     * Lakes and lava stay at least this far below the lowest crest. The noise chunk interpolates
     * the ground between corners four blocks apart, which rounds off a narrow crest by a block or
     * two; the rest is freeboard, so no shore sits exactly on the brim.
     */
    static final double RIM_MARGIN = 4.0;
    /**
     * Steepest gradient of the inner wall, in blocks per block (about 66 degrees), everything
     * included: steep enough to read as a crater wall, gentle enough that it never becomes one of
     * the sheer faces or slots the terrain avoids. Walls taller than {@link #TALL_WALL} get
     * gentler, down to {@link #TALL_WALL_SLOPE}, so they do not stand as one huge face.
     */
    private static final double WALL_SLOPE = 2.2;
    private static final double TALL_WALL = 80.0;
    private static final double TALL_WALL_SLOPE = 1.5;
    /** Largest share of a bearing's radius the wall may take; the rest is floor. */
    private static final double MAX_SHARE = 0.7;
    /** How much the share of the wall wanders with the bearing, either way. */
    private static final double FOOT_VARIATION = 0.25;
    /**
     * Most obliquely the outline may run across the circle around the centre: the tangent of
     * its angle to the circle (50 degrees). The wall's gradient grows with the angle, which the
     * slope budget allows for bearing by bearing; this only reins in the rare outline whose
     * turns would leave no room for a floor.
     */
    private static final double MAX_TURN = 1.2;
    /**
     * Steepest the crest may rise or fall along the rim, in blocks per block: the sides of a notch
     * are slopes of scree, not walls. A narrower notch gets a wider, V-shaped bottom instead.
     */
    private static final double CREST_SLOPE = 0.75;
    /** Steepest gradient of the simplex noise per unit of its input (99th percentile; the maximum is 7.3). */
    private static final double NOISE_SLOPE = 6.5;
    /** Heights of the wall, from the crest to the foot, at which the slope budget is checked. */
    private static final int WALL_LEVELS = 16;
    /** Deepest the gullies on the inner wall get, in blocks: their spacing does not grow with the wall. */
    private static final double MAX_GULLY = 2.5;
    /** How close the floor may come to the lowest crest when the slope budget raises it. */
    private static final double MIN_BOWL = 6.0;
    /**
     * Shallowest crater lake, over the deepest hollows of the floor: a crater whose floor had to
     * rise so far that such a lake would stand above the brim stays dry.
     */
    private static final double MIN_LAKE_DEPTH = 5.0;
    /**
     * How far the crater floor's relief rises and falls either way, in blocks, before a random
     * share of 0.7 to 1.15 of it, at most; and the largest size of its broad blobs: large craters
     * get more blobs rather than larger ones.
     */
    private static final double MAX_RELIEF = 14.0;
    private static final double RELIEF_SCALE = 80.0;
    /** Half-width, in table entries (ten degrees), of each of the two passes that smooth the outline for the wall's foot. */
    private static final int FOOT_SMOOTHING = 40;
    /** Radii of the circles the outline's noise is read on: broad lobes and finer scallops. */
    private static final double LOBE_RING = 1.15;
    private static final double SCALLOP_RING = 2.8;
    /** Radius of the circle the lip's height noise is read on, and the one of the wall's foot. */
    private static final double LIP_RING = 1.7;
    private static final double FOOT_RING = 1.2;
    static final int MAX_BAYS = 3;
    static final int MAX_CRESTS = 5;
    static final int MAX_MOUNDS = 5;
    /**
     * Rise of a mound or an island cone over its radius at most: with the blobby foot their
     * flanks stay at about 45 degrees, the slope of loose rock.
     */
    private static final double MOUND_RISE = 0.6;
    /** Island cones wider than this have a summit pit, whose rim stands at this share of the peak. */
    private static final double ISLAND_PIT_RADIUS = 40.0;
    private static final double ISLAND_PIT_RIM = 0.9;
    /**
     * Slopes of the flanks of the pit's rim, the ridge between the two basins: gentle towards the
     * main basin, steeper into the pit.
     */
    private static final double RIDGE_FLANK = 0.7;
    private static final double RIDGE_INNER = 1.0;
    /** Islands, mounds and ridges stay at least this far below the lowest crest. */
    private static final double FEATURE_MARGIN = 2.0;
    /**
     * How steeply the sides of crust plates on a lava lake fall away under the lava, in blocks per
     * unit of their noise: about a block per block.
     */
    private static final double CRUST_SIDE = 7.0;
    /** Length of the array {@link #surface} writes its results to. */
    static final int OUT = 3;
    private static final long SALT = 0xC4A7_E5B1_0B5EL;

    private final SimplexNoise noise;
    /** The outer cone around the crater: its lean, barrancos, lava flows and side cones. */
    final ConeShape cone;

    CraterShape(long volcanoSeed) {
        this.noise = new SimplexNoise(SeedMixer.derive(volcanoSeed, SALT));
        this.cone = new ConeShape(volcanoSeed);
    }

    /**
     * Makes the crater of a new volcano site (whose radius, depth and cone are already set) and
     * fills its lake or lava pool to a level the rim holds. {@code lake} chooses a crater lake;
     * otherwise the crater is dry or, for {@link TerrainModel.VolcanoSite#lava}, holds lava.
     * Sets the site's {@code crater}, {@code craterReach}, {@code waterLevel} and {@code lavaLevel}.
     */
    void shape(TerrainModel.VolcanoSite v, long seed, int cx, int cz, boolean lake) {
        Rolls u = new Rolls(seed, cx, cz);
        Crater k = new Crater();
        v.crater = k;
        double r0 = v.craterRadius;
        double depth = v.craterDepth;
        k.r0 = r0;
        k.noiseX = 2000.0 * u.next() - 1000.0;
        k.noiseZ = 2000.0 * u.next() - 1000.0;
        // the warp bends the outline where no curve of radius-by-bearing could, and squeezes the
        // frame where it bends it, which steepens the walls there: the budget allows for that
        k.warpScale = r0;
        k.warp = k.warpScale * (0.01 + 0.01 * u.next());
        k.squeeze = 1.0 + NOISE_SLOPE * k.warp / k.warpScale;
        k.lip = (3.0 + 0.035 * r0) * (0.7 + 0.6 * u.next());
        k.lipWidth = Math.max(10.0, 0.2 * r0) * (0.8 + 0.4 * u.next());
        k.wallExp = 1.4 + 0.5 * u.next();
        // blobs and hollows several blocks high: the lake is filled to within this relief, so its
        // shore wanders over them, apart from the rim, with headlands, bays and islets
        k.floorAmp = clamp(Math.min(0.06 * r0, 0.15 * depth), 4.0, MAX_RELIEF) * (0.7 + 0.45 * u.next());
        k.floorScale = Math.min(0.36 * r0, RELIEF_SCALE);
        k.ribs = Math.min(MAX_GULLY, (0.015 + 0.025 * u.next()) * depth);
        // gullies some fifty to sixty blocks apart at the rim
        k.ribRing = Math.max(2.0, r0 / 40.0);
        double lobes = 0.09 + 0.06 * u.next();
        double scallops = 0.025 + 0.025 * u.next();
        // a few blocks of roughness at a fixed size in blocks, so large rims are not smooth arcs
        double roughRing = Math.max(SCALLOP_RING, r0 / 24.0);
        double rough = Math.min(3.0, 0.022 * r0);
        double share0 = 0.3 + 0.3 * u.next();
        double fill = u.next();

        // bays: rounded scallops where part of the wall slumped outward; a bay cuts back less far
        // than it is wide, so its sides run across the bearing no more obliquely than the turn limit
        int bays = Math.min(MAX_BAYS, (int) (u.next() * 3.2));
        double[] bayAngle = new double[MAX_BAYS];
        double[] bayWidth = new double[MAX_BAYS];
        double[] baySize = new double[MAX_BAYS];
        for (int i = 0; i < MAX_BAYS; i++) {
            bayAngle[i] = TAU * u.next();
            bayWidth[i] = 0.25 + 0.35 * u.next();
            baySize[i] = r0 * bayWidth[i] * (0.2 + 0.2 * u.next());
        }
        // notches and breaches lower the lip over a narrow arc, shoulders raise it over a wide one;
        // a notch may cut below the cone, but not so far that the ground outside it would rise, nor
        // so steeply that its sides become walls
        double coneSlope = 1.65 * v.height / v.radius * Math.pow(1.0 - r0 / v.radius, 0.65);
        int notches = 1 + (int) (u.next() * 2.6);
        int shoulders = (int) (u.next() * 2.4);
        int crests = Math.min(MAX_CRESTS, notches + shoulders);
        double[] crestAngle = new double[MAX_CRESTS];
        double[] crestWidth = new double[MAX_CRESTS];
        double[] crestHeight = new double[MAX_CRESTS];
        for (int i = 0; i < MAX_CRESTS; i++) {
            crestAngle[i] = TAU * u.next();
            double a = u.next();
            double b = u.next();
            if (i < notches) {
                double half = (6.0 + 0.07 * r0) * (0.6 + 0.9 * a);
                crestWidth[i] = half / r0;
                crestHeight[i] = -Math.min(Math.min(k.lip * (0.6 + 1.3 * b), k.lip + 0.4 * coneSlope * k.lipWidth), 0.75 * half);
            } else {
                crestWidth[i] = 0.3 + 0.5 * a;
                crestHeight[i] = k.lip * (0.5 + 0.9 * b);
            }
        }
        // the pit, the island cone and the mounds, rolled whether or not the crater has them, so
        // that one of them coming or going does not reshuffle the others
        double pitRoll = u.next();
        double pitAngle = TAU * u.next();
        double pitSize = u.next();
        double pitBite = u.next();
        double pitDeep = u.next();
        double ridgeRoll = u.next();
        double ridgeVary = u.next();
        double islandRoll = u.next();
        double islandAngle = TAU * u.next();
        double islandDist = u.next();
        double islandTop = u.next();
        double moundRoll = u.next();
        double[] mound = new double[4 * MAX_MOUNDS];
        for (int i = 0; i < mound.length; i++) {
            mound[i] = u.next();
        }
        // walls of varying steepness, with ledges at heights that wander around the crater, and
        // crust on a lava lake (rolled last, so the rolls above stay as they were)
        double ledgeAmp = 0.35 + 0.35 * u.next();
        k.ledges = u.next() < 0.45 ? 2 : 3;
        double ledgePhase = TAU * u.next();
        k.crustCut = 0.42 + 0.25 * u.next();

        // ---- the outline ------------------------------------------------------------------------
        int n = RIM_SAMPLES;
        double[] fixed = new double[n];
        double[] wobble = new double[n];
        double[] cos = new double[n];
        double[] sin = new double[n];
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            cos[i] = c;
            sin[i] = s;
            wobble[i] = r0 * (lobes * noise.noise2(c * LOBE_RING + k.noiseX, s * LOBE_RING + k.noiseZ)
                + scallops * noise.noise2(c * SCALLOP_RING + k.noiseX + 71.3, s * SCALLOP_RING + k.noiseZ - 23.9))
                + rough * noise.noise2(c * roughRing + k.noiseZ + 211.0, s * roughRing - k.noiseX);
            double r = r0;
            for (int b = 0; b < bays; b++) {
                r += baySize[b] * window(a - bayAngle[b], bayWidth[b]);
            }
            fixed[i] = r;
        }
        double scale = turnScale(fixed, wobble);
        k.pit = pitRoll < 0.45;
        if (k.pit) {
            // a younger pit crater on the rim: its far side reaches a little past the old rim,
            // which the outline follows in a lobe, and its near side curves across the floor
            int ip = (int) Math.round(pitAngle / TAU * n) % n;
            double edge = fixed[ip] + scale * wobble[ip];
            double pr = r0 * (0.28 + 0.12 * pitSize);
            double dist = edge - pr * (0.75 + 0.3 * pitBite);
            double arc = Math.asin(Math.min(0.95, pr / dist));
            double width = 1.3 * arc;
            double bulge = Math.min(0.8 * Math.max(0.0, dist + pr - edge) + 0.04 * r0, 0.3 * width * edge);
            for (int i = 0; i < n; i++) {
                fixed[i] += bulge * window(i * TAU / n - pitAngle, width);
            }
            scale = turnScale(fixed, wobble);
            k.pitX = Math.cos(pitAngle) * dist;
            k.pitZ = Math.sin(pitAngle) * dist;
            k.inX = -Math.cos(pitAngle);
            k.inZ = -Math.sin(pitAngle);
            k.pitRadius = pr;
            k.pitArc = arc + 0.05;
            k.pitAngle = pitAngle;
            k.pitDeep = Math.min(depth, 0.4 * r0) * (0.1 + 0.15 * pitDeep);
        }
        k.outlineScale = scale;
        k.edge = new float[n];
        k.lipTable = new float[n];
        k.crest = new float[n];
        k.share = new float[n];
        k.exp = new float[n];
        k.ledge = new float[n];
        k.ledgePhase = new float[n];
        double[] crest = new double[n];
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n;
            double c = cos[i];
            double s = sin[i];
            double edge = fixed[i] + scale * wobble[i];
            double lip = k.lip * (1.0 + 0.5 * noise.noise2(c * LIP_RING + k.noiseX - 140.0, s * LIP_RING + k.noiseZ + 77.0));
            for (int b = 0; b < crests; b++) {
                lip += crestHeight[b] * window(a - crestAngle[b], crestWidth[b]);
            }
            k.edge[i] = (float) edge;
            crest[i] = cone(v, edge) + lip;
            k.share[i] = (float) (share0 * (1.0 + FOOT_VARIATION * noise.noise2(c * FOOT_RING + k.noiseX + 333.0, s * FOOT_RING + k.noiseZ - 444.0)));
            // concave scree slopes in places, straighter and steeper walls in others
            k.exp[i] = (float) clamp(k.wallExp + 0.4 * noise.noise2(c * 1.4 + k.noiseX + 520.0, s * 1.4 + k.noiseZ - 610.0), 1.15, 2.3);
            k.ledge[i] = (float) (ledgeAmp * clamp01(0.45 + 0.8 * noise.noise2(c * 2.2 + k.noiseZ + 700.0, s * 2.2 - k.noiseX)));
            k.ledgePhase[i] = (float) (ledgePhase + 2.4 * noise.noise2(c * 1.6 - k.noiseX + 830.0, s * 1.6 + k.noiseZ));
        }
        // the crest's slope along the rim stays within CREST_SLOPE: the lowest crest that is at
        // least as high as the rolled one everywhere, found by passing around the rim both ways
        for (int t = 0; t < 2 * n; t++) {
            int i = t % n;
            int a = i == 0 ? n - 1 : i - 1;
            crest[i] = Math.max(crest[i], crest[a] - CREST_SLOPE * 0.5 * (k.edge[i] + k.edge[a]) * TAU / n);
        }
        for (int t = 2 * n - 1; t >= 0; t--) {
            int i = t % n;
            int b = i == n - 1 ? 0 : i + 1;
            crest[i] = Math.max(crest[i], crest[b] - CREST_SLOPE * 0.5 * (k.edge[i] + k.edge[b]) * TAU / n);
        }
        double low = Double.MAX_VALUE;
        double sum = 0.0;
        double rMax = 0.0;
        for (int i = 0; i < n; i++) {
            k.crest[i] = (float) crest[i];
            k.lipTable[i] = (float) (k.crest[i] - cone(v, k.edge[i]));
            low = Math.min(low, k.crest[i]);
            sum += k.crest[i];
            rMax = Math.max(rMax, k.edge[i]);
        }
        k.rimLow = low;
        k.mean = sum / n;
        k.warpStart = rMax;
        // the wall's foot follows the outline smoothed over some twenty degrees either way: the
        // broad lobes, bays and the pit's lobe, but not the scallops and roughness of the rim,
        // which would otherwise run down the wall as creases and give the shore the rim's teeth
        float[] pass = k.edge;
        for (int t = 0; t < 2; t++) {
            float[] next = new float[n];
            double run = 0.0;
            for (int d = -FOOT_SMOOTHING; d <= FOOT_SMOOTHING; d++) {
                run += pass[(d + n) % n];
            }
            for (int i = 0; i < n; i++) {
                next[i] = (float) (run / (2 * FOOT_SMOOTHING + 1));
                run += pass[(i + FOOT_SMOOTHING + 1) % n] - pass[(i - FOOT_SMOOTHING + n) % n];
            }
            pass = next;
        }
        k.smooth = pass;
        v.craterReach = rMax + k.warp + k.lipWidth + 4.0;

        // ---- the wall within its slope budget ---------------------------------------------------
        // the lowest the floor gets under each bearing (its relief and the pit's basin) and the
        // steepest its own relief is there
        double[] drop = new double[n];
        double[] floorSlope = new double[n];
        double reliefSlope = k.floorAmp * NOISE_SLOPE * (0.8 + 0.2 / 0.5) / k.floorScale;
        for (int i = 0; i < n; i++) {
            boolean pit = k.pit && Math.abs(Math.IEEEremainder(i * TAU / n - k.pitAngle, TAU)) < k.pitArc;
            drop[i] = k.floorAmp + (pit ? k.pitDeep : 0.0);
            floorSlope[i] = reliefSlope + (pit ? 1.54 * k.pitDeep / k.pitRadius : 0.0);
        }
        // the gullies' noise and its slope across and down the wall, which depend only on the
        // bearing and the height on the wall, not on the wall's share or the floor
        int levels = WALL_LEVELS + 1;
        float[] gully = new float[3 * n * levels];
        double[] grad = new double[2];
        for (int i = 0; i < n; i++) {
            for (int l = 0; l < levels; l++) {
                double w = l / (double) WALL_LEVELS;
                double g = noise.noise2d(cos[i] * k.ribRing + k.noiseX + 150.0 + 0.9 * w, sin[i] * k.ribRing + k.noiseZ - 150.0 - 0.6 * w, grad);
                int at = 3 * (i * levels + l);
                gully[at] = (float) g;
                gully[at + 1] = (float) ((-sin[i] * grad[0] + cos[i] * grad[1]) * k.ribRing);
                gully[at + 2] = (float) (0.9 * grad[0] - 0.6 * grad[1]);
            }
        }
        double floor = cone(v, r0) - depth;
        double wanted = floor;
        // first the wall widens where it is too steep, up to MAX_SHARE; where it cannot widen
        // further the floor rises, which shortens the wall (up to a shallow bowl under the lowest
        // crest). With the floor raised the wall need not be as wide as it had to be, so it is
        // fitted again from its rolled shares: it keeps its wandering foot and the floor its width
        float[] rolled = k.share.clone();
        double worst = widen(k, rolled, floor, drop, floorSlope, gully);
        if (worst > 1.0) {
            floor = raiseFloor(k, floor, Math.max(floor, low - MIN_BOWL), drop, floorSlope, gully);
            float[] wide = k.share.clone();
            worst = widen(k, rolled, floor, drop, floorSlope, gully);
            if (worst > 1.0) {
                // the narrower fit does not hold everywhere: the wall keeps the width the floor was raised for
                System.arraycopy(wide, 0, k.share, 0, n);
                worst = 0.0;
                for (int i = 0; i < n; i++) {
                    worst = Math.max(worst, steepness(k, i, floor - drop[i], floorSlope[i], gully));
                }
            }
        }
        k.floorRel = floor;
        k.raised = floor - wanted;
        k.steepness = worst;

        // ---- the liquid: filled to within the floor's relief, so the shore runs over its blobs
        // and hollows and around the mounds, apart from the rim, as headlands, bays, islets and
        // separate pools; some lakes rise further, a little way up the wall's foot. Never
        // above a few blocks under the lowest crest, and a crater too shallow for a lake below
        // that stays dry
        double top = low - RIM_MARGIN;
        double liquidRel = Double.NaN;
        v.waterLevel = ColumnSample.NO_WATER;
        v.lavaLevel = ColumnSample.NO_WATER;
        double deepest = floor - k.floorAmp;
        double lakeLevel = Math.min(top, Math.max(deepest + MIN_LAKE_DEPTH, fill < 0.55 ? floor + k.floorAmp * (0.25 + 1.1 * fill)
            : floor + k.floorAmp + (0.05 + 0.6 * (fill - 0.55)) * (top - floor - k.floorAmp)));
        if (lake && lakeLevel >= deepest + MIN_LAKE_DEPTH) {
            v.waterLevel = Math.floor(v.baseHeight + lakeLevel);
            liquidRel = v.waterLevel - 1.0 - v.baseHeight;
        } else if (v.lava && deepest + 3.0 <= top) {
            v.lavaLevel = Math.floor(v.baseHeight + Math.min(top, Math.max(deepest + 3.0, floor + k.floorAmp * (0.3 + 0.5 * fill))));
            liquidRel = v.lavaLevel - 1.0 - v.baseHeight;
        }
        boolean liquid = !Double.isNaN(liquidRel);
        k.dried = lake && !liquid;
        k.liquid = liquidRel;
        k.crust = liquid && v.lavaLevel != ColumnSample.NO_WATER;
        // islands, mounds and ridges stand below the lowest crest, where the crest never cuts them
        double cap = low - FEATURE_MARGIN;
        double bowl = low - floor;

        // ---- the pit's rim: a ridge curving across the floor between the two basins ----------------
        if (k.pit) {
            // above the liquid three times in four, but dipping under it here and there, so it
            // breaks into islets; in a dry crater a low divide. It may stand only as high above
            // the pit's bottom as its steeper flank can fall within three quarters of the pit's
            // radius, so the pit keeps a basin of its own
            double lowest = floor - k.floorAmp - k.pitDeep;
            double room = Math.min(cap, floor - k.pitDeep + RIDGE_INNER * (0.75 * k.pitRadius - 6.0));
            k.ridgeAmp = Math.min(3.0 + 3.0 * ridgeVary, 0.3 * (room - lowest));
            k.ridgeTop = Math.min(liquid ? liquidRel - 1.0 + 7.0 * ridgeRoll : floor + bowl * (0.25 + 0.35 * ridgeRoll), room - k.ridgeAmp);
            k.pit = room - lowest > 3.0;
            // the top rises and dips over a few dozen blocks at least, read on a circle around the pit
            k.ridgeRing = k.pitRadius / Math.max(30.0, 0.3 * r0);
            k.ridgeSink = k.ridgeTop + k.ridgeAmp - lowest + 4.0;
            k.ridgeReach = 0.21 * k.pitRadius + 6.0 + (k.ridgeTop + k.ridgeAmp - lowest) / RIDGE_FLANK;
        }

        // ---- a younger cone on the crater floor: an island in a lake or a lava pool --------------
        k.island = islandRoll < (lake ? 0.6 : 0.4);
        if (k.island) {
            // opposite the pit if there is one, so the two do not collide; its top clears the
            // liquid by three blocks at least, and where the liquid is too deep for a cone of
            // loose rock that small it stands further out, on the foot of the wall
            double a = k.pit ? k.pitAngle + Math.PI + (islandAngle / TAU - 0.5) : islandAngle;
            double peak = Math.min(cap, liquid ? liquidRel + 3.0 + 8.0 * islandTop : floor + bowl * (0.3 + 0.4 * islandTop));
            double foot = footRadius(k, a);
            double rho = foot * 0.75 * islandDist;
            double radius = 0.0;
            for (; rho < foot; rho += 0.02 * r0) {
                radius = Math.max(8.0, (peak - wallAt(k, rho, a)) / MOUND_RISE);
                if (radius <= 0.35 * r0) {
                    break;
                }
            }
            k.island = rho < foot && peak > wallAt(k, rho, a) + 2.0 && (!liquid || peak >= liquidRel + 3.0);
            if (k.island) {
                k.islandX = Math.cos(a) * rho;
                k.islandZ = Math.sin(a) * rho;
                k.islandRadius = radius;
                // the summit pit of a larger cone leaves its rim at about nine tenths of the peak
                k.islandTop = radius > ISLAND_PIT_RADIUS ? Math.min(cap, floor + (peak - floor) / ISLAND_PIT_RIM) : peak;
            }
        }

        // ---- mounds of slumped rock: islets on the floor, or headlands on the foot of the wall ------
        // at least one where the crater holds a lake or lava, so its shore always has a headland
        // or an islet; mounds are wide enough to rise from the floor through the liquid where it
        // is shallow enough, and in deeper liquid they stand on the wall's foot instead
        int mounds = Math.min(MAX_MOUNDS, (liquid ? 1 : 0) + (int) (moundRoll * (1.5 + r0 / 80.0)));
        k.moundX = new double[mounds];
        k.moundZ = new double[mounds];
        k.moundRadius = new double[mounds];
        k.moundTop = new double[mounds];
        k.moundBase = new double[mounds];
        int placed = 0;
        for (int m = 0; m < mounds; m++) {
            double a = TAU * mound[4 * m];
            double peak = Math.min(cap, liquid ? liquidRel + 1.0 + 6.0 * mound[4 * m + 3] : floor + bowl * (0.2 + 0.35 * mound[4 * m + 3]));
            // wide enough to rise from the floor's hollows to its peak (in liquid, through it)
            double radius = clamp((peak - (floor - k.floorAmp - k.ribs)) / MOUND_RISE * (0.8 + 0.4 * mound[4 * m + 1]), 0.1 * r0, 0.3 * r0);
            double edge = lookup(k.edge, a);
            double rho = footRadius(k, a) * (0.15 + 0.85 * mound[4 * m + 2]);
            // a dome over the lowest ground beneath it (the foot of its inner side, less the
            // floor's relief, the gullies and the pit where it reaches the pit), sliding outward
            // until it reaches its height without too steep a flank: where it sits on the wall,
            // the wall rises behind it as a headland
            double base = 0.0;
            for (; rho < edge; rho += 0.02 * r0) {
                boolean overPit = k.pit && Math.hypot(Math.cos(a) * rho - k.pitX, Math.sin(a) * rho - k.pitZ) < k.pitRadius + radius;
                base = wallAt(k, Math.max(0.0, rho - radius), a) - k.floorAmp - k.ribs - (overPit ? k.pitDeep : 0.0);
                if (peak - base <= MOUND_RISE * radius) {
                    break;
                }
            }
            if (rho > edge - 0.4 * runAt(k, a) || peak <= wallAt(k, rho, a) + 1.0) {
                // it would only fit high up the wall, or it would not show
                continue;
            }
            k.moundX[placed] = Math.cos(a) * rho;
            k.moundZ[placed] = Math.sin(a) * rho;
            k.moundRadius[placed] = radius;
            k.moundTop[placed] = peak;
            k.moundBase[placed] = base;
            placed++;
        }
        k.mounds = placed;
    }

    /**
     * Height of the volcano's surface, relative to its base height, at offset {@code (dx, dz)}
     * from its centre, crater included. {@code out}, of length {@link #OUT} at least, receives the
     * crater influence in {@code out[0]}, from 1 at the centre to 0 at the rim, or -1 outside the
     * crater. Within the crater's reach, {@code out[1]} and {@code out[2]} receive the distance from
     * the centre and the radius of the rim's crest at that bearing, both in the crater's slightly
     * warped frame, where they compare exactly.
     */
    double surface(TerrainModel.VolcanoSite v, double dx, double dz, double[] out) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r >= v.craterReach) {
            out[0] = -1.0;
            return cone.flank(v, dx, dz, r);
        }
        Crater k = v.crater;
        // gentle domain warp, fading out before the edge of the crater's reach
        double fade = 1.0 - smoothstep(k.warpStart, v.craterReach, r);
        double wx = dx;
        double wz = dz;
        if (fade > 0.0) {
            wx += fade * k.warp * noise.noise2(dx / k.warpScale + k.noiseX + 311.0, dz / k.warpScale + k.noiseZ);
            wz += fade * k.warp * noise.noise2(dx / k.warpScale + k.noiseX, dz / k.warpScale + k.noiseZ - 311.0);
        }
        double rw = Math.sqrt(wx * wx + wz * wz);
        double c = rw > 1.0E-9 ? wx / rw : 1.0;
        double s = rw > 1.0E-9 ? wz / rw : 0.0;
        // the two table entries either side of the bearing
        double p = (Math.atan2(s, c) / TAU + 1.0) * RIM_SAMPLES;
        int i = (int) p;
        double f = p - i;
        i %= RIM_SAMPLES;
        int j = i + 1 == RIM_SAMPLES ? 0 : i + 1;
        double edge = k.edge[i] + (k.edge[j] - k.edge[i]) * f;
        out[1] = rw;
        out[2] = edge;
        if (rw >= edge) {
            // the lip falls back to the cone, flat at the crest so the rim reads as a rounded ridge;
            // the cone's relief fades in across it, so the crest itself stays as the tables hold it
            out[0] = -1.0;
            double lip = k.lipTable[i] + (k.lipTable[j] - k.lipTable[i]) * f;
            double t = (rw - edge) / k.lipWidth;
            double fall = t >= 1.0 ? 0.0 : 1.0 - t * t * (3.0 - 2.0 * t);
            double relief = smoothstep(0.3, 1.0, t);
            return cone(v, rw) + lip * fall + (relief > 0.0 ? relief * cone.relief(v, dx, dz, r, Math.atan2(dz, dx)) : 0.0);
        }
        double crest = k.crest[i] + (k.crest[j] - k.crest[i]) * f;
        double share = k.share[i] + (k.share[j] - k.share[i]) * f;
        double smooth = k.smooth[i] + (k.smooth[j] - k.smooth[i]) * f;
        double e = k.exp[i] + (k.exp[j] - k.exp[i]) * f;
        double ledge = k.ledge[i] + (k.ledge[j] - k.ledge[i]) * f;
        double phase = k.ledgePhase[i] + (k.ledgePhase[j] - k.ledgePhase[i]) * f;
        double floor = k.floorRel + k.floorAmp * (0.8 * noise.noise2(wx / k.floorScale + k.noiseX - 77.0, wz / k.floorScale + k.noiseZ + 51.0)
            + 0.2 * noise.noise2(wx / (0.5 * k.floorScale) + k.noiseZ, wz / (0.5 * k.floorScale) - k.noiseX));
        double px = wx - k.pitX;
        double pz = wz - k.pitZ;
        double pd = Math.sqrt(px * px + pz * pz);
        if (k.pit && pd < k.pitRadius) {
            // the pit's own basin, deeper than the main floor
            double q = 1.0 - pd * pd / (k.pitRadius * k.pitRadius);
            floor -= k.pitDeep * q * q;
        }
        // the inner wall over the outer share of the radius: steepest under the crest, its foot
        // flattening into the floor, with ledges and steeper steps between them, and the crest's
        // highs and lows fading on the way down
        double u = rw / edge;
        double w = Math.min(1.0, (edge - rw) / run(edge, smooth, share));
        double foot = 1.0 - w;
        double top = k.mean + (crest - k.mean) * foot * Math.sqrt(foot);
        double h = floor + (top - floor) * ledged(Math.pow(foot, e), ledge, phase, k.ledges);
        // gullies and spurs down the steep upper wall, bending as they run down; none at the crest,
        // and they fade out over the scree at the foot rather than converge on the centre
        h += k.ribs * 9.48 * w * foot * foot * foot
            * noise.noise2(c * k.ribRing + k.noiseX + 150.0 + 0.9 * w, s * k.ribRing + k.noiseZ - 150.0 - 0.6 * w);
        if (k.crust && h < k.liquid + 0.5) {
            // plates of cooled crust on a lava lake, flush with its surface, their sides sloping
            // away under the lava
            double plate = 0.8 * noise.noise2(wx / 22.0 + k.noiseX + 900.0, wz / 22.0 + k.noiseZ)
                + 0.2 * noise.noise2(wx / 8.0 - k.noiseZ, wz / 8.0 + k.noiseX + 900.0);
            h = Math.max(h, Math.min(k.liquid + 0.5, k.liquid + 0.5 - CRUST_SIDE * (k.crustCut - plate)));
        }
        // what stands on the floor never rises above the crest, so the rim stays continuous
        if (k.pit && Math.abs(pd - k.pitRadius) < k.ridgeReach && pd > 1.0E-9) {
            h = ridge(k, px / pd, pz / pd, pd, h, crest);
        }
        if (k.island) {
            h = island(k, wx, wz, h, crest);
        }
        for (int m = 0; m < k.mounds; m++) {
            h = mound(k, m, wx, wz, h, crest);
        }
        out[0] = 1.0 - u;
        return h;
    }

    /**
     * The rim of the pit, where it runs inside the crater: a ridge around the pit's centre at about
     * its radius, wandering in and out, whose top rises and dips along it and sinks into the floor
     * on the far side of the pit, where the crater's own wall takes over.
     */
    private double ridge(Crater k, double cp, double sp, double pd, double h, double crest) {
        double radius = k.pitRadius * (1.0 + 0.15 * noise.noise2(cp + k.noiseX + 500.0, sp + k.noiseZ - 500.0)
            + 0.05 * noise.noise2(cp * 2.0 + k.noiseZ + 600.0, sp * 2.0 + k.noiseX));
        double across = pd - radius;
        // it sinks into the floor over the far half of the pit, where the crater's wall takes over
        double top = k.ridgeTop + k.ridgeAmp * noise.noise2(cp * k.ridgeRing + k.noiseX - 700.0, sp * k.ridgeRing + k.noiseZ + 700.0)
            - k.ridgeSink * smoothstep(0.0, -0.9, cp * k.inX + sp * k.inZ);
        // a broad, rounded ridge of scree rather than a wall, steeper on the pit's side
        double ridge = top - (across < 0.0 ? RIDGE_INNER : RIDGE_FLANK) * (Math.sqrt(across * across + 36.0) - 6.0);
        return Math.max(h, Math.min(ridge, crest));
    }

    /** A younger cone standing on the crater floor, with a blobby foot and a shallow summit pit. */
    private double island(Crater k, double wx, double wz, double h, double crest) {
        double ix = wx - k.islandX;
        double iz = wz - k.islandZ;
        double d = Math.sqrt(ix * ix + iz * iz) / k.islandRadius;
        if (d >= 1.25) {
            return h;
        }
        d *= 1.0 + 0.2 * noise.noise2(ix / (0.9 * k.islandRadius) + k.noiseX + 40.0, iz / (0.9 * k.islandRadius) + k.noiseZ + 40.0);
        if (d >= 1.0) {
            return h;
        }
        // a rounded top over concave flanks that meet the ground beneath at the foot, and on
        // larger cones a shallow summit pit
        double rise = k.islandTop - h;
        double f = 1.0 - d;
        double cone = h + rise * f * Math.sqrt(f) * (1.0 + 1.5 * d);
        if (k.islandRadius > ISLAND_PIT_RADIUS) {
            double p = Math.max(0.0, 1.0 - d * d / 0.05);
            cone -= 0.15 * rise * p * p;
        }
        return Math.max(h, Math.min(cone, crest));
    }

    /**
     * A rounded mound of slumped rock with a blobby outline: a dome over a base level no higher
     * than the ground anywhere under it, so it meets the ground before its foot and stands on a
     * slope no steeper downhill than on the level.
     */
    private double mound(Crater k, int m, double wx, double wz, double h, double crest) {
        double mx = wx - k.moundX[m];
        double mz = wz - k.moundZ[m];
        double radius = k.moundRadius[m];
        double d2 = (mx * mx + mz * mz) / (radius * radius);
        if (d2 >= 1.5625) {
            return h;
        }
        double d = Math.sqrt(d2) * (1.0 + 0.2 * noise.noise2(mx / (0.9 * radius) + k.noiseX + 90.0 * m, mz / (0.9 * radius) + k.noiseZ - 60.0 * m));
        if (d >= 1.0) {
            return h;
        }
        double q = 1.0 - d * d;
        return Math.max(h, Math.min(k.moundBase[m] + (k.moundTop[m] - k.moundBase[m]) * q * q, crest));
    }

    /**
     * Radius of the rim's crest in the direction of offset {@code (dx, dz)}, for points beyond the
     * crater's reach, where the frame is no longer warped.
     */
    double rimRadius(TerrainModel.VolcanoSite v, double dx, double dz) {
        return lookup(v.crater.edge, Math.atan2(dz, dx));
    }

    /** A one-line account of a crater's shape, for the development tools. */
    static String describe(TerrainModel.VolcanoSite v) {
        Crater k = v.crater;
        double lo = Double.MAX_VALUE;
        double hi = 0.0;
        for (float s : k.share) {
            lo = Math.min(lo, s);
            hi = Math.max(hi, s);
        }
        return String.format(Locale.ROOT, "outline scale %.2f, wall share %.2f..%.2f, floor raised %.1f, steepness %.2f, "
                + "floor %.1f, lowest crest %.1f, mean crest %.1f,%s pit %s, island %s, mounds %d",
            k.outlineScale, lo, hi, k.raised, k.steepness, k.floorRel, k.rimLow, k.mean, k.dried ? " lake too shallow," : "", k.pit,
            k.island ? String.format(Locale.ROOT, "r %.0f top %.1f", k.islandRadius, k.islandTop) : "no", k.mounds)
            + (k.pit ? String.format(Locale.ROOT, ", ridge top %.1f..%.1f", k.ridgeTop - k.ridgeAmp, k.ridgeTop + k.ridgeAmp) : "")
            + (Double.isNaN(k.liquid) ? "" : String.format(Locale.ROOT, ", liquid top %.1f", k.liquid));
    }

    /**
     * Largest share (at most 1) of the outline's noise that keeps the outline within
     * {@link #MAX_TURN} of the circle at every bearing, the rest of the outline included.
     */
    private static double turnScale(double[] fixed, double[] wobble) {
        if (turn(fixed, wobble, 1.0) <= MAX_TURN) {
            return 1.0;
        }
        double lo = 0.0;
        double hi = 1.0;
        for (int it = 0; it < 12; it++) {
            double mid = 0.5 * (lo + hi);
            if (turn(fixed, wobble, mid) <= MAX_TURN) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Steepest turn of the outline against the circle: max |dr/dtheta| / r. */
    private static double turn(double[] fixed, double[] wobble, double scale) {
        int n = fixed.length;
        double worst = 0.0;
        for (int i = 0; i < n; i++) {
            int a = i == 0 ? n - 1 : i - 1;
            int b = i == n - 1 ? 0 : i + 1;
            double dr = (fixed[b] + scale * wobble[b] - fixed[a] - scale * wobble[a]) * n / (2.0 * TAU);
            worst = Math.max(worst, Math.abs(dr) / (fixed[i] + scale * wobble[i]));
        }
        return worst;
    }

    /**
     * How steep the wall gets at bearing {@code i} of the tables, over the slope it may have there
     * (1 is at the budget), at every height from the crest to the foot: the gradient of the
     * profile and its gullies, along the bearing and across it at the same distance from the
     * centre (where the outline, the crest and the foot turn), stretched by the warp's squeeze,
     * plus the steepest the floor's own relief can add. {@code floor} is the lowest the floor gets
     * under this bearing; {@code gully} holds the gullies' noise and its slopes across and down the
     * wall at each bearing and height.
     */
    private static double steepness(Crater k, int i, double floor, double floorSlope, float[] gully) {
        int n = RIM_SAMPLES;
        int a = i == 0 ? n - 1 : i - 1;
        int b = i == n - 1 ? 0 : i + 1;
        double dTheta = TAU / n;
        double e = k.exp[i];
        double crest = k.crest[i];
        double edge = k.edge[i];
        double run = run(edge, k.smooth[i], k.share[i]);
        double budget = Math.max(TALL_WALL_SLOPE, WALL_SLOPE - 0.004 * Math.max(0.0, crest - floor - TALL_WALL));
        double rib = k.ribs * 9.48;
        double worst = 0.0;
        for (int l = 0; l <= WALL_LEVELS; l++) {
            double w = l / (double) WALL_LEVELS;
            double foot = 1.0 - w;
            double rho = edge - run * w;
            double sq = Math.sqrt(foot);
            double top = k.mean + (crest - k.mean) * foot * sq;
            double pe = Math.pow(foot, e);
            double sv = ledged(pe, k.ledge[i], k.ledgePhase[i], k.ledges);
            // along the bearing, outward: the profile rises toward the crest as the foot term grows,
            // steeper between the ledges
            double radial = ((top - floor) * e * (foot > 0.0 ? pe / foot : 0.0) * ledgedSlope(pe, k.ledge[i], k.ledgePhase[i], k.ledges)
                + (crest - k.mean) * 1.5 * sq * sv) / run;
            double across = (profileAt(k, b, rho, floor) - profileAt(k, a, rho, floor)) / (2.0 * rho * dTheta);
            int at = 3 * (i * (WALL_LEVELS + 1) + l);
            double f3 = foot * foot * foot;
            radial -= rib * ((f3 - 3.0 * w * foot * foot) * gully[at] + w * f3 * gully[at + 2]) / run;
            across += rib * w * f3 * gully[at + 1] / rho;
            double g = Math.hypot(radial, across) * k.squeeze + floorSlope * (1.0 - sv);
            worst = Math.max(worst, g / budget);
        }
        return worst;
    }

    /** The wall's profile (without its gullies and the floor's relief) at distance {@code rho} along bearing {@code j} of the tables. */
    private static double profileAt(Crater k, int j, double rho, double floor) {
        double w = (k.edge[j] - rho) / run(k.edge[j], k.smooth[j], k.share[j]);
        if (w <= 0.0) {
            return k.crest[j];
        }
        if (w >= 1.0) {
            return floor;
        }
        double foot = 1.0 - w;
        double top = k.mean + (k.crest[j] - k.mean) * foot * Math.sqrt(foot);
        return floor + (top - floor) * ledged(Math.pow(foot, k.exp[j]), k.ledge[j], k.ledgePhase[j], k.ledges);
    }

    /** The wall's profile over the plain floor at distance {@code rho} along a bearing. */
    private static double wallAt(Crater k, double rho, double theta) {
        double edge = lookup(k.edge, theta);
        double w = (edge - rho) / runAt(k, theta);
        if (w <= 0.0) {
            return lookup(k.crest, theta);
        }
        if (w >= 1.0) {
            return k.floorRel;
        }
        double foot = 1.0 - w;
        double top = k.mean + (lookup(k.crest, theta) - k.mean) * foot * Math.sqrt(foot);
        return k.floorRel + (top - k.floorRel) * ledged(Math.pow(foot, lookup(k.exp, theta)), lookup(k.ledge, theta), lookup(k.ledgePhase, theta), k.ledges);
    }

    /**
     * The wall's profile from its foot (0) to its crest (1) at {@code q}, the power of the distance
     * from the foot: it rises evenly on the whole, but flattens into {@code ledges} ledges with
     * steeper steps between them, by {@code ledge} (below 1) of its slope either way; {@code phase}
     * moves the ledges up and down the wall.
     */
    private static double ledged(double q, double ledge, double phase, int ledges) {
        double a = TAU * ledges;
        return q + ledge * (Math.sin(a * q + phase) - Math.sin(phase)) / a;
    }

    /** Slope of {@link #ledged} over its argument. */
    private static double ledgedSlope(double q, double ledge, double phase, int ledges) {
        return 1.0 + ledge * Math.cos(TAU * ledges * q + phase);
    }

    /** Distance from the centre at which the wall meets the floor along a bearing. */
    private static double footRadius(Crater k, double theta) {
        return lookup(k.edge, theta) - runAt(k, theta);
    }

    /** How far the wall reaches in from the crest along a bearing. */
    private static double runAt(Crater k, double theta) {
        return run(lookup(k.edge, theta), lookup(k.smooth, theta), lookup(k.share, theta));
    }

    /**
     * How far the wall reaches in from a crest at radius {@code edge}: to the share {@code share}
     * of the smoothed outline {@code smooth} from the centre, and at least three tenths of the
     * share of the crest's own radius where a notch of the outline comes close to that.
     */
    private static double run(double edge, double smooth, double share) {
        return Math.max(edge - smooth * (1.0 - share), 0.3 * share * edge);
    }

    /**
     * Fits the wall's share at every bearing, starting from {@code from}: widens it where the
     * wall over {@code floor} is too steep, up to {@link #MAX_SHARE}, and returns how steep the
     * steepest bearing still is against its budget (at most 1 when the wall fits).
     */
    private static double widen(Crater k, float[] from, double floor, double[] drop, double[] floorSlope, float[] gully) {
        int n = RIM_SAMPLES;
        System.arraycopy(from, 0, k.share, 0, n);
        float[] need = new float[n];
        double worst = 0.0;
        for (int iter = 0; iter < 10; iter++) {
            worst = 0.0;
            for (int i = 0; i < n; i++) {
                double ratio = steepness(k, i, floor - drop[i], floorSlope[i], gully);
                worst = Math.max(worst, ratio);
                need[i] = (float) Math.min(MAX_SHARE, ratio > 1.0 ? k.share[i] * ratio * 1.02 : k.share[i]);
            }
            if (worst <= 1.0) {
                break;
            }
            smoothShare(need, k.share);
        }
        return worst;
    }

    /**
     * The lowest floor between {@code floor} and {@code ceiling} at which no bearing of the wall
     * as it stands is too steep, found by halving the interval; only the bearings near their
     * limit need checking.
     */
    private static double raiseFloor(Crater k, double floor, double ceiling, double[] drop, double[] floorSlope, float[] gully) {
        int n = RIM_SAMPLES;
        int[] near = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (steepness(k, i, floor - drop[i], floorSlope[i], gully) > 0.9) {
                near[count++] = i;
            }
        }
        double lo = floor;
        double hi = ceiling;
        for (int it = 0; it < 14; it++) {
            double mid = 0.5 * (lo + hi);
            double steepest = 0.0;
            for (int c = 0; c < count; c++) {
                int i = near[c];
                steepest = Math.max(steepest, steepness(k, i, mid - drop[i], floorSlope[i], gully));
            }
            if (steepest <= 1.0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /**
     * Widens the wall to at least {@code need} at every bearing, smoothly: the largest need over a
     * few neighbouring bearings, averaged over as many, which never falls below the need itself.
     */
    private static void smoothShare(float[] need, float[] share) {
        int n = need.length;
        int reach = 8;
        float[] peak = new float[n];
        for (int i = 0; i < n; i++) {
            float m = 0f;
            for (int d = -reach; d <= reach; d++) {
                m = Math.max(m, need[(i + d + n) % n]);
            }
            peak[i] = m;
        }
        for (int i = 0; i < n; i++) {
            float s = 0f;
            for (int d = -reach; d <= reach; d++) {
                s += peak[(i + d + n) % n];
            }
            share[i] = Math.max(need[i], Math.min((float) MAX_SHARE, s / (2 * reach + 1)));
        }
    }

    /** A table entry at a bearing, read between its two neighbouring entries. */
    static double lookup(float[] table, double theta) {
        double p = (theta / TAU - Math.floor(theta / TAU)) * RIM_SAMPLES;
        int i = Math.min((int) p, RIM_SAMPLES - 1);
        double f = p - i;
        int j = i + 1 == RIM_SAMPLES ? 0 : i + 1;
        return table[i] + (table[j] - table[i]) * f;
    }

    /** Power of the cone's profile around the crater (see {@link ConeShape} for the flanks). */
    static final double CONE_POWER = 1.65;

    /** The cone's height at a distance from the centre around the crater, without its relief. */
    static double cone(TerrainModel.VolcanoSite v, double r) {
        return r >= v.radius ? 0.0 : v.height * Math.pow(1.0 - r / v.radius, CONE_POWER);
    }

    /** A rounded bump over an angular window: 1 at its centre, 0 from {@code width} away on. */
    private static double window(double delta, double width) {
        double d = Math.IEEEremainder(delta, TAU);
        if (Math.abs(d) >= width) {
            return 0.0;
        }
        double k = 1.0 - (d / width) * (d / width);
        return k * k;
    }

    /**
     * One volcano's crater as {@link #shape} made it. Heights are relative to the volcano's base
     * height, angles in radians from east, distances in blocks. The tables hold one entry per
     * bearing, {@link #RIM_SAMPLES} of them from east around through south.
     */
    static final class Crater {
        /** Mean radius of the outline, the volcano's crater radius. */
        double r0;
        /** Offsets into the noise, so craters differ. */
        double noiseX;
        double noiseZ;
        /** Strength and scale of the domain warp, where it starts to fade out, and how much it can steepen a slope. */
        double warp;
        double warpScale;
        double warpStart;
        double squeeze;
        /** Share of its noise the outline kept within the turn limit. */
        double outlineScale;
        /** Radius of the rim's crest, height of the lip above the cone, and height of the crest. */
        float[] edge;
        float[] lipTable;
        float[] crest;
        /**
         * Share of the radius the inner wall takes at each bearing, measured on the outline
         * smoothed over some twenty degrees either way, which the wall's foot follows.
         */
        float[] share;
        float[] smooth;
        /** Mean height of the lip above the cone, and the width over which it falls back outside. */
        double lip;
        double lipWidth;
        /** Lowest and mean height of the crest. */
        double rimLow;
        double mean;
        /** Floor height, the height of its relief and the size of its broad blobs, and how far the slope budget raised it. */
        double floorRel;
        double floorAmp;
        double floorScale;
        double raised;
        /** Steepest the wall got against its budget (at most 1 unless the floor ran out of room). */
        double steepness;
        /** Whether the crater was rolled a lake but its floor had to rise too far to hold one. */
        boolean dried;
        /** Height of the top block of the lake or lava, or NaN. */
        double liquid;
        /** Power of the wall's profile: its mean, and at each bearing of the tables. */
        double wallExp;
        float[] exp;
        /**
         * The ledges of the wall: how many, how much they vary its slope at each bearing (0 is an
         * even slope), and where up the wall they lie at each bearing.
         */
        int ledges;
        float[] ledge;
        float[] ledgePhase;
        /** Whether the liquid is lava with plates of crust, and the share of the lake they leave free (their noise threshold). */
        boolean crust;
        double crustCut;
        /** Depth of the gullies on the inner wall, and the radius of the circle their noise is read on. */
        double ribs;
        double ribRing;
        /**
         * The pit: its centre, radius, bearing and the arc it spans seen from the centre, the
         * direction from it to the crater's centre, and the depth of its basin below the floor.
         */
        boolean pit;
        double pitX;
        double pitZ;
        double pitRadius;
        double pitAngle;
        double pitArc;
        double inX;
        double inZ;
        double pitDeep;
        /**
         * The ridge of the pit's rim: its mean top, how far the top rises and dips, the radius of
         * the circle that variation is read on, how far it sinks at its ends, and how far from the
         * pit's rim it can reach above the floor.
         */
        double ridgeTop;
        double ridgeAmp;
        double ridgeRing;
        double ridgeSink;
        double ridgeReach;
        /** The island cone: its centre, radius and peak. */
        boolean island;
        double islandX;
        double islandZ;
        double islandRadius;
        double islandTop;
        /** The mounds: centres, radii, tops and the levels their domes stand on. */
        int mounds;
        double[] moundX;
        double[] moundZ;
        double[] moundRadius;
        double[] moundTop;
        double[] moundBase;
    }

    /** Successive random rolls of one volcano site, on channels the site itself does not use. */
    private static final class Rolls {
        private final long seed;
        private final int cx;
        private final int cz;
        private int channel = 32;

        Rolls(long seed, int cx, int cz) {
            this.seed = seed;
            this.cx = cx;
            this.cz = cz;
        }

        double next() {
            return SeedMixer.cellUnit(seed, cx, cz, channel++);
        }
    }
}
