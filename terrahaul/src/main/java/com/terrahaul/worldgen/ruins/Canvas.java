package com.terrahaul.worldgen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A ruin under construction in its own canonical frame: {@code u} runs along the front wall,
 * {@code v} runs from the front (facing the trail, or the open sea for sunken ruins) to the back,
 * and {@code y} is the height above the floor course. Facings and axes are canonical too
 * ({@link RuinCell#NORTH} means "towards the front", an x axis means "along u").
 * <p>
 * Planners draw here as if every building faced north; {@link #toPlan} then rotates and mirrors
 * the drawing into world space with a {@link Frame}, converting facings, axes, vine faces and door
 * hinges, so hand-designed footprints can face any direction.
 */
final class Canvas {
    final int minU;
    final int minV;
    final int sizeU;
    final int sizeV;
    final int minY;
    final int sizeY;
    private final int[] cells;
    private final int[] fill;
    final List<RuinPlan.Decal> decals = new ArrayList<>();
    final List<RuinPlan.Extra> extras = new ArrayList<>();

    Canvas(int minU, int minV, int maxU, int maxV, int minY, int maxY) {
        this.minU = minU;
        this.minV = minV;
        this.sizeU = maxU - minU + 1;
        this.sizeV = maxV - minV + 1;
        this.minY = minY;
        this.sizeY = maxY - minY + 1;
        this.cells = new int[sizeU * sizeV * sizeY];
        this.fill = new int[sizeU * sizeV];
    }

    boolean inside(int u, int y, int v) {
        return u >= minU && u < minU + sizeU && v >= minV && v < minV + sizeV && y >= minY && y < minY + sizeY;
    }

    private int index(int u, int y, int v) {
        return ((u - minU) * sizeV + (v - minV)) * sizeY + (y - minY);
    }

    int get(int u, int y, int v) {
        return inside(u, y, v) ? cells[index(u, y, v)] : 0;
    }

    RuinPart part(int u, int y, int v) {
        return RuinCell.part(get(u, y, v));
    }

    void set(int u, int y, int v, int cell) {
        if (inside(u, y, v)) {
            cells[index(u, y, v)] = cell;
        }
    }

    void set(int u, int y, int v, RuinPart part) {
        set(u, y, v, RuinCell.of(part));
    }

    void set(int u, int y, int v, RuinPart part, int dir) {
        set(u, y, v, RuinCell.of(part, dir));
    }

    /** Sets the cell only if it is still empty. */
    void add(int u, int y, int v, int cell) {
        if (get(u, y, v) == 0) {
            set(u, y, v, cell);
        }
    }

    boolean empty(int u, int y, int v) {
        int c = get(u, y, v);
        return c == 0 || RuinCell.part(c) == RuinPart.AIR;
    }

    boolean structural(int u, int y, int v) {
        int c = get(u, y, v);
        return c != 0 && RuinCell.part(c).structural;
    }

    int fill(int u, int v) {
        return u >= minU && u < minU + sizeU && v >= minV && v < minV + sizeV ? fill[(u - minU) * sizeV + (v - minV)] : 0;
    }

    void setFill(int u, int v, int cell) {
        if (u >= minU && u < minU + sizeU && v >= minV && v < minV + sizeV) {
            fill[(u - minU) * sizeV + (v - minV)] = cell;
        }
    }

    void decal(int u, int v, int dy, int cell) {
        decals.add(new RuinPlan.Decal(u, v, dy, cell));
    }

    /** Converts the drawing into a world-space plan whose floor course lies at {@code floorY}. */
    RuinPlan toPlan(Frame f, int mode, int floorY, RuinMaterials materials, boolean underwater) {
        int x0 = Math.min(f.x(minU, minV), f.x(minU + sizeU - 1, minV + sizeV - 1));
        int x1 = Math.max(f.x(minU, minV), f.x(minU + sizeU - 1, minV + sizeV - 1));
        int z0 = Math.min(f.z(minU, minV), f.z(minU + sizeU - 1, minV + sizeV - 1));
        int z1 = Math.max(f.z(minU, minV), f.z(minU + sizeU - 1, minV + sizeV - 1));
        RuinPlan plan = new RuinPlan(mode, x0, floorY + minY, z0, x1 - x0 + 1, sizeY, z1 - z0 + 1, floorY, materials, underwater);
        for (int u = minU; u < minU + sizeU; u++) {
            for (int v = minV; v < minV + sizeV; v++) {
                int x = f.x(u, v);
                int z = f.z(u, v);
                int fc = fill(u, v);
                if (fc != 0) {
                    plan.setFill(x, z, f.cell(fc));
                }
                for (int y = minY; y < minY + sizeY; y++) {
                    int c = cells[index(u, y, v)];
                    if (c != 0) {
                        plan.set(x, floorY + y, z, f.cell(c));
                    }
                }
            }
        }
        for (RuinPlan.Decal d : decals) {
            plan.decals.add(new RuinPlan.Decal(f.x(d.x(), d.z()), f.z(d.x(), d.z()), d.dy(), f.cell(d.cell())));
        }
        for (RuinPlan.Extra e : extras) {
            plan.extras.add(new RuinPlan.Extra(f.x(e.x(), e.z()), e.groundRelative() ? e.y() : floorY + e.y(), f.z(e.x(), e.z()),
                e.groundRelative(), e.lootTable(), e.seed(), e.sign()));
        }
        plan.anchorX = f.x(0, 0);
        plan.anchorZ = f.z(0, 0);
        return plan;
    }

    /**
     * Maps canonical (u, v) to world (x, z): {@code (u, v) -> origin + u * U + v * V}, where U and V
     * are perpendicular unit vectors along the world axes. A left-handed pair mirrors the drawing.
     */
    record Frame(int ox, int oz, int ux, int uz, int vx, int vz) {
        int x(int u, int v) {
            return ox + u * ux + v * vx;
        }

        int z(int u, int v) {
            return oz + u * uz + v * vz;
        }

        boolean mirrored() {
            return ux * vz - uz * vx < 0;
        }

        /** World direction of a canonical direction. */
        int dir(int d) {
            int du = RuinCell.DX[d];
            int dv = RuinCell.DZ[d];
            return RuinCell.dirOf(du * ux + dv * vx, du * uz + dv * vz);
        }

        /** World axis of a canonical axis. */
        int axis(int a) {
            if (a == RuinCell.AXIS_Y) {
                return a;
            }
            boolean alongU = a == RuinCell.AXIS_X;
            int wx = alongU ? ux : vx;
            return wx != 0 ? RuinCell.AXIS_X : RuinCell.AXIS_Z;
        }

        int cell(int c) {
            RuinPart part = RuinCell.part(c);
            int out = RuinCell.withDir(c, dir(RuinCell.dir(c)));
            out = RuinCell.withAxis(out, axis(RuinCell.axis(c)));
            if (part == RuinPart.VINE || part == RuinPart.GLOW_LICHEN) {
                int faces = RuinCell.aux(c);
                int world = faces & 0x30;
                for (int d = 0; d < 4; d++) {
                    if ((faces & 1 << d) != 0) {
                        world |= 1 << dir(d);
                    }
                }
                out = RuinCell.withAux(out, world);
            } else if (part == RuinPart.DOOR && mirrored()) {
                // aux bit 0 is the hinge side, which a mirror image swaps
                out = RuinCell.withAux(out, RuinCell.aux(c) ^ 1);
            }
            return out;
        }
    }
}
