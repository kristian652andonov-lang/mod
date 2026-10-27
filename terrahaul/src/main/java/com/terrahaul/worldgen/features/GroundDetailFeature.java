package com.terrahaul.worldgen.features;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;

/**
 * {@code terrahaul:ground_detail}: places one pebble or knee-high rock like
 * {@code minecraft:simple_block}, except that a stone coming to rest on sand takes that sand's
 * sandstone (see {@link SandRock}). A block state provider cannot see the ground it is used on,
 * so the choice has to be made here, where the feature knows what lies under the position.
 * The ground details' patches use it in every world.
 */
public final class GroundDetailFeature extends Feature<SimpleBlockConfiguration> {
    public GroundDetailFeature(Codec<SimpleBlockConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleBlockConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockState state = context.config().toPlace().getState(context.random(), origin);
        state = SandRock.detail(state, level.getBlockState(origin.below()));
        if (!state.canSurvive(level, origin)) {
            return false;
        }
        level.setBlock(origin, state, 2);
        return true;
    }
}
