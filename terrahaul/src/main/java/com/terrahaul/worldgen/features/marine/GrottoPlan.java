package com.terrahaul.worldgen.features.marine;

import static com.terrahaul.worldgen.features.marine.MarineVolume.AIR;
import static com.terrahaul.worldgen.features.marine.MarineVolume.GLOW_LICHEN;
import static com.terrahaul.worldgen.features.marine.MarineVolume.GRAVEL;
import static com.terrahaul.worldgen.features.marine.MarineVolume.LIVE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.MOSSY_COBBLE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.PICKLE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.SAND;
import static com.terrahaul.worldgen.features.marine.MarineVolume.WATER;

import com.terrahaul.worldgen.noise.SeedMixer;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Underwater grottos: sea caves at the foot of steep underwater faces (sea cliffs, fjord walls,
 * the flanks of islands, seamounts and ridges). An arched mouth opens in the face, a short tunnel
 * leads into a domed chamber with a sandy floor, lit by glowing sea pickles and glow lichen; at
 * warm mouths coral grows on the rim, at cool ones algae. About a third of the grottos near the
 * surface are hidden grottos: the tunnel climbs into a chamber whose dome rises above sea level,
 * holding a pocket of air over a small sand beach, like the Blue Grotto of Capri. Where the face
 * belongs to a narrow ridge (a spur of a seamount or an island), the tunnel instead runs straight
 * through it and opens on the far side: a natural arch to swim through. Pure Java.
 * <p>
 * Nothing may float, so the planner only accepts a grotto when the terrain model puts thick rock
 * all round it: a roof that thickens from the mouth inwards to 7 blocks or more (more under cliffs,
 * whose 3D detail can eat into the rock; over a swim-through it thickens from both portals to 5 or
 * more), side walls at least 3 blocks thick and solid rock behind
 * and around the chamber. Shapes too big for the rock are shrunk until they fit or rejected. The
 * features add two runtime guards on top: they never carve next to an existing air pocket (a land
 * cave), and never under a roof that the real world made thinner than planned.
 */
public final class GrottoPlan {
    /** Grotto sites lie on a grid of this many blocks (at most one per cell). */
    public static final int CELL = 160;
    /** Farthest a carved block lies from the foot of the face. */
    public static final int REACH = 56;
    private static final int CANDIDATES = 16;
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final MarinePlanner p;
    private final TerrainModel model;
    private final int sl;
    private final ConcurrentHashMap<Long, Grotto> cache = new ConcurrentHashMap<>();
    private static final Grotto NONE = new Grotto();

    GrottoPlan(MarinePlanner p) {
        this.p = p;
        this.model = p.model();
        this.sl = p.seaLevel();
    }

    /** The grotto planner of a world (owned by its marine planner). */
    public static GrottoPlan of(MarinePlanner planner) {
        return planner.grottos();
    }

    /** A planned grotto. Coordinates along the axis are measured from the foot of the face. */
    public static final class Grotto {
        public boolean active;
        /** Foot of the face, where the porch starts. */
        public double mx;
        public double mz;
        /** Unit vector pointing into the rock. */
        public double ux;
        public double uz;
        /** Tunnel floor (lowest carved block) at the mouth. */
        public int floor;
        /** Height of the opening. */
        public int ho;
        /** Half width of the opening and tunnel. */
        public double hw;
        /** Distance from the foot to the mouth plane (where the face is high enough for the arch). */
        public int um;
        /** Tunnel length beyond the mouth plane. */
        public int lt;
        /** Chamber half lengths along the axis, across it and vertically. */
        public double ra;
        public double rv;
        public double ry;
        /** Floor of the chamber, and the centre height of its ellipsoid. */
        public int chamberFloor;
        public double chamberY;
        /** Hidden grotto: the chamber dome rises above sea level and holds air. */
        public boolean dome;
        /**
         * Swim-through: the tunnel runs straight through a narrow ridge and opens on its far
         * side, at {@link #farMouth} along the axis; there is no chamber.
         */
        public boolean swim;
        public int farMouth;
        /** Smallest roof margin the planner found (blocks of rock beyond the minimum). */
        public double margin;
        public long seed;
        /** "cliff", "fjord" or "slope". */
        public String kind;
        /** Coral species of the mouth rim in warm water, or -1 for an algae rim. */
        public int rimSpecies;
        public double temperature;

        public double u(double x, double z) {
            return (x - mx) * ux + (z - mz) * uz;
        }

        public double v(double x, double z) {
            return -(x - mx) * uz + (z - mz) * ux;
        }

        public double chamberU() {
            return um + lt + ra * 0.8;
        }

        /** Tunnel floor at axis distance u: flat, or climbing towards a dome chamber. */
        public double tunnelFloor(double u) {
            if (!dome || u <= um + 1) {
                return floor;
            }
            return Math.min(chamberFloor, floor + (u - um - 1) * 0.6);
        }

        /** Block position in front of the mouth, in open water (where the finder sends players). */
        public int[] front() {
            return new int[]{(int) Math.floor(mx - ux * 4.0), floor + 2, (int) Math.floor(mz - uz * 4.0)};
        }
    }

    // =========================================================================================
    // Sites
    // =========================================================================================

    /** The grotto of grid cell {@code (cx, cz)}, or null. Cached. */
    public Grotto grotto(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Grotto g = cache.get(key);
        if (g == null) {
            g = plan(cx, cz);
            if (cache.size() > 16384) {
                cache.clear();
            }
            cache.put(key, g);
        }
        return g.active ? g : null;
    }

    private double h(double x, double z) {
        return model.interpolatedHeight((int) Math.floor(x), (int) Math.floor(z));
    }

    private Grotto plan(int cx, int cz) {
        long seed = p.salt(60);
        if (SeedMixer.cellUnit(seed, cx, cz, 0) > 0.6) {
            return NONE;
        }
        Grotto best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int a = 0; a < CANDIDATES; a++) {
            int px = (int) Math.floor((cx + 0.1 + 0.8 * SeedMixer.cellUnit(seed, cx, cz, 1 + 2 * a)) * CELL);
            int pz = (int) Math.floor((cz + 0.1 + 0.8 * SeedMixer.cellUnit(seed, cx, cz, 2 + 2 * a)) * CELL);
            Grotto g = candidate(px, pz);
            if (g != null) {
                double score = g.ra * g.rv + 3.0 * Math.min(g.margin, 6.0) + (g.kind.equals("slope") ? 0.0 : 20.0) + (g.dome ? 15.0 : 0.0) + (g.swim ? 60.0 : 0.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = g;
                }
            }
        }
        return best == null ? NONE : best;
    }

    private Grotto candidate(int px, int pz) {
        ColumnSample s = model.sample(px & ~3, pz & ~3);
        double depth = sl - s.height;
        if (!s.isSea(sl) || s.trench > 0.1 || depth < 6 || depth > 50) {
            return null;
        }
        double coastCliff = s.coastCliff;
        double fjord = s.fjord;
        double temperature = s.temperature;
        double gx = (h(px + 6, pz) - h(px - 6, pz)) / 12.0;
        double gz = (h(px, pz + 6) - h(px, pz - 6)) / 12.0;
        double g = Math.hypot(gx, gz);
        boolean steep = coastCliff > 0.3 || fjord > 0.25 || g > 0.6;
        if (!steep || g < 0.05) {
            return null;
        }
        double ux = gx / g;
        double uz = gz / g;
        // walk up the slope to the foot of the face
        double mx = Double.NaN;
        double mz = Double.NaN;
        for (int k = 0; k < 20; k++) {
            if (h(px + ux * (k + 3), pz + uz * (k + 3)) - h(px + ux * k, pz + uz * k) >= 4.5) {
                mx = px + ux * k;
                mz = pz + uz * k;
                break;
            }
        }
        if (Double.isNaN(mx)) {
            return null;
        }
        // point the tunnel straight into the face
        double fx = mx + ux * 4.0;
        double fz = mz + uz * 4.0;
        double gx2 = (h(fx + 4, fz) - h(fx - 4, fz)) / 8.0;
        double gz2 = (h(fx, fz + 4) - h(fx, fz - 4)) / 8.0;
        double g2 = Math.hypot(gx2, gz2);
        if (g2 > 0.3) {
            ux = gx2 / g2;
            uz = gz2 / g2;
        }
        Grotto t = new Grotto();
        t.mx = mx;
        t.mz = mz;
        t.ux = ux;
        t.uz = uz;
        t.floor = (int) Math.floor(h(mx, mz)) + 1;
        t.seed = SeedMixer.hash2(p.salt(61), (int) Math.floor(mx), (int) Math.floor(mz));
        t.kind = coastCliff > 0.3 ? "cliff" : fjord > 0.25 ? "fjord" : "slope";
        t.temperature = temperature;
        t.ho = 4 + (int) (3.0 * unit(t.seed, 1));
        t.hw = 2.0 + 2.0 * unit(t.seed, 2);
        t.lt = 5 + (int) (8.0 * unit(t.seed, 3));
        t.ra = 5.0 + 4.0 * unit(t.seed, 4);
        t.rv = 5.0 + 5.0 * unit(t.seed, 5);
        t.ry = 3.5 + 2.5 * unit(t.seed, 6);
        // keep the mouth below the band where waves carve sea caves into cliffs
        int band = coastCliff > 0.15 ? sl - 9 : sl - 3;
        if (t.floor + t.ho > band) {
            t.ho = band - t.floor;
            if (t.ho < 3) {
                return null;
            }
        }
        // open water in front of the mouth
        for (int k = 1; k <= 3; k++) {
            if (h(mx - ux * k, mz - uz * k) > t.floor + 0.5) {
                return null;
            }
        }
        t.um = -1;
        for (int u = 0; u < 10; u++) {
            if (h(mx + ux * u, mz + uz * u) >= t.floor + t.ho + 1.5) {
                t.um = u;
                break;
            }
        }
        if (t.um < 0) {
            return null;
        }
        double cliffMax = 0.0;
        for (int u = t.um; u < t.um + 36; u += 4) {
            ColumnSample q = model.sample((int) Math.floor(mx + ux * u) & ~3, (int) Math.floor(mz + uz * u) & ~3);
            cliffMax = Math.max(cliffMax, q.cliff);
        }
        t.rimSpecies = temperature > 0.42 ? p.species((int) mx, (int) mz, sl - t.floor, unit(t.seed, 7), null) : -1;
        // swim-through: behind a narrow ridge the ground drops to the tunnel floor again, so the
        // tunnel can run straight through the ridge like an arch and open on its far side
        Grotto swim = swimThrough(t, cliffMax);
        if (swim != null) {
            return swim;
        }
        // hidden grotto: the tunnel climbs into a chamber that holds air under its dome
        if (unit(t.seed, 8) < 0.35 && t.floor >= sl - 16) {
            Grotto d = copy(t);
            d.dome = true;
            d.chamberFloor = sl - 4;
            d.lt = Math.max(d.lt, (int) Math.ceil((d.chamberFloor - d.floor) / 0.6) + 2);
            double domeTop = sl + 3 + (int) (3.0 * unit(t.seed, 9));
            d.ry = (domeTop - d.chamberFloor + 1) / 2.0;
            d.chamberY = d.chamberFloor - 1 + d.ry;
            if (fit(d, cliffMax)) {
                return d;
            }
        }
        t.chamberFloor = t.floor - 1;
        t.chamberY = t.chamberFloor + t.ry;
        return fit(t, cliffMax) ? t : null;
    }

    private Grotto swimThrough(Grotto t, double cliffMax) {
        int exit = -1;
        for (int u = t.um + 6; u <= t.um + 26; u++) {
            if (h(t.mx + t.ux * u, t.mz + t.uz * u) < t.floor + 0.5) {
                exit = u;
                break;
            }
        }
        if (exit < 0) {
            return null;
        }
        // open sea beyond the far portal
        double ex = t.mx + t.ux * (exit + 3);
        double ez = t.mz + t.uz * (exit + 3);
        ColumnSample q = model.sample(((int) Math.floor(ex) + 2) & ~3, ((int) Math.floor(ez) + 2) & ~3);
        if (!q.isSea(sl) || h(ex, ez) > t.floor + 0.5) {
            return null;
        }
        Grotto w = copy(t);
        w.swim = true;
        w.ra = 0.0;
        w.rv = 0.0;
        w.ry = 0.0;
        w.chamberFloor = t.floor;
        w.chamberY = t.floor;
        for (int tries = 0; tries < 6; tries++) {
            // the portals: where the rock over the arch first and last reaches the lintel thickness
            double lintel = w.floor + w.ho + 2.5;
            w.um = -1;
            w.farMouth = -1;
            for (int u = 0; u < exit; u++) {
                if (h(t.mx + t.ux * u, t.mz + t.uz * u) >= lintel) {
                    if (w.um < 0) {
                        w.um = u;
                    }
                    w.farMouth = u;
                }
            }
            if (w.um < 0 || w.um > 12) {
                return null;
            }
            w.lt = exit - w.um;
            double margin = checkSwim(w, cliffMax);
            if (margin >= 0.0) {
                w.margin = margin;
                w.active = true;
                return w;
            }
            if (margin == Double.NEGATIVE_INFINITY || w.ho <= 3 && w.hw <= 1.5) {
                return null;
            }
            w.ho = Math.max(3, w.ho - 1);
            w.hw = Math.max(1.5, w.hw - 0.5);
        }
        return null;
    }

    /**
     * Rock margin over a swim-through: the roof thickens from both portals towards the middle (to
     * at least 5 blocks plus an allowance for cliff detail), and the side walls stay 3 blocks thick.
     */
    private double checkSwim(Grotto t, double cliffMax) {
        if (t.farMouth - t.um < 4) {
            return -1.0;
        }
        double vx = -t.uz;
        double vz = t.ux;
        double roof = 5.0 + 8.0 * cliffMax;
        double min = Double.MAX_VALUE;
        double ceil = t.floor + t.ho;
        for (int u = t.um; u <= t.farMouth; u++) {
            int k = Math.min(u - t.um, t.farMouth - u);
            for (double v = -Math.ceil(t.hw + 1.0); v <= Math.ceil(t.hw + 1.0); v += 1.0) {
                double x = t.mx + t.ux * u + vx * v;
                double z = t.mz + t.uz * u + vz * v;
                ColumnSample s = model.sample(((int) Math.floor(x) + 2) & ~3, ((int) Math.floor(z) + 2) & ~3);
                if (s.lake > 0.0 || s.river > 0.0 || s.waterLevel != ColumnSample.NO_WATER) {
                    return Double.NEGATIVE_INFINITY;
                }
                double margin = h(x, z) - ceil - Math.min(roof, 1.5 + 1.2 * k) - 1.0;
                if (Math.abs(v) >= Math.ceil(t.hw) - 0.5) {
                    double sx = x + vx * Math.signum(v) * 3.0;
                    double sz = z + vz * Math.signum(v) * 3.0;
                    margin = Math.min(margin, h(sx, sz) - (ceil + 1.5));
                }
                min = Math.min(min, margin);
                if (margin < 0.0) {
                    return margin;
                }
            }
        }
        return min;
    }

    private static Grotto copy(Grotto t) {
        Grotto d = new Grotto();
        d.mx = t.mx;
        d.mz = t.mz;
        d.ux = t.ux;
        d.uz = t.uz;
        d.floor = t.floor;
        d.seed = t.seed;
        d.kind = t.kind;
        d.temperature = t.temperature;
        d.ho = t.ho;
        d.hw = t.hw;
        d.lt = t.lt;
        d.ra = t.ra;
        d.rv = t.rv;
        d.ry = t.ry;
        d.um = t.um;
        d.rimSpecies = t.rimSpecies;
        return d;
    }

    /** Shrinks a grotto until the rock around it is thick enough; false if it never fits. */
    private boolean fit(Grotto t, double cliffMax) {
        for (int tries = 0; tries < 6; tries++) {
            double margin = check(t, cliffMax);
            if (margin >= 0.0) {
                t.margin = margin;
                t.active = true;
                return true;
            }
            if (margin == Double.NEGATIVE_INFINITY) {
                return false;
            }
            t.ra *= 0.8;
            t.rv *= 0.8;
            if (!t.dome) {
                t.ry = Math.max(2.5, t.ry * 0.85);
                t.chamberY = t.chamberFloor + t.ry;
            }
            if (tries >= 2) {
                t.hw = Math.max(1.5, t.hw - 0.5);
                if (!t.dome) {
                    t.ho = Math.max(3, t.ho - 1);
                }
            }
            if (t.ra < 3.5 || t.rv < 3.0) {
                return false;
            }
        }
        return false;
    }

    /**
     * Smallest margin of rock around the planned grotto, by the terrain model: negative if the roof
     * or a wall is too thin, negative infinity if the grotto would reach a lake, river or elevated
     * water. The roof must thicken from 1.5 blocks at the mouth plane to {@code 7 + 12 * cliff}
     * blocks inside; chamber walls need 3 blocks of rock on every landward side, tunnel walls 1.5
     * above their ceiling 3 blocks out.
     */
    private double check(Grotto t, double cliffMax) {
        double vx = -t.uz;
        double vz = t.ux;
        double roof = 7.0 + 12.0 * cliffMax;
        double min = Double.MAX_VALUE;
        double cu = t.chamberU();
        int end = (int) Math.ceil(cu + t.ra) + 1;
        for (int u = t.um; u <= end; u++) {
            int k = u - t.um;
            double halfW;
            double ceil;
            double chamber = (u - cu) / t.ra;
            double c = Math.sqrt(Math.max(0.0, 1.0 - chamber * chamber));
            boolean inChamber = Math.abs(chamber) <= 1.0 && rv(t) * c > 0.5;
            double tunnelCeil = t.tunnelFloor(u) + t.ho;
            if (u < t.um + t.lt + 0.5) {
                halfW = t.hw;
                ceil = tunnelCeil;
                if (inChamber) {
                    halfW = Math.max(halfW, t.rv * c);
                    ceil = Math.max(ceil, t.chamberY + t.ry * c);
                }
            } else {
                halfW = Math.max(inChamber ? t.rv * c : 0.0, 0.0);
                ceil = t.chamberY + t.ry * c;
                if (halfW < 0.5) {
                    continue;
                }
            }
            for (double v = -Math.ceil(halfW + 1.0); v <= Math.ceil(halfW + 1.0); v += 1.0) {
                double x = t.mx + t.ux * u + vx * v;
                double z = t.mz + t.uz * u + vz * v;
                ColumnSample s = model.sample(((int) Math.floor(x) + 2) & ~3, ((int) Math.floor(z) + 2) & ~3);
                if (s.lake > 0.0 || s.river > 0.0 || s.waterLevel != ColumnSample.NO_WATER) {
                    return Double.NEGATIVE_INFINITY;
                }
                double need = Math.min(roof, 1.5 + 1.2 * k);
                double margin = h(x, z) - ceil - need - 1.0;
                if (u >= t.um + t.lt - 1) {
                    // chamber: rock all round on the landward side, at least 3 above its top
                    double around = Double.MAX_VALUE;
                    for (int du = -4; du <= 4; du += 2) {
                        if (u + du < t.um + 1) {
                            continue;
                        }
                        for (int dv = -4; dv <= 4; dv += 2) {
                            around = Math.min(around, h(t.mx + t.ux * (u + du) + vx * (v + dv), t.mz + t.uz * (u + du) + vz * (v + dv)));
                        }
                    }
                    margin = Math.min(margin, around - (ceil + 3.0));
                } else if (Math.abs(v) >= Math.ceil(halfW) - 0.5) {
                    // tunnel side walls at least 3 blocks thick
                    double sx = x + vx * Math.signum(v) * 3.0;
                    double sz = z + vz * Math.signum(v) * 3.0;
                    margin = Math.min(margin, h(sx, sz) - (ceil + 1.5));
                }
                min = Math.min(min, margin);
                if (margin < 0.0) {
                    return margin;
                }
            }
        }
        return min;
    }

    private static double rv(Grotto t) {
        return t.rv;
    }

    private static double unit(long h, int channel) {
        return SeedMixer.cellUnit(h, 0, 0, channel);
    }

    // =========================================================================================
    // Shape
    // =========================================================================================

    /** 0 if the block stays rock, {@link MarineVolume#WATER} or {@link MarineVolume#AIR} if carved. */
    public byte carve(Grotto t, int x, int y, int z) {
        double u = t.u(x + 0.5, z + 0.5);
        double v = t.v(x + 0.5, z + 0.5);
        if (u < -1.0 || u > t.chamberU() + t.ra + 2.0 || Math.abs(v) > Math.max(t.hw, t.rv) + 3.0) {
            return 0;
        }
        double n = p.noise3(MarinePlanner.CAVE, x / 5.0, y / 4.0, z / 5.0);
        boolean carved = false;
        // porch and tunnel: an arch over a flat floor
        if (u >= -0.5 && u <= t.um + t.lt + 0.5) {
            double w = t.hw * (1.0 + 0.15 * n);
            if (Math.abs(v) <= w) {
                double floor = Math.floor(t.tunnelFloor(u));
                double arch = Math.pow(Math.max(0.0, 1.0 - (v / w) * (v / w)), 0.35);
                double ceil = floor + t.ho * arch + 0.8 * n;
                carved = y >= floor && y < ceil;
            }
        }
        // chamber: an ellipsoid over a flat floor, its outline roughened by noise
        if (!carved && !t.swim && y >= t.chamberFloor) {
            double cu = (u - t.chamberU()) / t.ra;
            double cv = v / t.rv;
            double cy = (y + 0.5 - t.chamberY) / t.ry;
            carved = cu * cu + cv * cv + cy * cy <= 1.0 + 0.25 * n;
        }
        if (!carved) {
            return 0;
        }
        if (t.dome && beach(t, u, v, y)) {
            return 0;
        }
        return y <= p.surf ? WATER : AIR;
    }

    /** The sand beach filling the back of a hidden grotto's chamber, rising to just above the water. */
    private boolean beach(Grotto t, double u, double v, int y) {
        double cu = (u - t.chamberU()) / t.ra;
        if (cu < 0.25) {
            return false;
        }
        double top = t.chamberFloor + (cu - 0.25) / 0.75 * (sl + 1.5 - t.chamberFloor) - 0.4 * Math.abs(v) / t.rv;
        return y <= top;
    }

    /** True in the open water in front of a grotto's mouth, which rocks and boulders keep clear. */
    public static boolean inCorridor(Grotto t, double x, double z, double margin) {
        double u = t.u(x, z);
        double v = t.v(x, z);
        boolean front = u >= -10.0 - margin && u <= t.um + 1.0 + margin;
        boolean back = t.swim && u >= t.farMouth - 1.0 - margin && u <= t.um + t.lt + 10.0 + margin;
        return (front || back) && Math.abs(v) <= t.hw + 4.0 + margin;
    }

    /** True if a grotto may reach the chunk at {@code (minX, minZ)}. */
    public static boolean reaches(Grotto t, int minX, int minZ) {
        return t.mx + REACH >= minX && t.mx - REACH <= minX + 15 && t.mz + REACH >= minZ && t.mz - REACH <= minZ + 15;
    }

    /**
     * Plans every grotto reaching the volume's core into it: carved water and air, sand and gravel
     * floors, the beach of hidden grottos, glowing pickles, glow lichen, and coral or algae on the
     * rim of the mouth.
     */
    public void build(MarineVolume vol) {
        for (Grotto t : near(vol)) {
            build(vol, t);
        }
    }

    /** The grottos that may reach the chunk at {@code (minX, minZ)} (including a margin of one block). */
    public java.util.List<Grotto> near(int minX, int minZ) {
        return near(minX, minZ, minX + 15, minZ + 15, 0);
    }

    /** The grottos that may reach the core of a volume (including a margin of one block). */
    public java.util.List<Grotto> near(MarineVolume v) {
        return near(v.coreMinX, v.coreMinZ, v.coreMinX + v.coreSizeX - 1, v.coreMinZ + v.coreSizeZ - 1, 0);
    }

    /**
     * The grottos whose mouth corridors may matter to objects planned for the core of a volume:
     * objects reaching up to 26 blocks, whose sites lie up to 26 blocks from the core, around
     * corridors reaching up to 56 blocks from the mouth. Every chunk that plans an object must see
     * the same grottos, or the object would be cut at a chunk border.
     */
    public java.util.List<Grotto> nearCorridors(MarineVolume v) {
        return near(v.coreMinX, v.coreMinZ, v.coreMinX + v.coreSizeX - 1, v.coreMinZ + v.coreSizeZ - 1, CORRIDOR_EXTRA);
    }

    /** Extra distance {@link #nearCorridors} looks beyond {@link #REACH}. */
    public static final int CORRIDOR_EXTRA = 64;

    private java.util.List<Grotto> near(int minX, int minZ, int maxX, int maxZ, int extra) {
        int r = REACH + 1 + extra;
        java.util.List<Grotto> list = new java.util.ArrayList<>(2);
        for (int cx = Math.floorDiv(minX - r, CELL); cx <= Math.floorDiv(maxX + r, CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - r, CELL); cz <= Math.floorDiv(maxZ + r, CELL); cz++) {
                Grotto t = grotto(cx, cz);
                if (t != null && t.mx + r >= minX && t.mx - r <= maxX && t.mz + r >= minZ && t.mz - r <= maxZ) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /** Margin a grotto volume needs: the emitter checks the neighbours of every carved block. */
    public static final int MARGIN = 1;

    /** True for codes that stand for a dug-out block (water, air, or something placed in it). */
    public static boolean dug(byte code) {
        return code == WATER || code == AIR || code >= PICKLE && code < MarineVolume.SEAGRASS || code >= GLOW_LICHEN && code < GLOW_LICHEN + 5;
    }

    private void build(MarineVolume vol, Grotto t) {
        int top = (int) Math.ceil(Math.max(t.chamberY + t.ry, t.floor + t.ho + 1.0 + t.lt * (t.dome ? 0.6 : 0.0))) + 2;
        int bottom = Math.min(t.floor, t.chamberFloor) - 1;
        long salt = p.salt(62);
        boolean cold = t.temperature < 0.2;
        for (int x = vol.minX; x < vol.minX + vol.sizeX; x++) {
            for (int z = vol.minZ; z < vol.minZ + vol.sizeZ; z++) {
                double u = t.u(x + 0.5, z + 0.5);
                double v = t.v(x + 0.5, z + 0.5);
                if (u < -2.0 || u > t.chamberU() + t.ra + 3.0 || Math.abs(v) > Math.max(t.hw, t.rv) + 4.0) {
                    continue;
                }
                int lowest = Integer.MAX_VALUE;
                for (int y = bottom; y <= top; y++) {
                    byte c = carve(t, x, y, z);
                    if (c != 0) {
                        lowest = Math.min(lowest, y);
                        if (vol.get(x, y, z) == MarineVolume.NAT_SOLID) {
                            vol.set(x, y, z, c);
                        }
                    } else if (t.dome && y <= sl + 1 && y >= t.chamberFloor && beach(t, u, v, y) && insideChamber(t, u, v, y)) {
                        if (vol.get(x, y, z) == MarineVolume.NAT_SOLID) {
                            vol.set(x, y, z, SAND);
                        }
                    }
                }
                if (lowest == Integer.MAX_VALUE) {
                    rim(vol, t, x, z, u, bottom, top, salt);
                    continue;
                }
                // floor of sand or gravel, a few glowing pickles
                long h = SeedMixer.hash2(salt, x, z);
                if (vol.get(x, lowest - 1, z) == MarineVolume.NAT_SOLID && carve(t, x, lowest - 1, z) == 0) {
                    boolean sandy = t.temperature > -0.2 && p.noise(MarinePlanner.GRAVEL, x / 6.0, z / 6.0) > -0.2;
                    vol.set(x, lowest - 1, z, sandy ? SAND : GRAVEL);
                    if (u > t.um && lowest <= p.surf && SeedMixer.unit(h) < (cold ? 0.02 : 0.05)) {
                        vol.set(x, lowest, z, (byte) (PICKLE + 1 + (int) (SeedMixer.cellUnit(h, 0, 0, 1) * 3.0)));
                    }
                }
                lichen(vol, t, x, z, u, bottom, top, salt);
                rim(vol, t, x, z, u, bottom, top, salt);
            }
        }
    }

    private boolean insideChamber(Grotto t, double u, double v, int y) {
        double cu = (u - t.chamberU()) / t.ra;
        double cv = v / t.rv;
        double cy = (y + 0.5 - t.chamberY) / t.ry;
        return cu * cu + cv * cv + cy * cy <= 1.0;
    }

    /** Glow lichen on the ceiling and walls of the dark interior. */
    private void lichen(MarineVolume vol, Grotto t, int x, int z, double u, int bottom, int top, long salt) {
        if (u < t.um + 3) {
            return;
        }
        for (int y = bottom; y <= top; y++) {
            byte here = vol.get(x, y, z);
            if (here != WATER && here != AIR) {
                continue;
            }
            for (int k = 0; k < 5; k++) {
                // k = 0: ceiling; 1-4: walls north, east, south, west
                int nx = x + (k == 0 ? 0 : MarineVolume.DIR_X[k - 1]);
                int ny = y + (k == 0 ? 1 : 0);
                int nz = z + (k == 0 ? 0 : MarineVolume.DIR_Z[k - 1]);
                if (carve(t, nx, ny, nz) != 0 || ny > vol.column(nx, nz).floor) {
                    continue;
                }
                double chance = k == 0 ? 0.2 : 0.1;
                if (SeedMixer.unit(SeedMixer.hash3(salt + k, x, y, z)) < chance) {
                    vol.set(x, y, z, (byte) (GLOW_LICHEN + k));
                    break;
                }
            }
        }
    }

    /** Coral (warm water) or algae (cool water) on the rock around the mouth. */
    private void rim(MarineVolume vol, Grotto t, int x, int z, double u, int bottom, int top, long salt) {
        if (Math.abs(u - t.um) > 2.5 && !(t.swim && Math.abs(u - t.farMouth) <= 2.5)) {
            return;
        }
        for (int y = bottom; y <= Math.min(top, p.surf); y++) {
            if (vol.get(x, y, z) != MarineVolume.NAT_SOLID || carve(t, x, y, z) != 0) {
                continue;
            }
            // open water next to the rock: the grotto, or sea where no reef will grow over it later
            boolean nextToCave = false;
            for (int[] s : SIDES) {
                int ny = y + s[1];
                MarineColumn n = vol.column(x + s[0], z + s[2]);
                if (ny <= p.surf && (carve(t, x + s[0], ny, z + s[2]) == WATER || ny > n.floor && n.reefObjects <= 0.02)) {
                    nextToCave = true;
                    break;
                }
            }
            if (!nextToCave) {
                continue;
            }
            double r = SeedMixer.unit(SeedMixer.hash3(salt ^ 0x51DL, x, y, z));
            if (t.rimSpecies >= 0 && p.noise3(MarinePlanner.ALGAE, x / 3.0, y / 3.0, z / 3.0) > -0.15 && r < 0.75) {
                vol.set(x, y, z, (byte) (LIVE + t.rimSpecies));
            } else if (t.rimSpecies < 0 && t.temperature > -0.6 && p.noise3(MarinePlanner.ALGAE, x / 3.0, y / 3.0, z / 3.0) > 0.1) {
                vol.set(x, y, z, MOSSY_COBBLE);
            }
        }
    }
}
