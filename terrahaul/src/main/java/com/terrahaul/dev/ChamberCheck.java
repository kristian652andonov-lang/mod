package com.terrahaul.dev;

import com.terrahaul.worldgen.features.MagmaChamberFeature;
import com.terrahaul.worldgen.terrain.MagmaChamber;
import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * AutoTest's check of the magma chamber and conduit of the volcano nearest to a generated area
 * (see {@link MagmaChamber}), wherever they lie in the area, against the generated blocks:
 * <ul>
 *   <li><b>cavity:</b> the cells the plan holds lava, air or falling lava in, and how many hold
 *       something else (rock where lava should be, say);</li>
 *   <li><b>breaches:</b> faces between a cavity cell and a cell outside the cavity that is open
 *       (air, water, a plant): lava would flow out there, or a cave opens into the chamber. Lava
 *       beside the cavity only counts above the crater floor, where it is the crater's lake;</li>
 *   <li><b>stray lava:</b> lava outside the cavity below the lowest crater floor, within the
 *       chamber's box and a margin of sixteen blocks around it, and how much of it touches air: a
 *       leak, or a vanilla lava pool or spring of the caves around the volcano;</li>
 *   <li><b>holes in the wall:</b> open cells (air, water, plants) within the wall's thickness of the
 *       cavity, under the ground of their column;</li>
 *   <li><b>caves at the wall:</b> how often a cave's air touches the outside of the wall, which
 *       shows the wall sealing caves that ran into it;</li>
 *   <li>the cost of the feature in the chunks that built a chamber.</li>
 * </ul>
 */
final class ChamberCheck {
    private static final int MARGIN = 16;

    private ChamberCheck() {
    }

    static void check(ServerLevel level, TerrainModel model, int cx, int cz, int radius, List<String> report) {
        if (model == null) {
            return;
        }
        int ax0 = (cx - radius) << 4;
        int az0 = (cz - radius) << 4;
        int ax1 = ((cx + radius + 1) << 4) - 1;
        int az1 = ((cz + radius + 1) << 4) - 1;
        TerrainModel.VolcanoSite v = model.nearestVolcano((ax0 + ax1) / 2, (az0 + az1) / 2, 2000.0 + 16.0 * radius);
        if (v == null) {
            return;
        }
        MagmaChamber c = model.magmaChamber(v);
        if (!c.touches(ax0, az0, ax1, az1)) {
            return;
        }
        long t0 = System.nanoTime();
        int x0 = Math.max(ax0, c.minX - MARGIN);
        int x1 = Math.min(ax1, c.maxX + MARGIN);
        int z0 = Math.max(az0, c.minZ - MARGIN);
        int z1 = Math.min(az1, c.maxZ + MARGIN);
        int y0 = Math.max(level.getMinBuildHeight(), c.minY - MARGIN);
        int y1 = Math.min(level.getMaxBuildHeight() - 1, c.maxY + MagmaChamber.WALL + 1);
        int nx = x1 - x0 + 1;
        int ny = y1 - y0 + 1;
        int nz = z1 - z0 + 1;
        // the plan's cavity over the box, with one cell more on every side for the neighbours
        byte[] cells = new byte[(nx + 2) * (ny + 2) * (nz + 2)];
        int[] col = new int[MagmaChamber.COLUMN_OUT];
        double[] axis = new double[2];
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                c.column(x, z, col);
                for (int y = Math.max(col[MagmaChamber.FLOOR], y0 - 1); y < Math.min(col[MagmaChamber.CEILING], y1 + 2); y++) {
                    cells[index(x - x0, y - y0, z - z0, nx, ny)] = (byte) (y < c.lake ? MagmaChamber.LAVA
                        : col[MagmaChamber.UNDER_MOUTH] != 0 ? MagmaChamber.FALL : MagmaChamber.AIR);
                }
            }
        }
        for (int y = Math.max(c.top, y0 - 1); y <= Math.min(c.conduitTop, y1 + 1); y++) {
            c.axis(y, axis);
            double r = c.conduitRadius(y);
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    double dx = x + 0.5 - axis[0];
                    double dz = z + 0.5 - axis[1];
                    if (dx * dx + dz * dz < r * r) {
                        cells[index(x - x0, y - y0, z - z0, nx, ny)] = MagmaChamber.LAVA;
                    }
                }
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        int lakeKeep = c.craterFloor - 6;
        long[] expected = new long[4];
        long[] wrong = new long[4];
        int breaches = 0;
        int stray = 0;
        int strayAir = 0;
        int strayNear = 0;
        int caveFaces = 0;
        int holes = 0;
        List<String> holeExamples = new ArrayList<>();
        List<String> breachExamples = new ArrayList<>();
        List<String> wrongExamples = new ArrayList<>();
        List<String> strayExamples = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int k = cells[index(x - x0, y - y0, z - z0, nx, ny)];
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    FluidState fluid = state.getFluidState();
                    if (k != MagmaChamber.NONE) {
                        expected[k]++;
                        boolean ok = switch (k) {
                            case MagmaChamber.LAVA -> fluid.is(FluidTags.LAVA) && fluid.isSource();
                            case MagmaChamber.FALL -> fluid.is(FluidTags.LAVA) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
                            default -> state.isAir();
                        };
                        if (!ok) {
                            wrong[k]++;
                            if (wrongExamples.size() < 4) {
                                wrongExamples.add(x + " " + y + " " + z + " " + name(state) + " for " + kindName(k));
                            }
                        }
                        for (Direction d : Direction.values()) {
                            side.setWithOffset(pos, d);
                            if (cells[index(side.getX() - x0, side.getY() - y0, side.getZ() - z0, nx, ny)] != MagmaChamber.NONE) {
                                continue;
                            }
                            BlockState there = level.getBlockState(side);
                            boolean lavaThere = there.getFluidState().is(FluidTags.LAVA);
                            if (there.blocksMotion() || lavaThere && side.getY() >= lakeKeep) {
                                continue;
                            }
                            // open air over the crater's lake, at and above its surface, is the sky
                            if (k == MagmaChamber.LAVA && d == Direction.UP && c.open && y >= c.conduitTop) {
                                continue;
                            }
                            breaches++;
                            if (breachExamples.size() < 6) {
                                breachExamples.add(x + " " + y + " " + z + " " + d.getName() + " " + name(there));
                            }
                        }
                    } else {
                        if (fluid.is(FluidTags.LAVA) && y < lakeKeep) {
                            stray++;
                            if (nearCavity(cells, x - x0, y - y0, z - z0, nx, ny, nz, MagmaChamber.WALL + 2)) {
                                strayNear++;
                            }
                            boolean air = false;
                            for (Direction d : Direction.values()) {
                                air |= d != Direction.UP && level.getBlockState(side.setWithOffset(pos, d)).isAir();
                            }
                            strayAir += air ? 1 : 0;
                            if (strayExamples.size() < 6) {
                                strayExamples.add(x + " " + y + " " + z + (air ? " (beside air)" : ""));
                            }
                        }
                        boolean open = !state.blocksMotion() && !fluid.is(FluidTags.LAVA);
                        if (open && y < level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)
                            && nearCavity(cells, x - x0, y - y0, z - z0, nx, ny, nz, MagmaChamber.WALL)) {
                            holes++;
                            if (holeExamples.size() < 6) {
                                holeExamples.add(x + " " + y + " " + z + " " + name(state));
                            }
                        } else if (state.isAir() && y < lakeKeep && nearCavity(cells, x - x0, y - y0, z - z0, nx, ny, nz, MagmaChamber.WALL + 1)) {
                            caveFaces++;
                        }
                    }
                }
            }
        }
        report.add("  magma chamber: " + c.describe());
        report.add(String.format(Locale.ROOT, "    checked %d x %d x %d blocks in %.1f s: cavity cells lava %d, air %d, falling lava %d; not as planned: lava %d, air %d, falling %d%s",
            nx, ny, nz, (System.nanoTime() - t0) / 1e9, expected[MagmaChamber.LAVA], expected[MagmaChamber.AIR], expected[MagmaChamber.FALL],
            wrong[MagmaChamber.LAVA], wrong[MagmaChamber.AIR], wrong[MagmaChamber.FALL],
            wrongExamples.isEmpty() ? "" : ", e.g. " + String.join("; ", wrongExamples)));
        report.add("    breaches (cavity facing an open cell outside it): " + breaches
            + (breachExamples.isEmpty() ? "" : ", e.g. " + String.join("; ", breachExamples)));
        report.add("    lava below the crater floor (y < " + lakeKeep + ") outside the cavity: " + stray + ", of it beside air: " + strayAir
            + ", within " + (MagmaChamber.WALL + 2) + " blocks of the cavity (in or against the wall): " + strayNear
            + (strayExamples.isEmpty() ? "" : ", e.g. " + String.join("; ", strayExamples)));
        report.add("    open cells (air, water, plants) inside the wall, under the ground: " + holes
            + (holeExamples.isEmpty() ? "" : ", e.g. " + String.join("; ", holeExamples)));
        report.add("    air cells (caves) right outside the wall: " + caveFaces);
    }

    /** The cost of the feature over the whole run, for the end of the report. */
    static void cost(List<String> report) {
        long chunks = MagmaChamberFeature.CHUNKS.sum();
        long calls = MagmaChamberFeature.CALLS.sum();
        report.add(String.format(Locale.ROOT, "magma chamber feature: %d chunks built a chamber or conduit, %.1f ms/chunk, %d blocks placed (%.0f/chunk); "
                + "all %d calls %.3f ms/call (the others return after the first test)",
            chunks, MagmaChamberFeature.NANOS.sum() / 1e6 / Math.max(1, chunks), MagmaChamberFeature.BLOCKS.sum(),
            MagmaChamberFeature.BLOCKS.sum() / (double) Math.max(1, chunks), calls, MagmaChamberFeature.CALL_NANOS.sum() / 1e6 / Math.max(1, calls))
            + "; plants of finished neighbours cleared across chunk borders: " + MagmaChamberFeature.PLANTS.sum());
    }

    private static boolean nearCavity(byte[] cells, int ix, int iy, int iz, int nx, int ny, int nz, int reach) {
        // only cells just outside the wall: within reach of the cavity, but not within the wall itself
        boolean within = false;
        for (int dy = -reach; dy <= reach && !within; dy++) {
            for (int dz = -reach; dz <= reach && !within; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int qx = ix + dx;
                    int qy = iy + dy;
                    int qz = iz + dz;
                    if (qx < -1 || qy < -1 || qz < -1 || qx > nx || qy > ny || qz > nz) {
                        continue;
                    }
                    if (cells[index(qx, qy, qz, nx, ny)] != MagmaChamber.NONE) {
                        within = true;
                        break;
                    }
                }
            }
        }
        return within;
    }

    /** Index into the box with its border of one cell (coordinates from -1 to n). */
    private static int index(int ix, int iy, int iz, int nx, int ny) {
        return ((iz + 1) * (ny + 2) + iy + 1) * (nx + 2) + ix + 1;
    }

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static String kindName(int k) {
        return k == MagmaChamber.LAVA ? "lava" : k == MagmaChamber.FALL ? "falling lava" : "air";
    }
}
