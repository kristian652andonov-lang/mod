package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.weapons.ultimate.CrackNetwork;
import com.fantasyweapons.world.wreck.WorldWreck;
import com.fantasyweapons.world.wreck.WreckShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;

/**
 * The world's destruction, run on chunks far from the other tests (it rewrites whole chunk columns): each test lays a
 * known landscape into its own chunk first.
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WreckGameTests {
    private static final int GROUND = 80;

    private WreckGameTests() {
    }

    /**
     * A crack running across a plate the earthquake heaved up is open to the sky all along its middle: the plate's
     * raised top does not roof it over.
     */
    @GameTest(template = "arena", batch = "wreck")
    public static void cracksOnRaisedGroundOpenToTheSky(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        LevelChunk chunk = flatChunk(level, 2900, 2900, GROUND, false);
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        // the quake's centre 60 blocks west of the chunk, on the crack's line; one plate, raised 8 blocks everywhere
        Vec3 center = new Vec3(minX - 60, GROUND, minZ + 8);
        CrackNetwork crack = CrackNetwork.straight(40, 0, 100, 0, 2, 40);
        WorldWreck.rewrite(level, chunk, 1L, WreckShapes.quake(center, 300, 0, new double[][]{{0, 0}}, new int[]{8}, crack));
        int raised = GROUND + 8;
        // well clear of the crack and its heaved-up lips
        if (top(level, minX + 15, minZ + 15) != raised) h.fail("the plate was not raised: its top is at " + top(level, minX + 15, minZ + 15));
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ + 7; z <= minZ + 8; z++) {
                int top = top(level, x, z);
                if (top > raised - 6) h.fail("the crack is roofed over at " + x + ", " + z + ": its column is solid up to y " + top);
            }
        }
        h.succeed();
    }

    /**
     * Water at the edge of a hole the destruction opened is sealed off with the ground under it, so it does not stream
     * into the hole (or the void) - and the sea behind the seal stays as it was, still.
     */
    @GameTest(template = "arena", batch = "wreck")
    public static void cutWaterIsSealedOff(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        LevelChunk chunk = flatChunk(level, 2903, 2900, GROUND, true);
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        // the east half of the chunk dug out of the sea floor, the sea over it too
        WorldWreck.rewrite(level, chunk, 1L, c -> {
            for (int x = minX + 8; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    for (int y = c.top(x, z); y > GROUND - 20; y--) c.clear(x, y, z);
                }
            }
        });
        BlockPos edge = new BlockPos(minX + 7, GROUND + 2, minZ + 5), behind = edge.west();
        if (!level.getBlockState(edge).is(Blocks.STONE)) h.fail("the water at the hole's edge was not sealed off with the sea floor's stone: " + level.getBlockState(edge));
        if (!level.getBlockState(behind).is(Blocks.WATER)) h.fail("the sea behind the seal is gone: " + level.getBlockState(behind));
        if (level.getFluidTicks().hasScheduledTick(behind, Fluids.WATER)) h.fail("the sea behind the seal was woken to flow");
        // the sea's surface, open to the sky over the hole's rim, is left open
        if (!level.getBlockState(new BlockPos(minX + 3, GROUND + 5, minZ + 5)).is(Blocks.WATER)) h.fail("the sea's surface was sealed over");
        h.succeed();
    }

    /**
     * The Great Slash's cut and the canyon it tears along the ground meet cleanly: no thin blade of rock is left between
     * them, and no rubble hangs in the air of the cut - no column holds a sliver of one to three blocks with air both
     * above and below it.
     */
    @GameTest(template = "arena", batch = "wreck")
    public static void theSlashLeavesNoLineInsideItsCut(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        LevelChunk chunk = flatChunk(level, 2906, 2900, GROUND, false);
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        // cast from 150 blocks north of the chunk, looking south, as the ability casts it: almost straight down from high on
        // the left (so the cut, leaning, meets the ground 13 blocks west of the caster - in the middle of the chunk)
        Vec3 eye = new Vec3(minX + 21.5, GROUND + 41.6, minZ - 150), look = new Vec3(0, 0, 1);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(look).normalize();
        double tilt = Math.toRadians(-72);
        Vec3 blade = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt))).normalize();
        WorldWreck.rewrite(level, chunk, 7L, WreckShapes.all(WreckShapes.slab(eye, look, blade, 384, 96, 10),
                WreckShapes.slashScar(eye, look, blade, 384, 20, 34, 7L)));
        int cut = 0;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                int run = 0;
                boolean airAbove = false;
                for (int y = GROUND + 4; y > level.getMinBuildHeight(); y--) {
                    boolean air = level.getBlockState(new BlockPos(x, y, z)).isAir();
                    if (air && y <= GROUND) cut++;
                    if (!air) {
                        run++;
                        continue;
                    }
                    if (airAbove && run >= 1 && run <= 3) {
                        h.fail("a sliver " + run + " thick hangs in the cut at " + x + ", " + (y + 1) + ", " + z);
                        return;
                    }
                    airAbove = true;
                    run = 0;
                }
            }
        }
        if (cut < 500) h.fail("the slash hardly cut the chunk: " + cut + " blocks");
        h.succeed();
    }

    /**
     * A rewrite may fill a section with many kinds of block (its palette growing past what it held): the rewrite takes
     * them all, and the chunk is left whole.
     */
    @GameTest(template = "arena", batch = "wreck")
    public static void aRewriteCanFillASectionWithManyBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        LevelChunk chunk = flatChunk(level, 2909, 2900, GROUND, false);
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        BlockState[] kinds = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().map(net.minecraft.world.level.block.Block::defaultBlockState)
                .filter(s -> !s.hasBlockEntity() && s.getFluidState().isEmpty() && s.isCollisionShapeFullBlock(level, BlockPos.ZERO)
                        && s.getBlock().defaultDestroyTime() >= 0).limit(40).toArray(BlockState[]::new);
        WorldWreck.rewrite(level, chunk, 1L, c -> {
            for (int i = 0; i < kinds.length; i++) c.set(minX + (i & 15), GROUND - 2, minZ + (i >> 4), kinds[i]);
        });
        for (int i = 0; i < kinds.length; i++) {
            BlockState at = level.getBlockState(new BlockPos(minX + (i & 15), GROUND - 2, minZ + (i >> 4)));
            if (at != kinds[i]) h.fail("block " + i + " is " + at + ", not " + kinds[i]);
        }
        if (!level.getBlockState(new BlockPos(minX + 15, GROUND - 2, minZ + 15)).is(Blocks.STONE)) h.fail("the rest of the section was not kept");
        h.succeed();
    }

    /** The highest block of a column. */
    private static int top(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getMaxBuildHeight() - 1, z);
        while (p.getY() > level.getMinBuildHeight() && level.getBlockState(p).isAir()) p.move(0, -1, 0);
        return p.getY();
    }

    /** A chunk laid flat: stone up to {@code ground}, air above (under five blocks of sea if {@code sea}). */
    private static LevelChunk flatChunk(ServerLevel level, int cx, int cz, int ground, boolean sea) {
        LevelChunk chunk = level.getChunk(cx, cz);
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            var section = chunk.getSection(chunk.getSectionIndex(y));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState s = y <= ground ? stone : sea && y <= ground + 5 ? water : air;
                    section.setBlockState(x, y & 15, z, s);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
        return chunk;
    }
}
