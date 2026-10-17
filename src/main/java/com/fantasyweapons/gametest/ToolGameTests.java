package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.tool.AreaMining;
import com.fantasyweapons.tool.ToolKind;
import com.fantasyweapons.tool.ToolUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The legendary tools: the square they dig (3x3 new, 13x13 fully upgraded), what it leaves alone, where its drops go,
 * and the Fortune and Efficiency their upgrades give. Run in the arena (stone floor at relative y 1, 8 high).
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolGameTests {
    private static final String ARENA = "arena";
    /** A stone wall across the arena at relative z 16, x 8 to 16 and y 2 to 6, faced from z 13.5. */
    private static final int WALL_Z = 16;

    private ToolGameTests() {
    }

    private static ItemStack tool(ToolKind kind, ToolUpgrades upgrades) {
        ItemStack stack = new ItemStack(ModItems.tool(Material.VOID_CRYSTAL, kind));
        stack.set(ModComponents.TOOL_UPGRADES.get(), upgrades);
        return stack;
    }

    private static void wall(GameTestHelper h, BlockState state) {
        for (int x = 8; x <= 16; x++) for (int y = 2; y <= 6; y++) h.setBlock(new BlockPos(x, y, WALL_Z), state);
    }

    /** A player at the wall, looking straight at it (at relative block 12, 3), holding {@code stack}. */
    private static ServerPlayer atWall(GameTestHelper h, ItemStack stack) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 13.5), 0);
        p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p;
    }

    private static void dig(GameTestHelper h, ServerPlayer p, BlockPos relative) {
        p.gameMode.destroyBlock(h.absolutePos(relative));
    }

    private static int count(GameTestHelper h, Item item) {
        int n = 0;
        AABB all = h.getBounds().inflate(1);
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, all)) if (e.getItem().is(item)) n += e.getItem().getCount();
        return n;
    }

    @GameTest(template = ARENA)
    public static void aNewPickaxeDigsThreeByThreeStandingOnTheFeet(GameTestHelper h) {
        wall(h, Blocks.STONE.defaultBlockState());
        ServerPlayer p = atWall(h, tool(ToolKind.PICKAXE, ToolUpgrades.BASE));
        dig(h, p, new BlockPos(12, 3, WALL_Z));
        // the square's floor is at the player's feet (y 2), not a block below them
        for (int x = 11; x <= 13; x++) for (int y = 2; y <= 4; y++) h.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, WALL_Z));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(10, 3, WALL_Z));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(14, 3, WALL_Z));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(12, 5, WALL_Z));
        // all of it drops on the struck block: nine cobblestone, the struck block's own among them
        h.assertTrue(count(h, Items.COBBLESTONE) == 9, "expected 9 cobblestone, got " + count(h, Items.COBBLESTONE));
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void aFullyUpgradedPickaxeDigsThirteenByThirteen(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 12.5), 0);
        p.setXRot(90);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool(ToolKind.PICKAXE, ToolUpgrades.MAX));
        for (int x = 5; x <= 19; x++) for (int z = 5; z <= 19; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        dig(h, p, new BlockPos(12, 1, 12));
        int dug = 0;
        for (int x = 5; x <= 19; x++) {
            for (int z = 5; z <= 19; z++) {
                boolean inside = Math.abs(x - 12) <= 6 && Math.abs(z - 12) <= 6;
                boolean air = h.getBlockState(new BlockPos(x, 1, z)).isAir();
                if (inside != air) h.fail((inside ? "not dug" : "dug outside the square") + " at " + x + ", " + z);
                if (air) dug++;
            }
        }
        h.assertTrue(dug == 169, "dug " + dug + " blocks, expected 169");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void sneakingDigsOneBlock(GameTestHelper h) {
        wall(h, Blocks.STONE.defaultBlockState());
        ServerPlayer p = atWall(h, tool(ToolKind.PICKAXE, ToolUpgrades.MAX));
        p.setShiftKeyDown(true);
        dig(h, p, new BlockPos(12, 3, WALL_Z));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(12, 3, WALL_Z));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(11, 3, WALL_Z));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(12, 4, WALL_Z));
        h.succeed();
    }

    /** Chests keep their place and their contents; obsidian, far harder than stone, stays; dirt isn't a pickaxe's. */
    @GameTest(template = ARENA)
    public static void theSquareSparesChestsHardBlocksAndOtherToolsBlocks(GameTestHelper h) {
        wall(h, Blocks.STONE.defaultBlockState());
        h.setBlock(new BlockPos(11, 3, WALL_Z), Blocks.CHEST);
        h.setBlock(new BlockPos(13, 3, WALL_Z), Blocks.OBSIDIAN);
        h.setBlock(new BlockPos(12, 4, WALL_Z), Blocks.DIRT);
        h.setBlock(new BlockPos(12, 2, WALL_Z), Blocks.DEEPSLATE_DIAMOND_ORE);
        ServerPlayer p = atWall(h, tool(ToolKind.PICKAXE, ToolUpgrades.BASE));
        dig(h, p, new BlockPos(12, 3, WALL_Z));
        h.assertBlockPresent(Blocks.CHEST, new BlockPos(11, 3, WALL_Z));
        h.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(13, 3, WALL_Z));
        h.assertBlockPresent(Blocks.DIRT, new BlockPos(12, 4, WALL_Z));
        // an ore, a little harder than stone, goes with it
        h.assertBlockPresent(Blocks.AIR, new BlockPos(12, 2, WALL_Z));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(11, 4, WALL_Z));
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void aShovelDigsEarthAndAnAxeWood(GameTestHelper h) {
        wall(h, Blocks.DIRT.defaultBlockState());
        ServerPlayer p = atWall(h, tool(ToolKind.SHOVEL, ToolUpgrades.BASE));
        dig(h, p, new BlockPos(12, 3, WALL_Z));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(11, 4, WALL_Z));
        wall(h, Blocks.OAK_LOG.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND, tool(ToolKind.AXE, ToolUpgrades.BASE));
        dig(h, p, new BlockPos(12, 3, WALL_Z));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(13, 2, WALL_Z));
        h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(14, 3, WALL_Z));
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void upgradesGiveFortuneAndEfficiency(GameTestHelper h) {
        var lookup = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var fortune = lookup.getOrThrow(Enchantments.FORTUNE);
        var efficiency = lookup.getOrThrow(Enchantments.EFFICIENCY);
        ItemStack base = tool(ToolKind.PICKAXE, ToolUpgrades.BASE), max = tool(ToolKind.PICKAXE, ToolUpgrades.MAX);
        ItemStack bare = new ItemStack(ModItems.tool(Material.SOLARITE, ToolKind.AXE));
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, base) == 3, "a new tool has Fortune III");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(efficiency, base) == 5, "a new tool has Efficiency V");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, bare) == 3, "a tool never upgraded has Fortune III");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, max) == 10, "a fully upgraded tool has Fortune X");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(efficiency, max) == 10, "a fully upgraded tool has Efficiency X");
        h.assertTrue(max.getAllEnchantments(lookup).getLevel(efficiency) == 10, "the effects see Efficiency X too");
        h.assertTrue(!max.supportsEnchantment(lookup.getOrThrow(Enchantments.SILK_TOUCH)), "Silk Touch would undo its Fortune");
        h.succeed();
    }

    /**
     * Fortune X on a 13x13 floor of diamond ore: it averages over five and a half diamonds an ore (Fortune III a little over
     * two), every one of them gathered on the struck block.
     */
    @GameTest(template = ARENA)
    public static void fortuneTenMultipliesTheSquaresOre(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 12.5), 0);
        p.setXRot(90);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool(ToolKind.PICKAXE, ToolUpgrades.MAX));
        for (int x = 6; x <= 18; x++) for (int z = 6; z <= 18; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.DIAMOND_ORE);
        dig(h, p, new BlockPos(12, 1, 12));
        int diamonds = count(h, Items.DIAMOND);
        h.assertTrue(diamonds >= 700, "Fortune X gave only " + diamonds + " diamonds from 169 ores");
        // gathered: a few full stacks on the struck block, not 169 scattered drops
        int entities = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(1)).size();
        h.assertTrue(entities <= diamonds / 64 + 3, entities + " item entities for " + diamonds + " diamonds");
        h.succeed();
    }

    /** An oak at relative (x, 2, z): a trunk five logs high with a crown of leaves that grew there (not placed by hand). */
    private static void oak(GameTestHelper h, int x, int z) {
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)
                .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
        for (int y = 2; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 5; y <= 7; y++) {
                    BlockPos p = new BlockPos(x + dx, y, z + dz);
                    if (h.getBlockState(p).isAir()) h.setBlock(p, leaf);
                }
            }
        }
    }

    private static boolean oakGone(GameTestHelper h, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 2; y <= 7; y++) {
            if (!h.getBlockState(new BlockPos(x + dx, y, z + dz)).isAir()) return false;
        }
        return true;
    }

    @GameTest(template = ARENA)
    public static void anAxeFellsTheWholeTreeButNotACabinWall(GameTestHelper h) {
        oak(h, 12, WALL_Z);
        // a pillar of logs with no leaves: a building, not a tree
        for (int y = 2; y <= 6; y++) h.setBlock(new BlockPos(6, y, WALL_Z), Blocks.OAK_LOG);
        ServerPlayer p = atWall(h, tool(ToolKind.AXE, ToolUpgrades.BASE));
        dig(h, p, new BlockPos(12, 3, WALL_Z));
        h.assertTrue(oakGone(h, 12, WALL_Z), "the oak was not felled whole");
        h.assertTrue(count(h, Items.OAK_LOG) == 5, "expected the oak's 5 logs on the ground, got " + count(h, Items.OAK_LOG));
        // the pillar only loses the axe's square round the struck log
        dig(h, p, new BlockPos(6, 3, WALL_Z));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(6, 4, WALL_Z));
        h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(6, 6, WALL_Z));
        h.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void timberfallFellsEveryTreeAround(GameTestHelper h) {
        oak(h, 6, 6);
        oak(h, 18, 18);
        oak(h, 18, 6);
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 12.5), 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool(ToolKind.AXE, ToolUpgrades.BASE));
        com.fantasyweapons.tool.ToolAbilities.use(p);
        h.assertTrue(p.getCooldowns().isOnCooldown(p.getMainHandItem().getItem()), "Timberfall must rest after use");
        h.succeedWhen(() -> {
            h.assertTrue(oakGone(h, 6, 6) && oakGone(h, 18, 18) && oakGone(h, 18, 6), "not every tree was felled");
            h.assertTrue(count(h, Items.OAK_LOG) == 15, "expected 15 logs, got " + count(h, Items.OAK_LOG));
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void earthshaperLevelsTheEarthButNotTheStone(GameTestHelper h) {
        for (int x = 14; x <= 16; x++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, 12), Blocks.DIRT);
        h.setBlock(new BlockPos(12, 2, 15), Blocks.SAND);
        h.setBlock(new BlockPos(9, 2, 12), Blocks.STONE);
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 2, 12.5), 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool(ToolKind.SHOVEL, ToolUpgrades.BASE));
        com.fantasyweapons.tool.ToolAbilities.use(p);
        h.succeedWhen(() -> {
            for (int x = 14; x <= 16; x++) for (int y = 2; y <= 4; y++) h.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, 12));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(12, 2, 15));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(9, 2, 12));
            // the ground it stands on is not dug
            h.assertBlockNotPresent(Blocks.AIR, new BlockPos(14, 1, 12));
        });
    }

    @GameTest(template = ARENA)
    public static void oreSightFindsTheLegendaryOresInReach(GameTestHelper h) {
        h.setBlock(new BlockPos(14, 2, 12), com.fantasyweapons.registry.ModBlocks.ORES.get(Material.SOLARITE).get());
        h.setBlock(new BlockPos(12, 1, 6), com.fantasyweapons.registry.ModBlocks.ORES.get(Material.INFERNITE).get());
        h.setBlock(new BlockPos(22, 2, 22), com.fantasyweapons.registry.ModBlocks.ORES.get(Material.VOID_CRYSTAL).get());
        List<Material> found = new java.util.ArrayList<>();
        com.fantasyweapons.tool.ToolAbilities.scanOres(h.getLevel(), h.absolutePos(new BlockPos(12, 2, 12)), 8, (pos, ore) -> found.add(ore.material()));
        h.assertTrue(found.size() == 2 && found.contains(Material.SOLARITE) && found.contains(Material.INFERNITE),
                "within 8 blocks: expected Solarite and Infernite, found " + found);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void theSquareStandsOnTheFeetOrHoldsTheStruckBlock(GameTestHelper h) {
        BlockPos origin = new BlockPos(0, 64, 0);
        List<BlockPos> square = AreaMining.area(origin, Direction.NORTH, 6, 60);
        int minY = square.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        h.assertTrue(minY == 60 && square.size() == 169, "a 13x13 wall square from feet at 60: bottom " + minY + ", " + square.size() + " blocks");
        // struck far below the feet: the square still holds the struck block
        square = AreaMining.area(origin, Direction.EAST, 2, 80);
        h.assertTrue(square.contains(origin), "the square must hold the struck block");
        // digging down: centred on the struck block
        square = AreaMining.area(origin, Direction.UP, 1, 65);
        h.assertTrue(square.size() == 9 && square.stream().allMatch(b -> b.getY() == 64), "a 3x3 floor square");
        h.succeed();
    }
}
