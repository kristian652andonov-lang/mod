package com.fantasyweapons.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

/**
 * Breaking a machine shows a small block's worth of debris for each of its six blocks (each of them breaks in turn). The
 * game scatters debris over every box of a block's outline, at least eight a box, and the machines' outlines follow
 * their models in dozens of small boxes, so a broken machine used to burst into hundreds.
 */
public final class MachineBreakParticles implements IClientBlockExtensions {
    private static final int PER_BLOCK = 14;

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        if (!(level instanceof ClientLevel client)) return false;
        var random = level.random;
        for (int i = 0; i < PER_BLOCK; i++) {
            double fx = random.nextDouble(), fy = random.nextDouble(), fz = random.nextDouble();
            double x = pos.getX() + fx, y = pos.getY() + fy, z = pos.getZ() + fz;
            manager.add(new TerrainParticle(client, x, y, z, (fx - 0.5) * 0.4, (fy - 0.5) * 0.4 + 0.1, (fz - 0.5) * 0.4, state, pos)
                    .updateSprite(state, pos));
        }
        return true;
    }
}
