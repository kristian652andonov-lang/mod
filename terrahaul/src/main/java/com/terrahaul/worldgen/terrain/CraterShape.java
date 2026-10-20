package com.terrahaul.worldgen.terrain;

import static com.terrahaul.worldgen.noise.TMath.clamp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.noise.SimplexNoise;

/**
 * The craters of {@link TerrainModel}'s volcanoes: bowls with a blobby outline, a rim of uneven
 * height and an uneven floor, so that crater lakes get ragged shores, islands and sometimes a
 * second pool instead of being perfect discs of water in perfect funnels.
 * <p>
 * The crater is laid out in polar coordinates around the volcano's centre, after a gentle domain
 * warp that bends the outline a little further than any curve of radius-by-bearing could:
 * <ul>
 *   <li>the <b>outline</b>, the radius of the rim's crest at each bearing, is the crater's radius
 *       varied by noise read on circles (a few broad lobes, finer scallops and a roughness of a
 *       few blocks), widened by rounded bays where part of the wall slumped outward, and
 *       sometimes by the lobe of a pit crater that cut into the rim;</li>
 *   <li>the <b>crest</b> of the rim is the cone's height at the outline plus a raised lip whose
 *       height wanders: notches and breaches cut it down, broad shoulders stand higher. Bays lie
 *       further down the cone, so their rims are lower, as collapse scars are;</li>
 *   <li>the <b>inner wall</b> falls from the crest to the floor over the outer part of each
 *       bearing's radius, steepest under the crest and flattening into scree at its foot, ribbed
 *       by gullies on its upper part. The band is the same share of the radius all around, so
 *       the foot of the wall and every shore line are scaled copies of the outline, and it is
 *       wide enough that the wall stays within a slope budget where the outline pulls in
 *       furthest; elsewhere the wall is gentler. Together with outline noise whose flanks run
 *       no more than about forty degrees across the bearing, a gentle warp and bays as wide as
 *       they are deep, the chaos of the rim never turns into sheer faces;</li>
 *   <li>the <b>floor</b> is hummocky; some craters hold a younger cone standing as an island, or
 *       a ridge that cuts a second, smaller basin off the main one;</li>
 *   <li>outside the outline the lip falls back to the cone over a band a fifth of the crater's
 *       radius wide.</li>
 * </ul>
 * Whatever the floor does, the surface along the outline is the crest at that bearing, and every
 * path out of the crater crosses the outline, so the crater holds anything below its lowest
 * crest. That lowest point is found once per volcano by sampling the crest all around the rim,
 * and lakes and lava pools are filled to a few blocks below it at most: they stay enclosed however
 * chaotic the rim gets, with no water walls and nothing for the water solver to dam.
 * <p>
 * The per-volcano parameters live in a {@link Crater}, rolled once when the volcano site is made;
 * everything is a pure function of the position and the world seed. Heights are relative to the
 * volcano's base height.
 */
final class CraterShape {
    private static final double TAU = Math.PI * 2.0;
    /** Bearings sampled to find the lowest and highest points and the narrowest and widest reach of a rim. */
    private static final int RIM_SAMPLES = 1440;
    /**
     * Lakes and lava stay at least this far below the lowest crest. The noise chunk interpolates
     * the ground between corners four blocks apart, which rounds off a narrow crest by a block or
     * two; the rest is freeboard, so no shore sits exactly on the brim.
     */
    static final double RIM_MARGIN = 4.0;
    /**
     * Slope of the inner wall right under the crest where the outline pulls in furthest, in
     * blocks per block (about 65 degrees), before the gullies, the warp and the outline's turns add
     * their share: steep enough to read as a crater wall, gentle enough that it never becomes one
     * of the sheer faces the terrain avoids. Walls taller than {@link #TALL_WALL} get gentler, down
     * to {@link #TALL_WALL_SLOPE}, so they do not stand as one huge face.
     */
    private static final double WALL_SLOPE = 2.2;
    private static final double TALL_WALL = 80.0;
    private static final double TALL_WALL_SLOPE = 1.6;
    /** Deepest the gullies on the inner wall get, in blocks: their spacing does not grow with the wall. */
    private static final double MAX_GULLY = 4.0;
    /**
     * Room a lake keeps between the floor's highest hummocks and the brim it may fill to, even
     * where the wall had to give a crater too narrow for its depth a higher floor.
     */
    private static final double MIN_LAKE_DEPTH = 8.0;
    /** Radii of the circles the outline's noise is read on: broad lobes and finer scallops. */
    private static final double LOBE_RING = 1.15;
    private static final double SCALLOP_RING = 2.8;
    /** Radius of the circle the lip's height noise is read on. */
    private static final double LIP_RING = 1.7;
    static final int MAX_BAYS = 3;
    static final int MAX_CRESTS = 5;
    /** Island cones wider than this have a summit pit, whose rim stands at this share of the peak. */
    private static final double ISLAND_PIT_RADIUS = 40.0;
    private static final double ISLAND_PIT_RIM = 0.9;
    /** Length of the array {@link #surface} writes its results to. */
    static final int OUT = 3;
    private static final long SALT = 0xC4A7_E5B1_0B5EL;

    private final SimplexNoise noise;

    CraterShape(long volcanoSeed) {
        this.noise = new SimplexNoise(SeedMixer.derive(volcanoSeed, SALT));
    }

    /**
     * Rolls the crater of a new volcano site (whose radius, depth and cone are already set) and
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
        k.lobes = 0.09 + 0.06 * u.next();
        // the flanks of lobes and scallops run mostly within forty degrees of the bearing's circle
        k.scallops = 0.025 + 0.025 * u.next();
        // a few blocks of roughness at a fixed size in blocks, so large rims are not smooth arcs
        // (less on small rims, where it would make the outline spiky)
        k.roughRing = Math.max(SCALLOP_RING, r0 / 22.0);
        k.rough = Math.min(3.0, 0.025 * r0);
        // the warp squeezes the frame where it bends it, which steepens the walls there: kept to
        // a fifth at most (the noise's gradient reaches about 7 per unit)
        k.warpScale = r0;
        k.warp = k.warpScale * (0.02 + 0.01 * u.next());
        k.lip = (3.0 + 0.035 * r0) * (0.7 + 0.6 * u.next());
        k.lipWidth = Math.max(10.0, 0.2 * r0) * (0.8 + 0.4 * u.next());
        k.wallExp = 1.7 + 0.6 * u.next();
        // hummocks a few blocks high, gentle at any crater size
        k.floorAmp = Math.min(0.1 * depth, 0.05 * r0) * (0.6 + 0.5 * u.next());
        k.ribs = Math.min(MAX_GULLY, (0.015 + 0.025 * u.next()) * depth);
        // gullies some thirty to forty blocks apart at the rim
        k.ribRing = Math.max(2.0, r0 / 28.0);
        k.floorRel = cone(v, r0) - depth;

        // bays: rounded scallops where part of the wall slumped outward; a bay cuts back about as
        // far as it is wide, so its sides run no more obliquely across the bearing than a lobe's
        k.bays = Math.min(MAX_BAYS, (int) (u.next() * 3.2));
        for (int i = 0; i < k.bays; i++) {
            k.bayAngle[i] = TAU * u.next();
            k.bayWidth[i] = 0.25 + 0.35 * u.next();
            k.baySize[i] = r0 * k.bayWidth[i] * (0.25 + 0.3 * u.next());
        }
        // notches and breaches lower the lip over a narrow arc, shoulders raise it over a wide one;
        // a notch may cut below the cone, but not so far that the ground outside it would rise
        double coneSlope = 1.65 * v.height / v.radius * Math.pow(1.0 - r0 / v.radius, 0.65);
        int notches = 1 + (int) (u.next() * 2.6);
        int shoulders = (int) (u.next() * 2.4);
        k.crests = Math.min(MAX_CRESTS, notches + shoulders);
        for (int i = 0; i < k.crests; i++) {
            k.crestAngle[i] = TAU * u.next();
            if (i < notches) {
                k.crestWidth[i] = (6.0 + 0.07 * r0) * (0.6 + 0.9 * u.next()) / r0;
                k.crestHeight[i] = -Math.min(k.lip * (0.6 + 1.3 * u.next()), k.lip + 0.4 * coneSlope * k.lipWidth);
            } else {
                k.crestWidth[i] = 0.3 + 0.5 * u.next();
                k.crestHeight[i] = k.lip * (0.5 + 0.9 * u.next());
            }
        }

        // a second basin: a ridge across the crater cuts a smaller basin off the main one towards
        // one side, where a younger pit crater often bit into the rim and pushed it outward
        double pitRoll = u.next();
        double pitAngle = TAU * u.next();
        double pitFrac = u.next();
        double pitSize = u.next();
        double pitBite = u.next();
        double septumRoll = u.next();
        k.pit = pitRoll < 0.45;
        if (k.pit) {
            double edge = outlineBase(k, Math.cos(pitAngle), Math.sin(pitAngle), pitAngle);
            k.pitAngle = pitAngle;
            k.septum = edge * (0.4 + 0.2 * pitFrac);
            double pitRadius = (edge - k.septum) * (0.45 + 0.25 * pitSize);
            double pitDist = k.septum + pitRadius * 1.05 + (edge - k.septum - pitRadius) * pitBite;
            // the pit's lobe of the outline, where the pit reaches past the old rim, over the arc
            // it spans seen from the centre; no more oblique to the bearing than a bay's sides
            k.pitWidth = Math.asin(Math.min(0.95, pitRadius / pitDist)) * 1.3;
            k.pitBulge = Math.min(Math.max(0.0, pitDist + 0.85 * pitRadius - edge), 0.5 * k.pitWidth * edge);
        }

        // the rim all around: its lowest and highest crest, its mean, and its narrowest and
        // widest reach
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        double sum = 0.0;
        double rMin = Double.MAX_VALUE;
        double rMax = 0.0;
        for (int i = 0; i < RIM_SAMPLES; i++) {
            double a = i * TAU / RIM_SAMPLES;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double rim = outline(k, c, s, a);
            double crest = cone(v, rim) + lip(k, c, s, a);
            low = Math.min(low, crest);
            high = Math.max(high, crest);
            sum += crest;
            rMin = Math.min(rMin, rim);
            rMax = Math.max(rMax, rim);
        }
        k.rimLow = low;
        double mean = sum / RIM_SAMPLES;
        k.warpStart = rMax;
        v.craterReach = rMax + k.warp + k.lipWidth + 4.0;

        // the wall's share of the radius: at least what keeps it within the slope budget under
        // the highest crest where the outline pulls in furthest, more on a roll. A crater too
        // narrow for its depth gets a higher floor rather than steeper walls (unless that would
        // leave its lake too shallow)
        double wallRoll = u.next();
        double slope = Math.max(TALL_WALL_SLOPE, WALL_SLOPE - 0.004 * Math.max(0.0, high - k.floorRel - TALL_WALL));
        double share = k.wallExp * (high - k.floorRel) / (slope * rMin);
        if (share >= 1.0) {
            k.wallShare = 1.0;
            double raised = high - slope * rMin / k.wallExp;
            k.floorRel = Math.max(k.floorRel, Math.min(raised, low - RIM_MARGIN - MIN_LAKE_DEPTH - k.floorAmp));
        } else {
            k.wallShare = share + (1.0 - share) * (0.1 + 0.4 * wallRoll);
        }

        // the liquid: lakes from three fifths full to near the brim of the lowest notch, lava low down
        double fill = u.next();
        double top = low - RIM_MARGIN;
        double liquidRel = Double.NaN;
        v.waterLevel = ColumnSample.NO_WATER;
        v.lavaLevel = ColumnSample.NO_WATER;
        if (lake) {
            v.waterLevel = Math.floor(v.baseHeight + k.floorRel + (0.6 + 0.35 * fill) * (top - k.floorRel));
            liquidRel = v.waterLevel - 1.0 - v.baseHeight;
        } else if (v.lava) {
            v.lavaLevel = Math.floor(v.baseHeight + k.floorRel + Math.min(0.2 * (mean - k.floorRel), top - k.floorRel));
            liquidRel = v.lavaLevel - 1.0 - v.baseHeight;
        }

        if (k.pit) {
            // the ridge stands clear of the liquid three times in four (two pools), otherwise it
            // breaks the surface only in a few islets or stays a shoal; in a dry crater it is a
            // low divide
            k.septumTop = Double.isNaN(liquidRel)
                ? k.floorRel + depth * (0.2 + 0.2 * septumRoll)
                : liquidRel + (septumRoll < 0.25 ? -3.0 + 16.0 * septumRoll : 3.0 + 9.0 * (septumRoll - 0.25));
        }

        // a younger cone on the crater floor: an island in a lake or a lava pool
        double islandRoll = u.next();
        double islandAngle = TAU * u.next();
        double islandDist = u.next();
        double islandTop = u.next();
        k.island = islandRoll < (lake ? 0.6 : 0.4);
        if (k.island) {
            // on the floor, opposite the second basin if there is one, so the two do not collide
            double a = k.pit ? k.pitAngle + Math.PI + (islandAngle / TAU - 0.5) * 2.0 : islandAngle;
            double d = Math.max(0.15, 1.0 - k.wallShare) * rMin * 0.8 * islandDist;
            k.islandX = Math.cos(a) * d;
            k.islandZ = Math.sin(a) * d;
            double rise = Double.isNaN(liquidRel)
                ? depth * (0.15 + 0.25 * islandTop)
                : liquidRel + 3.0 + 12.0 * islandTop - k.floorRel;
            // flanks of a cinder cone, about as steep as scree; one that would need to be steeper
            // than that in a small crater stays a shoal under the lake
            double radius = clamp(rise / 0.8, 0.1 * r0, 0.35 * r0);
            rise = Math.min(rise, radius);
            k.islandRadius = Math.max(8.0, radius);
            // the summit pit of a larger cone leaves its rim at about nine tenths of the peak
            k.islandTop = k.floorRel + rise / (k.islandRadius > ISLAND_PIT_RADIUS ? ISLAND_PIT_RIM : 1.0);
        }
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
            return cone(v, r) * gullies(v, dx, dz, r);
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
        double theta = Math.atan2(s, c);
        double edge = outline(k, c, s, theta);
        out[1] = rw;
        out[2] = edge;
        double lip = lip(k, c, s, theta);
        if (rw >= edge) {
            // the lip falls back to the cone, flat at the crest so the rim reads as a rounded ridge
            out[0] = -1.0;
            double t = (rw - edge) / k.lipWidth;
            double fall = t >= 1.0 ? 0.0 : 1.0 - t * t * (3.0 - 2.0 * t);
            return cone(v, rw) + lip * fall;
        }
        double crest = cone(v, edge) + lip;
        double floor = k.floorRel + k.floorAmp * (0.75 * noise.noise2(wx / (0.36 * v.craterRadius) + k.noiseX - 77.0, wz / (0.36 * v.craterRadius) + k.noiseZ + 51.0)
            + 0.25 * noise.noise2(wx / (0.12 * v.craterRadius) + k.noiseZ, wz / (0.12 * v.craterRadius) - k.noiseX));
        // the inner wall over the outer share of the radius: steepest under the crest, its foot
        // flattening into the floor
        double u = rw / edge;
        double w = Math.min(1.0, (1.0 - u) / k.wallShare);
        double foot = 1.0 - w;
        double h = floor + (crest - floor) * Math.pow(foot, k.wallExp);
        // gullies and spurs down the steep upper wall, bending as they run down; none at the crest,
        // and they fade out over the scree at the foot rather than converge on the centre
        h += k.ribs * 9.48 * w * foot * foot * foot
            * noise.noise2(c * k.ribRing + k.noiseX + 150.0 + 0.9 * w, s * k.ribRing + k.noiseZ - 150.0 - 0.6 * w);
        // what stands on the floor never rises above the crest, so the rim stays continuous
        if (k.pit) {
            h = septum(k, v.craterRadius, wx, wz, h, crest);
        }
        if (k.island) {
            h = island(k, wx, wz, h, crest);
        }
        out[0] = 1.0 - u;
        return h;
    }

    /** The ridge across the crater that cuts the pit's basin off the main one. */
    private double septum(Crater k, double r0, double wx, double wz, double h, double crest) {
        // the ridge runs straight across the crater, from wall to wall, and wanders a little
        double c = Math.cos(k.pitAngle);
        double s = Math.sin(k.pitAngle);
        double along = -wx * s + wz * c;
        double across = wx * c + wz * s - k.septum - 0.06 * r0 * noise.noise2(along / (0.3 * r0) + k.noiseX, 9.5);
        double top = k.septumTop + 2.5 * noise.noise2(along / (0.15 * r0) + k.noiseZ, 3.5);
        // a broad, rounded ridge of scree rather than a wall
        double ridge = top - 0.55 * (Math.sqrt(across * across + 36.0) - 6.0);
        return Math.max(h, Math.min(ridge, crest));
    }

    /** A younger cone standing on the crater floor, with a blobby foot and a shallow summit pit. */
    private double island(Crater k, double wx, double wz, double h, double crest) {
        double ix = wx - k.islandX;
        double iz = wz - k.islandZ;
        double d = Math.sqrt(ix * ix + iz * iz) / k.islandRadius;
        if (d >= 1.3) {
            return h;
        }
        d *= 1.0 + 0.25 * noise.noise2(ix / (0.7 * k.islandRadius) + k.noiseX + 40.0, iz / (0.7 * k.islandRadius) + k.noiseZ + 40.0);
        if (d >= 1.0) {
            return h;
        }
        // a rounded top over concave flanks, and on larger cones a shallow summit pit
        double rise = k.islandTop - k.floorRel;
        double f = 1.0 - d;
        double cone = k.floorRel + rise * f * Math.sqrt(f) * (1.0 + 1.5 * d);
        if (k.islandRadius > ISLAND_PIT_RADIUS) {
            double p = Math.max(0.0, 1.0 - d * d / 0.05);
            cone -= 0.15 * rise * p * p;
        }
        return Math.max(h, Math.min(cone, crest));
    }

    /**
     * Radius of the rim's crest in the direction of offset {@code (dx, dz)}, for points beyond the
     * crater's reach, where the frame is no longer warped.
     */
    double rimRadius(TerrainModel.VolcanoSite v, double dx, double dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double c = r > 1.0E-9 ? dx / r : 1.0;
        double s = r > 1.0E-9 ? dz / r : 0.0;
        return outline(v.crater, c, s, Math.atan2(s, c));
    }

    /** Radius of the rim's crest at a bearing, the pit's lobe included. */
    private double outline(Crater k, double c, double s, double theta) {
        double r = outlineBase(k, c, s, theta);
        return k.pit ? r + k.pitBulge * window(theta - k.pitAngle, k.pitWidth) : r;
    }

    /** {@link #outline} without the pit's lobe. */
    private double outlineBase(Crater k, double c, double s, double theta) {
        double r = k.r0 * (1.0
            + k.lobes * noise.noise2(c * LOBE_RING + k.noiseX, s * LOBE_RING + k.noiseZ)
            + k.scallops * noise.noise2(c * SCALLOP_RING + k.noiseX + 71.3, s * SCALLOP_RING + k.noiseZ - 23.9))
            + k.rough * noise.noise2(c * k.roughRing + k.noiseZ + 211.0, s * k.roughRing - k.noiseX);
        for (int i = 0; i < k.bays; i++) {
            r += k.baySize[i] * window(theta - k.bayAngle[i], k.bayWidth[i]);
        }
        return r;
    }

    /** Height of the raised lip above the cone at a bearing: notches, breaches and shoulders. */
    private double lip(Crater k, double c, double s, double theta) {
        double lip = k.lip * (1.0 + 0.5 * noise.noise2(c * LIP_RING + k.noiseX - 140.0, s * LIP_RING + k.noiseZ + 77.0));
        for (int i = 0; i < k.crests; i++) {
            lip += k.crestHeight[i] * window(theta - k.crestAngle[i], k.crestWidth[i]);
        }
        return lip;
    }

    /** The cone's height at a distance from the centre, without its gullies. */
    static double cone(TerrainModel.VolcanoSite v, double r) {
        return r >= v.radius ? 0.0 : v.height * Math.pow(1.0 - r / v.radius, 1.65);
    }

    /** The radial gullies of the flanks, which start beyond the crater's reach. */
    private static double gullies(TerrainModel.VolcanoSite v, double dx, double dz, double r) {
        return 1.0 + 0.07 * Math.sin(Math.atan2(dz, dx) * v.ridges + v.phase) * smoothstep(v.craterReach, v.radius * 0.6, r);
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
     * One volcano's crater as {@link #shape} rolled it. Heights are relative to the volcano's base
     * height, angles in radians from east, distances in blocks.
     */
    static final class Crater {
        /** Mean radius of the outline, the volcano's crater radius. */
        double r0;
        /** Offsets into the noise, so craters differ. */
        double noiseX;
        double noiseZ;
        /** Relative amplitudes of the outline's broad lobes and finer scallops. */
        double lobes;
        double scallops;
        /** Roughness of the outline in blocks, and the radius of the circle it is read on. */
        double rough;
        double roughRing;
        /** Strength and scale of the domain warp, and where it starts to fade out. */
        double warp;
        double warpScale;
        double warpStart;
        /** Mean height of the lip above the cone, and the width over which it falls back outside. */
        double lip;
        double lipWidth;
        /** Floor height, and the height of its hummocks. */
        double floorRel;
        double floorAmp;
        /** Share of each bearing's radius the inner wall takes, and the power of its profile. */
        double wallShare;
        double wallExp;
        /** Depth of the gullies on the inner wall, and the radius of the circle their noise is read on. */
        double ribs;
        double ribRing;
        /** Lowest point of the rim's crest. */
        double rimLow;
        int bays;
        final double[] bayAngle = new double[MAX_BAYS];
        final double[] bayWidth = new double[MAX_BAYS];
        final double[] baySize = new double[MAX_BAYS];
        /** Notches (negative heights) and shoulders (positive) of the lip. */
        int crests;
        final double[] crestAngle = new double[MAX_CRESTS];
        final double[] crestWidth = new double[MAX_CRESTS];
        final double[] crestHeight = new double[MAX_CRESTS];
        /** The second basin: its bearing, the pit's lobe of the outline, and the dividing ridge. */
        boolean pit;
        double pitAngle;
        double pitBulge;
        double pitWidth;
        double septum;
        double septumTop;
        /** The island cone: its centre, radius and peak. */
        boolean island;
        double islandX;
        double islandZ;
        double islandRadius;
        double islandTop;
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
