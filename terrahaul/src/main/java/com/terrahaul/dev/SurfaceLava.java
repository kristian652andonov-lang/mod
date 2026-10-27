package com.terrahaul.dev;

import com.terrahaul.worldgen.terrain.TerrainModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * AutoTest check of lava near the surface, inside volcanoes and outside them.
 * <p>
 * Every lava block of the area (source or flowing) no deeper than 48 blocks under the ground of
 * its column ({@code OCEAN_FLOOR}: the top block that is not a fluid or a plant) is counted when
 * it lies open to the sky (at or above that height, so only fluids, plants or air are over it) or
 * when daylight reaches it (sky light of at least 1: within 15 blocks of an opening to the sky,
 * as at a cave mouth or in a hillside pocket). Columns where the terrain
 * model has a volcano ({@code volcano > 0}, the same test the volcano features use to skip a
 * chunk) are counted apart: crater lava lakes belong there. Outside volcanoes no lava should be
 * open to the sky or lit by it; a few positions are logged. Totals over all areas are kept for
 * {@link #summary}.
 */
final class SurfaceLava {
    /** Deepest lava under the ground of its column that is looked at. */
    private static final int DEPTH = 48;
    private static final long[] TOTALS = new long[4];
    private static final List<String> EXAMPLES = new ArrayList<>();

    private SurfaceLava() {
    }

    static void check(ServerLevel level, TerrainModel model, int cx, int cz, int radius, List<String> report) {
        long t0 = System.nanoTime();
        // open in volcanoes, open outside, lit in volcanoes, lit outside
        long[] counts = new long[4];
        List<String> examples = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                var sections = chunk.getSections();
                int bx = chunk.getPos().getMinBlockX();
                int bz = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.getFluidState().is(FluidTags.LAVA))) {
                        continue;
                    }
                    int y0 = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = section.getBlockState(x, y, z);
                                if (!st.getFluidState().is(FluidTags.LAVA)) {
                                    continue;
                                }
                                int wx = bx + x;
                                int wy = y0 + y;
                                int wz = bz + z;
                                pos.set(wx, wy, wz);
                                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, wx, wz);
                                if (wy < floor - DEPTH) {
                                    continue;
                                }
                                boolean open = wy >= floor;
                                boolean lit = level.getBrightness(LightLayer.SKY, pos) > 0;
                                if (!open && !lit) {
                                    continue;
                                }
                                boolean volcano = model != null && model.sample(wx & ~3, wz & ~3).volcano > 0.0;
                                if (open) {
                                    counts[volcano ? 0 : 1]++;
                                }
                                if (lit) {
                                    counts[volcano ? 2 : 3]++;
                                }
                                if (!volcano && examples.size() < 6) {
                                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                                    examples.add(String.format(Locale.ROOT, "%d %d %d (%s, %s, %d below the top, sky light %d)", wx, wy, wz,
                                        st.getFluidState().isSource() ? "source" : "flowing", open ? "open" : "lit", top - wy,
                                        level.getBrightness(LightLayer.SKY, pos)));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (int k = 0; k < 4; k++) {
            TOTALS[k] += counts[k];
        }
        if (EXAMPLES.size() < 12) {
            EXAMPLES.addAll(examples.subList(0, Math.min(examples.size(), 12 - EXAMPLES.size())));
        }
        report.add(String.format(Locale.ROOT, "  lava near the surface (%.0f ms): open to the sky %d in volcanoes, %d outside; lit by daylight %d in volcanoes, %d outside%s",
            (System.nanoTime() - t0) / 1e6, counts[0], counts[1], counts[2], counts[3], examples.isEmpty() ? "" : "; outside e.g. " + String.join("; ", examples)));
    }

    /** Totals over every area checked so far. */
    static void summary(List<String> report) {
        report.add(String.format(Locale.ROOT, "lava near the surface, all areas: open to the sky %d in volcanoes, %d outside; lit by daylight %d in volcanoes, %d outside",
            TOTALS[0], TOTALS[1], TOTALS[2], TOTALS[3]));
        if (!EXAMPLES.isEmpty()) {
            report.add("  outside volcanoes e.g. " + String.join("; ", EXAMPLES));
        }
    }
}
