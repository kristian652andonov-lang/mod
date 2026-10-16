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

/** The Worldforge: placed whole in a clear 7x7x7 space, shaped like its model, taken apart whole, and forging its rites. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldforgeGameTests {
    private static final String ARENA = "arena";
    private static final BlockPos CORE = new BlockPos(12, 1, 12);

    private WorldforgeGameTests() {
    }

    private static BlockPos place(GameTestHelper h, Player player, BlockPos rel) {
        ItemStack stack = new ItemStack(ModItems.WORLDFORGE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = h.absolutePos(rel);
        ModItems.WORLDFORGE.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        return at;
    }

    /** Placing it fills its whole 7x7x7 space (the core in the middle of the bottom), facing the player; there must be room. */
    @GameTest(template = ARENA, batch = "worldforge")
    public static void placingFillsItsSpace(GameTestHelper h) {
        clear(h);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180);
        player.moveTo(h.absoluteVec(new Vec3(12.5, 1, 20.5)));
        BlockPos at = place(h, player, CORE);
        var core = h.getLevel().getBlockState(at);
        if (!core.is(ModBlocks.WORLDFORGE.get())) {
            h.fail("placed " + core);
            return;
        }
        if (core.getValue(WorldforgeBlock.FACING) != Direction.SOUTH) h.fail("faces " + core.getValue(WorldforgeBlock.FACING));
        int parts = 0;
        for (BlockPos p : WorldforgeBlock.space(at)) {
            var s = h.getLevel().getBlockState(p);
            if (s.is(ModBlocks.WORLDFORGE_PART.get())) {
                parts++;
                if (!ModBlocks.WORLDFORGE_PART.get().core(s, p).equals(at)) h.fail("a part at " + p + " points at " + ModBlocks.WORLDFORGE_PART.get().core(s, p));
            }
        }
        if (parts != 7 * 7 * 7 - 1) h.fail(parts + " parts");
        if (!(h.getLevel().getBlockEntity(at) instanceof WorldforgeBlockEntity)) h.fail("no block entity");
        // a block in the way: it is not placed
        clear(h);
        h.setBlock(CORE.offset(2, 5, -3), Blocks.STONE);
        BlockPos at2 = place(h, player, CORE);
        if (h.getLevel().getBlockState(at2).is(ModBlocks.WORLDFORGE.get())) h.fail("placed without room");
        h.succeed();
    }

    /** Its tiers can be stood on (solid at the floor all round the edge), the air between its pylons high up is empty. */
    @GameTest(template = ARENA, batch = "worldforge")
    public static void itIsShapedLikeItsModel(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int d = -3; d <= 3; d++) {
                if (WorldforgeBlock.shape(facing, d, 0, -3).isEmpty() || WorldforgeBlock.shape(facing, -3, 0, d).isEmpty()) {
                    h.fail("the bottom tier is missing at the edge facing " + facing);
                }
            }
            // the three tiers rise in steps of a few pixels each, so the edge can be walked up
            var low = WorldforgeBlock.shape(facing, 3, 0, 0).bounds();
            if (low.maxY > 0.75) h.fail("the outer tiers reach " + low.maxY + " high facing " + facing + ", expected low steps: "
                    + WorldforgeBlock.shape(facing, 3, 0, 0).toAabbs());
            if (!WorldforgeBlock.shape(facing, 3, 5, 0).isEmpty()) h.fail("solid in the air at the edge, facing " + facing);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy < 7; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        AABB b = WorldforgeBlock.shape(facing, dx, dy, dz).isEmpty() ? null : WorldforgeBlock.shape(facing, dx, dy, dz).bounds();
                        if (b != null && (b.minX < -1e-6 || b.maxX > 1 + 1e-6 || b.minY < -1e-6 || b.maxY > 1 + 1e-6 || b.minZ < -1e-6 || b.maxZ > 1 + 1e-6)) {
                            h.fail("cell " + dx + "," + dy + "," + dz + " reaches out of its block: " + b);
                        }
                    }
                }
            }
        }
        h.succeed();
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
        ServerPlayer p = FWGameTests.player(h, new Vec3(12.5, 1, 20.5), 180);
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
