package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.material.MaterialItem;
import com.fantasyweapons.material.MaterialOreItem;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(FantasyWeapons.MOD_ID);

    public static final Map<String, DeferredItem<FantasyWeaponItem>> WEAPONS = new LinkedHashMap<>();
    public static final Map<Material, DeferredItem<MaterialOreItem>> ORES = new EnumMap<>(Material.class);
    public static final Map<Material, DeferredItem<MaterialItem>> RAW = new EnumMap<>(Material.class);
    public static final Map<Material, DeferredItem<MaterialItem>> REFINED = new EnumMap<>(Material.class);
    public static final Map<com.fantasyweapons.armor.ArmorSet, Map<net.minecraft.world.item.ArmorItem.Type, DeferredItem<com.fantasyweapons.armor.FantasyArmorItem>>> ARMOR =
            new EnumMap<>(com.fantasyweapons.armor.ArmorSet.class);

    public static final DeferredItem<com.fantasyweapons.refinery.ArcaneRefineryItem> ARCANE_REFINERY = REGISTER.register("arcane_refinery",
            () -> new com.fantasyweapons.refinery.ArcaneRefineryItem(ModBlocks.ARCANE_REFINERY.get(), new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<com.fantasyweapons.forge.LegendaryCraftingTableItem> LEGENDARY_CRAFTING_TABLE = REGISTER.register("legendary_crafting_table",
            () -> new com.fantasyweapons.forge.LegendaryCraftingTableItem(ModBlocks.LEGENDARY_CRAFTING_TABLE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<com.fantasyweapons.forge.WorldforgeItem> WORLDFORGE = REGISTER.register("worldforge",
            () -> new com.fantasyweapons.forge.WorldforgeItem(ModBlocks.WORLDFORGE.get(), new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    /** The guide every player is given on first joining. */
    public static final DeferredItem<com.fantasyweapons.guide.WayfarersCodexItem> CODEX = REGISTER.register("wayfarers_codex",
            () -> new com.fantasyweapons.guide.WayfarersCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    static {
        // the Legendary Forge became the Legendary Crafting Table when it got its model
        REGISTER.addAlias(FantasyWeapons.id("legendary_forge"), FantasyWeapons.id("legendary_crafting_table"));
        for (WeaponDefinition def : Weapons.all()) {
            WEAPONS.put(def.id(), REGISTER.register(def.id(),
                    () -> new FantasyWeaponItem(def, new Item.Properties().stacksTo(1).fireResistant())));
        }
        // legendary materials never burn, not even the ones dug out of the Nether
        for (Material m : Material.values()) {
            ORES.put(m, REGISTER.register(m.oreId(), () -> new MaterialOreItem(ModBlocks.ORES.get(m).get(),
                    new Item.Properties().rarity(Rarity.RARE).fireResistant())));
            RAW.put(m, REGISTER.register(m.rawId(), () -> new MaterialItem(m, false, new Item.Properties().rarity(Rarity.RARE).fireResistant())));
            REFINED.put(m, REGISTER.register(m.refinedId(), () -> new MaterialItem(m, true, new Item.Properties().rarity(Rarity.EPIC).fireResistant())));
        }
        for (var set : com.fantasyweapons.armor.ArmorSet.values()) {
            Map<net.minecraft.world.item.ArmorItem.Type, DeferredItem<com.fantasyweapons.armor.FantasyArmorItem>> pieces =
                    new EnumMap<>(net.minecraft.world.item.ArmorItem.Type.class);
            for (var type : com.fantasyweapons.armor.ArmorSet.PIECES) {
                pieces.put(type, REGISTER.register(set.itemId(type), () -> new com.fantasyweapons.armor.FantasyArmorItem(set,
                        ModArmorMaterials.MATERIALS.get(set), type, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                        .durability(type.getDurability(com.fantasyweapons.armor.ArmorClass.DURABILITY)))));
            }
            ARMOR.put(set, pieces);
        }
    }

    private ModItems() {
    }
}
