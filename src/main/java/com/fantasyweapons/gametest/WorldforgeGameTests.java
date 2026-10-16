package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.forge.WorldforgeBlock;
import com.fantasyweapons.forge.WorldforgeBlockEntity;
import com.fantasyweapons.forge.WorldforgeMenu;
import com.fantasyweapons.forge.WorldforgeRecipe;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Worldforge: placed whole in a clear 21x22x21 space, shaped like its model, taken apart whole, and forging its rites. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldforgeGameTests {
    private static final String ARENA = "arena";
    private static final BlockPos CORE = new BlockPos(11, 1, 11);

    private WorldforgeGameTests() {
    }

    /** Places the forge with its core at {@code rel}: the player chooses the foot of its stairs, in front of them. */
    private static BlockPos place(GameTestHelper h, Player player, BlockPos rel) {
        ItemStack stack = new ItemStack(ModItems.WORLDFORGE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = h.absolutePos(rel);
        // the top of the floor where the stairs will start, as a player clicks it (laid there: the arena may be turned,
        // and the foot of the stairs is at its very edge)
        BlockPos floor = at.relative(player.getDirection().getOpposite(), WorldforgeBlock.REACH + 1).below();
        h.getLevel().setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
        ModItems.WORLDFORGE.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        return at;
    }

    /** Placing it fills its whole 21x22x21 space (the core in the middle of the bottom), facing the player; there must be room. */
    @GameTest(template = ARENA, batch = "worldforge")
    public static void placingFillsItsSpace(GameTestHelper h) {
        clear(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180);
        player.moveTo(h.absoluteVec(new Vec3(11.5, 1, 22.5)));
        for (BlockPos p : WorldforgeBlock.space(h.absolutePos(CORE))) {
            if (!h.getLevel().getBlockState(p).isAir()) {
                h.fail("the space is not clear: " + h.getLevel().getBlockState(p) + " at " + h.relativePos(p));
                return;
            }
        }
        var inWay = h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, new AABB(h.absolutePos(CORE)).inflate(11, 0, 11).expandTowards(0, 22, 0),
                e -> e instanceof net.minecraft.world.entity.LivingEntity);
        if (!inWay.isEmpty()) {
            h.fail("in the way: " + inWay);
            return;
        }
        BlockPos at = place(h, player, CORE);
        var core = h.getLevel().getBlockState(at);
        if (!core.is(ModBlocks.WORLDFORGE.get())) {
            BlockPos found = null;
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, -2, -12), at.offset(12, 3, 12))) {
                if (h.getLevel().getBlockState(p).is(ModBlocks.WORLDFORGE.get())) {
                    found = h.relativePos(p.immutable());
                    break;
                }
            }
            h.fail("placed " + core + " (player at " + h.relativeVec(player.position()) + " facing " + player.getDirection() + ", the core is at " + found
                    + ", floor " + h.getLevel().getBlockState(at.relative(player.getDirection().getOpposite(), WorldforgeBlock.REACH + 1).below()) + ")");
            return;
        }
        if (core.getValue(WorldforgeBlock.FACING) != Direction.SOUTH) h.fail("faces " + core.getValue(WorldforgeBlock.FACING));
        int parts = 0, solid = 0;
        for (BlockPos p : WorldforgeBlock.space(at)) {
            var s = h.getLevel().getBlockState(p);
            if (s.is(ModBlocks.WORLDFORGE_PART.get())) {
                parts++;
                if (com.fantasyweapons.forge.WorldforgePartBlock.solid(s)) solid++;
            }
        }
        int side = WorldforgeBlock.SIDE;
        if (parts != side * side * WorldforgeBlock.LAYERS - 1) h.fail(parts + " parts");
        if (solid < 1000) h.fail("only " + solid + " parts hold some of the model");
        // a part finds its core, from anywhere in the space
        for (BlockPos rel : new BlockPos[]{new BlockPos(-10, 0, -10), new BlockPos(9, 20, 4), new BlockPos(0, 5, -7)}) {
            BlockPos found = WorldforgeBlock.findCore(h.getLevel(), at.offset(rel));
            if (!at.equals(found)) h.fail("the part at " + rel + " finds its core at " + found);
        }
        if (!(h.getLevel().getBlockEntity(at) instanceof WorldforgeBlockEntity)) h.fail("no block entity");
        // a block in the way: it is not placed
        clear(h);
        h.setBlock(CORE.offset(9, 20, -9), Blocks.STONE);
        BlockPos at2 = place(h, player, CORE);
        if (h.getLevel().getBlockState(at2).is(ModBlocks.WORLDFORGE.get())) h.fail("placed without room");
        h.setBlock(CORE.offset(9, 20, -9), Blocks.AIR);
        h.succeed();
    }

    /**
     * Its base is solid all round; the air high over its stairs is empty; every block's shape stays inside the block; and
     * each of its four stairs can be walked up from the ground to the altar, no step higher than a player steps.
     */
    @GameTest(template = ARENA, batch = "worldforge")
    public static void itIsShapedLikeItsModel(GameTestHelper h) {
        int r = WorldforgeBlock.REACH;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int d = -r; d <= r; d++) {
                if (WorldforgeBlock.shape(facing, d, 0, -r).isEmpty() || WorldforgeBlock.shape(facing, -r, 0, d).isEmpty()) {
                    h.fail("the base is missing at the edge facing " + facing + " (" + d + ")");
                }
            }
            if (!WorldforgeBlock.shape(facing, 0, 12, -r).isEmpty()) h.fail("solid in the air over the stairs, facing " + facing);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = 0; dy < WorldforgeBlock.LAYERS; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        var shape = WorldforgeBlock.shape(facing, dx, dy, dz);
                        AABB b = shape.isEmpty() ? null : shape.bounds();
                        if (b != null && (b.minX < -1e-6 || b.maxX > 1 + 1e-6 || b.minY < -1e-6 || b.maxY > 1 + 1e-6 || b.minZ < -1e-6 || b.maxZ > 1 + 1e-6)) {
                            h.fail("cell " + dx + "," + dy + "," + dz + " reaches out of its block: " + b);
                        }
                    }
                }
            }
            // up each stair: from just outside the forge to the platform round the altar, every eighth of a block, a little off
            // its middle (a channel of blood runs down the middle, too narrow to fall into)
            for (Direction side : Direction.Plane.HORIZONTAL) {
                double prev = 0;
                Direction across = side.getClockWise();
                for (double t = r + 1.4; t >= 2.2; t -= 0.125) {
                    double x = 0.5 + side.getStepX() * t + across.getStepX() * 0.8, z = 0.5 + side.getStepZ() * t + across.getStepZ() * 0.8;
                    double top = surface(facing, x, z);
                    if (top - prev > 0.6 + 1e-6) {
                        h.fail("facing " + facing + ", the " + side + " stair rises " + String.format("%.2f", top - prev) + " at " + String.format("%.2f", t)
                                + " blocks from the core (" + String.format("%.2f", prev) + " to " + String.format("%.2f", top) + ")");
                        break;
                    }
                    prev = top;
                }
            }
        }
        if (WorldforgeBlock.shapeCount() > com.fantasyweapons.forge.WorldforgePartBlock.CAPACITY) h.fail(WorldforgeBlock.shapeCount() + " shapes");
        h.succeed();
    }

    /** The height of the forge's top surface (blocks over its bottom) at a point of its footprint, relative to the core's corner. */
    private static double surface(Direction facing, double x, double z) {
        int dx = (int) Math.floor(x), dz = (int) Math.floor(z);
        double fx = x - dx, fz = z - dz, top = 0;
        if (Math.abs(dx) > WorldforgeBlock.REACH || Math.abs(dz) > WorldforgeBlock.REACH) return 0;
        for (int dy = 0; dy < WorldforgeBlock.LAYERS; dy++) {
            for (AABB b : WorldforgeBlock.shape(facing, dx, dy, dz).toAabbs()) {
                if (fx >= b.minX && fx <= b.maxX && fz >= b.minZ && fz <= b.maxZ) top = Math.max(top, dy + b.maxY);
            }
        }
        return top;
    }

    /** Breaking any of it takes the whole forge down and drops it as one item, with what lay on its grid. */
    @GameTest(template = ARENA, batch = "worldforge")
    public static void breakingTakesItAllDown(GameTestHelper h) {
        clear(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180);
        BlockPos at = place(h, player, CORE);
        if (!(h.getLevel().getBlockEntity(at) instanceof WorldforgeBlockEntity forge)) {
            h.fail("not placed");
            return;
        }
        forge.grid().setItem(60, new ItemStack(net.minecraft.world.item.Items.NETHER_STAR));
        BlockPos part = at.offset(-3, 0, 2);
        var state = h.getLevel().getBlockState(part);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE));
        state.getBlock().playerWillDestroy(h.getLevel(), part, state, player);
        h.getLevel().removeBlock(part, false);
        for (BlockPos p : WorldforgeBlock.space(at)) {
            var s = h.getLevel().getBlockState(p);
            if (s.is(ModBlocks.WORLDFORGE.get()) || s.is(ModBlocks.WORLDFORGE_PART.get())) {
                h.fail("left " + s + " at " + p);
                return;
            }
        }
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5));
        boolean item = drops.stream().anyMatch(e -> e.getItem().is(ModItems.WORLDFORGE.get()));
        boolean star = drops.stream().anyMatch(e -> e.getItem().is(net.minecraft.world.item.Items.NETHER_STAR));
        if (!item) h.fail("the forge did not drop");
        if (!star) h.fail("its grid did not drop");
        drops.forEach(e -> e.discard());
        h.succeed();
    }

    /** Each rite takes every one of the 25 legendary weapons; laid whole on the grid, Forge consumes it and the forge works on it. */
    @GameTest(template = ARENA, batch = "worldforge")
    public static void theRitesForgeTheUltimates(GameTestHelper h) {
        var all = WorldforgeRecipe.ordered(h.getLevel());
        if (all.size() != 2) h.fail(all.size() + " rites");
        java.util.Set<net.minecraft.world.item.Item> legends = new java.util.HashSet<>();
        for (var def : com.fantasyweapons.weapon.Weapons.legendaries()) legends.add(ModItems.WEAPONS.get(def.id()).get());
        for (var r : all) {
            java.util.Set<net.minecraft.world.item.Item> used = new java.util.HashSet<>();
            for (var ing : r.value().pattern().ingredients()) for (var s : ing.getItems()) used.add(s.getItem());
            if (!used.containsAll(legends)) h.fail(r.id() + " does not take every legend");
            if (!r.value().result().getItem().equals(ModItems.WEAPONS.get(com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration.ID).get())
                    && !r.value().result().getItem().equals(ModItems.WEAPONS.get(com.fantasyweapons.weapons.domination.SpellBookOfDomination.ID).get())) {
                h.fail(r.id() + " makes " + r.value().result());
            }
        }
        clear(h);
        ServerPlayer p = FWGameTests.player(h, new Vec3(11.5, 1, 22.5), 180);
        BlockPos at = place(h, p, CORE);
        if (!(h.getLevel().getBlockEntity(at) instanceof WorldforgeBlockEntity forge)) {
            h.fail("not placed");
            return;
        }
        var recipe = all.get(0).value();
        var layout = WorldforgeMenu.layout(recipe);
        for (int i = 0; i < layout.length; i++) if (layout[i] != null) forge.grid().setItem(i, layout[i].getItems()[0].copy());
        WorldforgeMenu menu = new WorldforgeMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), at), forge);
        if (!menu.ready()) h.fail("the laid rite is not ready");
        menu.clickMenuButton(p, WorldforgeMenu.FORGE_BUTTON);
        if (!forge.busy()) h.fail("Forge did not start");
        if (!ItemStack.isSameItem(forge.forging().getItem(0), recipe.result())) h.fail("forging " + forge.forging().getItem(0));
        for (int i = 0; i < forge.grid().getContainerSize(); i++) {
            // the dragon's breath leaves its bottles behind, as in any crafting
            if (!forge.grid().getItem(i).isEmpty() && !forge.grid().getItem(i).is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
                h.fail("the grid kept " + forge.grid().getItem(i));
                break;
            }
        }
        if (forge.total() != recipe.time()) h.fail("forging takes " + forge.total());
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Clears the forge's space and what earlier tests left in it. */
    private static void clear(GameTestHelper h) {
        BlockPos at = h.absolutePos(CORE);
        for (BlockPos p : WorldforgeBlock.space(at)) h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
    }
}
