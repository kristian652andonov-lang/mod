package com.fantasyweapons.quest;

import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModBlocks;
import com.fantasyweapons.registry.ModItems;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every quest of the journal: the first steps on the road, each ore found and mined deep, each core refined, each weapon
 * forged and mastered, each armor set worn, the great hunts, the journeys to the other realms and the arts of mastery.
 * Rewards are ores and their additives, cores, gems, netherite and the rarest of treasures, growing with the quest.
 */
public final class Quests {
    private static List<Quest> all;
    private static Map<String, Quest> byId;

    private Quests() {
    }

    public static List<Quest> all() {
        if (all == null) build();
        return all;
    }

    @Nullable
    public static Quest get(String id) {
        if (byId == null) build();
        return byId.get(id);
    }

    public static List<Quest> of(Chapter chapter) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : all()) if (q.chapter() == chapter) out.add(q);
        return out;
    }

    private static void build() {
        List<Quest> list = new ArrayList<>();
        firstSteps(list);
        ores(list);
        refining(list);
        weapons(list);
        armor(list);
        hunts(list);
        journeys(list);
        mastery(list);
        tools(list);
        Map<String, Quest> map = new LinkedHashMap<>();
        for (Quest q : list) map.put(q.id(), q);
        all = Collections.unmodifiableList(list);
        byId = map;
    }

    // ---- the chapters ----

    private static void firstSteps(List<Quest> list) {
        list.add(quest("first_page", Chapter.FIRST_STEPS, "The First Page", "Every legend starts with a book. Open the Wayfarer's Codex and read where the road begins.",
                () -> stack(ModItems.CODEX.get(), 1))
                .count("codex", 1, "Open the Wayfarer's Codex", () -> stack(ModItems.CODEX.get(), 1))
                .reward(Items.BREAD, 12).reward(Items.TORCH, 32).reward(Items.IRON_INGOT, 6).xp(30).build());
        list.add(quest("diamond_pick", Chapter.FIRST_STEPS, "A Pick Worthy of Legends", "Only diamond breaks the legendary ores. Make yourself a diamond pickaxe.",
                () -> stack(Items.DIAMOND_PICKAXE, 1))
                .collect(() -> Items.DIAMOND_PICKAXE, 1, "Hold a Diamond Pickaxe")
                .reward(Items.DIAMOND, 2).reward(Items.LAPIS_LAZULI, 16).xp(50).requires("first_page").build());
        list.add(quest("arcane_engineering", Chapter.FIRST_STEPS, "Arcane Engineering", "Raw legendary ore is useless until it is refined. Build an Arcane Refinery.",
                () -> stack(ModItems.ARCANE_REFINERY.get(), 1))
                .collect(() -> ModItems.ARCANE_REFINERY.get(), 1, "Hold an Arcane Refinery")
                .reward(Items.COAL_BLOCK, 8).reward(Items.BLAZE_ROD, 4).reward(Items.LAVA_BUCKET, 1).xp(80).requires("diamond_pick").build());
        list.add(quest("trophy_table", Chapter.FIRST_STEPS, "A Table of Trophies", "Raid, Wither, Dragon and trial chamber: the Legendary Crafting Table is built from all their spoils.",
                () -> stack(ModItems.LEGENDARY_CRAFTING_TABLE.get(), 1))
                .collect(() -> ModItems.LEGENDARY_CRAFTING_TABLE.get(), 1, "Hold the Legendary Crafting Table")
                .reward(Items.DIAMOND, 6).reward(Items.GOLDEN_APPLE, 3).reward(Items.NETHERITE_INGOT, 1).xp(300).requires("arcane_engineering").build());
        list.add(quest("hammer_falls", Chapter.FIRST_STEPS, "The Hammer Falls", "Lay a legendary pattern on the table and start forging it.",
                () -> stack(Items.ANVIL, 1))
                .count("forge", 1, "Start a forging at the table", () -> stack(Items.ANVIL, 1))
                .reward(Items.TOTEM_OF_UNDYING, 1).reward(Items.EMERALD, 12).xp(150).requires("trophy_table").build());
    }

    private static void ores(List<Quest> list) {
        for (Material m : Material.values()) {
            String raw = m.rawId(), ore = BuiltInRegistries.BLOCK.getKey(ModBlocks.ORES.get(m).get()).toString();
            QuestGroup group = new QuestGroup("ore_" + raw, m.materialName(), () -> stack(ModItems.ORES.get(m).get(), 1));
            list.add(quest("prospect_" + raw, Chapter.ORES, "Prospector: " + m.materialName(), m.found() + ". Find its ore and break into it.",
                    () -> stack(ModItems.ORES.get(m).get(), 1))
                    .count("mine:" + ore, 8, "Mine 8 " + m.oreName(), () -> stack(ModItems.ORES.get(m).get(), 1))
                    .reward(() -> stack(ModItems.RAW.get(m).get(), 8)).reward(() -> stack(m.additive(), 8)).reward(Items.EMERALD, 4).xp(60).group(group).build());
            list.add(quest("vein_" + raw, Chapter.ORES, "Deep Vein: " + m.materialName(), "A prospector finds one ore; a legend empties the vein. Mine its ore by the dozen.",
                    () -> stack(ModItems.ORES.get(m).get(), 1))
                    .count("mine:" + ore, 32, "Mine 32 " + m.oreName(), () -> stack(ModItems.ORES.get(m).get(), 1))
                    .reward(() -> stack(ModItems.RAW.get(m).get(), 16)).reward(() -> stack(m.additive(), 16)).reward(Items.DIAMOND, 3).xp(200)
                    .requires("prospect_" + raw).group(group).build());
        }
    }

    private static void refining(List<Quest> list) {
        for (Material m : Material.values()) {
            list.add(quest("refine_" + m.refinedId(), Chapter.REFINING, "Purified: " + m.refinedName(),
                    "Melt its raw ore and additive down in the Arcane Refinery, eight and eight to a core.", () -> stack(ModItems.REFINED.get(m).get(), 1))
                    .collect(() -> ModItems.REFINED.get(m).get(), 4, "Hold 4 " + m.refinedName() + "s")
                    .reward(() -> stack(ModItems.REFINED.get(m).get(), 1)).reward(() -> stack(m.additive(), 16)).reward(Items.DIAMOND, 1).xp(120)
                    .requires("prospect_" + m.rawId()).build());
        }
        Builder b = quest("master_alchemist", Chapter.REFINING, "Master Alchemist", "Hold one of every legendary core at once: all thirteen, from Void to Infernite.",
                () -> stack(ModItems.REFINED.get(Material.ASTRALITE).get(), 1));
        for (Material m : Material.values()) b.collect(() -> ModItems.REFINED.get(m).get(), 1, m.refinedName());
        list.add(b.reward(Items.NETHERITE_INGOT, 2).reward(Items.ENCHANTED_GOLDEN_APPLE, 1).xp(800).build());
    }

    private static void weapons(List<Quest> list) {
        for (WeaponDefinition def : Weapons.legendaries()) {
            String id = def.id(), name = def.displayName();
            Material m = Material.ofWeapon(id);
            Supplier<ItemStack> icon = () -> stack(ModItems.WEAPONS.get(id).get(), 1);
            QuestGroup group = new QuestGroup("weapon_" + id, name, icon);
            list.add(quest("forge_" + id, Chapter.WEAPONS, "Forged: " + name, "Lay the " + name + "'s pattern on the Legendary Crafting Table and forge it.", icon)
                    .collect(() -> ModItems.WEAPONS.get(id).get(), 1, "Hold the " + name)
                    .reward(() -> stack(ModItems.REFINED.get(m).get(), 4)).reward(Items.EXPERIENCE_BOTTLE, 16).xp(400).group(group).build());
            list.add(quest("blooded_" + id, Chapter.WEAPONS, "Blooded: " + name, "Fight with the " + name + " until it reaches level 25.", icon)
                    .reach(id, 25, "Raise the " + name + " to level 25", icon)
                    .reward(() -> stack(ModItems.REFINED.get(m).get(), 3)).reward(Items.DIAMOND, 4).reward(Items.NETHERITE_SCRAP, 2).xp(500)
                    .requires("forge_" + id).group(group).build());
            list.add(quest("champion_" + id, Chapter.WEAPONS, "Champion: " + name, "Fight on until the " + name + " reaches level 50.", icon)
                    .reach(id, 50, "Raise the " + name + " to level 50", icon)
                    .reward(() -> stack(ModItems.REFINED.get(m).get(), 6)).reward(Items.NETHERITE_INGOT, 1).reward(Items.ENCHANTED_GOLDEN_APPLE, 1).xp(1200)
                    .requires("blooded_" + id).group(group).build());
        }
        Builder b = quest("the_thirteen", Chapter.WEAPONS, "The Thirteen", "Hold every one of the thirteen legendary weapons at once.",
                () -> stack(ModItems.WEAPONS.get("monolith").get(), 1));
        for (WeaponDefinition def : Weapons.blades()) b.collect(() -> ModItems.WEAPONS.get(def.id()).get(), 1, def.displayName());
        list.add(b.reward(Items.NETHER_STAR, 1).reward(Items.ENCHANTED_GOLDEN_APPLE, 3).reward(Items.NETHERITE_INGOT, 8).xp(3000).build());
        Builder mage = quest("archmage", Chapter.WEAPONS, "Archmage", "Hold every staff and spellbook of the six elements at once.",
                () -> stack(ModItems.WEAPONS.get("lumen_psalter").get(), 1));
        for (WeaponDefinition def : Weapons.focuses()) mage.collect(() -> ModItems.WEAPONS.get(def.id()).get(), 1, def.displayName());
        list.add(mage.reward(Items.NETHER_STAR, 1).reward(Items.ENCHANTED_GOLDEN_APPLE, 3).reward(Items.EXPERIENCE_BOTTLE, 64).xp(3000).build());
        ultimates(list);
    }

    /** The Worldforge and the two ultimate weapons forged on it, and what they do to the world. */
    private static void ultimates(List<Quest> list) {
        String sword = com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration.ID, book = com.fantasyweapons.weapons.domination.SpellBookOfDomination.ID;
        Supplier<ItemStack> forge = () -> stack(ModItems.WORLDFORGE.get(), 1);
        QuestGroup group = new QuestGroup("ultimate_weapons", "The Ultimate Weapons", () -> stack(ModItems.WEAPONS.get(sword).get(), 1));
        list.add(quest("raise_worldforge", Chapter.WEAPONS, "The Worldforge", "Raise the Worldforge: a Legendary Crafting Table on netherite, "
                + "with nether stars, heavy cores and the Dragon's egg. It needs a clear space 21 blocks wide and deep and 22 high, and rises in front of you.", forge)
                .collect(() -> ModItems.WORLDFORGE.get(), 1, "Hold the Worldforge")
                .reward(Items.NETHERITE_INGOT, 2).reward(Items.DIAMOND, 8).xp(2000).requires("trophy_table").group(group).build());
        list.add(quest("first_rite", Chapter.WEAPONS, "The Rite Begins", "Lay a whole rite on the Worldforge's eleven-by-eleven grid, "
                + "every legendary weapon among it, and begin the forging.", () -> stack(Items.NETHER_STAR, 1))
                .count("worldforge", 1, "Begin a rite on the Worldforge", () -> stack(Items.NETHER_STAR, 1))
                .reward(Items.ENCHANTED_GOLDEN_APPLE, 2).reward(Items.EXPERIENCE_BOTTLE, 32).xp(3000).requires("raise_worldforge").group(group).build());
        list.add(quest("forge_" + sword, Chapter.WEAPONS, "Obliteration", "Forge the Great Sword of Obliteration, the end of all things.",
                () -> stack(ModItems.WEAPONS.get(sword).get(), 1))
                .collect(() -> ModItems.WEAPONS.get(sword).get(), 1, "Hold the Great Sword of Obliteration")
                .reward(Items.NETHER_STAR, 2).reward(Items.ENCHANTED_GOLDEN_APPLE, 4).xp(10000).requires("first_rite").group(group).build());
        list.add(quest("forge_" + book, Chapter.WEAPONS, "Absolute Domination", "Bind the Spell Book of Absolute Domination, the last word.",
                () -> stack(ModItems.WEAPONS.get(book).get(), 1))
                .collect(() -> ModItems.WEAPONS.get(book).get(), 1, "Hold the Spell Book of Absolute Domination")
                .reward(Items.NETHER_STAR, 2).reward(Items.ENCHANTED_GOLDEN_APPLE, 4).xp(10000).requires("first_rite").group(group).build());
        list.add(quest("world_ender", Chapter.WEAPONS, "World Ender", "Unleash one of the powers of an ultimate weapon, and watch the land break.",
                () -> stack(Items.TNT, 1))
                .count("apocalyptic", 1, "Unleash a power of an ultimate weapon", () -> stack(Items.TNT, 1))
                .reward(Items.TOTEM_OF_UNDYING, 2).reward(Items.NETHERITE_INGOT, 2).xp(4000).requires("first_rite").group(group).build());
        list.add(quest("dominion_sight", Chapter.WEAPONS, "Dominion Sight", "Open the targeting map and bring a power down on a place far from where you stand.",
                () -> stack(Items.FILLED_MAP, 1))
                .count("from_afar", 1, "Cast a power through the targeting map", () -> stack(Items.FILLED_MAP, 1))
                .reward(Items.ENDER_EYE, 8).reward(Items.DIAMOND, 6).xp(3000).requires("world_ender").group(group).build());
        list.add(quest("end_of_days", Chapter.WEAPONS, "The End of Days", "Unleash the powers of the ultimate weapons twelve times.",
                () -> stack(Items.WITHER_SKELETON_SKULL, 1))
                .count("apocalyptic", 12, "Unleash 12 powers of the ultimate weapons", () -> stack(Items.TNT, 1))
                .reward(Items.NETHER_STAR, 3).reward(Items.NETHERITE_BLOCK, 1).xp(15000).requires("world_ender").group(group).build());
    }

    private static void armor(List<Quest> list) {
        for (ArmorSet set : ArmorSet.values()) {
            Material m = set.material();
            list.add(quest("wear_" + set.id(), Chapter.ARMOR, "Regalia: " + set.displayName(), "Forge all four pieces of the " + set.displayName()
                    + " armor and wear them together, until the set's power wakes.", () -> stack(ModItems.ARMOR.get(set).get(ArmorItem.Type.CHESTPLATE).get(), 1))
                    .count("wear:" + set.id(), 1, "Wear the full " + set.displayName() + " set", () -> stack(ModItems.ARMOR.get(set).get(ArmorItem.Type.HELMET).get(), 1))
                    .reward(() -> stack(ModItems.RAW.get(m).get(), 8)).reward(Items.NETHERITE_SCRAP, 4).reward(Items.DIAMOND, 2).xp(600).build());
        }
        Builder b = quest("wardrobe", Chapter.ARMOR, "Wardrobe of Legends", "Wear every one of the thirteen warriors' legendary armor sets.",
                () -> stack(ModItems.ARMOR.get(ArmorSet.STARFORGE).get(ArmorItem.Type.CHESTPLATE).get(), 1));
        for (ArmorSet set : ArmorSet.values()) {
            if (!set.regalia()) b.count("wear:" + set.id(), 1, set.displayName(), () -> stack(ModItems.ARMOR.get(set).get(ArmorItem.Type.HELMET).get(), 1));
        }
        list.add(b.reward(Items.TOTEM_OF_UNDYING, 2).reward(Items.NETHERITE_INGOT, 4).xp(2500).build());
        Builder robes = quest("robes_of_the_elements", Chapter.ARMOR, "Robes of Every Element", "Wear every one of the Mage's six regalia.",
                () -> stack(ModItems.ARMOR.get(ArmorSet.LUMINARCH).get(ArmorItem.Type.CHESTPLATE).get(), 1));
        for (ArmorSet set : ArmorSet.values()) {
            if (set.regalia()) robes.count("wear:" + set.id(), 1, set.displayName(), () -> stack(ModItems.ARMOR.get(set).get(ArmorItem.Type.HELMET).get(), 1));
        }
        list.add(robes.reward(Items.TOTEM_OF_UNDYING, 2).reward(Items.EXPERIENCE_BOTTLE, 64).xp(2500).build());
    }

    private static void hunts(List<Quest> list) {
        Supplier<ItemStack> sword = () -> stack(Items.IRON_SWORD, 1);
        list.add(quest("hunter_50", Chapter.HUNTS, "Hunter", "Slay 50 hostile creatures with a legendary weapon.", sword)
                .count("kill:hostile", 50, "Slay 50 hostiles with a legendary weapon", () -> stack(Items.ROTTEN_FLESH, 1))
                .reward(Items.EMERALD, 16).reward(Items.GOLDEN_APPLE, 4).xp(200).build());
        list.add(quest("hunter_250", Chapter.HUNTS, "Slayer", "Slay 250 hostile creatures with a legendary weapon.", () -> stack(Items.SKELETON_SKULL, 1))
                .count("kill:hostile", 250, "Slay 250 hostiles with a legendary weapon", () -> stack(Items.BONE, 1))
                .reward(Items.DIAMOND, 8).reward(Items.NETHERITE_SCRAP, 2).xp(600).requires("hunter_50").build());
        list.add(quest("hunter_1000", Chapter.HUNTS, "Legend of the Hunt", "Slay a thousand hostile creatures with a legendary weapon.",
                () -> stack(Items.WITHER_SKELETON_SKULL, 1))
                .count("kill:hostile", 1000, "Slay 1000 hostiles with a legendary weapon", () -> stack(Items.WITHER_SKELETON_SKULL, 1))
                .reward(Items.NETHERITE_INGOT, 2).reward(Items.ENCHANTED_GOLDEN_APPLE, 1).xp(2000).requires("hunter_250").build());
        hunt(list, "slay_wither", EntityType.WITHER, "The Wither Falls", "Bring down the Wither with a legendary weapon.", Items.NETHER_STAR,
                new Reward[]{r(Items.NETHERITE_INGOT, 2), r(Items.GOLDEN_APPLE, 4)}, 1500);
        hunt(list, "slay_dragon", EntityType.ENDER_DRAGON, "Dragonslayer", "Slay the Ender Dragon with a legendary weapon.", Items.DRAGON_HEAD,
                new Reward[]{r(Items.NETHERITE_INGOT, 3), r(Items.ELYTRA, 1)}, 2000);
        hunt(list, "slay_warden", EntityType.WARDEN, "Silence the Warden", "Slay a Warden with a legendary weapon.", Items.SCULK_CATALYST,
                new Reward[]{r(Items.ECHO_SHARD, 8), r(Items.NETHERITE_INGOT, 2)}, 1500);
        hunt(list, "slay_elder_guardian", EntityType.ELDER_GUARDIAN, "Lord of the Monument", "Slay an Elder Guardian with a legendary weapon.", Items.PRISMARINE_CRYSTALS,
                new Reward[]{r(Items.HEART_OF_THE_SEA, 1), r(Items.NETHERITE_SCRAP, 2), r(Items.SPONGE, 8)}, 800);
        hunt(list, "slay_ravager", EntityType.RAVAGER, "Ravager Breaker", "Slay a Ravager with a legendary weapon.", Items.SADDLE,
                new Reward[]{r(Items.EMERALD_BLOCK, 2), r(Items.SADDLE, 1)}, 400);
        hunt(list, "slay_evoker", EntityType.EVOKER, "Spellbreaker", "Slay an Evoker with a legendary weapon.", Items.TOTEM_OF_UNDYING,
                new Reward[]{r(Items.TOTEM_OF_UNDYING, 1), r(Items.EMERALD, 16)}, 400);
        hunt(list, "slay_piglin_brute", EntityType.PIGLIN_BRUTE, "Bastion Breaker", "Slay a Piglin Brute with a legendary weapon.", Items.GOLDEN_AXE,
                new Reward[]{r(Items.GOLD_BLOCK, 4), r(Items.NETHERITE_SCRAP, 1)}, 400);
        hunt(list, "slay_breeze", EntityType.BREEZE, "Calm the Storm", "Slay a Breeze with a legendary weapon.", Items.BREEZE_ROD,
                new Reward[]{r(Items.BREEZE_ROD, 4), r(Items.WIND_CHARGE, 16), r(Items.TRIAL_KEY, 2)}, 400);
        hunt(list, "slay_enderman", EntityType.ENDERMAN, "Void Walker", "Slay an Enderman with a legendary weapon.", Items.ENDER_PEARL,
                new Reward[]{r(Items.ENDER_PEARL, 8), r(Items.ENDER_EYE, 2)}, 200);
    }

    private static void hunt(List<Quest> list, String id, EntityType<?> type, String title, String description, ItemLike icon, Reward[] rewards, int xp) {
        String key = "kill:" + BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Builder b = quest(id, Chapter.HUNTS, title, description, () -> stack(icon, 1))
                .count(key, 1, "Slay " + type.getDescription().getString() + " with a legendary weapon", () -> stack(icon, 1));
        for (Reward r : rewards) b.reward(r.item, r.count);
        list.add(b.xp(xp).build());
    }

    private static void journeys(List<Quest> list) {
        list.add(quest("into_the_nether", Chapter.JOURNEYS, "Into the Nether", "Bloodstone, Soulshard and Infernite burn in the Nether. Go there.",
                () -> stack(Items.NETHERRACK, 1))
                .count("visit:minecraft:the_nether", 1, "Enter the Nether", () -> stack(Items.NETHERRACK, 1))
                .reward(Items.OBSIDIAN, 4).reward(Items.GOLD_INGOT, 8).reward(Items.FIRE_CHARGE, 4).xp(100).build());
        list.add(quest("into_the_end", Chapter.JOURNEYS, "Beyond the Portal", "Void Crystal and Astralite wait at the world's end. Go there.",
                () -> stack(Items.END_STONE, 1))
                .count("visit:minecraft:the_end", 1, "Enter the End", () -> stack(Items.END_STONE, 1))
                .reward(Items.ENDER_PEARL, 8).reward(Items.DIAMOND, 4).xp(300).requires("into_the_nether").build());
        list.add(quest("deep_dark", Chapter.JOURNEYS, "Into the Deep Dark", "Necrostone grows by the ancient cities. Go down into the Deep Dark.",
                () -> stack(Items.SCULK, 1))
                .count("visit:minecraft:deep_dark", 1, "Stand in the Deep Dark", () -> stack(Items.SCULK, 1))
                .reward(Items.ECHO_SHARD, 2).reward(Items.DIAMOND, 2).xp(200).build());
        list.add(quest("high_peaks", Chapter.JOURNEYS, "Where Lightning Lives", "Thundercryst and Frostheart lie high in the peaks. Climb above height 160.",
                () -> stack(Items.SNOW_BLOCK, 1))
                .count("visit:height", 1, "Climb above height 160", () -> stack(Items.SNOW_BLOCK, 1))
                .reward(Items.COPPER_BLOCK, 4).reward(Items.PACKED_ICE, 16).xp(150).build());
    }

    /** The legendary tools and the table that upgrades them. */
    private static void tools(List<Quest> list) {
        Supplier<ItemStack> pick = () -> stack(ModItems.tool(Material.VOID_CRYSTAL, com.fantasyweapons.tool.ToolKind.PICKAXE), 1);
        Supplier<ItemStack> table = () -> stack(ModItems.UPGRADING_TABLE.get(), 1);
        QuestGroup group = new QuestGroup("legendary_tools", "Legendary Tools", pick);
        list.add(quest("tools_of_legend", Chapter.MASTERY, "Tools of Legend", "Forge a legendary pickaxe, shovel or axe at the Legendary Crafting Table: "
                + "it digs a 3x3 square (an axe fells whole trees) with Fortune III and Efficiency V from the start.", pick)
                .holds(s -> s.getItem() instanceof com.fantasyweapons.tool.LegendaryTool, 1, "Hold a legendary tool", pick)
                .reward(Items.DIAMOND, 4).reward(Items.EXPERIENCE_BOTTLE, 16).xp(300).requires("trophy_table").group(group).build());
        list.add(quest("upgrade_bench", Chapter.MASTERY, "The Upgrader's Bench", "Build a Legendary Upgrading Table: a smithing table between an anvil "
                + "and a grindstone, in copper, over netherite.", table)
                .collect(() -> ModItems.UPGRADING_TABLE.get(), 1, "Hold a Legendary Upgrading Table")
                .reward(Items.COPPER_BLOCK, 8).reward(Items.EXPERIENCE_BOTTLE, 24).xp(300).requires("tools_of_legend").group(group).build());
        list.add(quest("honed", Chapter.MASTERY, "Honed", "Lay a legendary tool in the table's vise and its refined material in the cup, and raise "
                + "its mining area, Fortune or Efficiency.", table)
                .count("tool_upgrade", 1, "Upgrade a legendary tool", table)
                .reward(Items.EXPERIENCE_BOTTLE, 32).reward(Items.DIAMOND, 3).xp(500).requires("upgrade_bench").group(group).build());
        list.add(quest("earth_movers", Chapter.MASTERY, "Earth Movers", "Use the powers of the legendary tools: Ore Sight, Timberfall and Earthshaper.",
                () -> stack(Items.GRASS_BLOCK, 1))
                .count("tool_power", 10, "Use a legendary tool's power 10 times", () -> stack(Items.GRASS_BLOCK, 1))
                .reward(Items.DIAMOND, 3).reward(Items.GOLDEN_APPLE, 2).xp(400).requires("tools_of_legend").group(group).build());
        list.add(quest("excavator", Chapter.MASTERY, "Excavator", "Dig, fell and level the land with the legendary tools: their squares, felled trees and powers.",
                () -> stack(Items.STONE, 1))
                .count("dig_area", 5000, "Dig 5,000 blocks with a legendary tool", () -> stack(Items.STONE, 1))
                .reward(Items.NETHERITE_SCRAP, 4).reward(Items.EXPERIENCE_BOTTLE, 32).xp(1000).requires("tools_of_legend").group(group).build());
        list.add(quest("perfected", Chapter.MASTERY, "Perfected", "Raise a legendary tool as far as it goes: a 13x13 square, Fortune X and Efficiency X.",
                () -> stack(ModItems.tool(Material.ASTRALITE, com.fantasyweapons.tool.ToolKind.PICKAXE), 1))
                .count("tool_maxed", 1, "Fully upgrade a legendary tool", () -> stack(ModItems.tool(Material.ASTRALITE, com.fantasyweapons.tool.ToolKind.PICKAXE), 1))
                .reward(Items.NETHER_STAR, 1).reward(Items.NETHERITE_INGOT, 2).xp(3000).requires("honed").group(group).build());
    }

    private static void mastery(List<Quest> list) {
        list.add(quest("unleashed", Chapter.MASTERY, "Unleashed", "Cast one of a legendary weapon's abilities.", () -> stack(Items.BLAZE_POWDER, 1))
                .count("ability", 1, "Cast an ability", () -> stack(Items.BLAZE_POWDER, 1))
                .reward(Items.EXPERIENCE_BOTTLE, 8).xp(50).build());
        list.add(quest("adept", Chapter.MASTERY, "Adept", "Cast a hundred abilities.", () -> stack(Items.ENCHANTED_BOOK, 1))
                .count("ability", 100, "Cast 100 abilities", () -> stack(Items.BLAZE_POWDER, 1))
                .reward(Items.DIAMOND, 3).reward(Items.EXPERIENCE_BOTTLE, 24).xp(400).requires("unleashed").build());
        list.add(quest("archmage_of_arms", Chapter.MASTERY, "Master of Arms", "Cast a thousand abilities.", () -> stack(Items.ENCHANTED_BOOK, 1))
                .count("ability", 1000, "Cast 1000 abilities", () -> stack(Items.BLAZE_POWDER, 1))
                .reward(Items.NETHERITE_INGOT, 1).reward(Items.EXPERIENCE_BOTTLE, 64).xp(1500).requires("adept").build());
        list.add(quest("cataclysm", Chapter.MASTERY, "Cataclysm", "Unleash a legendary weapon's ultimate.", () -> stack(Items.END_CRYSTAL, 1))
                .count("ultimate", 1, "Unleash an ultimate", () -> stack(Items.END_CRYSTAL, 1))
                .reward(Items.DIAMOND, 4).reward(Items.GOLDEN_APPLE, 2).xp(500).build());
        list.add(quest("apocalypse", Chapter.MASTERY, "Apocalypse", "Unleash twenty-five ultimates.", () -> stack(Items.END_CRYSTAL, 1))
                .count("ultimate", 25, "Unleash 25 ultimates", () -> stack(Items.END_CRYSTAL, 1))
                .reward(Items.ENCHANTED_GOLDEN_APPLE, 1).reward(Items.NETHERITE_INGOT, 2).xp(2000).requires("cataclysm").build());
        list.add(quest("infused", Chapter.MASTERY, "Infused", "Craft a legendary weapon together with its refined cores to raise it at once.",
                () -> stack(ModItems.REFINED.get(Material.VOID_CRYSTAL).get(), 1))
                .count("infuse", 1, "Infuse a weapon with its cores", () -> stack(ModItems.REFINED.get(Material.VOID_CRYSTAL).get(), 1))
                .reward(Items.EXPERIENCE_BOTTLE, 16).reward(Items.DIAMOND, 2).xp(200).build());
        list.add(quest("mythic", Chapter.MASTERY, "Mythic", "Raise any legendary weapon to level 100, its last.", () -> stack(Items.NETHER_STAR, 1))
                .reach(null, 100, "Raise a legendary weapon to level 100", () -> stack(Items.NETHER_STAR, 1))
                .reward(Items.NETHERITE_INGOT, 4).reward(Items.NETHER_STAR, 1).xp(5000).build());
    }

    // ---- building ----

    private record Reward(ItemLike item, int count) {
    }

    private static Reward r(ItemLike item, int count) {
        return new Reward(item, count);
    }

    private static ItemStack stack(ItemLike item, int count) {
        return new ItemStack(item, count);
    }

    private static Builder quest(String id, Chapter chapter, String title, String description, Supplier<ItemStack> icon) {
        return new Builder(id, chapter, title, description, icon);
    }

    private static final class Builder {
        private final String id, title, description;
        private final Chapter chapter;
        private final Supplier<ItemStack> icon;
        private final List<QuestObjective> objectives = new ArrayList<>();
        private final List<Supplier<ItemStack>> rewards = new ArrayList<>();
        private int xp;
        private String requires;
        private QuestGroup group;

        Builder(String id, Chapter chapter, String title, String description, Supplier<ItemStack> icon) {
            this.id = id;
            this.chapter = chapter;
            this.title = title;
            this.description = description;
            this.icon = icon;
        }

        Builder count(String key, int target, String label, Supplier<ItemStack> icon) {
            objectives.add(new QuestObjective.Count(key, target, label, icon));
            return this;
        }

        Builder collect(Supplier<? extends ItemLike> item, int target, String label) {
            objectives.add(new QuestObjective.Collect(item, target, label));
            return this;
        }

        Builder holds(java.util.function.Predicate<ItemStack> test, int target, String label, Supplier<ItemStack> icon) {
            objectives.add(new QuestObjective.Holds(test, target, label, icon));
            return this;
        }

        Builder reach(String weapon, int level, String label, Supplier<ItemStack> icon) {
            objectives.add(new QuestObjective.Reach(weapon, level, label, icon));
            return this;
        }

        Builder reward(ItemLike item, int count) {
            rewards.add(() -> new ItemStack(item, count));
            return this;
        }

        Builder reward(Supplier<ItemStack> stack) {
            rewards.add(stack);
            return this;
        }

        Builder xp(int xp) {
            this.xp = xp;
            return this;
        }

        Builder requires(String id) {
            this.requires = id;
            return this;
        }

        Builder group(QuestGroup group) {
            this.group = group;
            return this;
        }

        Quest build() {
            return new Quest(id, chapter, title, description, icon, List.copyOf(objectives), List.copyOf(rewards), xp, requires, group);
        }
    }
}
