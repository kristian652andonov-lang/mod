package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.ArmorStat;
import com.fantasyweapons.armor.FantasyArmorItem;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The legendary armor: piece bonuses, full sets and the recipes for armor and weapons. */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorGameTests {
    private static final String ARENA = "arena";

    private ArmorGameTests() {
    }

    private static void wear(Player p, ArmorSet set) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.HELMET).get()));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.CHESTPLATE).get()));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.LEGGINGS).get()));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ARMOR.get(set).get(ArmorItem.Type.BOOTS).get()));
    }

    /** Every set worn in full gives its class's armor, +4 max health and each piece's stat bonuses. */
    @GameTest(template = ARENA)
    public static void fullSetsGiveTheirBonuses(GameTestHelper h) {
        for (ArmorSet set : ArmorSet.values()) {
            Player p = h.makeMockPlayer(GameType.SURVIVAL);
            wear(p, set);
            p.tick(); // equipment changes apply their attribute modifiers
            if (!FantasyArmorItem.fullSet(p, set)) h.fail(set + ": not recognised as a full set");
            double armor = 0, expectStat;
            for (ArmorItem.Type t : ArmorSet.PIECES) {
                armor += set.armorClass().defense(t);
                for (ArmorSet.Bonus b : set.bonuses(t)) {
                    if (b.attribute().get() == Attributes.ARMOR) armor += b.amount();
                }
            }
            double have = p.getAttributeValue(Attributes.ARMOR);
            // vanilla caps armor at 30
            if (Math.abs(have - Math.min(30, armor)) > 1e-6) h.fail(set + ": armor " + have + ", expected " + armor);
            if (Math.abs(p.getAttributeValue(Attributes.MAX_HEALTH) - 24) > 1e-6) h.fail(set + ": max health " + p.getAttributeValue(Attributes.MAX_HEALTH));
            for (ArmorItem.Type t : ArmorSet.PIECES) {
                for (ArmorSet.Bonus b : set.bonuses(t)) {
                    if (b.stat() == null) continue;
                    expectStat = b.amount();
                    if (Math.abs(b.stat().get(p) - expectStat) > 1e-6) h.fail(set + " " + t + ": " + b.stat() + " = " + b.stat().get(p));
                }
            }
        }
        h.succeed();
    }

    /** A piece only counts in its own slot, and taking it off removes its bonus. */
    @GameTest(template = ARENA)
    public static void bonusesFollowTheSlot(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = new ItemStack(ModItems.ARMOR.get(ArmorSet.VOIDFANG).get(ArmorItem.Type.HELMET).get());
        p.setItemSlot(EquipmentSlot.MAINHAND, helmet.copy());
        p.tick();
        if (ArmorStat.VOID_ABILITY_DAMAGE.get(p) != 0) h.fail("helmet in the hand gives its bonus");
        p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.HEAD, helmet);
        p.tick();
        if (Math.abs(ArmorStat.VOID_ABILITY_DAMAGE.get(p) - 0.15) > 1e-6) h.fail("worn helmet: " + ArmorStat.VOID_ABILITY_DAMAGE.get(p));
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        p.tick();
        if (ArmorStat.VOID_ABILITY_DAMAGE.get(p) != 0) h.fail("bonus stays after taking the helmet off");
        h.succeed();
    }

    /** Every armor piece and every weapon is forged at the Legendary Crafting Table from the matching refined material, and nowhere else. */
    @GameTest(template = ARENA)
    public static void armorAndWeaponsHaveRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (ArmorSet set : ArmorSet.values()) {
            for (ArmorItem.Type t : ArmorSet.PIECES) {
                var item = ModItems.ARMOR.get(set).get(t).get();
                var r = recipes.byKey(FantasyWeapons.id("forging/" + set.itemId(t)));
                if (r.isEmpty() || r.get().value().getType() != com.fantasyweapons.registry.ModRecipes.FORGING.get()) {
                    h.fail("no forge recipe for " + set.itemId(t));
                    continue;
                }
                var refined = ModItems.REFINED.get(set.material()).get();
                long count = r.get().value().getIngredients().stream().filter(i -> i.test(new ItemStack(refined))).count();
                if (count != ArmorSet.cost(t)) h.fail(set.itemId(t) + " costs " + count + " refined, expected " + ArmorSet.cost(t));
                if (r.get().value().getResultItem(h.getLevel().registryAccess()).getItem() != item) h.fail(set.itemId(t) + " makes the wrong item");
            }
        }
        for (var def : com.fantasyweapons.weapon.Weapons.legendaries()) {
            var r = recipes.byKey(FantasyWeapons.id("forging/" + def.id()));
            if (r.isEmpty()) h.fail("no forge recipe for " + def.id());
            else if (r.get().value().getResultItem(h.getLevel().registryAccess()).getItem() != ModItems.WEAPONS.get(def.id()).get()) {
                h.fail(def.id() + " recipe makes the wrong item");
            }
        }
        // the legendary gear is no longer made on a crafting table
        for (var r : recipes.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            var out = r.value().getResultItem(h.getLevel().registryAccess()).getItem();
            if (out instanceof com.fantasyweapons.armor.FantasyArmorItem || out instanceof com.fantasyweapons.weapon.FantasyWeaponItem) {
                if (!(r.value() instanceof com.fantasyweapons.material.InfusionRecipe)) h.fail("crafting table recipe for " + out);
            }
        }
        h.succeed();
    }
}
