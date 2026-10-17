package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorClass;
import com.fantasyweapons.armor.ArmorSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One armor material per set: its class's defense and toughness, repaired with the set's refined material. */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> REGISTER = DeferredRegister.create(Registries.ARMOR_MATERIAL, FantasyWeapons.MOD_ID);
    public static final Map<ArmorSet, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIALS = new EnumMap<>(ArmorSet.class);

    static {
        for (ArmorSet set : ArmorSet.values()) {
            ArmorClass c = set.armorClass();
            MATERIALS.put(set, REGISTER.register(set.id(), () -> {
                Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                for (ArmorItem.Type t : ArmorItem.Type.values()) defense.put(t, c.defense(t));
                return new ArmorMaterial(defense, c.enchantability(), c.equipSound(),
                        () -> Ingredient.of(ModItems.REFINED.get(set.material()).get()),
                        // never drawn: GeckoLib draws the set's own model
                        List.of(new ArmorMaterial.Layer(FantasyWeapons.id(set.id()))), c.toughness(), c.knockbackResistance());
            }));
        }
    }

    private ModArmorMaterials() {
    }
}
