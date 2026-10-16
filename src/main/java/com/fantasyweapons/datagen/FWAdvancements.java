package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.advancement.Deeds;
import com.fantasyweapons.advancement.ModTriggers;
import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.function.Consumer;

/**
 * The mod's deeds: a tab of their own in the advancements screen (and the Codex's Deeds chapter), from opening the
 * Wayfarer's Codex through every ore, core and weapon to a hundred levels of mastery and every armor set worn. Titles
 * carry their own colours (each material's glow, the tiers' gold, blue and violet); none of them uses vanilla's toast or
 * chat line, the mod announces them itself ({@link Deeds}).
 */
final class FWAdvancements implements AdvancementProvider.AdvancementGenerator {
    static final ResourceLocation BACKGROUND = FantasyWeapons.id("textures/gui/advancements/backgrounds/legends.png");
    private static final int GOLD = 0xF2C46B, ARCANE = 0x6FD8F0, EMBER = 0xFF8A3D, BONE = 0xE6DCC2, VIOLET = 0xC98CFF, CRIMSON = 0xFF5050;

    private Consumer<AdvancementHolder> saver;

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper files) {
        this.saver = saver;
        Material[] all = Material.values();

        AdvancementHolder root = Advancement.Builder.advancement()
                .display(new ItemStack(ModItems.CODEX.get()), title("root", "Legends of the Forge", 0x7A4E0E),
                        desc("root", "Thirteen legendary weapons wait to be forged. Your Wayfarer's Codex knows the way."),
                        BACKGROUND, AdvancementType.TASK, false, false, false)
                .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
                .save(saver, id("root"));
        AdvancementHolder codex = deed("open_the_codex", root, ModItems.CODEX.get(), "First Page", BONE,
                "Open the Wayfarer's Codex and read where the road begins", AdvancementType.TASK,
                b -> b.addCriterion("opened", ModTriggers.CODEX_OPENED.get().any()));

        // ---- the ores ----
        AdvancementHolder glimmer = deed("glimmer", codex, ModItems.RAW.get(Material.SOLARITE).get(), "A Glimmer in the Rock", GOLD,
                "Mine any legendary ore", AdvancementType.TASK, b -> {
                    for (Material m : all) b.addCriterion(m.rawId(), has(ModItems.RAW.get(m).get()));
                    b.requirements(AdvancementRequirements.Strategy.OR);
                });
        String[][] ores = {
                {"Shard of Nothing", "Mine Void Crystal on the End's outer islands"},
                {"Captured Daylight", "Mine Solarite beneath badlands and deserts"},
                {"Ice in the Veins", "Mine Frostheart in the frozen peaks"},
                {"Blood from a Stone", "Mine Bloodstone in the Nether's crimson forests"},
                {"Caught Lightning", "Mine Thundercryst high in the windswept hills"},
                {"Grave Matters", "Mine Necrostone in the Deep Dark, by the ancient cities"},
                {"Splinter of a Soul", "Mine Soulshard in the soul sand valleys"},
                {"Root and Bloom", "Mine Verdant ore in the lush caves"},
                {"Between Day and Night", "Find the rare Eclipsite deep in the overworld's caves"},
                {"Fallen Star", "Mine Astralite on the End's highland islands"},
                {"Breath of the Sky", "Mine Aetherite under the high meadows and cherry groves"},
                {"Bones of the Mountain", "Mine Titanstone in the deepslate below"},
                {"Ember in the Rock", "Mine Infernite beside the lava of the basalt deltas"}};
        for (int i = 0; i < all.length; i++) {
            Material m = all[i];
            Item raw = ModItems.RAW.get(m).get();
            deed("ore_" + m.rawId(), glimmer, raw, ores[i][0], m.color(), ores[i][1], AdvancementType.TASK, b -> b.addCriterion("has", has(raw)));
        }
        deed("every_ore", glimmer, ModItems.ORES.get(Material.ECLIPSITE).get(), "Prospector of Legends", VIOLET,
                "Mine all thirteen legendary ores, in all three realms", AdvancementType.CHALLENGE, b -> {
                    for (Material m : all) b.addCriterion(m.rawId(), has(ModItems.RAW.get(m).get()));
                });

        // ---- the refinery ----
        AdvancementHolder refinery = deed("arcane_refinery", glimmer, ModItems.ARCANE_REFINERY.get(), "Arcane Engineering", ARCANE,
                "Build an Arcane Refinery", AdvancementType.TASK, b -> b.addCriterion("has", has(ModItems.ARCANE_REFINERY.get())));
        AdvancementHolder core = deed("first_core", refinery, ModItems.REFINED.get(Material.INFERNITE).get(), "Purified by Fire", EMBER,
                "Refine eight raw ores and eight of their additive into a core", AdvancementType.TASK, b -> {
                    for (Material m : all) b.addCriterion(m.refinedId(), has(ModItems.REFINED.get(m).get()));
                    b.requirements(AdvancementRequirements.Strategy.OR);
                });
        deed("every_core", core, ModItems.REFINED.get(Material.ASTRALITE).get(), "Master Alchemist", VIOLET,
                "Refine every one of the thirteen cores", AdvancementType.CHALLENGE, b -> {
                    for (Material m : all) b.addCriterion(m.refinedId(), has(ModItems.REFINED.get(m).get()));
                });

        // ---- the table ----
        AdvancementHolder table = deed("legendary_table", core, ModItems.LEGENDARY_CRAFTING_TABLE.get(), "A Table of Trophies", GOLD,
                "Build the Legendary Crafting Table from the spoils of raids, the Wither, the Dragon and the trial chambers",
                AdvancementType.GOAL, b -> b.addCriterion("has", has(ModItems.LEGENDARY_CRAFTING_TABLE.get())));
        AdvancementHolder hammer = deed("first_forging", table, Items.ANVIL, "The Hammer Falls", EMBER,
                "Lay a legendary pattern on the table and start forging it", AdvancementType.TASK,
                b -> b.addCriterion("forged", ModTriggers.FORGE_STARTED.get().any()));

        // ---- the weapons ----
        AdvancementHolder reborn = deed("legend_reborn", hammer, ModItems.WEAPONS.get("solaris").get(), "Legend Reborn", ARCANE,
                "Forge a legendary weapon", AdvancementType.GOAL, b -> {
                    for (WeaponDefinition def : Weapons.legendaries()) b.addCriterion(def.id(), has(ModItems.WEAPONS.get(def.id()).get()));
                    b.requirements(AdvancementRequirements.Strategy.OR);
                });
        String[][] weapons = {
                {"voidfang", "The Void Bites", "Forge the Voidfang"},
                {"solaris", "Dawn in Hand", "Forge Solaris"},
                {"frostrend", "Winter's Edge", "Forge the Frostrend"},
                {"doomcleaver", "Doom Given Form", "Forge the Doomcleaver"},
                {"stormbreaker", "Thunder Answers", "Forge the Stormbreaker"},
                {"gravebite", "The Grave Hungers", "Forge the Gravebite"},
                {"soulreaper", "Reaper of Souls", "Forge the Soulreaper"},
                {"bloomfall", "Bloom and Fall", "Forge Bloomfall"},
                {"eclipse_reaper", "Light and Shadow", "Forge the Eclipse Reaper"},
                {"starforge", "Starborn", "Forge the Starforge"},
                {"aetherlance", "Skypiercer", "Forge the Aetherlance"},
                {"monolith", "The Unmoving Stone", "Forge the Monolith"},
                {"infernochain", "Chains of the Inferno", "Forge the Infernochain"},
                {"cinderspire", "The Spire Kindles", "Forge the Cinderspire"},
                {"pyre_codex", "The Pyre Opens", "Bind the Pyre Codex"},
                {"rimeheart", "A Heart of Rime", "Forge the Rimeheart"},
                {"frostbound_grimoire", "The Book That Keeps", "Bind the Frostbound Grimoire"},
                {"tidecaller", "The Tide Answers", "Forge the Tidecaller"},
                {"codex_of_tides", "Mariner's Mercy", "Bind the Codex of Tides"},
                {"thornwake", "The First Green", "Forge the Thornwake"},
                {"verdant_almanac", "An Early Spring", "Bind the Verdant Almanac"},
                {"sunwarden", "Herald of the Dawn", "Forge the Sunwarden"},
                {"lumen_psalter", "The Choir Sings", "Bind the Lumen Psalter"},
                {"gloamreach", "The Long Dusk", "Forge the Gloamreach"},
                {"umbral_codex", "Small Deaths", "Bind the Umbral Codex"}};
        for (String[] w : weapons) {
            Item weapon = ModItems.WEAPONS.get(w[0]).get();
            deed("weapon_" + w[0], reborn, weapon, w[1], Material.ofWeapon(w[0]).color(), w[2], AdvancementType.GOAL, b -> b.addCriterion("has", has(weapon)));
        }
        deed("the_thirteen", reborn, ModItems.WEAPONS.get("monolith").get(), "The Thirteen", VIOLET,
                "Forge every one of the thirteen legendary weapons", AdvancementType.CHALLENGE, b -> {
                    for (WeaponDefinition def : Weapons.blades()) b.addCriterion(def.id(), has(ModItems.WEAPONS.get(def.id()).get()));
                });
        deed("archmage", reborn, ModItems.WEAPONS.get("lumen_psalter").get(), "Archmage", ARCANE,
                "Forge every staff and bind every spellbook of the six elements", AdvancementType.CHALLENGE, b -> {
                    for (WeaponDefinition def : Weapons.focuses()) b.addCriterion(def.id(), has(ModItems.WEAPONS.get(def.id()).get()));
                });

        // ---- mastery ----
        AdvancementHolder blood = deed("first_blood", reborn, Items.IRON_SWORD, "First Blood", CRIMSON,
                "Slay a foe with a legendary weapon", AdvancementType.TASK, b -> b.addCriterion("kill", ModTriggers.WEAPON_MILESTONE.get().kills(1)));
        AdvancementHolder centurion = deed("centurion", blood, Items.SKELETON_SKULL, "Centurion", CRIMSON,
                "Slay a hundred foes with one legendary weapon", AdvancementType.GOAL, b -> b.addCriterion("kills", ModTriggers.WEAPON_MILESTONE.get().kills(100)));
        deed("thousand_souls", centurion, Items.WITHER_SKELETON_SKULL, "A Thousand Souls", VIOLET,
                "Slay a thousand foes with one legendary weapon", AdvancementType.CHALLENGE, b -> b.addCriterion("kills", ModTriggers.WEAPON_MILESTONE.get().kills(1000)));
        deed("giant_slayer", blood, Items.DRAGON_HEAD, "Giant Slayer", GOLD,
                "Fell a boss with a legendary weapon", AdvancementType.GOAL, b -> b.addCriterion("boss", ModTriggers.WEAPON_MILESTONE.get().bossKills(1)));
        AdvancementHolder blooded = deed("level_10", reborn, Items.EXPERIENCE_BOTTLE, "Blooded", BONE,
                "Raise a legendary weapon to level 10", AdvancementType.TASK, b -> b.addCriterion("level", ModTriggers.WEAPON_MILESTONE.get().level(10)));
        AdvancementHolder seasoned = deed("level_30", blooded, Items.ENCHANTED_BOOK, "Seasoned", ARCANE,
                "Raise a legendary weapon to level 30", AdvancementType.GOAL, b -> b.addCriterion("level", ModTriggers.WEAPON_MILESTONE.get().level(30)));
        AdvancementHolder champion = deed("level_50", seasoned, Items.TOTEM_OF_UNDYING, "Champion", GOLD,
                "Raise a legendary weapon to level 50", AdvancementType.GOAL, b -> b.addCriterion("level", ModTriggers.WEAPON_MILESTONE.get().level(50)));
        deed("level_100", champion, Items.NETHER_STAR, "Mythic", VIOLET,
                "Raise a legendary weapon to level 100, its last", AdvancementType.CHALLENGE, b -> b.addCriterion("level", ModTriggers.WEAPON_MILESTONE.get().level(100)));
        AdvancementHolder unleashed = deed("first_ability", reborn, Items.BLAZE_POWDER, "Unleashed", EMBER,
                "Cast one of a legendary weapon's abilities", AdvancementType.TASK, b -> b.addCriterion("cast", ModTriggers.ABILITY_CAST.get().any()));
        deed("ultimate", unleashed, Items.END_CRYSTAL, "Cataclysm", VIOLET,
                "Unleash a legendary weapon's ultimate", AdvancementType.GOAL, b -> b.addCriterion("cast", ModTriggers.ABILITY_CAST.get().named("ultimate")));
        deed("infused", reborn, ModItems.REFINED.get(Material.VOID_CRYSTAL).get(), "Infused", ARCANE,
                "Infuse a legendary weapon with its refined cores", AdvancementType.TASK, b -> b.addCriterion("infused", ModTriggers.INFUSED.get().any()));

        // ---- armor ----
        AdvancementHolder plated = deed("legendary_armor", hammer, ModItems.ARMOR.get(ArmorSet.SOLARIS).get(ArmorItem.Type.HELMET).get(),
                "Plated in Legend", GOLD, "Forge a piece of legendary armor", AdvancementType.TASK, b -> {
                    for (ArmorSet set : ArmorSet.values()) for (ArmorItem.Type t : ArmorSet.PIECES) b.addCriterion(set.itemId(t), has(ModItems.ARMOR.get(set).get(t).get()));
                    b.requirements(AdvancementRequirements.Strategy.OR);
                });
        AdvancementHolder regalia = deed("full_set", plated, ModItems.ARMOR.get(ArmorSet.SOLARIS).get(ArmorItem.Type.CHESTPLATE).get(),
                "Full Regalia", ARCANE, "Wear a whole legendary armor set and wake its power", AdvancementType.GOAL,
                b -> b.addCriterion("worn", ModTriggers.FULL_SET.get().any()));
        deed("every_set", regalia, ModItems.ARMOR.get(ArmorSet.STARFORGE).get(ArmorItem.Type.CHESTPLATE).get(), "Wardrobe of Legends", VIOLET,
                "Wear every one of the thirteen legendary armor sets", AdvancementType.CHALLENGE, b -> {
                    for (ArmorSet set : ArmorSet.values()) b.addCriterion(set.id(), ModTriggers.FULL_SET.get().named(set.id()));
                });
    }

    private AdvancementHolder deed(String name, AdvancementHolder parent, ItemLike icon, String title, int color, String description,
                                   AdvancementType type, Consumer<Advancement.Builder> criteria) {
        Advancement.Builder b = Advancement.Builder.advancement().parent(parent)
                .display(icon, title(name, title, color), desc(name, description), null, type, false, false, false);
        criteria.accept(b);
        return b.save(saver, id(name));
    }

    private static Component title(String name, String text, int color) {
        return Component.translatableWithFallback("advancements.fantasyweapons." + name + ".title", text).withColor(color);
    }

    private static Component desc(String name, String text) {
        return Component.translatableWithFallback("advancements.fantasyweapons." + name + ".description", text);
    }

    private static net.minecraft.advancements.Criterion<InventoryChangeTrigger.TriggerInstance> has(ItemLike item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(item);
    }

    private static String id(String name) {
        return FantasyWeapons.id(name).toString();
    }
}
