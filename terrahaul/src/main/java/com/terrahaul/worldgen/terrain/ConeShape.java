package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.clamp01;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;

/**
 * The outer cone of {@link TerrainModel}'s volcanoes, outside the crater (see {@link CraterShape}):
 * a cone that is never a smooth, symmetric dome of contour rings, but leans to one side, is cut by
 * barrancos, carries lava flows and side cones, and is rough everywhere.
 * <ul>
 *   <li>the <b>profile</b> differs from one side to the other: past the crater the power of the
 *       cone's profile changes with the bearing (most on one side, varied by noise around the
 *       cone), so one flank is concave, steep under the rim and long and low further down, and
 *       the opposite one full, with high shoulders; the foot stays where it was;</li>
 *   <li><b>barrancos</b>, V-shaped gullies, run down the flanks along the zero lines of a noise
 *       read on circles around the centre and stretched along the radius, so they run roughly
 *       downhill, wander, join and split, with sharp ridges between neighbours; finer rills
 *       groove the upper flanks between them. Their depth grows down the flank as far as their
 *       sides stay gentler than about 40 degrees;</li>
 *   <li><b>lava flows</b> leave the crater at a notch of its rim, the lowest point of the crest
 *       first, and run down the flank as tongues of rock a few blocks thick, draped over the
 *       ground they cross, meandering, widening downhill and ending in a lobed front, with margins
 *       of blocky rock. The same flows lay their rock in {@link VolcanoZones};</li>
 *   <li><b>side cones</b>, one or two small cinder cones on the flanks with a summit crater
 *       (dry: open downhill where the flank is steeper than the bowl is deep);</li>
 *   <li>a <b>rough</b> surface of broad shoulders and hummocks down to bumps a dozen blocks
 *       across, so no slope of the cone ever steps down in even rings.</li>
 * </ul>
 * All of it fades in across the rim's lip, so the crest of the rim, which holds the crater's lake
 * or lava, is exactly what {@link CraterShape} made it. Everything is a pure function of the
 * position and the world seed; heights are relative to the volcano's base height.
 */
final class ConeShape {
    private static final double TAU = Math.PI * 2.0;
    /** Bearings in the lean table: a degree apart, the lean changes slowly. */
    private static final int LEAN_SAMPLES = 360;
    static final int MAX_FLOWS = 5;
    static final int MAX_CONES = 3;
    /** Length of the array {@link #zones} writes its results to. */
    static final int OUT = 4;
    /** Half-width of a barranco's V in noise units: the share of the flank the barrancos take. */
    private static final double GULLY_WIDTH = 0.4;
    /**
     * Steepest side of a barranco, in blocks per block, for the typical slope of the noise (its
     * steepest few percent are about three times steeper over a few blocks).
     */
    private static final double GULLY_SIDE = 0.8;
    /** Typical steepness of the simplex noise per unit of its input (about the 90th percentile). */
    private static final double NOISE_GRADIENT = 2.2;
    /** Steepness of a lava flow's margin, in blocks per block at its steepest. */
    private static final double FLOW_MARGIN = 1.1;
    /**
     * How fast the distance from a flow's middle changes per block across its lobed margin, at
     * most for the typical slope of the lobes' noise.
     */
    private static final double MARGIN_GRADIENT = 1.5;
    private static final long SALT = 0x5EED_C0FE_1A7AL;
    /** First random channel of a site cell used here; the crater's rolls stay below it. */
    private static final int CHANNEL = 200;

    private final SimplexNoise noise;

    ConeShape(long volcanoSeed) {
        this.noise = new SimplexNoise(SeedMixer.derive(volcanoSeed, SALT));
    }

    /**
     * Makes the outer cone of a site whose crater is already shaped and whose activity is set:
     * its lean, barrancos, roughness, lava flows and side cones. Sets the site's {@code cone}.
     */
    void shape(TerrainModel.VolcanoSite v, long seed, int cx, int cz) {
        Cone k = new Cone();
        v.cone = k;
        int channel = CHANNEL;
        k.noiseX = 2000.0 * SeedMixer.cellUnit(seed, cx, cz, channel++) - 1000.0;
        k.noiseZ = 2000.0 * SeedMixer.cellUnit(seed, cx, cz, channel++) - 1000.0;
        double radius = v.radius;
        k.inner = v.craterReach;

        // ---- the lean: a concave flank on one side, a full one with high shoulders opposite ---------
        double maxLean = 0.3 + 0.3 * SeedMixer.cellUnit(seed, cx, cz, channel++);
        double leanAngle = TAU * SeedMixer.cellUnit(seed, cx, cz, channel++);
        k.lean = new float[LEAN_SAMPLES];
        for (int i = 0; i < LEAN_SAMPLES; i++) {
            double a = i * TAU / LEAN_SAMPLES;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double side = 0.5 + 0.4 * Math.cos(a - leanAngle) + 0.35 * noise.noise2(c * 1.1 + k.noiseX, s * 1.1 + k.noiseZ)
                + 0.12 * noise.noise2(c * 3.0 + k.noiseZ - 50.0, s * 3.0 - k.noiseX);
            k.lean[i] = (float) (maxLean * (2.0 * clamp01(side) - 0.8));
        }

        // ---- barrancos and the roughness of the flanks -------------------------------------------
        k.gullyDepth = clamp(0.05 * v.height, 6.0, 30.0) * (0.7 + 0.6 * SeedMixer.cellUnit(seed, cx, cz, channel++));
        // some five to eight barrancos around the cone, many more rills
        k.ring = 3.2 + 2.0 * SeedMixer.cellUnit(seed, cx, cz, channel++);
        k.rillRing = k.ring * (2.4 + 0.6 * SeedMixer.cellUnit(seed, cx, cz, channel++));
        // long along the radius, so they run downhill; shorter for the rills, which wander more
        k.gullyLength = 0.32 * radius;
        k.rillLength = 0.14 * radius;
        k.rough = Math.min(6.0, 3.0 + 0.006 * v.height);
        k.hummock = clamp(0.03 * v.height, 6.0, 18.0);
        k.hummockScale = clamp(0.12 * radius, 70.0, 220.0);

        // ---- lava flows: tongues of rock from notches of the rim ------------------------------------
        int flows = Math.min(MAX_FLOWS, (int) (v.activity * 4.2 + 1.2 * SeedMixer.cellUnit(seed, cx, cz, channel++)));
        double craterT = v.craterRadius / radius;
        double bareEdge = craterT + 0.07 + 0.22 * v.activity;
        double start = SeedMixer.cellUnit(seed, cx, cz, channel++);
        CraterShape.Crater crater = v.crater;
        // the lowest point of the crest, where lava overflows first
        int lowest = 0;
        for (int i = 1; i < crater.crest.length; i++) {
            if (crater.crest[i] < crater.crest[lowest]) {
                lowest = i;
            }
        }
        double lowAngle = lowest * TAU / crater.crest.length;
        k.flows = flows;
        k.flowAngle = new double[flows];
        k.flowStart = new double[flows];
        k.flowEnd = new double[flows];
        k.flowWidth = new double[flows];
        k.flowHalo = new double[flows];
        k.flowMeander = new double[flows];
        k.flowWave = new double[flows];
        k.flowPhase = new double[flows];
        k.flowThickness = new double[flows];
        for (int i = 0; i < flows; i++) {
            int c = channel + 8 * i;
            double angle = i == 0 ? lowAngle : lowAngle + TAU * (start + (i + 0.5 * (SeedMixer.cellUnit(seed, cx, cz, c) - 0.5)) / flows);
            k.flowAngle[i] = angle;
            k.flowStart[i] = CraterShape.lookup(crater.edge, angle) + 0.5 * crater.lipWidth;
            double length = 0.1 + 0.5 * SeedMixer.cellUnit(seed, cx, cz, c + 1);
            k.flowEnd[i] = radius * Math.min(0.88, bareEdge + length * (1.0 - bareEdge));
            k.flowWidth[i] = 7.0 + 15.0 * SeedMixer.cellUnit(seed, cx, cz, c + 2) + 0.008 * radius;
            k.flowHalo[i] = 10.0 + 0.4 * k.flowWidth[i] + 14.0 * SeedMixer.cellUnit(seed, cx, cz, c + 3);
            k.flowMeander[i] = 6.0 + 0.012 * radius;
            k.flowWave[i] = 70.0 + 0.05 * radius;
            k.flowPhase[i] = 100.0 * SeedMixer.cellUnit(seed, cx, cz, c + 4);
            k.flowThickness[i] = clamp(0.22 * k.flowWidth[i], 2.5, 6.5) * (0.8 + 0.4 * SeedMixer.cellUnit(seed, cx, cz, c + 5));
        }
        channel += 8 * MAX_FLOWS;

        // ---- side cones: small cinder cones on the flanks ------------------------------------------
        double count = SeedMixer.cellUnit(seed, cx, cz, channel++);
        int cones = Math.min(MAX_CONES, (count < 0.25 ? 0 : count < 0.7 ? 1 : 2) + (radius > 1300.0 ? 1 : 0));
        k.cones = cones;
        k.coneX = new double[cones];
        k.coneZ = new double[cones];
        k.coneRadius = new double[cones];
        k.coneHeight = new double[cones];
        k.coneCrater = new double[cones];
        k.coneDepth = new double[cones];
        k.coneAxisX = new double[cones];
        k.coneAxisZ = new double[cones];
        k.coneStretch = new double[cones];
        for (int i = 0; i < cones; i++) {
            int c = channel + 8 * i;
            double angle = TAU * SeedMixer.cellUnit(seed, cx, cz, c);
            double size = clamp(radius / 900.0, 0.7, 1.5);
            double rp = (45.0 + 75.0 * SeedMixer.cellUnit(seed, cx, cz, c + 1)) * size;
            // on the flank, its foot well inside the cone's
            double foot = radius;
            double t = 0.32 + 0.35 * SeedMixer.cellUnit(seed, cx, cz, c + 2);
            double dist = Math.max(v.craterReach + 2.0 * rp, Math.min(t * foot, 0.9 * foot - 2.0 * rp));
            k.coneX[i] = Math.cos(angle) * dist;
            k.coneZ[i] = Math.sin(angle) * dist;
            k.coneRadius[i] = rp;
            k.coneHeight[i] = rp * (0.32 + 0.18 * SeedMixer.cellUnit(seed, cx, cz, c + 3));
            k.coneCrater[i] = 0.28 + 0.12 * SeedMixer.cellUnit(seed, cx, cz, c + 4);
            // the bowl's sides stay gentler than about 40 degrees
            k.coneDepth[i] = Math.min(0.3 * k.coneHeight[i], 0.4 * k.coneCrater[i] * rp);
            // elongated along a fissure, up to half as long again as it is wide
            double axis = TAU * SeedMixer.cellUnit(seed, cx, cz, c + 5);
            k.coneAxisX[i] = Math.cos(axis);
            k.coneAxisZ[i] = Math.sin(axis);
            k.coneStretch[i] = 1.0 / (1.0 + 0.5 * SeedMixer.cellUnit(seed, cx, cz, c + 6));
        }
    }

    /**
     * Height of the cone, relative to the volcano's base, at offset {@code (dx, dz)} at distance
     * {@code r} from the centre, beyond the crater's reach: the leaning profile with all its relief.
     * The power of the profile changes only past the crater's reach, and so slowly that the flank
     * keeps falling all the way down.
     */
    double flank(TerrainModel.VolcanoSite v, double dx, double dz, double r) {
        Cone k = v.cone;
        double theta = Math.atan2(dz, dx);
        double power = CraterShape.CONE_POWER + lean(k, theta) * smoothstep(k.inner, v.radius, r);
        double cone = r >= v.radius ? 0.0 : v.height * Math.pow(1.0 - r / v.radius, power);
        return cone + relief(v, dx, dz, r, theta);
    }

    /**
     * The relief of the cone at offset {@code (dx, dz)}, bearing {@code theta} and distance
     * {@code r}: roughness and hummocks, barrancos and rills, lava flows and side cones. Around the
     * crater's rim it is faded in by the caller.
     */
    double relief(TerrainModel.VolcanoSite v, double dx, double dz, double r, double theta) {
        Cone k = v.cone;
        double radius = v.radius;
        // fades out at the foot of the cone, where the volcano ends
        double foot = 1.0 - smoothstep(0.78 * radius, 0.98 * radius, r);
        if (foot <= 0.0) {
            return 0.0;
        }
        // the hummocks rise only past the crater's reach, slowly enough that the rim's lip, across
        // which the rest fades in, never steepens into a step
        double rough = k.rough * (0.7 * noise.noise2(dx / 34.0 + k.noiseX, dz / 34.0 + k.noiseZ)
            + 0.3 * noise.noise2(dx / 12.0 - k.noiseZ, dz / 12.0 + k.noiseX))
            + k.hummock * smoothstep(k.inner, k.inner + Math.max(60.0, 3.0 * k.hummock), r)
            * noise.noise2(dx / k.hummockScale + k.noiseZ + 90.0, dz / k.hummockScale - k.noiseX);

        // barrancos and rills: V-shaped along the zero lines of noise read on circles
        double gully = 0.0;
        double env = smoothstep(k.inner, k.inner + 0.12 * radius, r);
        if (env > 0.0) {
            double c = dx / r;
            double s = dz / r;
            double n = noise.noise3(c * k.ring + k.noiseX + 41.0, s * k.ring + k.noiseZ - 17.0, r / k.gullyLength);
            double vee = Math.max(0.0, 1.0 - Math.abs(n) / GULLY_WIDTH);
            // as deep as the sides allow: their slope is the depth over the V's half-width in blocks
            double depth = Math.min(k.gullyDepth, GULLY_SIDE * GULLY_WIDTH * r / (k.ring * NOISE_GRADIENT));
            gully = depth * vee;
            double upper = 1.0 - smoothstep(0.35 * radius, 0.65 * radius, r);
            if (upper > 0.0) {
                double m = noise.noise3(c * k.rillRing + k.noiseZ - 230.0, s * k.rillRing + k.noiseX + 190.0, r / k.rillLength);
                double rill = Math.max(0.0, 1.0 - Math.abs(m) / GULLY_WIDTH);
                gully += upper * Math.min(0.35 * k.gullyDepth, GULLY_SIDE * GULLY_WIDTH * r / (k.rillRing * NOISE_GRADIENT)) * rill;
            }
            gully *= env;
        }

        // lava flows: tongues of rock draped over the ground they ran down, barrancos included (a
        // tongue that filled a deep barranco would end in a wall where its margin crosses it)
        double tongue = 0.0;
        for (int i = 0; i < k.flows; i++) {
            if (r < k.flowStart[i] || r > k.flowEnd[i] + k.flowWidth[i] + 12.0) {
                continue;
            }
            double w = flowWidth(k, i, r);
            double dist = flowDistance(k, i, dx, dz, r, theta, w, false);
            if (dist < w) {
                // as thick as it is wide, so its margins stay as gentle wherever it runs
                double thick = k.flowThickness[i] * w / k.flowWidth[i];
                double margin = Math.max(0.45 * w, 1.5 * MARGIN_GRADIENT * thick / FLOW_MARGIN);
                double p = smoothstep(0.0, margin, w - dist) * smoothstep(k.flowStart[i], k.flowStart[i] + 30.0, r);
                // blocky, uneven top
                double top = thick * p * (1.0 + 0.25 * noise.noise2(dx / 9.0 + 13.0 * i, dz / 9.0 - 7.0 * i));
                tongue = Math.max(tongue, top);
            }
        }

        // side cones, standing on the flank, the barrancos fading out under them
        double cones = 0.0;
        double buried = 0.0;
        for (int i = 0; i < k.cones; i++) {
            double d = coneDistance(k, i, dx, dz);
            if (d >= 1.0) {
                continue;
            }
            double cr = k.coneCrater[i];
            double h = k.coneHeight[i];
            double rim = h * Math.pow(1.0 - cr, 1.6);
            // concave flanks up to the rim of a bowl-shaped summit crater
            double cone = d > cr ? h * Math.pow(1.0 - d, 1.6) : rim - k.coneDepth[i] * (1.0 - d * d / (cr * cr));
            cones = Math.max(cones, cone);
            buried = Math.max(buried, smoothstep(1.0, 0.6, d));
        }
        return foot * (rough + tongue - gully * (1.0 - buried) + cones);
    }

    /**
     * Per-column values for {@link VolcanoZones} at offset {@code (dx, dz)} from the centre, into
     * {@code out} (length {@link #OUT}): {@code out[0]} inside a lava flow, from just above 0 at its
     * margin to 1 in its core (0 outside); {@code out[1]} the scorched halo of the flows, 1 at a
     * flow's margin fading to 0 in the forest; {@code out[2]} the barrancos, -1 on a gully floor
     * to 1 on the ridges between them; {@code out[3]} on a side cone, 1 on its cone fading to 0 at
     * its foot.
     */
    void zones(TerrainModel.VolcanoSite v, double dx, double dz, double[] out) {
        Cone k = v.cone;
        double r = Math.sqrt(dx * dx + dz * dz);
        double theta = Math.atan2(dz, dx);
        double flow = 0.0;
        double halo = 0.0;
        for (int i = 0; i < k.flows; i++) {
            double w = flowWidth(k, i, r);
            double reachMax = w + 2.0 * k.flowHalo[i];
            if (r < k.flowStart[i] || r > k.flowEnd[i] + reachMax) {
                continue;
            }
            double dist = flowDistance(k, i, dx, dz, r, theta, w, true);
            if (dist == Double.MAX_VALUE) {
                continue;
            }
            if (dist < w) {
                flow = Math.max(flow, Math.max(1.0E-3, clamp01((w - dist) / (0.45 * w))));
            }
            // scorched margin, wider ahead of the front where the lava ran into the forest
            double toe = k.flowEnd[i] - w;
            double reach = k.flowHalo[i] * (r > toe ? 1.0 + 0.8 * clamp01((r - toe) / w) : 1.0);
            halo = Math.max(halo, smoothstep(w + reach, w + 0.25 * reach, dist));
        }
        out[0] = flow;
        out[1] = halo;
        double gully = 1.0;
        if (r > k.inner && r > 1.0) {
            double n = noise.noise3(dx / r * k.ring + k.noiseX + 41.0, dz / r * k.ring + k.noiseZ - 17.0, r / k.gullyLength);
            gully = -1.0 + 2.0 * Math.min(1.0, Math.abs(n) / GULLY_WIDTH);
        }
        out[2] = gully;
        double cone = 0.0;
        for (int i = 0; i < k.cones; i++) {
            cone = Math.max(cone, smoothstep(1.1, 0.8, coneDistance(k, i, dx, dz)));
        }
        out[3] = cone;
    }

    /**
     * Distance of a point from the centre of side cone {@code i} over the cone's radius, along
     * its elongated, blobby outline: 1 at its foot; large well away from it.
     */
    private double coneDistance(Cone k, int i, double dx, double dz) {
        double px = dx - k.coneX[i];
        double pz = dz - k.coneZ[i];
        double rp = k.coneRadius[i];
        if (px * px + pz * pz >= 4.0 * rp * rp) {
            return 2.0;
        }
        double along = (px * k.coneAxisX[i] + pz * k.coneAxisZ[i]) * k.coneStretch[i];
        double across = -px * k.coneAxisZ[i] + pz * k.coneAxisX[i];
        return Math.sqrt(along * along + across * across) / rp
            * (1.0 + 0.25 * noise.noise2(px / (0.8 * rp) + 31.0 * i, pz / (0.8 * rp) - 17.0 * i));
    }

    /** Half-width of flow {@code i} at distance {@code r}: it widens downhill. */
    private static double flowWidth(Cone k, int i, double r) {
        double along = clamp01((r - k.flowStart[i]) / Math.max(1.0, k.flowEnd[i] - k.flowStart[i]));
        return k.flowWidth[i] * (0.55 + 0.45 * along);
    }

    /**
     * Distance of a point from the middle of flow {@code i} whose half-width there is {@code w},
     * measured across the flow and, past its toe, from the toe; its margins lobed, and with
     * {@code ragged} a few blocks of raggedness that only the rock's outline shows, not the
     * tongue's height, whose margins would steepen. Large where the point is far off the flow.
     */
    private double flowDistance(Cone k, int i, double dx, double dz, double r, double theta, double w, boolean ragged) {
        double dTheta = Math.IEEEremainder(theta - k.flowAngle[i], TAU);
        if (Math.abs(dTheta) * r > w + k.flowMeander[i] + 2.0 * k.flowHalo[i] + 16.0) {
            return Double.MAX_VALUE;
        }
        double meander = k.flowMeander[i] * noise.noise2(r / k.flowWave[i] + k.flowPhase[i], 17.3 * i + 101.0);
        double lateral = dTheta * r - meander;
        double toe = k.flowEnd[i] - w;
        double dist = r > toe ? Math.sqrt(lateral * lateral + (r - toe) * (r - toe)) : Math.abs(lateral);
        // lobes along the margins and the front, and a few blocks of raggedness
        dist += 0.2 * w * noise.noise2(dx / (0.9 * w) + 7.0 * i + k.noiseX, dz / (0.9 * w) - 5.0 * i);
        return ragged ? dist + 2.5 * noise.noise2(dx / 7.0 + 3.1 * i, dz / 7.0 - 5.3) : dist;
    }

    /** The lean at a bearing, read between neighbouring table entries. */
    private static double lean(Cone k, double theta) {
        double p = (theta / TAU - Math.floor(theta / TAU)) * LEAN_SAMPLES;
        int i = Math.min((int) p, LEAN_SAMPLES - 1);
        double f = p - i;
        int j = i + 1 == LEAN_SAMPLES ? 0 : i + 1;
        return k.lean[i] + (k.lean[j] - k.lean[i]) * f;
    }

    /** One volcano's outer cone as {@link #shape} made it; distances in blocks, angles in radians from east. */
    static final class Cone {
        double noiseX;
        double noiseZ;
        /** Distance from the centre where the lean and the barrancos begin: the crater's reach. */
        double inner;
        /** How much the power of the profile changes past the crater at each bearing (about -0.5 .. 0.7). */
        float[] lean;
        /** Deepest barranco, the radii of the circles the barrancos' and rills' noise is read on, and their lengths along the radius. */
        double gullyDepth;
        double ring;
        double rillRing;
        double gullyLength;
        double rillLength;
        /** Height of the fine roughness and of the broad hummocks, and the hummocks' size. */
        double rough;
        double hummock;
        double hummockScale;
        /** The lava flows: bearing, start and end distance, half-width, halo, meander and thickness. */
        int flows;
        double[] flowAngle;
        double[] flowStart;
        double[] flowEnd;
        double[] flowWidth;
        double[] flowHalo;
        double[] flowMeander;
        double[] flowWave;
        double[] flowPhase;
        double[] flowThickness;
        /** The side cones: centre (from the volcano's centre), radius, height, crater share and crater depth. */
        int cones;
        double[] coneX;
        double[] coneZ;
        double[] coneRadius;
        double[] coneHeight;
        double[] coneCrater;
        double[] coneDepth;
        /** Direction of each side cone's long axis, and how much it is drawn out along it (1 / its elongation). */
        double[] coneAxisX;
        double[] coneAxisZ;
        double[] coneStretch;
    }
}
