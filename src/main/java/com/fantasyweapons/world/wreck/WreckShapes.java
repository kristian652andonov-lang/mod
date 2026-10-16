package com.fantasyweapons.world.wreck;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shapers shared by the ultimate weapons' abilities. */
public final class WreckShapes {
    private WreckShapes() {
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    static final BlockState COAL = Blocks.COAL_BLOCK.defaultBlockState();

    static final BlockState NETHERRACK = Blocks.NETHERRACK.defaultBlockState();
    static final BlockState DEEPSLATE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

    /** How big the rock chunks of a seared wall are (blocks across). */
    private static final double WALL_CELL = 7;

    /**
     * A shaft erased from the sky through the bottom of the world, bedrock and all, so it opens on the void. Its edge is
     * not a clean circle: it wanders in and out, and the rim crumbles back where the ground tore. Its walls are seared
     * into big chunks of dark rock that jut out into the shaft, split by seams of glowing magma set back a block or two
     * behind them; no lava is left in them, and water at the edge is seared to obsidian so nothing pours in.
     */
    public static ChunkShaper shaft(Vec3 center, double radius) {
        double salt = Math.floorMod((long) Math.floor(center.x * 0.41 + center.z * 0.73), 991L);
        // how far the edge wanders in and out of the circle, and how deep the wall's relief goes
        double wander = Math.min(8, radius * 0.06), band = 4;
        return c -> {
            double[] cell = new double[3];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius + wander + band + 7) continue;
                    double ca = dx / Math.max(1e-6, d), sa = dz / Math.max(1e-6, d);
                    double lobes = 0.65 * noise(ca * 12 + salt, sa * 12 + salt) + 0.35 * noise(ca * 36 + salt + 200, sa * 36 + salt + 200);
                    double edge = radius + wander * (lobes * 2 - 1);
                    int top = c.top(x, z);
                    // the rim crumbles back over the last few blocks below the ground, more in some places than others
                    double crumble = 3 + 5 * noise(x * 0.8 + salt, z * 0.8 - salt);
                    double wx = center.x + ca * edge, wz = center.z + sa * edge;
                    for (int y = c.minY(); y <= top; y++) {
                        if (d < edge - 4) {
                            c.erase(x, y, z);
                            continue;
                        }
                        // the chunk of rock the wall is made of here, and whether this is a seam between two
                        cells(wx, y, wz, cell);
                        boolean seam = cell[1] - cell[0] < 0.85;
                        double jut = seam ? -1.6 : (1.0 + 1.8 * cell[2]) * (1 - 0.6 * Math.min(1, cell[0] / (WALL_CELL * 0.6)));
                        double surface = edge - jut + Math.max(0, crumble - (top - y)) * 1.1;
                        if (d < surface) {
                            c.erase(x, y, z);
                            continue;
                        }
                        if (d > surface + band) continue;
                        BlockState old = c.get(x, y, z);
                        if (old.is(Blocks.LAVA)) {
                            c.set(x, y, z, BLACKSTONE);
                            continue;
                        }
                        if (!old.getFluidState().isEmpty()) {
                            c.set(x, y, z, OBSIDIAN);
                            continue;
                        }
                        if (!WreckChunk.terrain(old)) {
                            if (!old.isAir()) c.set(x, y, z, AIR);
                            continue;
                        }
                        // the seam's face glows; behind it, and through every chunk, seared rock
                        if (seam && d < surface + 1.2) c.set(x, y, z, MAGMA);
                        else c.set(x, y, z, rock(cell[2]));
                    }
                }
            }
        };
    }

    /** The rock a seared chunk is made of, by its own random value: mostly blackstone, some basalt, deepslate and netherrack. */
    private static BlockState rock(double v) {
        return v < 0.5 ? BLACKSTONE : v < 0.7 ? BASALT : v < 0.85 ? DEEPSLATE : NETHERRACK;
    }

    /**
     * Cellular noise for a seared wall: the distances from a point to the nearest and second-nearest of the chunks'
     * centres (scattered one to a {@link #WALL_CELL}-sized cube), and the nearest chunk's own random value in [0, 1).
     */
    static void cells(double x, double y, double z, double[] out) {
        double gx = x / WALL_CELL, gy = y / WALL_CELL, gz = z / WALL_CELL;
        int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy), iz = (int) Math.floor(gz);
        double f1 = 1e9, f2 = 1e9, id = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    int cx = ix + i, cy = iy + j, cz = iz + k;
                    double px = cx + hash3(cx, cy, cz, 1), py = cy + hash3(cx, cy, cz, 2) * 0.8 + 0.1, pz = cz + hash3(cx, cy, cz, 3);
                    double ddx = (px - gx) * WALL_CELL, ddy = (py - gy) * WALL_CELL, ddz = (pz - gz) * WALL_CELL;
                    double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    if (dist < f1) {
                        f2 = f1;
                        f1 = dist;
                        id = hash3(cx, cy, cz, 4);
                    } else if (dist < f2) f2 = dist;
                }
            }
        }
        out[0] = f1;
        out[1] = f2;
        out[2] = id;
    }

    private static double hash3(int x, int y, int z, int salt) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ salt * 2654435761L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        h ^= h >>> 17;
        return ((h >>> 16) & 0xFFFF) / 65536.0;
    }

    /**
     * A sphere erased from the world, but nowhere a clean one: its edge wanders in and out, the rim crumbles where it
     * broke through the ground, and the bowl it leaves is lined with big chunks of obsidian, blackstone and deepslate
     * jutting out of it, split by seams of crying obsidian set back behind them. Water or lava at its edge is crushed
     * into obsidian so nothing pours in.
     */
    public static ChunkShaper sphere(Vec3 center, double radius) {
        double salt = Math.floorMod((long) Math.floor(center.x * 0.29 + center.z * 0.83), 983L);
        double band = 3, reach = radius + ragReach(radius) + band + 10;
        return c -> {
            double[] cell = new double[3];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d2 = dx * dx + dz * dz;
                    if (d2 > reach * reach) continue;
                    double half = Math.sqrt(reach * reach - d2);
                    int top = c.top(x, z);
                    int y0 = Math.max(c.minY(), (int) Math.floor(center.y - half)), y1 = Math.min(top, (int) Math.ceil(center.y + half));
                    double crumble = 3 + 6 * noise(x * 0.8 + salt, z * 0.8 - salt);
                    for (int y = y0; y <= y1; y++) {
                        double dy = y + 0.5 - center.y, r = Math.sqrt(d2 + dy * dy);
                        double ux = dx / Math.max(1e-6, r), uy = dy / Math.max(1e-6, r), uz = dz / Math.max(1e-6, r);
                        double ex = center.x + ux * radius, ey = center.y + uy * radius, ez = center.z + uz * radius;
                        double edge = radius + rag(ex, ey, ez, radius, salt);
                        if (r < edge - 4) {
                            c.clear(x, y, z);
                            continue;
                        }
                        if (r > edge + 4 + band + Math.max(0, crumble)) continue;
                        cells(center.x + ux * edge, center.y + uy * edge, center.z + uz * edge, cell);
                        boolean seam = cell[1] - cell[0] < 0.85;
                        double jut = seam ? -1.5 : (1.0 + 1.8 * cell[2]) * (1 - 0.6 * Math.min(1, cell[0] / (WALL_CELL * 0.6)));
                        double surface = edge - jut + Math.max(0, crumble - (top - y)) * 1.1;
                        if (r < surface) {
                            c.clear(x, y, z);
                            continue;
                        }
                        if (r > surface + band) continue;
                        BlockState old = c.get(x, y, z);
                        if (!old.getFluidState().isEmpty()) c.set(x, y, z, OBSIDIAN);
                        else if (!WreckChunk.terrain(old)) {
                            if (!old.isAir()) c.set(x, y, z, AIR);
                        } else if (seam && r < surface + 1.2) c.set(x, y, z, CRYING);
                        else c.set(x, y, z, cell[2] < 0.45 ? OBSIDIAN : cell[2] < 0.8 ? BLACKSTONE : DEEPSLATE);
                    }
                }
            }
        };
    }

    /**
     * How far the rim of a round crater strays from its circle at the point (x, y, z) on it (blocks, outward positive):
     * a few wide lobes and bays, smaller notches along those, and here and there a whole block of ground broken away
     * or left standing proud, so the rim never reads as a circle however big the crater is.
     */
    static double rag(double x, double y, double z, double radius, double salt) {
        // value noise keeps near its middle, so each layer is stretched to use its whole range
        double wide = stretch(0.8 * noise(x * 0.1 + salt, z * 0.1 - salt) + 0.2 * noise(y * 0.1 + salt, (x - z) * 0.07));
        double mid = stretch(0.75 * noise(x * 0.45 - salt, z * 0.45 + salt) + 0.25 * noise(y * 0.45 + 300, (x + z) * 0.32 + salt));
        double fine = noise3(x * 2.6, y * 2.6, z * 2.6, salt + 600) * 2 - 1;
        double off = wide * radius * 0.2 + mid * 9 + fine * 2.5;
        // whole chunks of ground (about twelve blocks across) bitten out, or left jutting into the hole
        int gx = (int) Math.floor(x / 12 + 0.37 * noise(z * 0.9, y * 0.9)), gy = (int) Math.floor(y / 12), gz = (int) Math.floor(z / 12 + 0.37 * noise(x * 0.9, y * 0.9 + 70));
        double h = hash3(gx, gy, gz, (int) salt);
        if (h < 0.2) off += 5 + 9 * (h / 0.2);
        else if (h > 0.9) off -= 3 + 4 * ((h - 0.9) / 0.1);
        return off;
    }

    /** Noise in [0, 1] spread out to fill [-1, 1]. */
    private static double stretch(double n) {
        return Math.max(-1, Math.min(1, (n - 0.5) * 3.4));
    }

    /** The farthest {@link #rag} can stray outward from a crater of this radius. */
    public static double ragReach(double radius) {
        return radius * 0.2 + 9 + 2.5 + 14;
    }

    /** Smooth value noise in [0, 1] over three dimensions, from two crossed slices of {@link #noise}. */
    private static double noise3(double x, double y, double z, double salt) {
        return 0.5 * (noise(x + salt, z - salt) + noise(y + salt * 1.7, (x - z) * 0.71 + salt * 0.3));
    }

    static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();

    /**
     * The block for ground seared to {@code heat} (1 white-hot, 0 untouched): magma, then blackstone, basalt, deepslate,
     * tuff and charred dirt as it cools, dithered across each boundary (by the block, and in small clumps) so the bands
     * fade into each other instead of meeting at a line. Null where the heat is too little to change the ground.
     */
    static BlockState seared(double heat, int x, int z, boolean lava) {
        double h = heat + (hash(x * 7 + 3, z * 13 + 5) - 0.5) * 0.16 + (noise(x * 3.1 + 7, z * 3.1 - 11) - 0.5) * 0.14;
        if (lava && h > 0.97) return LAVA;
        if (h > 0.8) return MAGMA;
        if (h > 0.64) return BLACKSTONE;
        if (h > 0.5) return BASALT;
        if (h > 0.4) return DEEPSLATE;
        if (h > 0.31) return TUFF;
        if (h > 0.22) return COARSE_DIRT;
        return null;
    }

    /** Cracks running with lava, carved in full (see {@link #crack}). */
    public static ChunkShaper cracks(Vec3 center, com.fantasyweapons.weapons.ultimate.CrackNetwork network) {
        return c -> {
            double[] k = new double[3];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    if (network.near(x + 0.5 - center.x, z + 0.5 - center.z, Double.MAX_VALUE, 2.2, k) == null) continue;
                    int ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    crack(c, x, z, ground, k[0], k[1], k[2], 0);
                }
            }
        };
    }

    /**
     * One column of a crack in the ground, {@code dist} from its centre line, {@code width} its half-width: a V that
     * steps down to a narrow river of lava at its bottom, its walls magma low down, blackstone above and basalt near the
     * top, its lips heaved up into rubble, and the ground beside it seared, the heat fading out over a couple of widths.
     *
     * @param warm how hot the ground around is already (0 to 1), so a crack inside a crater blends into it
     * @return the height of the column's new surface
     */
    static int crack(WreckChunk c, int x, int z, int ground, double dist, double width, double depth, double warm) {
        // the walls wander: wider here, narrower there
        double w = width * (0.75 + 0.5 * noise(x * 0.6 + 31, z * 0.6 - 17));
        double t = dist / w;
        if (t >= 1) {
            // beside it: the lips heaved up into rubble, and the ground seared
            double heat = Math.max(warm, 0.78 * (1 - (t - 1) / 2.2));
            int top = ground;
            if (t < 1.6 && noise(x * 1.9 + 5, z * 1.9 + 9) > 0.45) {
                top = ground + (t < 1.3 ? 2 : 1);
                for (int y = ground + 1; y <= top; y++) c.set(x, y, z, hash(x + y * 31, z - y * 17) < 0.5 ? BASALT : BLACKSTONE);
            }
            if (heat > 0.15) {
                for (int y = c.top(x, z); y > top; y--) if (c.get(x, y, z).getFluidState().isEmpty()) c.clear(x, y, z);
            }
            BlockState s = seared(heat, x, z, false);
            if (s != null && WreckChunk.terrain(c.get(x, top, z))) c.set(x, top, z, s);
            return top;
        }
        // the V: widest at the top, narrowing to a slit a block or two wide at its deepest; a little rough
        double fall = depth * Math.pow(1 - t, 2.2) * (0.85 + 0.3 * noise(x * 1.3 - 40, z * 1.3 + 40));
        int bottom = Math.max(c.minY() + 1, ground - (int) Math.round(fall));
        for (int y = c.top(x, z); y > bottom; y--) c.clear(x, y, z);
        if (t < 0.12) {
            // the river of lava at the bottom, over magma
            c.set(x, bottom, z, LAVA);
            c.set(x, bottom - 1, z, MAGMA);
            return bottom;
        }
        // the walls: hotter the deeper down
        double heat = 0.5 + 0.45 * (1 - t) * Math.min(1, fall / Math.max(1, depth * 0.5));
        BlockState face = seared(Math.max(heat, warm), x, z, false);
        c.set(x, bottom, z, face == null ? BASALT : face);
        c.set(x, bottom - 1, z, hash(x * 3, z * 5) < 0.5 ? BLACKSTONE : BASALT);
        return bottom;
    }

    /**
     * The land blown out by an eruption: everything on it gone and the ground blasted into a rough crater, deepest in
     * the middle, its cracks torn open and running with lava. Its edge wanders far in and out of the circle; the heat
     * fades from magma round the hottest ground and pools of lava, through blackstone and basalt, to deepslate, tuff and
     * charred dirt at the edge and patches of scorch past it, every boundary dithered so nothing stops at a line.
     */
    public static ChunkShaper eruption(Vec3 center, double radius, double depth, com.fantasyweapons.weapons.ultimate.CrackNetwork network) {
        double salt = Math.floorMod((long) Math.floor(center.x * 0.53 + center.z * 0.31), 977L);
        double outer = radius * 1.12 + 12;
        return c -> {
            double[] k = new double[3];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > outer) continue;
                    double ca = dx / Math.max(1e-6, d), sa = dz / Math.max(1e-6, d);
                    double lobes = 0.6 * noise(ca * 12 + salt, sa * 12 + salt) + 0.25 * noise(ca * 36 + salt, sa * 36 - salt) + 0.15 * noise(x * 0.4, z * 0.4);
                    double edge = radius * (0.8 + 0.3 * lobes);
                    double u = d / edge;
                    int top = c.top(x, z), ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    // how hot the ground is here: hottest in the middle, in clumps, round the pools of lava
                    double pool = noise(x * 0.5 + 91, z * 0.5 + 17);
                    double heat = 0.86 * Math.pow(Math.max(0, 1 - u), 0.7) * (0.72 + 0.28 * noise(x * 1.2 + salt, z * 1.2))
                            + 0.3 * Math.max(0, Math.min(1, (pool - 0.6) / 0.2));
                    if (u > 1) heat = Math.max(0, 0.42 * (1 - (u - 1) / 0.2)) * noise(x * 0.9 - salt, z * 0.9 + salt) * 1.6;
                    boolean inside = u <= 1;
                    // the crater floor, rough
                    double blast = inside ? depth * (1 - u * u) * (0.75 + 0.5 * noise(x, z)) : 0;
                    int surface = Math.max(c.minY() + 2, (int) Math.round(ground - blast));
                    if (inside || heat > 0.15) {
                        boolean rim = u > 0.9;
                        for (int y = Math.max(top, ground); y > surface; y--) {
                            BlockState old = c.get(x, y, z);
                            // water at the edge is boiled into obsidian, so the sea does not stand in a wall
                            if (!old.getFluidState().isEmpty()) {
                                if (rim || !inside) c.set(x, y, z, OBSIDIAN);
                                else c.clear(x, y, z);
                            } else c.clear(x, y, z);
                        }
                    }
                    if (network.near(dx, dz, Double.MAX_VALUE, 2.2, k) != null && u < 1.15) {
                        crack(c, x, z, surface, k[0], k[1], k[2], Math.min(heat, 0.7));
                        continue;
                    }
                    BlockState cap = seared(heat, x, z, inside && pool > 0.8);
                    if (cap == null) continue;
                    if (!inside && !WreckChunk.terrain(c.get(x, surface, z))) continue;
                    c.set(x, surface, z, cap);
                    // the seared crust under it
                    if (inside) for (int y = surface - 1; y >= surface - 2; y--) c.set(x, y, z, heat > 0.6 && hash(x + y, z) < 0.3 ? MAGMA : hash(x - y, z + y) < 0.5 ? BLACKSTONE : BASALT);
                }
            }
        };
    }

    /** Smooth value noise in [0, 1] on a coarse grid (about 12 blocks), the same everywhere for the same column. */
    static double noise(double x, double z) {
        double gx = x / 12, gz = z / 12;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double fx = gx - ix, fz = gz - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private static double hash(int x, int z) {
        long h = x * 73856093L ^ z * 19349663L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h >>> 20) & 0xFFFF) / 65535.0;
    }

    /**
     * A canyon torn straight across the land, down to bedrock: {@code length} long through {@code center} along
     * {@code dir}, {@code halfWidth} to each side at the top (narrowing to its ends). Its walls are broken, not smooth:
     * they step down in ragged ledges, narrowing as they go, with chunks of rock jutting out of them and hanging over;
     * the heat of the lava sears their lowest reaches, fading up through magma and blackstone to the bare rock. Its lips
     * crumble, with fissures running back from them; lava drowns its floor up to {@code lavaY}, clumps of fallen rock
     * standing out of it. The sea at its lips is sealed off with obsidian.
     */
    public static ChunkShaper canyon(Vec3 center, Vec3 dir, double length, double halfWidth, int lavaY) {
        double ux = dir.x, uz = dir.z, half = length / 2;
        double salt = Math.floorMod((long) Math.floor(center.x * 0.47 + center.z * 0.19), 971L);
        return c -> {
            double[] cell = new double[3];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double rx = x + 0.5 - center.x, rz = z + 0.5 - center.z;
                    double along = rx * ux + rz * uz, side = -rx * uz + rz * ux, across = Math.abs(side);
                    if (Math.abs(along) > half) continue;
                    double taper = Math.min(1, (half - Math.abs(along)) / (length * 0.18));
                    double w = halfWidth * Math.sqrt(Math.max(0, taper)) * (0.78 + 0.44 * noise(along * 0.9 + 400, 13));
                    // fissures running back from the lips
                    double fissure = noise(along * 2.2 + salt, side > 0 ? 7 : -7);
                    boolean crack = fissure > 0.82 && across < w + 14 * (fissure - 0.82) / 0.18 && across > w;
                    if (across > w + 7 && !crack) continue;
                    int top = c.top(x, z), ground = Math.max(c.ground(x, z), c.minY() + 1);
                    if (across > w) {
                        // the lip: crumbling, the sea sealed off; a fissure splits back into the land
                        double edge = (across - w) / 7;
                        int bite = crack ? 6 + (int) (10 * (1 - edge)) : noise(x * 1.5 + salt, z * 1.5) > 0.35 + edge * 0.5 ? (int) (3 * (1 - edge)) + 1 : 0;
                        for (int y = top; y > ground - Math.max(6, bite + 2) && y > c.minY(); y--) {
                            BlockState old = c.get(x, y, z);
                            if (!old.getFluidState().isEmpty()) c.set(x, y, z, OBSIDIAN);
                            else if (y > ground - bite) c.clear(x, y, z);
                        }
                        continue;
                    }
                    double depth = Math.max(1, ground - c.minY());
                    for (int y = Math.max(top, ground); y > c.minY(); y--) {
                        double down = ground - y;
                        // the ledges: every 7 to 12 blocks down the wall steps in by 2 to 6 blocks, unevenly along it
                        double wy = w * (1 - 0.5 * Math.pow(Math.max(0, down) / depth, 1.5));
                        int step = (int) Math.floor((down + 5 * noise(along * 0.7 + salt, side > 0 ? 50 : -50)) / (7 + 5 * noise(along * 0.3, 90)));
                        wy -= Math.max(0, step) * (2 + 4 * noise(along * 0.6 + step * 13.7, side > 0 ? 30 : -30)) * 0.6;
                        wy = Math.max(1.5, wy);
                        if (across < wy - 6) {
                            if (y <= lavaY) c.set(x, y, z, LAVA);
                            else c.clear(x, y, z);
                            continue;
                        }
                        // near the wall's face: chunks of rock jutting out of it, seams set back
                        cells(along, y, side > 0 ? 500 : -500, cell);
                        boolean seam = cell[1] - cell[0] < 0.9;
                        double jut = seam ? -1.2 : (0.8 + 2.4 * cell[2]) * (1 - 0.6 * Math.min(1, cell[0] / (WALL_CELL * 0.6)));
                        double face = wy - jut;
                        if (across < face) {
                            if (y <= lavaY) c.set(x, y, z, LAVA);
                            else c.clear(x, y, z);
                            continue;
                        }
                        if (across > face + 2.5) continue;
                        BlockState old = c.get(x, y, z);
                        if (!old.getFluidState().isEmpty()) {
                            c.set(x, y, z, OBSIDIAN);
                            continue;
                        }
                        if (!WreckChunk.terrain(old)) {
                            if (!old.isAir()) c.clear(x, y, z);
                            continue;
                        }
                        // the lava's heat sears the lowest reaches of the walls, fading out up them
                        double heat = 1 - (y - lavaY) / 26.0;
                        BlockState s2 = seared(seam ? heat + 0.12 : heat, x + y * 7, z - y * 3, false);
                        if (s2 != null) c.set(x, y, z, s2);
                    }
                    // clumps of fallen rock standing out of the lava
                    cells(x * 0.8, 0, z * 0.8, cell);
                    if (cell[0] < 3.2 && cell[2] < 0.14 && c.get(x, lavaY, z).is(Blocks.LAVA)) {
                        int h = 1 + (int) (3 * (1 - cell[0] / 3.2));
                        for (int y = lavaY + 1; y <= lavaY + h; y++) if (c.get(x, y, z).isAir()) c.set(x, y, z, cell[2] < 0.07 ? BLACKSTONE : BASALT);
                    }
                }
            }
        };
    }

    static final BlockState ROOT_WOOD = Blocks.DARK_OAK_WOOD.defaultBlockState();
    static final BlockState ROOT_MANGROVE = Blocks.MANGROVE_WOOD.defaultBlockState();
    static final BlockState ROOT_TIP = Blocks.MANGROVE_ROOTS.defaultBlockState();
    static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();

    /** What one block of a root sees: where it is, how far along the root (0 to 1), how far from its axis and how thick it is there. */
    @FunctionalInterface
    public interface RootVisitor {
        void block(int x, int y, int z, double u, double d, double radius, boolean back);
    }

    /**
     * Every block within a root (and the ring just around it, {@code d > radius}) inside the given bounds, piece by
     * piece along it. Server and client walk the same blocks, so the root that grows on screen is the one built.
     */
    public static void rootBlocks(com.fantasyweapons.weapons.ultimate.RootSpline root, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, RootVisitor visit) {
        int n = 28;
        Vec3[] pts = root.samples(n);
        for (int i = 0; i < n; i++) {
            Vec3 a = pts[i], b = pts[i + 1];
            double ta = i / (double) n, tb = (i + 1) / (double) n;
            double reach = root.radius(ta) + 1.2;
            int xa = Math.max(minX, (int) Math.floor(Math.min(a.x, b.x) - reach)), xb = Math.min(maxX, (int) Math.ceil(Math.max(a.x, b.x) + reach));
            int za = Math.max(minZ, (int) Math.floor(Math.min(a.z, b.z) - reach)), zb = Math.min(maxZ, (int) Math.ceil(Math.max(a.z, b.z) + reach));
            int ya = Math.max(minY, (int) Math.floor(Math.min(a.y, b.y) - reach)), yb = Math.min(maxY, (int) Math.ceil(Math.max(a.y, b.y) + reach));
            if (xa > xb || za > zb || ya > yb) continue;
            double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z, len2 = dx * dx + dy * dy + dz * dz;
            for (int x = xa; x <= xb; x++) {
                for (int z = za; z <= zb; z++) {
                    for (int y = ya; y <= yb; y++) {
                        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
                        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - a.x) * dx + (py - a.y) * dy + (pz - a.z) * dz) / len2));
                        double qx = a.x + dx * t - px, qy = a.y + dy * t - py, qz = a.z + dz * t - pz;
                        double d = Math.sqrt(qx * qx + qy * qy + qz * qz);
                        double u = ta + (tb - ta) * t, r = root.radius(u);
                        if (d > r + 1.2) continue;
                        visit.block(x, y, z, u, d, r, py > a.y + dy * t + r * 0.55);
                    }
                }
            }
        }
    }

    /** The block of a root at a place: bark, streaked with mangrove wood, moss on its back, a tangle of roots at its tip. */
    public static BlockState rootBlock(int x, int y, int z, double u, boolean back) {
        if (u > 0.86) return ROOT_TIP;
        double roll = hash3(x, y, z, 9);
        return back && roll < 0.55 ? MOSS : roll < 0.22 ? ROOT_MANGROVE : ROOT_WOOD;
    }

    /**
     * Giant roots built into the world for good: solid bark, streaked with mangrove wood, moss on their backs and a
     * tangle of roots at their thin ends; where they leave the ground it is broken into rooted dirt.
     */
    public static ChunkShaper roots(java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> roots) {
        return c -> {
            int x0 = c.minX(), z0 = c.minZ();
            for (var root : roots) {
                if (root.maxX() < x0 || root.minX() > x0 + 16 || root.maxZ() < z0 || root.minZ() > z0 + 16) continue;
                rootBlocks(root, x0, x0 + 15, c.minY(), c.maxY() - 1, z0, z0 + 15, (x, y, z, u, d, r, back) -> {
                    if (c.masked(x, z)) return;
                    BlockState old = c.get(x, y, z);
                    if (d > r) {
                        // where the root breaks the ground, the earth around it is torn and rooted
                        if (WreckChunk.terrain(old) && hash3(x, y, z, 5) < 0.5) c.set(x, y, z, ROOTED_DIRT);
                        return;
                    }
                    if (old.is(Blocks.DARK_OAK_WOOD) || old.is(Blocks.MANGROVE_WOOD) || old.is(Blocks.MOSS_BLOCK) || old.is(Blocks.MANGROVE_ROOTS)) return;
                    c.set(x, y, z, rootBlock(x, y, z, u, back));
                });
            }
        };
    }

    static final BlockState ICE = Blocks.ICE.defaultBlockState();
    static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();

    /**
     * Everything frozen where the Ice Age's wave has passed: water and the sea become ice (blue ice at the surface), lava
     * obsidian; trees are frozen into ice where they stood, their leaves shattered; plants are swept away; the ground is
     * glazed in snow and ice, and jagged spikes of ice stand here and there.
     */
    public static ChunkShaper freeze(Vec3 center, double radius) {
        return freeze(center, radius, 0);
    }

    static final BlockState SNOW_LAYER = Blocks.SNOW.defaultBlockState();

    /**
     * {@link #freeze(Vec3, double)}, sparing the ground within {@code spare} of the centre. Nowhere does the cold stop at
     * a clean circle: the solid ice reaches out in tongues and is cut into by bays (anywhere from 60 to 92% of the
     * radius, wandering round the circle), and beyond that it breaks up into patches that thin out, the outermost no
     * more than snow on the ground, until the last at the rim.
     */
    public static ChunkShaper freeze(Vec3 center, double radius, double spare) {
        // each cast's own shape
        double salt = Math.floorMod((long) Math.floor(center.x * 0.37 + center.z * 0.61), 997L);
        return c -> {
            net.minecraft.util.RandomSource r = c.random();
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius) continue;
                    double ca = dx / Math.max(1e-6, d), sa = dz / Math.max(1e-6, d);
                    // how far round the circle the solid ice reaches here: big lobes, smaller ones on them, and a wander
                    double lobes = 0.6 * noise(ca * 12 + salt, sa * 12 + salt) + 0.25 * noise(ca * 40 + salt + 300, sa * 40 + salt + 300)
                            + 0.15 * noise(x * 0.35 + salt, z * 0.35 - salt);
                    double edge = radius * (0.6 + 0.32 * lobes);
                    if (d < spare * (0.8 + 0.35 * lobes)) continue;
                    double cover = d <= edge ? 1 : 1 - (d - edge) / Math.max(1, radius - edge);
                    // past the edge, patches: frozen where the patchy noise falls under what cover is left
                    if (cover < 1 && noise(x * 1.7 + salt * 3, z * 1.7 - salt) > cover * 0.9) continue;
                    // the outer patches only frost over: no ice through the trees, no spikes
                    boolean deep = cover > 0.55;
                    int top = c.top(x, z), ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    boolean surface = true;
                    for (int y = top; y > ground; y--) {
                        BlockState old = c.get(x, y, z);
                        if (old.isAir()) continue;
                        if (old.is(Blocks.LAVA)) c.set(x, y, z, OBSIDIAN);
                        else if (!old.getFluidState().isEmpty()) {
                            if (deep) c.set(x, y, z, surface ? BLUE_ICE : y > ground + 3 ? ICE : PACKED_ICE);
                            else if (surface) c.set(x, y, z, ICE);
                            surface = false;
                        } else if (!deep) {
                            // a light frost: snow settles on what it can
                            if (old.is(net.minecraft.tags.BlockTags.LEAVES) && r.nextFloat() < 0.3f) c.set(x, y, z, PACKED_ICE);
                            else if (old.canBeReplaced() && !old.is(Blocks.SNOW)) c.clear(x, y, z);
                        } else if (old.is(net.minecraft.tags.BlockTags.LOGS)) c.set(x, y, z, PACKED_ICE);
                        else if (old.is(net.minecraft.tags.BlockTags.LEAVES)) c.set(x, y, z, r.nextFloat() < 0.45f ? ICE : AIR);
                        else if (old.canBeReplaced() || !old.blocksMotion()) c.clear(x, y, z);
                        else c.set(x, y, z, r.nextFloat() < 0.5f ? PACKED_ICE : ICE);
                    }
                    // the ground glazed in snow and ice, more snow and less ice the thinner the cover
                    float roll = r.nextFloat();
                    if (WreckChunk.terrain(c.get(x, ground, z))) {
                        if (deep) {
                            c.set(x, ground, z, roll < 0.45f ? SNOW : roll < 0.8f ? PACKED_ICE : BLUE_ICE);
                            if (r.nextFloat() < 0.6f) c.set(x, ground - 1, z, PACKED_ICE);
                        } else {
                            c.set(x, ground, z, roll < cover * 0.6f ? PACKED_ICE : SNOW);
                            if (c.get(x, ground + 1, z).isAir() && r.nextFloat() < 0.5f) c.set(x, ground + 1, z, SNOW_LAYER);
                        }
                    }
                    // jagged spikes of ice, rarer towards the edge
                    if (deep && r.nextFloat() < 0.004f * cover) {
                        int h = (int) ((5 + r.nextInt(14)) * (0.5 + 0.5 * cover));
                        for (int y = ground + 1; y <= ground + h; y++) c.set(x, y, z, PACKED_ICE);
                    }
                }
            }
        };
    }

    static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    static final BlockState COBBLED_DEEPSLATE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

    /**
     * The canyon the Great Slash tears into the ground where its plane meets it, along its whole length: a ragged V up
     * to {@code width} across and {@code depth} deep (shallower towards its ends), its floor strewn with rubble, and its
     * lips heaved up a block or two with the ground they were made of. Columns further than {@code length} along the
     * slash, or behind the blade, are left alone.
     */
    public static ChunkShaper slashScar(Vec3 origin, Vec3 forward, Vec3 blade, double length, double width, double depth, long seed) {
        Vec3 n = forward.cross(blade).normalize();
        Vec3 flat = new Vec3(forward.x, 0, forward.z);
        Vec3 ahead = flat.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : flat.normalize();
        double salt = Math.floorMod(seed, 997L);
        return c -> {
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - origin.x, dz = z + 0.5 - origin.z;
                    double along = dx * ahead.x + dz * ahead.z;
                    if (along < 4 || along > length) continue;
                    int ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    // how far the column lies from where the plane cuts the ground here
                    double s = n.x * dx + n.y * (ground + 0.5 - origin.y) + n.z * dz;
                    double flatness = Math.sqrt(Math.max(1e-3, n.x * n.x + n.z * n.z));
                    s /= flatness;
                    double ends = Math.min(1, Math.min((along - 4) / 24, (length - along) / 40));
                    double half = width * 0.5 * (0.7 + 0.6 * noise(along * 0.8 + salt, salt * 0.3)) * (0.5 + 0.5 * ends);
                    double t = Math.abs(s) / Math.max(1, half);
                    if (t > 1.5) continue;
                    if (t >= 1) {
                        // the lips, heaved up with the ground they were made of
                        if (noise(x * 1.7 + salt, z * 1.7 - salt) > 0.4) {
                            BlockState surface = c.get(x, ground, z);
                            int up = t < 1.25 ? 2 : 1;
                            for (int y = ground + 1; y <= ground + up; y++) {
                                if (c.get(x, y, z).isAir() || c.get(x, y, z).canBeReplaced()) c.set(x, y, z, WreckChunk.terrain(surface) ? surface : COBBLE);
                            }
                        }
                        continue;
                    }
                    double fall = depth * ends * Math.pow(1 - t, 1.2) * (0.8 + 0.4 * noise(x * 1.1 - salt, z * 1.1 + salt));
                    int bottom = Math.max(c.minY() + 1, ground - (int) Math.round(fall));
                    for (int y = c.top(x, z); y > bottom; y--) {
                        BlockState old = c.get(x, y, z);
                        if (!old.getFluidState().isEmpty() && t > 0.85) c.set(x, y, z, OBSIDIAN);
                        else c.clear(x, y, z);
                    }
                    // rubble on the floor and the lower walls
                    double h = hash(x * 5 + 11, z * 7 - 3);
                    if (h < 0.55) c.set(x, bottom, z, h < 0.2 ? GRAVEL : h < 0.4 ? COBBLE : COBBLED_DEEPSLATE);
                    if (t < 0.5 && hash(x - 9, z + 4) < 0.18) c.set(x, bottom + 1, z, h < 0.3 ? COBBLE : COBBLED_DEEPSLATE);
                }
            }
        };
    }

    /**
     * The Great Slash's cut: a slab {@code thickness} thick through everything, in the plane through {@code origin}
     * spanned by {@code forward} and {@code blade}, from the blade out to {@code length}, fanning to {@code halfWidth}
     * either side.
     */
    public static ChunkShaper slab(Vec3 origin, Vec3 forward, Vec3 blade, double length, double halfWidth, double thickness) {
        Vec3 n = forward.cross(blade).normalize();
        double half = thickness / 2;
        return c -> {
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - origin.x, dz = z + 0.5 - origin.z;
                    double a = n.x * dx + n.z * dz;
                    int y0, y1;
                    if (Math.abs(n.y) > 1e-3) {
                        double ya = origin.y + (-half - a) / n.y, yb = origin.y + (half - a) / n.y;
                        y0 = (int) Math.floor(Math.min(ya, yb));
                        y1 = (int) Math.ceil(Math.max(ya, yb));
                    } else {
                        if (Math.abs(a) > half) continue;
                        y0 = (int) Math.floor(origin.y - halfWidth - 2);
                        y1 = (int) Math.ceil(origin.y + halfWidth + 2);
                    }
                    y0 = Math.max(c.minY(), y0);
                    y1 = Math.min(c.maxY() - 1, y1);
                    for (int y = y0; y <= y1; y++) {
                        double dy = y + 0.5 - origin.y;
                        double f = forward.x * dx + forward.y * dy + forward.z * dz;
                        if (f < 1.5 || f > length) continue;
                        double l = Math.abs(blade.x * dx + blade.y * dy + blade.z * dz);
                        if (l > halfWidth * Math.sqrt(Math.min(1, f / (length * 0.35)))) continue;
                        if (Math.abs(n.x * dx + n.y * dy + n.z * dz) > half) continue;
                        c.clear(x, y, z);
                    }
                }
            }
        };
    }

    /**
     * The Grand Earthquake's broken land: the ground is split into plates (a Voronoi web around {@code plates}, given
     * relative to the centre, with how far each heaves up or sinks in {@code shifts}); along their edges fissures tear
     * down deep, some to bedrock, lava in their depths; the radial cracks of {@code cracks} run through it all. Nothing
     * within {@code safe} of the centre is touched.
     */
    public static ChunkShaper quake(Vec3 center, double radius, double safe, double[][] plates, int[] shifts,
                                    com.fantasyweapons.weapons.ultimate.CrackNetwork cracks) {
        return c -> {
            double[] k = new double[1], nearCrack = new double[3];
            int bottom = c.minY() + 6;
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius || d < safe) continue;
                    double fade = Math.min(1, (d - safe) / 6) * Math.min(1, (radius - d) / 12);
                    // which plate, and how near its edge
                    int best = 0;
                    double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
                    for (int i = 0; i < plates.length; i++) {
                        double px = dx - plates[i][0], pz = dz - plates[i][1], pd = px * px + pz * pz;
                        if (pd < d1) {
                            d2 = d1;
                            d1 = pd;
                            best = i;
                        } else if (pd < d2) d2 = pd;
                    }
                    double edge = Math.sqrt(d2) - Math.sqrt(d1);
                    int top = c.top(x, z), ground = c.ground(x, z);
                    if (ground <= bottom) continue;
                    // the plate heaves or sinks, fully away from its edges
                    int shift = (int) Math.round(shifts[best] * fade * Math.min(1, Math.max(0, (edge - 3) / 8)));
                    if (shift > 0) {
                        for (int y = Math.min(top, c.maxY() - 1 - shift); y >= bottom; y--) c.set(x, y + shift, z, c.get(x, y, z));
                    } else if (shift < 0) {
                        for (int y = bottom - shift; y <= top; y++) c.set(x, y + shift, z, c.get(x, y, z));
                        for (int y = top + shift + 1; y <= top; y++) c.clear(x, y, z);
                    }
                    top += shift;
                    ground += shift;
                    // the fissures along the plates' edges, and the cracks running out from the blow
                    double width = (2.2 + 3.5 * noise(x * 0.7, z * 0.7)) * fade;
                    double floor = Double.NaN;
                    if (edge < width) {
                        double deep = noise(x * 0.3 + 55, z * 0.3 + 91) > 0.6 ? ground - bottom : 22 + 30 * noise(x * 0.5, z * 0.5 + 7);
                        floor = ground - deep * (1 - edge / width * 0.5);
                    }
                    // the cracks running out from the blow: deep V's, narrowing to a slit
                    if (cracks.near(dx, dz, Double.MAX_VALUE, 2.2, nearCrack) != null) {
                        int surface = crack(c, x, z, ground, nearCrack[0], nearCrack[1], nearCrack[2] * fade, 0.3);
                        if (Double.isNaN(floor) || floor >= surface) continue;
                    }
                    if (Double.isNaN(floor)) continue;
                    int f = Math.max(bottom, (int) Math.round(floor));
                    for (int y = top; y > f + 2; y--) c.clear(x, y, z);
                    if (f < 40) for (int y = f; y <= f + 2; y++) c.set(x, y, z, LAVA);
                    else c.set(x, f, z, c.random().nextFloat() < 0.3f ? MAGMA : BLACKSTONE);
                }
            }
        };
    }

    /**
     * A giant ice spike driven into the ground: a crater blasted where it struck ({@code crater} across), the spike itself
     * - a long cone of packed and blue ice, {@code length} long, leaning along {@code dir}, a third of it buried - and the
     * ground around frozen.
     */
    public static ChunkShaper iceSpike(Vec3 impact, Vec3 dir, double length, double base, double crater) {
        Vec3 d = dir.normalize();
        Vec3 tip = impact.subtract(d.scale(length * 0.66)), root = impact.add(d.scale(length * 0.34));
        return c -> {
            int r = (int) Math.ceil(Math.max(crater + 6, base + length * 0.7));
            for (int x = Math.max(c.minX(), (int) impact.x - r); x <= Math.min(c.minX() + 15, (int) impact.x + r); x++) {
                for (int z = Math.max(c.minZ(), (int) impact.z - r); z <= Math.min(c.minZ() + 15, (int) impact.z + r); z++) {
                    double dx = x + 0.5 - impact.x, dz = z + 0.5 - impact.z, hd = Math.sqrt(dx * dx + dz * dz);
                    int ground = c.ground(x, z);
                    // frost around the strike
                    if (hd < crater + 6 && ground > c.minY()) {
                        for (int y = c.top(x, z); y > ground; y--) if (!c.get(x, y, z).isAir()) c.clear(x, y, z);
                        if (c.random().nextFloat() < 0.8f) c.set(x, ground, z, c.random().nextBoolean() ? SNOW : PACKED_ICE);
                    }
                    int y0 = (int) Math.floor(Math.min(tip.y, root.y) - crater - 1), y1 = (int) Math.ceil(Math.max(tip.y, root.y) + base + 1);
                    for (int y = Math.max(c.minY(), y0); y <= Math.min(c.maxY() - 1, y1); y++) {
                        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
                        // the crater
                        double cx = px - impact.x, cy = py - impact.y, cz = pz - impact.z;
                        if (cx * cx + cy * cy + cz * cz < crater * crater) c.clear(x, y, z);
                        // the spike: a cone from its buried root (wide) to its tip (a point)
                        Vec3 rel = new Vec3(px - root.x, py - root.y, pz - root.z);
                        double along = -rel.dot(d);
                        if (along < 0 || along > length) continue;
                        double off = rel.add(d.scale(along)).length();
                        double rad = base * (1 - along / length);
                        if (off <= rad) c.set(x, y, z, off < rad * 0.45 ? BLUE_ICE : PACKED_ICE);
                    }
                }
            }
        };
    }

    static final BlockState THORN_CORE = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();

    /**
     * A giant thorn standing forever where it burst out of the ground: a cone of bark from {@code base} (sunk a little
     * into the earth) to a point at {@code tip}, {@code radius} thick at its foot, moss on its back, its heart pale
     * stripped wood, small thorns spiralling up its sides; the ground at its foot is torn into rooted dirt.
     */
    public static ChunkShaper thornSpire(Vec3 base, Vec3 tip, double radius) {
        Vec3 axis = tip.subtract(base);
        double length = axis.length();
        Vec3 d = axis.scale(1 / length);
        Vec3[] b = basisOf(d);
        return c -> {
            int reach = (int) Math.ceil(radius + 4);
            int x0 = (int) Math.floor(Math.min(base.x, tip.x)) - reach, x1 = (int) Math.ceil(Math.max(base.x, tip.x)) + reach;
            int z0 = (int) Math.floor(Math.min(base.z, tip.z)) - reach, z1 = (int) Math.ceil(Math.max(base.z, tip.z)) + reach;
            int y0 = (int) Math.floor(Math.min(base.y, tip.y)) - reach, y1 = (int) Math.ceil(Math.max(base.y, tip.y)) + reach;
            for (int x = Math.max(c.minX(), x0); x <= Math.min(c.minX() + 15, x1); x++) {
                for (int z = Math.max(c.minZ(), z0); z <= Math.min(c.minZ() + 15, z1); z++) {
                    for (int y = Math.max(c.minY(), y0); y <= Math.min(c.maxY() - 1, y1); y++) {
                        Vec3 rel = new Vec3(x + 0.5 - base.x, y + 0.5 - base.y, z + 0.5 - base.z);
                        double along = rel.dot(d);
                        if (along < -2 || along > length) continue;
                        Vec3 side = rel.subtract(d.scale(along));
                        double off = side.length();
                        double u = Math.max(0, along) / length;
                        double rad = radius * (1 - u) * (1 - u * 0.2);
                        // little thorns spiralling up its flanks
                        double ang = Math.atan2(side.dot(b[1]), side.dot(b[0]));
                        double spiral = Math.sin(ang * 3 - along * 0.9);
                        if (spiral > 0.92 && u > 0.12 && u < 0.85 && off < rad + 2.2 * (1 - u)) rad += 2.2 * (1 - u);
                        if (off > rad + 1.3) continue;
                        BlockState old = c.get(x, y, z);
                        if (off > rad) {
                            if (along < 2 && WreckChunk.terrain(old) && c.random().nextFloat() < 0.6f) c.set(x, y, z, ROOTED_DIRT);
                            continue;
                        }
                        boolean top = side.y > 0 && side.normalize().y > 0.5 && off > rad - 1.2;
                        c.set(x, y, z, off < rad * 0.4 ? THORN_CORE : top && c.random().nextFloat() < 0.6f ? MOSS : c.random().nextFloat() < 0.2f ? ROOT_MANGROVE : ROOT_WOOD);
                    }
                }
            }
        };
    }

    /** Two unit vectors square to {@code n} and to each other. */
    static Vec3[] basisOf(Vec3 n) {
        Vec3 a = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = n.cross(a).normalize();
        return new Vec3[]{u, n.cross(u).normalize()};
    }

    /**
     * Ground split open by hellfire along a {@link com.fantasyweapons.weapons.ultimate.CrackNetwork}: trenches with lava in
     * their bottoms and walls seared to magma, coal and blackstone, their lips burnt to coal and magma.
     */
    public static ChunkShaper hellSplit(Vec3 center, com.fantasyweapons.weapons.ultimate.CrackNetwork network) {
        return c -> {
            double[] k = new double[3];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    if (network.near(x + 0.5 - center.x, z + 0.5 - center.z, Double.MAX_VALUE, 2.2, k) == null) continue;
                    int ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    crack(c, x, z, ground, k[0], k[1], k[2], 0.4);
                }
            }
        };
    }

    private static BlockState seared(float roll) {
        return roll < 0.4f ? MAGMA : roll < 0.7f ? COAL : roll < 0.9f ? BLACKSTONE : BASALT;
    }

    /**
     * Land burnt to ruin: within {@code radius} of {@code center} what grew or was built on the ground is gone (more
     * surely nearer the middle), and the ground itself is scorched into coal, magma, blackstone and basalt. Water stays.
     */
    public static ChunkShaper scorch(Vec3 center, double radius) {
        return c -> {
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    if (c.masked(x, z)) continue;
                    double d = Math.hypot(x + 0.5 - center.x, z + 0.5 - center.z);
                    if (d > radius) continue;
                    double heat = 1 - d / radius;
                    if (c.random().nextFloat() > 0.35 + 0.65 * Math.sqrt(heat)) continue;
                    int g = c.ground(x, z);
                    if (g <= c.minY() || !c.get(x, g + 1, z).getFluidState().isEmpty()) continue;
                    for (int y = c.top(x, z); y > g; y--) if (c.get(x, y, z).getFluidState().isEmpty()) c.clear(x, y, z);
                    float roll = c.random().nextFloat();
                    if (roll < 0.15f) continue;
                    c.set(x, g, z, roll < 0.32f ? COAL : roll < 0.47f ? MAGMA : roll < 0.85f ? BLACKSTONE : BASALT);
                }
            }
        };
    }

    /** Several shapers applied to a chunk one after another. */
    public static ChunkShaper all(ChunkShaper... shapers) {
        return c -> {
            for (ChunkShaper s : shapers) s.shape(c);
        };
    }

    /**
     * A chunk refilled from another one's snapshot as a {@link com.fantasyweapons.weapons.ultimate.ShufflePlan} says:
     * turned, hung upside down, shifted and tumbled, from the bottom of the world to the sky. Unbreakable blocks stay
     * where they are and are not carried; nor are block entities. A source that could not be snapshotted is replaced by
     * the chunk's own blocks.
     */
    public static ChunkShaper shuffle(com.fantasyweapons.weapons.ultimate.ShufflePlan.Move m,
                                      java.util.Map<Long, net.minecraft.world.level.chunk.PalettedContainer<BlockState>[]> snaps) {
        return c -> {
            var self = snaps.computeIfAbsent(m.dest().toLong(), k -> c.snapshot());
            var src = snaps.getOrDefault(m.source().toLong(), self);
            int height = c.maxY() - c.minY();
            int[] at = new int[3];
            for (int h = 0; h < height; h++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState s = AIR;
                        if (com.fantasyweapons.weapons.ultimate.ShufflePlan.source(m, x, h, z, height, at)) {
                            s = src[at[1] >> 4].get(at[0], at[1] & 15, at[2]);
                            if (WreckChunk.unbreakable(s) || s.hasBlockEntity()) s = AIR;
                        }
                        c.set(c.minX() + x, c.minY() + h, c.minZ() + z, s);
                    }
                }
            }
        };
    }
}
