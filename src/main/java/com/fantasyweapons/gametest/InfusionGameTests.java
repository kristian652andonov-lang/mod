package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModComponents;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Refined cores upgrade their weapon: crafted together, one whole level per core. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfusionGameTests {
    private static final String ARENA = "arena";

    private InfusionGameTests() {
    }

    private static ItemStack weapon(String id, int level, UUID uuid) {
        ItemStack stack = new ItemStack(ModItems.WEAPONS.get(id).get());
        stack.set(ModComponents.WEAPON_DATA.get(), WeaponData.EMPTY.withId(uuid).withProgress(level, 40, 1000, 3));
        return stack;
    }

    private static ItemStack core(Material m) {
        return new ItemStack(ModItems.REFINED.get(m).get());
    }

    /** What the crafting grid holding {@code items} would make (empty if nothing). */
    private static ItemStack craft(GameTestHelper h, ItemStack... items) {
        List<ItemStack> grid = new ArrayList<>(List.of(items));
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var level = h.getLevel();
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    @GameTest(template = ARENA)
    public static void coresRaiseTheirWeaponALevelEach(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        ItemStack out = craft(h, weapon("voidfang", 5, id), core(Material.VOID_CRYSTAL), core(Material.VOID_CRYSTAL));
        h.assertTrue(out.getItem() == ModItems.WEAPONS.get("voidfang").get(), "the weapon and two of its cores should craft: " + out);
        WeaponData d = FantasyWeaponItem.data(out);
        h.assertTrue(d.level() == 7, "two cores, two levels: " + d.level());
        h.assertTrue(d.weaponId().orElse(null) == null || d.weaponId().get().equals(id), "it is the same weapon");
        h.assertTrue(d.exp() == 40, "progress towards the next level is kept: " + d.exp());
        var voidfang = com.fantasyweapons.weapon.Weapons.get("voidfang");
        int points = 3 + ProgressionMath.masteryPointsForLevel(voidfang, 6) + ProgressionMath.masteryPointsForLevel(voidfang, 7);
        h.assertTrue(d.masteryPoints() == points, "the levels' mastery points are granted: " + d.masteryPoints() + ", expected " + points);
        long total = 1000 + ProgressionMath.expToNext(5) + ProgressionMath.expToNext(6);
        h.assertTrue(d.totalExp() == total, "the levels' experience counts: " + d.totalExp());
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void onlyItsOwnCoresAndNoFurtherThanTheTop(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        h.assertTrue(craft(h, weapon("voidfang", 5, id), core(Material.SOLARITE)).isEmpty(), "another weapon's core does nothing");
        h.assertTrue(craft(h, weapon("voidfang", 5, id), core(Material.VOID_CRYSTAL), core(Material.SOLARITE)).isEmpty(), "mixed cores do nothing");
        h.assertTrue(craft(h, weapon("voidfang", 5, id), core(Material.VOID_CRYSTAL), new ItemStack(Items.DIRT)).isEmpty(), "nothing else in the grid");
        h.assertTrue(craft(h, weapon("voidfang", 5, id), weapon("voidfang", 5, UUID.randomUUID()), core(Material.VOID_CRYSTAL)).isEmpty(),
                "one weapon at a time");
        h.assertTrue(craft(h, weapon("voidfang", 5, id), new ItemStack(ModItems.RAW.get(Material.VOID_CRYSTAL).get())).isEmpty(),
                "raw material must be refined first");
        int max = ProgressionMath.maxLevel();
        h.assertTrue(craft(h, weapon("voidfang", max - 1, id), core(Material.VOID_CRYSTAL), core(Material.VOID_CRYSTAL)).isEmpty(),
                "no more cores than levels left");
        ItemStack top = craft(h, weapon("voidfang", max - 1, id), core(Material.VOID_CRYSTAL));
        h.assertTrue(FantasyWeaponItem.data(top).level() == max && FantasyWeaponItem.data(top).exp() == 0, "the last level: " + top);
        // every weapon takes its own core
        for (Material m : Material.values()) {
            ItemStack out = craft(h, weapon(m.weapon(), 1, UUID.randomUUID()), core(m));
            h.assertTrue(FantasyWeaponItem.data(out).level() == 2, m.weapon() + " is not upgraded by " + m.refinedName());
        }
        h.succeed();
    }
}
