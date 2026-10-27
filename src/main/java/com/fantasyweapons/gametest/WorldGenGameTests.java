package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.world.gen.FittedHeightPlacement;
import com.fantasyweapons.world.gen.WorldShape;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** The legendary ores' height: vanilla's in worlds of the vanilla height, moved with the ground in taller ones. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldGenGameTests {
    private static final String ARENA = "arena";
    /** Aetherite's: Y 70 to 160, under ground that lies at Y 105 in vanilla worlds. */
    private static final HeightProvider HEIGHT = UniformHeight.of(VerticalAnchor.absolute(70), VerticalAnchor.absolute(160));
    private static final int GROUND = 105;

    private WorldGenGameTests() {
    }

    /** Generation in a world built from {@code minY}, {@code height} high, with its ground everywhere at {@code ground}. */
    private static PlacementContext context(ServerLevel level, int minY, int height, int ground) {
        return new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty()) {
            @Override
            public int getMinBuildHeight() {
                return minY;
            }

            @Override
            public int getMinGenY() {
                return minY;
            }

            @Override
            public int getGenDepth() {
                return height;
            }

            @Override
            public int getHeight(Heightmap.Types type, int x, int z) {
                return ground + 1;
            }
        };
    }

    /** At the vanilla height the ores lie exactly where vanilla's height range puts them, from the same random draws. */
    @GameTest(template = ARENA)
    public static void vanillaHeightUnchanged(GameTestHelper h) {
        PlacementContext context = context(h.getLevel(), -64, 384, 300);
        BlockPos pos = h.absolutePos(BlockPos.ZERO);
        for (long seed = 0; seed < 500; seed++) {
            RandomSource a = RandomSource.create(seed), b = RandomSource.create(seed);
            List<BlockPos> fitted = new FittedHeightPlacement(HEIGHT, GROUND).getPositions(context, a, pos).toList();
            List<BlockPos> vanilla = HeightRangePlacement.of(HEIGHT).getPositions(context, b, pos).toList();
            h.assertTrue(fitted.equals(vanilla), "seed " + seed + ": " + fitted + " where vanilla has " + vanilla);
            h.assertTrue(a.nextLong() == b.nextLong(), "seed " + seed + ": the draws after it differ from vanilla's");
        }
        h.succeed();
    }

    /** The descriptions name heights in a vanilla world and depths under the ground in a taller one; the test world is of the vanilla height. */
    @GameTest(template = ARENA)
    public static void descriptionsFitTheWorld(GameTestHelper h) {
        h.assertFalse(WorldShape.fitted(h.getLevel().getServer()), "the vanilla-height test world counts as a taller one");
        h.assertTrue(Material.FROSTHEART.foundIn(false).equals("Frozen peaks and ice spikes, between Y 60 and 200"), Material.FROSTHEART.foundIn(false));
        h.assertTrue(Material.FROSTHEART.foundIn(true).equals("Frozen peaks and ice spikes, up to 65 blocks under the ground"), Material.FROSTHEART.foundIn(true));
        h.assertTrue(Material.AETHERITE.foundIn(true).equals(Material.AETHERITE.foundIn(false)), "a description without heights changed");
        h.succeed();
    }

    /** In a taller world the ores keep as deep under the ground as in vanilla, wherever the ground lies; never outside the world. */
    @GameTest(template = ARENA)
    public static void tallWorldFollowsGround(GameTestHelper h) {
        BlockPos pos = h.absolutePos(BlockPos.ZERO);
        for (int ground : new int[]{65, 400, 1500}) {
            PlacementContext context = context(h.getLevel(), -512, 2544, ground);
            for (long seed = 0; seed < 500; seed++) {
                List<BlockPos> fitted = new FittedHeightPlacement(HEIGHT, GROUND).getPositions(context, RandomSource.create(seed), pos).toList();
                int vanilla = HEIGHT.sample(RandomSource.create(seed), context);
                h.assertTrue(fitted.size() == 1 && fitted.getFirst().getY() == vanilla + ground - GROUND && fitted.getFirst().getX() == pos.getX()
                        && fitted.getFirst().getZ() == pos.getZ(), "ground " + ground + ", seed " + seed + ": " + fitted + " for vanilla Y " + vanilla);
            }
        }
        // ground so low the moved height falls below the bottom of the world: no vein rather than one on the floor
        PlacementContext low = context(h.getLevel(), -512, 2544, -505);
        for (long seed = 0; seed < 100; seed++) {
            List<BlockPos> fitted = new FittedHeightPlacement(HEIGHT, GROUND).getPositions(low, RandomSource.create(seed), pos).toList();
            h.assertTrue(fitted.stream().allMatch(p -> p.getY() >= -512), "a vein below the world: " + fitted);
        }
        h.succeed();
    }
}
