package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.forge.ForgeRecipe;
import com.fantasyweapons.forge.LegendaryForgeMenu;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Legendary Forge: its 9x9 grid forges the legendary gear, and crafts like a crafting table. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeGameTests {
    private static final String ARENA = "arena";

    private ForgeGameTests() {
    }

    private static LegendaryForgeMenu open(GameTestHelper h, ServerPlayer p) {
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, ModBlocks.LEGENDARY_FORGE.get());
        return new LegendaryForgeMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(pos)));
    }

    /** Lays a recipe's pattern out on the grid, {@code dx} and {@code dy} slots from the top left. */
    private static void lay(LegendaryForgeMenu menu, ForgeRecipe recipe, int dx, int dy) {
        var pattern = recipe.pattern();
        for (int r = 0; r < pattern.height(); r++) {
            for (int c = 0; c < pattern.width(); c++) {
                Ingredient ing = pattern.ingredients().get(c + r * pattern.width());
                if (ing.isEmpty()) continue;
                menu.slots.get(LegendaryForgeMenu.GRID_START + (c + dx) + (r + dy) * ForgeRecipe.SIZE).set(ing.getItems()[0].copy());
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
            LegendaryForgeMenu menu = open(h, p);
            // off-centre: in the bottom right corner
            lay(menu, recipe, ForgeRecipe.SIZE - recipe.pattern().width(), ForgeRecipe.SIZE - recipe.pattern().height());
            ItemStack out = menu.slots.get(LegendaryForgeMenu.RESULT).getItem();
            if (out.getItem() != recipe.result().getItem()) {
                h.fail(holder.id() + " made " + out + " instead of " + recipe.result());
                return;
            }
            var result = menu.slots.get(LegendaryForgeMenu.RESULT);
            ItemStack taken = result.remove(1);
            result.onTake(p, taken);
            for (int i = LegendaryForgeMenu.GRID_START; i < LegendaryForgeMenu.GRID_END; i++) {
                if (menu.slots.get(i).hasItem()) {
                    h.fail(holder.id() + ": the grid was not used up");
                    return;
                }
            }
            if (!menu.slots.get(LegendaryForgeMenu.RESULT).getItem().isEmpty()) h.fail(holder.id() + ": a result is left with an empty grid");
        }
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** A Voidfang's pattern missing its eye of ender forges nothing. */
    @GameTest(template = ARENA)
    public static void anIncompletePatternForgesNothing(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        LegendaryForgeMenu menu = open(h, p);
        var voidfang = h.getLevel().getRecipeManager().byKey(FantasyWeapons.id("forging/voidfang")).orElseThrow();
        lay(menu, (ForgeRecipe) voidfang.value(), 0, 0);
        if (menu.slots.get(LegendaryForgeMenu.RESULT).getItem().getItem() != ModItems.WEAPONS.get("voidfang").get()) h.fail("the full pattern forges nothing");
        for (int i = LegendaryForgeMenu.GRID_START; i < LegendaryForgeMenu.GRID_END; i++) {
            if (menu.slots.get(i).getItem().is(Items.ENDER_EYE)) menu.slots.get(i).set(ItemStack.EMPTY);
        }
        if (!menu.slots.get(LegendaryForgeMenu.RESULT).getItem().isEmpty()) h.fail("forged without its catalyst");
        FWGameTests.cleanup(p);
        h.succeed();
    }

    /** Ordinary crafting works on the forge's grid too, anywhere on it. */
    @GameTest(template = ARENA)
    public static void craftsLikeACraftingTable(GameTestHelper h) {
        ServerPlayer p = FWGameTests.player(h, new Vec3(2.5, 2, 2.5), 0);
        LegendaryForgeMenu menu = open(h, p);
        menu.slots.get(LegendaryForgeMenu.GRID_START + 40).set(new ItemStack(Items.OAK_LOG));
        ItemStack out = menu.slots.get(LegendaryForgeMenu.RESULT).getItem();
        if (!out.is(Items.OAK_PLANKS) || out.getCount() != 4) h.fail("a log in the middle made " + out);
        FWGameTests.cleanup(p);
        h.succeed();
    }
}
