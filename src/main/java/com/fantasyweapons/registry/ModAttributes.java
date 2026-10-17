package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.armor.ArmorStat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The armor stats as player attributes ({@code fantasyweapons:<stat>}, 0 without armor). */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> REGISTER = DeferredRegister.create(Registries.ATTRIBUTE, FantasyWeapons.MOD_ID);

    static {
        for (ArmorStat stat : ArmorStat.values()) {
            com.fantasyweapons.armor.ArmorStats.bind(stat, REGISTER.register(stat.id(),
                    () -> new RangedAttribute("attribute.name." + FantasyWeapons.MOD_ID + "." + stat.id(), 0, 0, 64).setSyncable(true)));
        }
    }

    private ModAttributes() {
    }

    public static void addToPlayers(EntityAttributeModificationEvent event) {
        for (ArmorStat stat : ArmorStat.values()) {
            if (!event.has(EntityType.PLAYER, stat.holder())) event.add(EntityType.PLAYER, stat.holder());
        }
    }
}
