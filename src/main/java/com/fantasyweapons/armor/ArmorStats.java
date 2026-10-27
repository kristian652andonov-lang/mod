package com.fantasyweapons.armor;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

/** Wiring between the stats and their registered attributes. */
public final class ArmorStats {
    private ArmorStats() {
    }

    public static void bind(ArmorStat stat, Holder<Attribute> holder) {
        stat.holder = holder;
    }
}
