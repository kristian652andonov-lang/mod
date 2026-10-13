package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.forge.ForgeRecipe;
import com.fantasyweapons.forge.LegendaryCraftingMenu;
import com.fantasyweapons.forge.LegendaryCraftingTableBlock;
import com.fantasyweapons.forge.LegendaryCraftingTableBlockEntity;
import com.fantasyweapons.machine.MachinePart;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Legendary Crafting Table: its 9x9 grid forges the legendary gear, and crafts like a crafting table. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeGameTests {
    private static final String ARENA = "arena";

    private ForgeGameTests() {
    }

    private static final BlockPos CORE = new BlockPos(4, 2, 4);

    /** Builds the table (core and five parts) facing north with its core at {@code core}. */
    private static BlockPos build(GameTestHelper h, BlockPos core) {
        BlockPos at = h.absolutePos(core);
        LegendaryCraftingTableBlock table = ModBlocks.LEGENDARY_CRAFTING_TABLE.get();
        h.getLevel().setBlock(at, table.defaultBlockState().setValue(LegendaryCraftingTableBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        table.placeParts(h.getLevel(), at, Direction.NORTH);
        return at;
    }

    private static LegendaryCraftingMenu open(GameTestHelper h, ServerPlayer p) {
        BlockPos at = h.getLevel().getBlockState(h.absolutePos(CORE)).is(ModBlocks.LEGENDARY_CRAFTING_TABLE.get()) ? h.absolutePos(CORE) : build(h, CORE);
        return new LegendaryCraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), at));
    }

    /** Lays a recipe's pattern out on the grid, {@code dx} and {@code dy} slots from the top left. */
    private static void lay(LegendaryCraftingMenu menu, ForgeRecipe recipe, int dx, int dy) {
        var pattern = recipe.pattern();
        for (int r = 0; r < pattern.height(); r++) {
            for (int c = 0; c < pattern.width(); c++) {
                Ingredient ing = pattern.ingredients().get(c + r * pattern.width());
                if (ing.isEmpty()) continue;
                menu.slots.get(LegendaryCraftingMenu.GRID_START + (c + dx) + (r + dy) * ForgeRecipe.SIZE).set(ing.getItems()[0].copy());
            }
        }
    }

    /** Every weapon's and armor piece's pattern, laid anywhere on the grid, forges it; taking it uses the grid up. */
    @GameTest(template = ARENA)
    public static void forgesEveryWeaponAndPiece(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        var recipes = h.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.FORGING.get());
        if (recipes.size() != 13 + 13 * 4) h.fail("expected 65 forge recipes, found " + recipes.size());
        for (RecipeHolder<ForgeRecipe> holder : recipes) {
            ForgeRecipe recipe = holder.value();
            LegendaryCraftingMenu menu = open(h, p);
            // off-centre: in the bottom right corner
            lay(menu, recipe, ForgeRecipe.SIZE - recipe.pattern().width(), ForgeRecipe.SIZE - recipe.pattern().height());
            ItemStack out = menu.slots.get(LegendaryCraftingMenu.RESULT).getItem();
            if (out.getItem() != recipe.result().getItem()) {
                h.fail(holder.id() + " made " + out + " instead of " + recipe.result());
                return;
            }
            var result = menu.slots.get(LegendaryCraftingMenu.RESULT);
            ItemStack taken = result.remove(1);
            result.onTake(p, taken);
            for (int i = LegendaryCraftingMenu.GRID_START; i < LegendaryCraftingMenu.GRID_END; i++) {
                if (menu.slots.get(i).hasItem()) {
                    h.fail(holder.id() + ": the grid was not used up");
                    return;
                }
            }
            if (!menu.slots.get(LegendaryCraftingMenu.RESULT).getItem().isEmpty()) h.fail(holder.id() + ": a result is left with an empty grid");
        }
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A Voidfang's pattern missing its eye of ender forges nothing. */
    @GameTest(template = ARENA)
    public static void anIncompletePatternForgesNothing(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        LegendaryCraftingMenu menu = open(h, p);
        var voidfang = h.getLevel().getRecipeManager().byKey(FantasyWeapons.id("forging/voidfang")).orElseThrow();
        lay(menu, (ForgeRecipe) voidfang.value(), 0, 0);
        if (menu.slots.get(LegendaryCraftingMenu.RESULT).getItem().getItem() != ModItems.WEAPONS.get("voidfang").get()) h.fail("the full pattern forges nothing");
        for (int i = LegendaryCraftingMenu.GRID_START; i < LegendaryCraftingMenu.GRID_END; i++) {
            if (menu.slots.get(i).getItem().is(Items.ENDER_EYE)) menu.slots.get(i).set(ItemStack.EMPTY);
        }
        if (!menu.slots.get(LegendaryCraftingMenu.RESULT).getItem().isEmpty()) h.fail("forged without its catalyst");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Building the table itself takes a trophy of every dimension's hardest trial. */
    @GameTest(template = ARENA)
    public static void theTableIsHardToBuild(GameTestHelper h) {
        var recipe = h.getLevel().getRecipeManager().byKey(FantasyWeapons.id("legendary_crafting_table")).orElseThrow().value();
        if (recipe.getResultItem(h.getLevel().registryAccess()).getItem() != ModItems.LEGENDARY_CRAFTING_TABLE.get()) h.fail("the recipe makes something else");
        java.util.Map<net.minecraft.world.item.Item, Integer> needs = new java.util.HashMap<>();
        for (Ingredient ing : recipe.getIngredients()) if (!ing.isEmpty()) needs.merge(ing.getItems()[0].getItem(), 1, Integer::sum);
        java.util.Map<net.minecraft.world.item.Item, Integer> expected = java.util.Map.of(Items.NETHER_STAR, 1, Items.DRAGON_BREATH, 2,
                Items.NETHERITE_INGOT, 2, Items.TOTEM_OF_UNDYING, 2, Items.HEAVY_CORE, 1);
        if (!needs.equals(expected)) h.fail("the table takes " + needs + ", expected " + expected);
        h.succeed();
    }

    /** Placing the item builds the whole table, three wide and two high, facing the player; there must be room for it. */
    @GameTest(template = ARENA)
    public static void placingBuildsTheWholeTable(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(180); // looking north: the table faces back south, towards the player
        ItemStack stack = new ItemStack(ModItems.LEGENDARY_CRAFTING_TABLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = h.absolutePos(CORE);
        var result = ModItems.LEGENDARY_CRAFTING_TABLE.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        if (!result.consumesAction()) {
            h.fail("could not place: " + result);
            return;
        }
        BlockState core = h.getLevel().getBlockState(at);
        if (!core.is(ModBlocks.LEGENDARY_CRAFTING_TABLE.get())) h.fail("placed " + core);
        Direction facing = core.getValue(LegendaryCraftingTableBlock.FACING);
        if (facing != Direction.SOUTH) h.fail("faces " + facing + ", expected south (towards the player)");
        for (MachinePart part : MachinePart.values()) {
            if (!h.getLevel().getBlockState(part.from(at, facing)).is(ModBlocks.LEGENDARY_CRAFTING_TABLE_PART.get())) h.fail("missing part " + part);
        }
        if (!(h.getLevel().getBlockEntity(at) instanceof LegendaryCraftingTableBlockEntity)) h.fail("no block entity");
        BlockPos other = CORE.offset(6, 0, 0);
        h.setBlock(MachinePart.TOP_RIGHT.from(other, Direction.SOUTH), Blocks.STONE);
        BlockPos at2 = h.absolutePos(other);
        ItemStack stack2 = new ItemStack(ModItems.LEGENDARY_CRAFTING_TABLE.get());
        var blocked = ModItems.LEGENDARY_CRAFTING_TABLE.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack2,
                new BlockHitResult(Vec3.atCenterOf(at2), Direction.UP, at2, false)));
        if (blocked.consumesAction()) h.fail("placed without room");
        h.succeed();
    }

    /**
     * The table's outline is its model's (about 2.2 blocks across its front, 1.25 deep), turned with it, the same whole
     * outline from each of its six blocks; what is solid stays inside each block, and the far ends of the side blocks are open.
     */
    @GameTest(template = ARENA)
    public static void theOutlineFitsTheModel(GameTestHelper h) {
        LegendaryCraftingTableBlock table = ModBlocks.LEGENDARY_CRAFTING_TABLE.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB whole = table.outline(facing, null).bounds();
            Direction right = facing.getClockWise();
            double across = right.getAxis() == Direction.Axis.X ? whole.getXsize() : whole.getZsize();
            double deep = facing.getAxis() == Direction.Axis.X ? whole.getXsize() : whole.getZsize();
            if (Math.abs(across - 34.6 / 16) > 0.01 || Math.abs(deep - 20 / 16.0) > 0.01 || Math.abs(whole.maxY - 34 / 16.0) > 0.01) {
                h.fail(facing + ": outline " + whole);
            }
            for (MachinePart part : MachinePart.values()) {
                BlockPos at = part.from(BlockPos.ZERO, facing);
                AABB fromPart = table.outline(facing, part).bounds().move(at.getX(), at.getY(), at.getZ());
                if (!same(fromPart, whole)) {
                    h.fail(facing + " " + part + ": outline " + fromPart + " is not the table's " + whole);
                }
                var solid = table.collision(facing, part);
                if (!solid.isEmpty()) {
                    AABB b = solid.bounds();
                    if (b.minX < 0 || b.minY < 0 || b.minZ < 0 || b.maxX > 1 || b.maxY > 1 || b.maxZ > 1) h.fail(facing + " " + part + ": solid outside " + b);
                }
            }
            // the left block: solid only on its inner side (the table and bottle end 0.58 blocks out from the core's edge)
            AABB left = table.collision(facing, MachinePart.LEFT).bounds();
            double reach = right.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? 1 - (right.getAxis() == Direction.Axis.X ? left.minX : left.minZ)
                    : (right.getAxis() == Direction.Axis.X ? left.maxX : left.maxZ);
            if (Math.abs(reach - 9.3 / 16) > 0.01) h.fail(facing + ": left block solid " + reach + " in, expected " + 9.3 / 16);
        }
        if (!same(table.collision(Direction.NORTH, null).bounds(), new AABB(0, 0, 0, 1, 1, 1))) h.fail("the core is not solid");
        h.succeed();
    }

    private static boolean same(AABB a, AABB b) {
        return Math.abs(a.minX - b.minX) < 1e-6 && Math.abs(a.minY - b.minY) < 1e-6 && Math.abs(a.minZ - b.minZ) < 1e-6
                && Math.abs(a.maxX - b.maxX) < 1e-6 && Math.abs(a.maxY - b.maxY) < 1e-6 && Math.abs(a.maxZ - b.maxZ) < 1e-6;
    }

    /** Mining any one of its blocks with a pickaxe takes the whole table down and drops it, once. */
    @GameTest(template = ARENA)
    public static void miningAPartDropsTheTable(GameTestHelper h) {
        BlockPos at = build(h, CORE);
        ServerPlayer p = FWGameTests.player(h, new Vec3(4.5, 2, 2.5), 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        BlockPos part = MachinePart.TOP_LEFT.from(at, Direction.NORTH);
        if (!p.gameMode.destroyBlock(part)) h.fail("could not mine the part");
        h.runAfterDelay(2, () -> {
            if (!h.getLevel().getBlockState(at).isAir()) h.fail("core still there");
            for (MachinePart mp : MachinePart.values()) {
                if (!h.getLevel().getBlockState(mp.from(at, Direction.NORTH)).isAir()) h.fail(mp + " still there");
            }
            int dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4))
                    .stream().filter(e -> e.getItem().is(ModItems.LEGENDARY_CRAFTING_TABLE.get())).mapToInt(e -> e.getItem().getCount()).sum();
            if (dropped != 1) h.fail("dropped " + dropped + " tables");
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }

    /** The table crafts (its crafting animation plays) while a recipe lies on the grid, and for a moment after forging. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theTableCraftsWhileARecipeLies(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(4.5, 2, 2.5), 0);
        LegendaryCraftingMenu menu = open(h, p);
        BlockPos at = h.absolutePos(CORE);
        java.util.function.BooleanSupplier crafting = () -> h.getLevel().getBlockState(at).getValue(LegendaryCraftingTableBlock.CRAFTING);
        menu.slots.get(LegendaryCraftingMenu.GRID_START).set(new ItemStack(Items.DIRT));
        h.runAfterDelay(3, () -> {
            if (crafting.getAsBoolean()) h.fail("crafting with nothing to make");
            var voidfang = (ForgeRecipe) h.getLevel().getRecipeManager().byKey(FantasyWeapons.id("forging/voidfang")).orElseThrow().value();
            menu.slots.get(LegendaryCraftingMenu.GRID_START).set(ItemStack.EMPTY);
            lay(menu, voidfang, 0, 0);
        });
        h.runAfterDelay(6, () -> {
            if (!crafting.getAsBoolean()) h.fail("not crafting with a recipe on the grid");
            menu.slots.get(LegendaryCraftingMenu.RESULT).onTake(p, menu.slots.get(LegendaryCraftingMenu.RESULT).remove(1));
            menu.removed(p);
        });
        h.runAfterDelay(9, () -> {
            if (!crafting.getAsBoolean()) h.fail("stopped crafting the moment the item was forged");
        });
        h.runAfterDelay(12 + LegendaryCraftingTableBlockEntity.AFTERGLOW, () -> {
            if (crafting.getAsBoolean()) h.fail("still crafting long after the grid was left");
            FWGameTests.cleanup(p);
            h.succeed();
        });
    }

    /** Worlds from before the table had its model keep their tables: the old Legendary Forge is the same block and item. */
    @GameTest(template = ARENA)
    public static void theOldForgeIsTheTable(GameTestHelper h) {
        var old = FantasyWeapons.id("legendary_forge");
        if (BuiltInRegistries.BLOCK.get(old) != ModBlocks.LEGENDARY_CRAFTING_TABLE.get()) h.fail("the old block is " + BuiltInRegistries.BLOCK.get(old));
        if (BuiltInRegistries.ITEM.get(old) != ModItems.LEGENDARY_CRAFTING_TABLE.get()) h.fail("the old item is " + BuiltInRegistries.ITEM.get(old));
        // as saved in a chunk and in an inventory
        var saved = new net.minecraft.nbt.CompoundTag();
        saved.putString("Name", old.toString());
        BlockState loaded = net.minecraft.nbt.NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
        if (!loaded.is(ModBlocks.LEGENDARY_CRAFTING_TABLE.get())) h.fail("a saved old forge loads as " + loaded);
        var stack = new net.minecraft.nbt.CompoundTag();
        stack.putString("id", old.toString());
        stack.putInt("count", 1);
        ItemStack item = ItemStack.parseOptional(h.getLevel().registryAccess(), stack);
        if (!item.is(ModItems.LEGENDARY_CRAFTING_TABLE.get())) h.fail("a saved old forge item loads as " + item);
        h.succeed();
    }

    /** An old forge (a lone core, as it loads from a world saved before the table was three blocks wide) builds the rest of the table. */
    @GameTest(template = ARENA)
    public static void anOldForgeBuildsTheTable(GameTestHelper h) {
        h.setBlock(CORE, ModBlocks.LEGENDARY_CRAFTING_TABLE.get());
        BlockPos at = h.absolutePos(CORE);
        h.runAfterDelay(3, () -> {
            if (!ModBlocks.LEGENDARY_CRAFTING_TABLE.get().intact(h.getLevel(), at, Direction.NORTH)) h.fail("the old forge did not build the table");
            h.succeed();
        });
    }

    /** ...and where there is no room for the rest, comes down as the table's item instead. */
    @GameTest(template = ARENA)
    public static void anOldForgeWithoutRoomDrops(GameTestHelper h) {
        BlockPos at = h.absolutePos(CORE);
        h.setBlock(MachinePart.TOP.from(CORE, Direction.NORTH), Blocks.STONE);
        h.setBlock(CORE, ModBlocks.LEGENDARY_CRAFTING_TABLE.get());
        h.runAfterDelay(3, () -> {
            if (!h.getLevel().getBlockState(at).isAir()) h.fail("still there: " + h.getLevel().getBlockState(at));
            boolean dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream()
                    .anyMatch(e -> e.getItem().is(ModItems.LEGENDARY_CRAFTING_TABLE.get()));
            if (!dropped) h.fail("the table was not dropped");
            h.succeed();
        });
    }

    /** Every block and item of the mod has an English name (a missing one shows its raw translation key). */
    @GameTest(template = ARENA)
    public static void everythingHasAName(GameTestHelper h) {
        com.google.gson.JsonObject lang;
        try (var in = ForgeGameTests.class.getResourceAsStream("/assets/fantasyweapons/lang/en_us.json")) {
            if (in == null) throw new IllegalStateException("no en_us.json");
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (var b : ModBlocks.REGISTER.getEntries()) if (!lang.has(b.get().getDescriptionId())) missing.add(b.get().getDescriptionId());
        for (var i : ModItems.REGISTER.getEntries()) if (!lang.has(i.get().getDescriptionId())) missing.add(i.get().getDescriptionId());
        if (!missing.isEmpty()) h.fail("no name for " + missing);
        h.succeed();
    }

    /** Ordinary crafting works on the table's grid too, anywhere on it. */
    @GameTest(template = ARENA)
    public static void craftsLikeACraftingTable(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        LegendaryCraftingMenu menu = open(h, p);
        menu.slots.get(LegendaryCraftingMenu.GRID_START + 40).set(new ItemStack(Items.OAK_LOG));
        ItemStack out = menu.slots.get(LegendaryCraftingMenu.RESULT).getItem();
        if (!out.is(Items.OAK_PLANKS) || out.getCount() != 4) h.fail("a log in the middle made " + out);
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
