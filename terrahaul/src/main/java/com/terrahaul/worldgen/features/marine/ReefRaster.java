package com.terrahaul.worldgen.features.marine;

import static com.terrahaul.worldgen.features.marine.MarineVolume.CALCITE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.DEAD;
import static com.terrahaul.worldgen.features.marine.MarineVolume.DEAD_FAN;
import static com.terrahaul.worldgen.features.marine.MarineVolume.DEAD_PLANT;
import static com.terrahaul.worldgen.features.marine.MarineVolume.DIR_X;
import static com.terrahaul.worldgen.features.marine.MarineVolume.DIR_Z;
import static com.terrahaul.worldgen.features.marine.MarineVolume.FAN;
import static com.terrahaul.worldgen.features.marine.MarineVolume.FRAME;
import static com.terrahaul.worldgen.features.marine.MarineVolume.LIVE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.NAT_SOLID;
import static com.terrahaul.worldgen.features.marine.MarineVolume.NAT_WATER;
import static com.terrahaul.worldgen.features.marine.MarineVolume.PICKLE;
import static com.terrahaul.worldgen.features.marine.MarineVolume.PLANT;
import static com.terrahaul.worldgen.features.marine.MarineVolume.SAND;
import static com.terrahaul.worldgen.features.marine.MarineVolume.SEAGRASS;
import static com.terrahaul.worldgen.features.marine.MarineVolume.TALL_LOWER;
import static com.terrahaul.worldgen.features.marine.MarineVolume.TALL_UPPER;
import static com.terrahaul.worldgen.features.marine.MarineVolume.WALL_FAN;
import static com.terrahaul.worldgen.features.marine.MarineVolume.isDead;
import static com.terrahaul.worldgen.features.marine.MarineVolume.isLive;
import static com.terrahaul.worldgen.features.marine.MarineVolume.isReef;
import static com.terrahaul.worldgen.features.marine.MarineVolume.isSolid;
import static com.terrahaul.worldgen.features.marine.MarineVolume.isWaterLike;
import static com.terrahaul.worldgen.noise.TMath.lerp;
import static com.terrahaul.worldgen.noise.TMath.smoothstep;

import com.terrahaul.worldgen.noise.SeedMixer;
import java.util.HashMap;
import java.util.Map;

/**
 * Rasterises the coral reefs of a {@link MarineVolume}: the reef framework, the coral objects
 * standing on the sand, the colours of the colonies and the life on and around them. Pure Java.
 * <p>
 * The layout follows the zones of a real fringing reef, seen from the beach outwards:
 * <ol>
 *   <li>a sandy lagoon with seagrass, sea pickles and scattered coral heads, some grown up to the
 *       low-water level into microatolls (flat grey tops with a living rim);</li>
 *   <li>the reef flat, two blocks under the surface, a mosaic of colonies with low domes, grooves
 *       between colonies, sand pockets, pools and winding sand channels;</li>
 *   <li>the reef front: a steep, rough wall with wall fans, crevices full of glowing sea pickles,
 *       and spurs of coral running seaward between sand grooves;</li>
 *   <li>the fore reef: coral heads (bommies, some mushroom-shaped), table corals, staghorn
 *       thickets, pillar corals and, deeper down, gardens of sea fans;</li>
 * </ol>
 * and, out on wide sandy shelves, oval patch reefs ringed by coral heads.
 * <p>
 * Every colony is a single species, and every stretch of reef is dominated by two or three
 * species (see {@link MarinePlanner#species}), so the reef reads as coherent patches of colour.
 * <p>
 * Determinism: each object is planned from its grid cell's hash and the terrain model only, and
 * objects are rasterised in a fixed order where only the first one to claim a water cell wins, so
 * every chunk computes the same blocks for the columns it shares with its neighbours. All shapes
 * are anchored by construction: heightfields stand on the floor and branches grow as connected
 * chains from it, so no coral ever floats.
 */
public final class ReefRaster {
    private static final double[] BOMMIE_BIAS = {1.0, 2.0, 0.7, 0.5, 1.5};
    private static final double[] TABLE_BIAS = {2.0, 0.3, 0.5, 0.5, 2.0};
    private static final double[] THICKET_BIAS = {1.5, 0.2, 0.5, 1.5, 2.0};
    private static final double[] PILLAR_BIAS = {1.0, 0.2, 2.0, 2.0, 0.5};
    private static final double[] DOME_BIAS = {0.6, 2.0, 0.6, 0.4, 1.6};
    private static final double[] FAN_BIAS = {1.0, 0.05, 2.0, 1.5, 0.05};

    // zones of the fringing reef
    static final int NONE = 0;
    static final int LAGOON = 1;
    static final int FLAT = 2;
    static final int FRONT = 3;
    static final int FORE = 4;
    static final int DEEP = 5;

    // object grids: cell size and reach beyond the site
    private static final int SPUR_CELL = 8;
    private static final int SPUR_REACH = 26;
    private static final int BOMMIE_CELL = 11;
    private static final int BOMMIE_REACH = 11;
    private static final int TABLE_CELL = 13;
    private static final int TABLE_REACH = 8;
    private static final int THICKET_CELL = 15;
    private static final int THICKET_REACH = 10;
    private static final int PILLAR_CELL = 23;
    private static final int PILLAR_REACH = 5;
    private static final int DOME_CELL = 7;
    private static final int DOME_REACH = 6;
    private static final int FANS_CELL = 9;
    private static final int FANS_REACH = 5;
    private static final int PATCH_REACH = 44;

    /** Margin around the core that a reef volume needs (halos and wall fans look this far). */
    public static final int MARGIN = 4;

    private final MarineVolume v;
    private final MarinePlanner p;
    private final int sl;
    private final int surf;
    private final int crest;
    private final int coralTop;
    private final Map<Long, Integer> speciesCache = new HashMap<>();
    /** Grottos near the chunk: the reef leaves the water in front of their mouths open. */
    private final java.util.List<GrottoPlan.Grotto> grottos;

    private ReefRaster(MarineVolume v) {
        this.v = v;
        this.p = v.planner();
        this.sl = p.seaLevel();
        this.surf = p.surf;
        this.crest = p.crest;
        this.coralTop = p.coralTop;
        this.grottos = GrottoPlan.of(p).nearCorridors(v);
    }

    private boolean blocksGrotto(double x, double z, double margin) {
        for (GrottoPlan.Grotto g : grottos) {
            if (GrottoPlan.inCorridor(g, x, z, margin)) {
                return true;
            }
        }
        return false;
    }

    /** Plans the reefs of the volume's region and decorates its core. */
    public static void build(MarineVolume v) {
        ReefRaster r = new ReefRaster(v);
        r.platform();
        r.patchReefs();
        r.spurs();
        r.objects();
        r.surface();
        r.decorate();
    }

    static int zone(MarineColumn c, int seaLevel) {
        if (!c.sea || c.reefObjects <= 0.0) {
            return NONE;
        }
        double d = seaLevel - c.height;
        if (d < 1.5) {
            return NONE;
        }
        if (d < c.back) {
            return LAGOON;
        }
        if (d <= c.front) {
            return FLAT;
        }
        if (d < c.front + 3.0) {
            return FRONT;
        }
        if (d <= 20.0) {
            return FORE;
        }
        return d <= 34.0 ? DEEP : NONE;
    }

    /** Top of the solid ground a colony grows on: the reef platform, or the sea floor. */
    private int ground(MarineColumn c) {
        return Math.max(c.floor, c.platform);
    }

    private static double unit(long h, int channel) {
        return SeedMixer.cellUnit(h, 0, 0, channel);
    }

    // =========================================================================================
    // Framework: platform, patch reefs, spurs
    // =========================================================================================

    private void platform() {
        for (int x = v.minX; x < v.minX + v.sizeX; x++) {
            for (int z = v.minZ; z < v.minZ + v.sizeZ; z++) {
                MarineColumn c = v.column(x, z);
                if (c.hasPlatform() && blocksGrotto(x + 0.5, z + 0.5, 0.0)) {
                    continue;
                }
                for (int y = c.floor + 1; y <= c.platform; y++) {
                    v.set(x, y, z, FRAME);
                }
            }
        }
    }

    private void patchReefs() {
        int cell = MarinePlanner.PATCH_CELL;
        for (int gx = Math.floorDiv(v.minX - PATCH_REACH, cell); gx <= Math.floorDiv(v.minX + v.sizeX + PATCH_REACH, cell); gx++) {
            for (int gz = Math.floorDiv(v.minZ - PATCH_REACH, cell); gz <= Math.floorDiv(v.minZ + v.sizeZ + PATCH_REACH, cell); gz++) {
                MarinePlanner.PatchReef pr = p.patchReef(gx, gz);
                if (pr != null && overlaps(pr.x, pr.z, PATCH_REACH)) {
                    patchReef(pr);
                }
            }
        }
    }

    private boolean overlaps(double x, double z, double reach) {
        return x + reach >= v.minX && x - reach < v.minX + v.sizeX && z + reach >= v.minZ && z - reach < v.minZ + v.sizeZ;
    }

    /** An oval coral island with a flat top at the crest, sloping flanks and a ring of coral heads. */
    private void patchReef(MarinePlanner.PatchReef pr) {
        int reach = (int) Math.ceil(pr.radius * 1.22 + 12.0);
        for (int x = Math.max(v.minX, pr.x - reach); x <= Math.min(v.minX + v.sizeX - 1, pr.x + reach); x++) {
            for (int z = Math.max(v.minZ, pr.z - reach); z <= Math.min(v.minZ + v.sizeZ - 1, pr.z + reach); z++) {
                MarineColumn c = v.column(x, z);
                if (!c.sea || blocksGrotto(x + 0.5, z + 0.5, 0.0)) {
                    continue;
                }
                double dx = x + 0.5 - pr.x;
                double dz = z + 0.5 - pr.z;
                double r = Math.sqrt(dx * dx + dz * dz);
                double edge = pr.edge(Math.atan2(dz, dx));
                int top;
                if (r <= edge) {
                    if (pr.pool && r < 0.3 * pr.radius) {
                        continue;
                    }
                    top = p.flatLevel(x, z);
                } else {
                    top = (int) Math.floor(crest - 1.5 * (r - edge));
                }
                for (int y = c.floor + 1; y <= top; y++) {
                    v.put(x, y, z, FRAME);
                }
            }
        }
        // satellite coral heads around the reef
        int n = 4 + (int) (unit(pr.seed, 1) * 7.0);
        for (int i = 0; i < n; i++) {
            long h = SeedMixer.hash2(pr.seed, i, 77);
            double a = (i + 0.7 * unit(h, 1)) * Math.PI * 2.0 / n;
            double dist = pr.edge(a) + 3.0 + 4.0 * unit(h, 2);
            int sx = (int) Math.floor(pr.x + Math.cos(a) * dist);
            int sz = (int) Math.floor(pr.z + Math.sin(a) * dist);
            MarineColumn c = v.column(sx, sz);
            if (c.sea && c.depth >= 5 && !blocksGrotto(sx + 0.5, sz + 0.5, BOMMIE_REACH)) {
                foreBommie(sx, sz, c, h);
            }
        }
    }

    /**
     * Spurs: fingers of coral running seaward from the reef front, 3-6 blocks wide, separated by
     * the untouched sand of the grooves. They follow the slope down and sink from the crest to a
     * couple of blocks above the shelf at their rounded tips.
     */
    private void spurs() {
        long salt = p.salt(10);
        for (int gx = Math.floorDiv(v.minX - SPUR_REACH, SPUR_CELL); gx <= Math.floorDiv(v.minX + v.sizeX + SPUR_REACH, SPUR_CELL); gx++) {
            for (int gz = Math.floorDiv(v.minZ - SPUR_REACH, SPUR_CELL); gz <= Math.floorDiv(v.minZ + v.sizeZ + SPUR_REACH, SPUR_CELL); gz++) {
                long h = SeedMixer.hash2(salt, gx, gz);
                if (SeedMixer.unit(h) > 0.75) {
                    continue;
                }
                int sx = (int) Math.floor((gx + 0.15 + 0.7 * unit(h, 1)) * SPUR_CELL);
                int sz = (int) Math.floor((gz + 0.15 + 0.7 * unit(h, 2)) * SPUR_CELL);
                if (!overlaps(sx, sz, SPUR_REACH)) {
                    continue;
                }
                MarineColumn c = v.column(sx, sz);
                double d = sl - c.height;
                if (c.reef < 0.45 || d < c.front - 1.5 || d > c.front + 1.0 || c.slope < 0.02) {
                    continue;
                }
                spur(sx, sz, c, h);
            }
        }
    }

    private void spur(int sx, int sz, MarineColumn c, long h) {
        double length = 7.0 + 13.0 * unit(h, 3);
        double width = 1.6 + 1.4 * unit(h, 4);
        double limit = Math.min(c.front + 12.0, 22.0);
        double[] px = new double[16];
        double[] pz = new double[16];
        int n = 0;
        double x = sx + 0.5;
        double z = sz + 0.5;
        double dx = -c.gradX / c.slope;
        double dz = -c.gradZ / c.slope;
        px[n] = x;
        pz[n++] = z;
        while (n < px.length && (n - 1) * 2.0 < length) {
            MarineColumn here = v.column((int) Math.floor(x), (int) Math.floor(z));
            if (here.slope > 0.02) {
                // follow the slope, but only gradually, so spurs stay straight-ish fingers
                dx = 0.75 * dx - 0.25 * here.gradX / here.slope;
                dz = 0.75 * dz - 0.25 * here.gradZ / here.slope;
                double len = Math.sqrt(dx * dx + dz * dz);
                dx /= len;
                dz /= len;
            }
            x += 2.0 * dx;
            z += 2.0 * dz;
            MarineColumn next = v.column((int) Math.floor(x), (int) Math.floor(z));
            if (!next.sea || sl - next.height > limit) {
                break;
            }
            px[n] = x;
            pz[n++] = z;
        }
        double total = (n - 1) * 2.0;
        if (total < 5.0) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, px[i]);
            maxX = Math.max(maxX, px[i]);
            minZ = Math.min(minZ, pz[i]);
            maxZ = Math.max(maxZ, pz[i]);
        }
        int x0 = Math.max(v.minX, (int) Math.floor(minX - width - 1));
        int x1 = Math.min(v.minX + v.sizeX - 1, (int) Math.ceil(maxX + width + 1));
        int z0 = Math.max(v.minZ, (int) Math.floor(minZ - width - 1));
        int z1 = Math.min(v.minZ + v.sizeZ - 1, (int) Math.ceil(maxZ + width + 1));
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                double qx = bx + 0.5;
                double qz = bz + 0.5;
                double best = Double.MAX_VALUE;
                double along = 0.0;
                for (int i = 0; i + 1 < n; i++) {
                    double ex = px[i + 1] - px[i];
                    double ez = pz[i + 1] - pz[i];
                    double t = Math.max(0.0, Math.min(1.0, ((qx - px[i]) * ex + (qz - pz[i]) * ez) / (ex * ex + ez * ez)));
                    double fx = px[i] + ex * t - qx;
                    double fz = pz[i] + ez * t - qz;
                    double dd = fx * fx + fz * fz;
                    if (dd < best) {
                        best = dd;
                        along = (i + t) * 2.0;
                    }
                }
                double dist = Math.sqrt(best);
                double w = width * (1.0 - 0.2 * along / total);
                if (dist > w) {
                    continue;
                }
                MarineColumn col = v.column(bx, bz);
                if (!col.sea || blocksGrotto(bx + 0.5, bz + 0.5, 0.0)) {
                    continue;
                }
                double top = lerp(along / total, sl - 3.5, col.floor + 2.5) - 1.5 * (dist / w) * (dist / w);
                int itop = Math.min(crest, Math.max(col.floor + 1, (int) Math.floor(top)));
                for (int y = col.floor + 1; y <= itop; y++) {
                    v.put(bx, y, bz, FRAME);
                }
            }
        }
    }

    // =========================================================================================
    // Coral objects on the sand
    // =========================================================================================

    /** Enumerates the objects of every grid in a fixed order (so overlaps resolve the same everywhere). */
    private void objects() {
        grid(BOMMIE_CELL, BOMMIE_REACH, 20, (sx, sz, c, z, h) -> {
            double accept = switch (z) {
                case FORE -> 0.5;
                case LAGOON -> 0.18;
                case DEEP -> 0.15;
                default -> 0.0;
            };
            if (!c.hasPlatform() && SeedMixer.unit(h) < accept * strength(sx, sz, c)) {
                if (z == LAGOON) {
                    lagoonBommie(sx, sz, c, h);
                } else if (z == FORE) {
                    foreBommie(sx, sz, c, h);
                } else {
                    deepBommie(sx, sz, c, h);
                }
            }
        });
        grid(TABLE_CELL, TABLE_REACH, 21, (sx, sz, c, z, h) -> {
            double d = sl - c.height;
            if (!c.hasPlatform() && d >= Math.max(6.0, c.front + 1.0) && d <= 18.0 && SeedMixer.unit(h) < 0.14 * strength(sx, sz, c)) {
                table(sx, sz, c, h);
            }
        });
        grid(THICKET_CELL, THICKET_REACH, 22, (sx, sz, c, z, h) -> {
            double d = sl - c.height;
            if (z != NONE && d >= 3.0 && d <= 14.0 && SeedMixer.unit(h) < 0.22 * strength(sx, sz, c)) {
                thicket(sx, sz, c, h);
            }
        });
        grid(PILLAR_CELL, PILLAR_REACH, 23, (sx, sz, c, z, h) -> {
            double d = sl - c.height;
            if (!c.hasPlatform() && d >= 5.0 && d <= 16.0 && SeedMixer.unit(h) < 0.08 * strength(sx, sz, c)) {
                pillars(sx, sz, c, h);
            }
        });
        grid(DOME_CELL, DOME_REACH, 24, (sx, sz, c, z, h) -> {
            double accept = switch (z) {
                case LAGOON -> 0.3;
                case FORE -> 0.16;
                case DEEP -> 0.08;
                default -> 0.0;
            };
            if (!c.hasPlatform() && SeedMixer.unit(h) < accept * strength(sx, sz, c)) {
                dome(sx, sz, c, h);
            }
        });
        grid(FANS_CELL, FANS_REACH, 25, (sx, sz, c, z, h) -> {
            double d = sl - c.height;
            if (!c.hasPlatform() && d >= 13.0 && d <= 34.0 && SeedMixer.unit(h) < 0.32 * strength(sx, sz, c)) {
                fanGarden(sx, sz, c, h);
            }
        });
    }

    @FunctionalInterface
    private interface Site {
        void place(int sx, int sz, MarineColumn c, int zone, long hash);
    }

    private void grid(int cell, int reach, long kind, Site site) {
        long salt = p.salt(kind);
        for (int gx = Math.floorDiv(v.minX - reach, cell); gx <= Math.floorDiv(v.minX + v.sizeX + reach, cell); gx++) {
            for (int gz = Math.floorDiv(v.minZ - reach, cell); gz <= Math.floorDiv(v.minZ + v.sizeZ + reach, cell); gz++) {
                long h = SeedMixer.hash2(salt, gx, gz);
                int sx = (int) Math.floor((gx + 0.15 + 0.7 * unit(h, 1)) * cell);
                int sz = (int) Math.floor((gz + 0.15 + 0.7 * unit(h, 2)) * cell);
                if (!overlaps(sx, sz, reach) || blocksGrotto(sx + 0.5, sz + 0.5, reach)) {
                    continue;
                }
                MarineColumn c = v.column(sx, sz);
                int z = zone(c, sl);
                if (z != NONE) {
                    site.place(sx, sz, c, z, h);
                }
            }
        }
    }

    /**
     * Acceptance factor of an object: the local reef strength, raised in coral gardens and lowered
     * on the open sand between them, so objects cluster instead of spreading like polka dots.
     */
    private double strength(int x, int z, MarineColumn c) {
        double cluster = smoothstep(-0.4, 0.5, p.noise(MarinePlanner.CLUSTER, x / 40.0, z / 40.0));
        return c.reefObjects * (0.4 + 1.2 * cluster);
    }

    private int tint(int primary, int secondary) {
        return 1 + primary + 5 * secondary;
    }

    private void foreBommie(int sx, int sz, MarineColumn c, long h) {
        double a = 2.5 + 4.5 * unit(h, 3) * unit(h, 3);
        double height = Math.min(3.0 + 1.3 * a + 3.0 * unit(h, 4), sl - c.height - 2.5);
        bommie(sx, sz, c, h, a, height, false);
    }

    private void lagoonBommie(int sx, int sz, MarineColumn c, long h) {
        double a = 1.5 + 1.8 * unit(h, 3);
        bommie(sx, sz, c, h, a, crest - c.floor, true);
    }

    private void deepBommie(int sx, int sz, MarineColumn c, long h) {
        double a = 1.5 + 2.0 * unit(h, 3);
        bommie(sx, sz, c, h, a, 2.0 + 2.5 * unit(h, 4), false);
    }

    /**
     * A coral head: a rounded, lumpy mound on an oval, slightly lobed outline. Large heads are
     * undercut into a mushroom shape by bioerosion near their base; heads in the lagoon grow up to
     * the low-water level into microatolls whose flat tops die off. The framework is coloured by
     * the surface pass with the head's two species.
     */
    private void bommie(int sx, int sz, MarineColumn c, long h, double a, double height, boolean microatoll) {
        if (height < 1.5) {
            return;
        }
        double b = a * (0.7 + 0.3 * unit(h, 5));
        double theta = unit(h, 6) * Math.PI * 2.0;
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        int water = surf - c.floor;
        int primary = p.species(sx, sz, water, unit(h, 7), BOMMIE_BIAS);
        int secondary = p.species(sx, sz, water, unit(h, 8), BOMMIE_BIAS);
        int tint = tint(primary, secondary);
        boolean undercut = a >= 3.5 && height >= 5.0;
        int reach = (int) Math.ceil(a * 1.3) + 1;
        int span = 2 * reach + 1;
        int fc = c.floor;
        // pass 1: the top of every column, and whether it lies on the undercut rim or the dead
        // centre of a microatoll
        int[] tops = new int[span * span];
        byte[] kind = new byte[span * span];
        final byte outside = 0;
        final byte body = 1;
        final byte rimKind = 2;
        final byte deadTop = 3;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int i = (dx + reach) * span + dz + reach;
                double lx = (dx * cos + dz * sin) / a;
                double lz = (-dx * sin + dz * cos) / b;
                double phi = Math.atan2(lz, lx);
                double rho = Math.sqrt(lx * lx + lz * lz) / (1.0 + 0.18 * Math.sin(3.0 * phi + 5.0 * theta) + 0.1 * Math.sin(5.0 * phi + 11.0 * theta));
                if (rho > 1.0 || !v.column(sx + dx, sz + dz).sea) {
                    kind[i] = outside;
                    continue;
                }
                double profile = Math.pow(1.0 - Math.pow(rho, 2.2), 0.55);
                tops[i] = Math.min(crest, (int) Math.floor(fc + 0.5 + height * profile + 0.7 * p.noise(MarinePlanner.LUMPS, (sx + dx) / 3.0, (sz + dz) / 3.0)));
                kind[i] = undercut && rho > 0.82 ? rimKind : microatoll && rho < 0.6 ? deadTop : body;
            }
        }
        // pass 2: build; an undercut rim column only hangs from a grounded neighbour reaching above
        // its base, so the mushroom never comes loose
        int raised = fc + 1 + (int) Math.round(0.35 * height);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int i = (dx + reach) * span + dz + reach;
                int x = sx + dx;
                int z = sz + dz;
                if (kind[i] == outside || !v.inRegion(x, z)) {
                    continue;
                }
                int top = tops[i];
                int bottom = v.column(x, z).floor + 1;
                if (kind[i] == rimKind) {
                    for (int k = 0; k < 4; k++) {
                        int nx = dx + DIR_X[k];
                        int nz = dz + DIR_Z[k];
                        if (Math.abs(nx) <= reach && Math.abs(nz) <= reach) {
                            int j = (nx + reach) * span + nz + reach;
                            if ((kind[j] == body || kind[j] == deadTop) && tops[j] >= raised) {
                                bottom = Math.max(bottom, raised);
                                break;
                            }
                        }
                    }
                }
                for (int y = bottom; y <= top; y++) {
                    if (v.put(x, y, z, FRAME)) {
                        v.setTint(x, y, z, tint);
                    }
                }
                if (kind[i] == deadTop && top >= crest && v.get(x, top, z) == FRAME) {
                    v.set(x, top, z, (byte) (DEAD + primary));
                }
            }
        }
    }

    /** Table coral: a thin, wavy-edged plate on a short stem, sometimes cup-shaped, shading pickles. */
    private void table(int sx, int sz, MarineColumn c, long h) {
        int sp = p.species(sx, sz, surf - c.floor, unit(h, 3), TABLE_BIAS);
        byte live = (byte) (LIVE + sp);
        int stem = 2 + (int) (2.0 * unit(h, 4));
        double radius = 2.8 + 2.7 * unit(h, 5);
        double theta = unit(h, 6) * Math.PI * 2.0;
        boolean cup = unit(h, 7) < 0.3;
        int plate = Math.min(crest, c.floor + stem + 1);
        if (plate < c.floor + 2) {
            return;
        }
        int stems = radius > 4.5 ? 2 : 1;
        for (int dx = 0; dx < stems; dx++) {
            for (int dz = 0; dz < stems; dz++) {
                MarineColumn col = v.column(sx + dx, sz + dz);
                for (int y = col.floor + 1; y < plate; y++) {
                    v.put(sx + dx, y, sz + dz, live);
                }
            }
        }
        int reach = (int) Math.ceil(radius * 1.15) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                double edge = radius * (1.0 + 0.15 * Math.sin(4.0 * Math.atan2(dz, dx) + theta));
                double rho = r / edge;
                if (rho > 1.0) {
                    continue;
                }
                v.put(sx + dx, plate, sz + dz, live);
                if (rho < 0.45) {
                    v.put(sx + dx, plate - 1, sz + dz, live);
                }
                if (cup && rho > 0.8 && plate + 1 <= coralTop) {
                    v.put(sx + dx, plate + 1, sz + dz, live);
                }
            }
        }
    }

    /**
     * Staghorn thicket: a few dozen branches leaning out from the floor, some forking, each a
     * connected chain of coral blocks, all of one colony.
     */
    private void thicket(int sx, int sz, MarineColumn c, long h) {
        int sp = p.species(sx, sz, surf - ground(c), unit(h, 3), THICKET_BIAS);
        byte live = (byte) (LIVE + sp);
        double radius = 3.5 + 3.5 * unit(h, 4);
        int n = 5 + (int) Math.round(1.2 * radius);
        for (int i = 0; i < n; i++) {
            long bh = SeedMixer.hash2(h, i, 5);
            double a = unit(bh, 1) * Math.PI * 2.0;
            double rr = radius * Math.sqrt(unit(bh, 2));
            int bx = (int) Math.floor(sx + 0.5 + Math.cos(a) * rr);
            int bz = (int) Math.floor(sz + 0.5 + Math.sin(a) * rr);
            MarineColumn col = v.column(bx, bz);
            int base = ground(col) + 1;
            if (!col.sea || base > coralTop - 1) {
                continue;
            }
            double azimuth = unit(bh, 3) * Math.PI * 2.0;
            double incline = Math.toRadians(55.0 + 25.0 * unit(bh, 4));
            double length = 3.0 + 3.0 * unit(bh, 5);
            // a fork starts exactly on a block of the parent's chain, so the limbs stay connected
            double[] fork = branch(bx + 0.5, base + 0.2, bz + 0.5, azimuth, incline, length, live, 0.6);
            if (unit(bh, 6) < 0.45 && fork != null) {
                branch(fork[0], fork[1], fork[2], azimuth + 0.61, incline, length * 0.5, live, 0.0);
                branch(fork[0], fork[1], fork[2], azimuth - 0.61, incline, length * 0.5, live, 0.0);
            }
        }
    }

    /**
     * Lays a straight branch as a 6-connected chain of blocks. Returns the point at about
     * {@code forkAt} of its length (a sample of the chain, so a fork starting there is connected),
     * or null if the branch stopped before it.
     */
    private double[] branch(double x0, double y0, double z0, double azimuth, double incline, double length, byte code, double forkAt) {
        double dx = Math.cos(azimuth) * Math.cos(incline);
        double dy = Math.sin(incline);
        double dz = Math.sin(azimuth) * Math.cos(incline);
        int px = (int) Math.floor(x0);
        int py = (int) Math.floor(y0);
        int pz = (int) Math.floor(z0);
        if (py > coralTop) {
            return null;
        }
        double forkS = Math.max(0.5, Math.floor(length * forkAt * 2.0) * 0.5);
        double[] fork = null;
        v.put(px, py, pz, code);
        for (double s = 0.5; s <= length; s += 0.5) {
            int nx = (int) Math.floor(x0 + s * dx);
            int ny = (int) Math.floor(y0 + s * dy);
            int nz = (int) Math.floor(z0 + s * dz);
            if (ny > coralTop) {
                break;
            }
            if (s == forkS) {
                fork = new double[]{x0 + s * dx, y0 + s * dy, z0 + s * dz};
            }
            while (px != nx || py != ny || pz != nz) {
                if (py != ny) {
                    py += Integer.signum(ny - py);
                } else if (px != nx) {
                    px += Integer.signum(nx - px);
                } else {
                    pz += Integer.signum(nz - pz);
                }
                v.put(px, py, pz, code);
            }
        }
        return fork;
    }

    /** Pillar coral: a cluster of upright columns. */
    private void pillars(int sx, int sz, MarineColumn c, long h) {
        int sp = p.species(sx, sz, surf - c.floor, unit(h, 3), PILLAR_BIAS);
        byte live = (byte) (LIVE + sp);
        int n = 3 + (int) (5.0 * unit(h, 4));
        for (int i = 0; i < n; i++) {
            long ph = SeedMixer.hash2(h, i, 9);
            int px = sx + (int) Math.round((unit(ph, 1) - 0.5) * 5.0);
            int pz = sz + (int) Math.round((unit(ph, 2) - 0.5) * 5.0);
            int height = 3 + (int) (5.0 * unit(ph, 3));
            boolean wide = unit(ph, 4) < 0.15;
            for (int w = 0; w < (wide ? 2 : 1); w++) {
                MarineColumn col = v.column(px + w, pz);
                int base = ground(col) + 1;
                for (int y = base; y < base + height && y <= coralTop; y++) {
                    v.put(px + w, y, pz, live);
                }
            }
        }
    }

    /**
     * Massive colony: a low dome of one or two lobes (brain and star corals). Where it reaches
     * the low-water level its flat top dies off into a microatoll with a living rim.
     */
    private void dome(int sx, int sz, MarineColumn c, long h) {
        int base = ground(c) + 1;
        int sp = p.species(sx, sz, surf - base + 1, unit(h, 3), DOME_BIAS);
        double r = 1.4 + 2.2 * unit(h, 4);
        double height = r * (0.75 + 0.35 * unit(h, 5));
        int lobes = 1 + (unit(h, 6) < 0.45 ? 1 : 0);
        for (int l = 0; l < lobes; l++) {
            double ox = l == 0 ? 0.0 : (unit(h, 7) - 0.5) * r * 1.2;
            double oz = l == 0 ? 0.0 : (unit(h, 8) - 0.5) * r * 1.2;
            double lr = l == 0 ? r : r * (0.55 + 0.3 * unit(h, 9));
            double lh = l == 0 ? height : height * 0.75;
            int reach = (int) Math.ceil(lr + Math.abs(ox) + Math.abs(oz)) + 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double rr = Math.sqrt((dx - ox) * (dx - ox) + (dz - oz) * (dz - oz)) / lr;
                    if (rr > 1.0) {
                        continue;
                    }
                    int x = sx + dx;
                    int z = sz + dz;
                    MarineColumn col = v.column(x, z);
                    int local = ground(col) + 1;
                    if (!col.sea || local < base - 3) {
                        continue;
                    }
                    int top = base - 1 + (int) Math.round(lh * Math.sqrt(1.0 - rr * rr));
                    boolean capped = top >= coralTop;
                    top = Math.min(top, coralTop);
                    for (int y = local; y <= top; y++) {
                        byte code = (byte) (capped && y == top && rr < 0.7 ? DEAD + sp : LIVE + sp);
                        v.put(x, y, z, code);
                    }
                }
            }
        }
    }

    /** A garden of sea fans on the deeper fore reef, some on knobs of their own coral. */
    private void fanGarden(int sx, int sz, MarineColumn c, long h) {
        int water = surf - c.floor;
        int sp = p.species(sx, sz, water, unit(h, 3), FAN_BIAS);
        int other = p.species(sx, sz, water, unit(h, 4), FAN_BIAS);
        int n = 3 + (int) (6.0 * unit(h, 5));
        for (int i = 0; i < n; i++) {
            long fh = SeedMixer.hash2(h, i, 11);
            int fx = sx + (int) Math.round((unit(fh, 1) - 0.5) * 6.0);
            int fz = sz + (int) Math.round((unit(fh, 2) - 0.5) * 6.0);
            MarineColumn col = v.column(fx, fz);
            if (!col.sea) {
                continue;
            }
            int y = ground(col) + 1;
            int fsp = unit(fh, 3) < 0.7 ? sp : other;
            if (unit(fh, 4) < 0.4 && v.put(fx, y, fz, (byte) (LIVE + fsp))) {
                y++;
            }
            if (isSolid(v.get(fx, y - 1, fz))) {
                v.put(fx, y, fz, (byte) (FAN + fsp));
            }
        }
    }

    // =========================================================================================
    // Colours
    // =========================================================================================

    private int cachedSpecies(long id, int seedX, int seedZ, int water, double roll, double[] bias) {
        long key = id * 31L + water * 7L + (bias == null ? 0 : 1);
        Integer sp = speciesCache.get(key);
        if (sp == null) {
            sp = p.species(seedX, seedZ, water, roll, bias);
            speciesCache.put(key, sp);
        }
        return sp;
    }

    private int pavement(MarinePlanner.Colony col) {
        long key = col.id ^ 0x5A5A5A5AL;
        Integer sp = speciesCache.get(key);
        if (sp == null) {
            sp = p.dominant(col.seedX, col.seedZ);
            speciesCache.put(key, sp);
        }
        return sp;
    }

    /**
     * Turns exposed framework into coral: the tops by the 2D colony mosaic, walls and the sides of
     * coral heads by a 3D one. Live colonies get their species' coral block, pavement and bleached
     * colonies dead coral, and shallow pavement on the flat holds pockets of sand. Framework hidden
     * inside the reef becomes calcite, the limestone core of the reef.
     */
    private void surface() {
        int x0 = Math.max(v.minX, v.coreMinX - 2);
        int x1 = Math.min(v.minX + v.sizeX - 1, v.coreMinX + v.coreSizeX + 1);
        int z0 = Math.max(v.minZ, v.coreMinZ - 2);
        int z1 = Math.min(v.minZ + v.sizeZ - 1, v.coreMinZ + v.coreSizeZ + 1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int hi = v.storedMax(x, z);
                for (int y = v.storedMin(x, z); y < hi; y++) {
                    byte code = v.get(x, y, z);
                    if (code == FRAME) {
                        v.set(x, y, z, colour(x, y, z));
                    } else if (isLive(code) && !exposed(x, y, z)) {
                        // the inside of a massive colony is skeleton, not living tissue
                        v.set(x, y, z, CALCITE);
                    }
                }
            }
        }
    }

    private boolean exposed(int x, int y, int z) {
        return isWaterLike(v.get(x, y + 1, z)) || isWaterLike(v.get(x, y - 1, z)) || isWaterLike(v.get(x + 1, y, z)) || isWaterLike(v.get(x - 1, y, z))
            || isWaterLike(v.get(x, y, z + 1)) || isWaterLike(v.get(x, y, z - 1));
    }

    private byte colour(int x, int y, int z) {
        boolean top = isWaterLike(v.get(x, y + 1, z));
        if (!exposed(x, y, z)) {
            return CALCITE;
        }
        int water = surf - y;
        int tint = v.tint(x, y, z);
        MarinePlanner.Colony col = top ? p.colony(x, z) : p.wallColony(x, y, z);
        double liveShare = tint != 0 ? 0.8 : top ? 0.0 : 0.8;
        boolean live = top && tint == 0 ? col.live() : col.roll() < liveShare;
        if (live) {
            int sp;
            if (tint != 0) {
                sp = SeedMixer.cellUnit(col.id, 0, 0, 14) < 0.7 ? (tint - 1) % 5 : (tint - 1) / 5;
            } else {
                sp = cachedSpecies(col.id, col.seedX, col.seedZ, Math.max(1, water), SeedMixer.cellUnit(col.id, 0, 0, 15),
                    col.massive() ? DOME_BIAS : null);
            }
            // microatolls: massive colonies capped by the low-water level have a dead, flat top
            if (top && y >= coralTop && col.massive() && col.d1 < 0.4 * col.d2) {
                return (byte) (DEAD + sp);
            }
            return (byte) (LIVE + sp);
        }
        if (top && water <= 5 && !col.bleached() && p.noise(MarinePlanner.LUMPS, x / 5.0 + 40.0, z / 5.0) < -0.3 && isSolid(v.get(x, y - 1, z))) {
            return SAND;
        }
        // pavement on the reef tops is partly pale rubble of broken, bleached coral (whole
        // colonies of it, never single blocks), elsewhere grey dead coral
        if (top && tint == 0 && !col.bleached() && SeedMixer.cellUnit(col.id, 0, 0, 16) < (water <= 4 ? 0.45 : 0.2)) {
            return CALCITE;
        }
        return (byte) (DEAD + (tint != 0 ? (tint - 1) % 5 : pavement(col)));
    }

    // =========================================================================================
    // Decoration (core columns only)
    // =========================================================================================

    /**
     * Coral plants and fans on live colonies (more fans in deeper water), wall fans on reef walls,
     * glowing sea pickles in crevices and under table corals, the odd dead plant on pavement, and
     * seagrass meadows on the lagoon sand, kept clear of the reef by a bare grazing halo.
     */
    private void decorate() {
        long salt = p.salt(30);
        long fanSalt = p.salt(31);
        for (int x = v.coreMinX; x < v.coreMinX + v.coreSizeX; x++) {
            for (int z = v.coreMinZ; z < v.coreMinZ + v.coreSizeZ; z++) {
                MarineColumn c = v.column(x, z);
                if (!c.sea) {
                    continue;
                }
                int hi = Math.min(surf, Math.max(v.storedMax(x, z), c.floor + 3));
                for (int y = Math.min(v.storedMin(x, z), c.floor + 1); y <= hi; y++) {
                    if (v.get(x, y, z) != NAT_WATER) {
                        continue;
                    }
                    byte below = v.get(x, y - 1, z);
                    if (!isSolid(below)) {
                        continue;
                    }
                    long h = SeedMixer.hash3(salt, x, y, z);
                    double r = SeedMixer.unit(h);
                    int water = surf - y + 1;
                    if (isLive(below)) {
                        int sp = below - LIVE;
                        if (r < 0.3) {
                            double fanShare = water > 6 ? 0.45 : 0.18;
                            v.set(x, y, z, (byte) ((r < 0.3 * fanShare ? FAN : PLANT) + sp));
                            continue;
                        }
                    }
                    double r2 = SeedMixer.cellUnit(h, 0, 0, 1);
                    if (isReef(below) && below != SAND) {
                        int sides = 0;
                        for (int k = 0; k < 4; k++) {
                            if (isSolid(v.get(x + DIR_X[k], y, z + DIR_Z[k]))) {
                                sides++;
                            }
                        }
                        boolean roofed = isSolid(v.get(x, y + 1, z));
                        if ((sides >= 2 || roofed) && r2 < (roofed ? 0.3 : 0.2)) {
                            v.set(x, y, z, (byte) (PICKLE + (int) (SeedMixer.cellUnit(h, 0, 0, 2) * 4.0)));
                            continue;
                        }
                    }
                    if (isDead(below) && r2 < 0.025) {
                        v.set(x, y, z, (byte) ((SeedMixer.cellUnit(h, 0, 0, 3) < 0.5 ? DEAD_PLANT : DEAD_FAN) + (below - DEAD)));
                        continue;
                    }
                    if (below == NAT_SOLID && y == c.floor + 1) {
                        floorLife(x, y, z, c, h);
                    }
                }
                wallFans(x, z, c, fanSalt);
            }
        }
    }

    private void wallFans(int x, int z, MarineColumn c, long salt) {
        int hi = Math.min(surf, v.storedMax(x, z) + 1);
        for (int y = c.floor + 1; y <= hi; y++) {
            if (v.get(x, y, z) != NAT_WATER) {
                continue;
            }
            for (int k = 0; k < 4; k++) {
                int bx = x - DIR_X[k];
                int bz = z - DIR_Z[k];
                byte support = v.get(bx, y, bz);
                if (isLive(support) && SeedMixer.unit(SeedMixer.hash3(salt + k, bx, y, bz)) < 0.08) {
                    v.set(x, y, z, (byte) (WALL_FAN + (support - LIVE) * 4 + k));
                    break;
                }
            }
        }
    }

    /** Life on the natural sand around the reef: seagrass beyond the grazing halo, and pickles. */
    private void floorLife(int x, int y, int z, MarineColumn c, long h) {
        double r = SeedMixer.cellUnit(h, 0, 0, 4);
        if (r < 0.004) {
            v.set(x, y, z, (byte) (PICKLE + (int) (SeedMixer.cellUnit(h, 0, 0, 2) * 3.0)));
            return;
        }
        if (c.reefObjects <= 0.02 || c.depth > 20) {
            return;
        }
        double m = c.meadow;
        double chance = 0.95 * smoothstep(0.1, 0.6, m) + 0.03;
        if (r >= chance) {
            return;
        }
        // grazing halo: fish feeding from the reef keep a ring of bare sand around it
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (isReef(v.get(x + dx, y + dy, z + dz))) {
                        return;
                    }
                }
            }
        }
        if (r < 0.25 * m && y + 1 <= surf && v.get(x, y + 1, z) == NAT_WATER) {
            v.set(x, y, z, TALL_LOWER);
            v.set(x, y + 1, z, TALL_UPPER);
        } else {
            v.set(x, y, z, SEAGRASS);
        }
    }
}
