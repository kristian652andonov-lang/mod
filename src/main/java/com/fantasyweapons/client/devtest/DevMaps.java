package com.fantasyweapons.client.devtest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * DEVELOPMENT ONLY: a top-down picture of the world from the server's own blocks (like a map), shaded by height, so the
 * destruction of the ultimate weapons can be checked without a camera. Unloaded chunks are drawn black.
 */
public final class DevMaps {
    private DevMaps() {
    }

    public static void dump(ServerLevel level, int cx, int cz, int radius, File out) {
        int size = radius * 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] prev = new int[size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int x = cx - radius + i, z = cz - radius + j;
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    img.setRGB(i, j, 0);
                    continue;
                }
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockState s = level.getBlockState(m.set(x, h, z));
                MapColor mc = s.getMapColor(level, m);
                int rgb = mc == MapColor.NONE ? 0x101010 : mc.col;
                // height shading: brighter up, darker down (relative to sea level), with a north-south relief
                float k = Math.max(0.25f, Math.min(1.3f, 0.75f + (h - 63) / 160f));
                if (j > 0) k *= h > prev[i] ? 1.12f : h < prev[i] ? 0.86f : 1f;
                prev[i] = h;
                int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
                img.setRGB(i, j, r << 16 | g << 8 | b);
            }
        }
        try {
            javax.imageio.ImageIO.write(img, "png", out);
        } catch (java.io.IOException e) {
            com.fantasyweapons.FantasyWeapons.LOGGER.error("map dump failed", e);
        }
    }
}
