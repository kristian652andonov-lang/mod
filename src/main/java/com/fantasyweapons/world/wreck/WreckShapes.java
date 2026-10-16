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

    /**
     * A perfect cylinder erased from the sky to the bottom of the world: its walls are seared into glowing magma with
     * seams of lava, and a lava sea pools on the floor.
     *
     * @param lavaDepth layers of lava over the floor
     */
    public static ChunkShaper shaft(Vec3 center, double radius, int lavaDepth) {
        double r2 = radius * radius, wall = radius + 2.5, wall2 = wall * wall;
        return c -> {
            int floor = c.minY() + 1;
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d2 = dx * dx + dz * dz;
                    if (d2 > wall2) continue;
                    int top = c.top(x, z);
                    if (d2 <= r2) {
                        for (int y = floor; y <= top; y++) c.set(x, y, z, y < floor + lavaDepth ? LAVA : AIR);
                        if (top < floor + lavaDepth) for (int y = Math.max(floor, top + 1); y < floor + lavaDepth; y++) c.set(x, y, z, LAVA);
                    } else {
                        // the seared wall: magma, threaded with veins of lava where the beam's edge bit hardest
                        double edge = Math.sqrt(d2) - radius;
                        for (int y = floor; y <= top; y++) {
                            BlockState old = c.get(x, y, z);
                            if (!old.getFluidState().isEmpty()) {
                                // water and lava at the edge are seared into a wall of obsidian that holds back the rest
                                c.set(x, y, z, OBSIDIAN);
                                continue;
                            }
                            if (!WreckChunk.terrain(old)) {
                                if (!old.isAir()) c.set(x, y, z, AIR);
                                continue;
                            }
                            float roll = c.random().nextFloat();
                            BlockState seared = edge < 1.2 && roll < 0.08 ? LAVA : roll < 0.75 ? MAGMA : roll < 0.9 ? BLACKSTONE : OBSIDIAN;
                            c.set(x, y, z, seared);
                        }
                    }
                }
            }
        };
    }

    /**
     * A sphere erased from the world, the bowl it leaves scarred: a crust of blackstone, obsidian and crying obsidian two
     * blocks thick, and water or lava at its edge crushed into obsidian so nothing pours in.
     */
    public static ChunkShaper sphere(Vec3 center, double radius) {
        double shell = radius + 2.2;
        return c -> {
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d2 = dx * dx + dz * dz;
                    if (d2 > shell * shell) continue;
                    double half = Math.sqrt(shell * shell - d2);
                    int y0 = Math.max(c.minY(), (int) Math.floor(center.y - half)), y1 = Math.min(c.top(x, z), (int) Math.ceil(center.y + half));
                    for (int y = y0; y <= y1; y++) {
                        double dy = y + 0.5 - center.y, r = Math.sqrt(d2 + dy * dy);
                        if (r <= radius) {
                            c.clear(x, y, z);
                            continue;
                        }
                        if (r > shell) continue;
                        BlockState old = c.get(x, y, z);
                        if (!old.getFluidState().isEmpty()) c.set(x, y, z, OBSIDIAN);
                        else if (WreckChunk.terrain(old)) {
                            float roll = c.random().nextFloat();
                            c.set(x, y, z, roll < 0.15f ? CRYING : roll < 0.5f ? OBSIDIAN : BLACKSTONE);
                        } else if (!old.isAir()) c.clear(x, y, z);
                    }
                }
            }
        };
    }

    /**
     * Cracks torn into the ground along a {@link com.fantasyweapons.weapons.ultimate.CrackNetwork}: trenches down from the
     * ground, deepest along their middle, edged in magma, with lava welling up in the bottom.
     */
    public static ChunkShaper cracks(Vec3 center, com.fantasyweapons.weapons.ultimate.CrackNetwork network) {
        return c -> {
            double[] k = new double[1];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    var seg = network.at(x + 0.5 - center.x, z + 0.5 - center.z, Double.MAX_VALUE, k);
                    if (seg == null) continue;
                    int ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    int bottom = Math.max(c.minY() + 1, ground - (int) Math.round(seg.depth() * (0.35 + 0.65 * k[0])));
                    for (int y = c.top(x, z); y > bottom + 2; y--) c.clear(x, y, z);
                    for (int y = bottom; y <= bottom + 2; y++) c.set(x, y, z, LAVA);
                    // the lips of the crack glow
                    if (k[0] < 0.3) c.set(x, bottom - 1, z, MAGMA);
                }
            }
        };
    }

    /**
     * The land blown out by an eruption: within {@code radius} everything on it is gone and the ground is blasted into a
     * shallow crater ({@code depth} at the middle), its cracks torn deeper and running with lava, and nothing is left on
     * top but lava, magma and coal over scorched blackstone and basalt. A band of scorched ground rings it.
     */
    public static ChunkShaper eruption(Vec3 center, double radius, double depth, com.fantasyweapons.weapons.ultimate.CrackNetwork network) {
        double scorch = radius + 10;
        return c -> {
            double[] k = new double[1];
            net.minecraft.util.RandomSource r = c.random();
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > scorch) continue;
                    int top = c.top(x, z), ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    if (d > radius) {
                        // scorched ring: plants and trees burnt away (the sea stays), the ground charred here and there
                        for (int y = top; y > ground; y--) {
                            if (c.get(x, y, z).getFluidState().isEmpty()) c.clear(x, y, z);
                        }
                        float roll = r.nextFloat();
                        if (roll < 0.45f * (1 - (d - radius) / 10)) c.set(x, ground, z, roll < 0.15f ? COAL : roll < 0.3f ? BLACKSTONE : BASALT);
                        continue;
                    }
                    // the crater: deepest in the middle, rough
                    double u = d / radius;
                    double blast = depth * (1 - u * u) * (0.75 + 0.5 * noise(x, z));
                    int surface = (int) Math.round(ground - blast);
                    var seg = network.at(dx, dz, Double.MAX_VALUE, k);
                    boolean crack = seg != null;
                    if (crack) surface -= (int) Math.round(seg.depth() * (0.4 + 0.6 * k[0]));
                    surface = Math.max(c.minY() + 2, surface);
                    boolean rim = d > radius - 4;
                    for (int y = Math.max(top, ground); y > surface; y--) {
                        BlockState old = c.get(x, y, z);
                        // water at the very edge is boiled into obsidian, so the sea does not stand in a wall
                        if (rim && !old.getFluidState().isEmpty()) c.set(x, y, z, OBSIDIAN);
                        else c.clear(x, y, z);
                    }
                    if (crack) {
                        // rivers of lava down the cracks
                        for (int y = surface; y <= surface + 2; y++) c.set(x, y, z, LAVA);
                        c.set(x, surface - 1, z, MAGMA);
                        continue;
                    }
                    float roll = r.nextFloat();
                    double pool = noise(x * 0.5 + 91, z * 0.5 + 17);
                    BlockState cap = pool > 0.72 ? LAVA : roll < 0.4f ? MAGMA : roll < 0.75f ? COAL : roll < 0.88f ? BLACKSTONE : BASALT;
                    c.set(x, surface, z, cap);
                    for (int y = surface - 1; y >= surface - 3; y--) {
                        float q = r.nextFloat();
                        c.set(x, y, z, q < 0.35f ? BLACKSTONE : q < 0.65f ? BASALT : q < 0.85f ? MAGMA : COAL);
                    }
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
     * {@code dir}, {@code halfWidth} to each side (narrowing to its ends, its walls jagged), its floor sloping up steeply
     * at the walls and drowned in lava up to {@code lavaY}, broken rock floating on the lava. The sea at its lips is
     * sealed off with obsidian.
     */
    public static ChunkShaper canyon(Vec3 center, Vec3 dir, double length, double halfWidth, int lavaY) {
        double ux = dir.x, uz = dir.z, half = length / 2;
        return c -> {
            net.minecraft.util.RandomSource r = c.random();
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double rx = x + 0.5 - center.x, rz = z + 0.5 - center.z;
                    double along = rx * ux + rz * uz, across = Math.abs(-rx * uz + rz * ux);
                    if (Math.abs(along) > half) continue;
                    double taper = Math.min(1, (half - Math.abs(along)) / (length * 0.18));
                    double w = halfWidth * Math.sqrt(Math.max(0, taper)) * (0.78 + 0.44 * noise(along * 0.9 + 400, 13));
                    if (across > w + 3) continue;
                    int top = c.top(x, z), ground = Math.max(c.ground(x, z), c.minY() + 1);
                    if (across > w) {
                        // the lip: broken, the sea sealed off
                        for (int y = top; y > ground - 6 && y > c.minY(); y--) {
                            BlockState old = c.get(x, y, z);
                            if (!old.getFluidState().isEmpty()) c.set(x, y, z, OBSIDIAN);
                            else if (y > ground - 3 && r.nextFloat() < 0.4f) c.clear(x, y, z);
                        }
                        continue;
                    }
                    double k = across / Math.max(1, w);
                    int floor = c.minY() + 1 + (int) Math.round(Math.pow(k, 5) * (ground - c.minY()) * 0.9);
                    for (int y = Math.max(top, ground); y > floor; y--) c.clear(x, y, z);
                    for (int y = floor; y <= lavaY; y++) c.set(x, y, z, LAVA);
                    if (floor < lavaY && r.nextFloat() < 0.025f) {
                        // broken rock floating on the lava
                        c.set(x, lavaY + 1, z, r.nextBoolean() ? BLACKSTONE : BASALT);
                    }
                    // the deep walls glow
                    if (k > 0.85 && floor < 40) c.set(x, floor - 1, z, MAGMA);
                }
            }
        };
    }

    static final BlockState ROOT_WOOD = Blocks.DARK_OAK_WOOD.defaultBlockState();
    static final BlockState ROOT_MANGROVE = Blocks.MANGROVE_WOOD.defaultBlockState();
    static final BlockState ROOT_TIP = Blocks.MANGROVE_ROOTS.defaultBlockState();
    static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();

    /**
     * Giant roots built into the world for good: solid bark, streaked with mangrove wood, moss on their backs and a
     * tangle of roots at their thin ends; where they leave the ground it is broken into rooted dirt.
     */
    public static ChunkShaper roots(java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> roots) {
        return c -> {
            int x0 = c.minX(), z0 = c.minZ();
            for (var root : roots) {
                if (root.maxX() < x0 || root.minX() > x0 + 16 || root.maxZ() < z0 || root.minZ() > z0 + 16) continue;
                int n = 28;
                Vec3[] pts = root.samples(n);
                // each piece of the root only looks at the blocks around itself
                for (int i = 0; i < n; i++) {
                    Vec3 a = pts[i], b = pts[i + 1];
                    double ta = i / (double) n, tb = (i + 1) / (double) n;
                    double reach = root.radius(ta) + 1.2;
                    int xa = Math.max(x0, (int) Math.floor(Math.min(a.x, b.x) - reach)), xb = Math.min(x0 + 15, (int) Math.ceil(Math.max(a.x, b.x) + reach));
                    int za = Math.max(z0, (int) Math.floor(Math.min(a.z, b.z) - reach)), zb = Math.min(z0 + 15, (int) Math.ceil(Math.max(a.z, b.z) + reach));
                    int ya = Math.max(c.minY(), (int) Math.floor(Math.min(a.y, b.y) - reach)), yb = Math.min(c.maxY() - 1, (int) Math.ceil(Math.max(a.y, b.y) + reach));
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
                                BlockState old = c.get(x, y, z);
                                if (d > r) {
                                    // where the root breaks the ground, the earth around it is torn and rooted
                                    if (WreckChunk.terrain(old) && c.random().nextFloat() < 0.5f) c.set(x, y, z, ROOTED_DIRT);
                                    continue;
                                }
                                if (old.is(Blocks.DARK_OAK_WOOD) || old.is(Blocks.MANGROVE_WOOD) || old.is(Blocks.MOSS_BLOCK)) continue;
                                if (u > 0.86) {
                                    c.set(x, y, z, ROOT_TIP);
                                    continue;
                                }
                                boolean back = py > a.y + dy * t + r * 0.55;
                                float roll = c.random().nextFloat();
                                c.set(x, y, z, back && roll < 0.55f ? MOSS : roll < 0.22f ? ROOT_MANGROVE : ROOT_WOOD);
                            }
                        }
                    }
                }
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

    /** {@link #freeze(Vec3, double)}, sparing the ground within {@code spare} of the centre. */
    public static ChunkShaper freeze(Vec3 center, double radius, double spare) {
        return c -> {
            net.minecraft.util.RandomSource r = c.random();
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z;
                    if (dx * dx + dz * dz > radius * radius || dx * dx + dz * dz < spare * spare) continue;
                    int top = c.top(x, z), ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    boolean surface = true;
                    for (int y = top; y > ground; y--) {
                        BlockState old = c.get(x, y, z);
                        if (old.isAir()) continue;
                        if (old.is(Blocks.LAVA)) c.set(x, y, z, OBSIDIAN);
                        else if (!old.getFluidState().isEmpty()) {
                            c.set(x, y, z, surface ? BLUE_ICE : y > ground + 3 ? ICE : PACKED_ICE);
                            surface = false;
                        } else if (old.is(net.minecraft.tags.BlockTags.LOGS)) c.set(x, y, z, PACKED_ICE);
                        else if (old.is(net.minecraft.tags.BlockTags.LEAVES)) c.set(x, y, z, r.nextFloat() < 0.45f ? ICE : AIR);
                        else if (old.canBeReplaced() || !old.blocksMotion()) c.clear(x, y, z);
                        else c.set(x, y, z, r.nextFloat() < 0.5f ? PACKED_ICE : ICE);
                    }
                    // the ground glazed in snow and ice
                    float roll = r.nextFloat();
                    if (WreckChunk.terrain(c.get(x, ground, z))) {
                        c.set(x, ground, z, roll < 0.45f ? SNOW : roll < 0.8f ? PACKED_ICE : BLUE_ICE);
                        if (r.nextFloat() < 0.6f) c.set(x, ground - 1, z, PACKED_ICE);
                    }
                    // jagged spikes of ice
                    if (r.nextFloat() < 0.004f) {
                        int h = 5 + r.nextInt(14);
                        for (int y = ground + 1; y <= ground + h; y++) c.set(x, y, z, PACKED_ICE);
                    }
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
            double[] k = new double[1];
            int bottom = c.minY() + 6;
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
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
                    var seg = cracks.at(dx, dz, Double.MAX_VALUE, k);
                    if (seg != null) {
                        double cf = ground - seg.depth() * (0.4 + 0.6 * k[0]) * fade;
                        floor = Double.isNaN(floor) ? cf : Math.min(floor, cf);
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
            double[] k = new double[1];
            for (int x = c.minX(); x < c.minX() + 16; x++) {
                for (int z = c.minZ(); z < c.minZ() + 16; z++) {
                    var seg = network.at(x + 0.5 - center.x, z + 0.5 - center.z, Double.MAX_VALUE, k);
                    if (seg == null) continue;
                    int ground = c.ground(x, z);
                    if (ground <= c.minY()) continue;
                    int bottom = Math.max(c.minY() + 1, ground - (int) Math.round(seg.depth() * (0.35 + 0.65 * k[0])));
                    for (int y = c.top(x, z); y > bottom + 2; y--) c.clear(x, y, z);
                    for (int y = bottom; y <= bottom + 2; y++) c.set(x, y, z, LAVA);
                    for (int y = Math.max(c.minY(), bottom - 2); y < bottom; y++) c.set(x, y, z, seared(c.random().nextFloat()));
                    if (k[0] > 0.3) continue;
                    // the lips: the ground beside the crack burnt
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx < c.minX() || nx > c.minX() + 15 || nz < c.minZ() || nz > c.minZ() + 15) continue;
                            if (network.at(nx + 0.5 - center.x, nz + 0.5 - center.z, Double.MAX_VALUE, null) != null) continue;
                            int g = c.ground(nx, nz);
                            if (g <= c.minY() || !c.get(nx, g + 1, nz).getFluidState().isEmpty()) continue;
                            for (int y = c.top(nx, nz); y > g; y--) c.clear(nx, y, nz);
                            c.set(nx, g, nz, seared(c.random().nextFloat()));
                        }
                    }
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
}
