package com.fantasyweapons.weapon;

import com.fantasyweapons.weapons.aetherlance.Aetherlance;
import com.fantasyweapons.weapons.bloomfall.Bloomfall;
import com.fantasyweapons.weapons.doomcleaver.Doomcleaver;
import com.fantasyweapons.weapons.eclipse.EclipseReaper;
import com.fantasyweapons.weapons.frostrend.Frostrend;
import com.fantasyweapons.weapons.gravebite.Gravebite;
import com.fantasyweapons.weapons.infernochain.Infernochain;
import com.fantasyweapons.weapons.monolith.Monolith;
import com.fantasyweapons.weapons.solaris.Solaris;
import com.fantasyweapons.weapons.soulreaper.Soulreaper;
import com.fantasyweapons.weapons.starforge.Starforge;
import com.fantasyweapons.weapons.stormbreaker.Stormbreaker;
import com.fantasyweapons.weapons.voidfang.Voidfang;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the 13 weapon definitions, in the order of the design brief. Each weapon lives in its own package
 * ({@code com.fantasyweapons.weapons.<name>}): a definition class and its server-side abilities.
 */
public final class Weapons {
    private static final Map<String, WeaponDefinition> BY_ID = new LinkedHashMap<>();

    static {
        add(Voidfang.create());
        add(Solaris.create());
        add(Frostrend.create());
        add(Doomcleaver.create());
        add(Stormbreaker.create());
        add(Gravebite.create());
        add(Soulreaper.create());
        add(Bloomfall.create());
        add(EclipseReaper.create());
        add(Starforge.create());
        add(Aetherlance.create());
        add(Monolith.create());
        add(Infernochain.create());
    }

    private Weapons() {
    }

    private static void add(WeaponDefinition def) {
        if (BY_ID.put(def.id(), def) != null) throw new IllegalStateException("Duplicate weapon " + def.id());
    }

    public static List<WeaponDefinition> all() {
        return Collections.unmodifiableList(List.copyOf(BY_ID.values()));
    }

    public static WeaponDefinition get(String id) {
        return BY_ID.get(id);
    }
}
