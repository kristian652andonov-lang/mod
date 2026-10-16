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
import com.fantasyweapons.weapons.cinderspire.Cinderspire;
import com.fantasyweapons.weapons.pyrecodex.PyreCodex;
import com.fantasyweapons.weapons.rimeheart.Rimeheart;
import com.fantasyweapons.weapons.frostbound.FrostboundGrimoire;
import com.fantasyweapons.weapons.tidecaller.Tidecaller;
import com.fantasyweapons.weapons.codexoftides.CodexOfTides;
import com.fantasyweapons.weapons.thornwake.Thornwake;
import com.fantasyweapons.weapons.verdantalmanac.VerdantAlmanac;
import com.fantasyweapons.weapons.sunwarden.Sunwarden;
import com.fantasyweapons.weapons.lumenpsalter.LumenPsalter;
import com.fantasyweapons.weapons.gloamreach.Gloamreach;
import com.fantasyweapons.weapons.umbralcodex.UmbralCodex;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the weapon definitions: the 13 legendary weapons in the order of the design brief, then the mage's twelve
 * staffs and spellbooks, element by element. Each weapon lives in its own package ({@code com.fantasyweapons.weapons.<name>}):
 * a definition class and its server-side abilities.
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
        // the mage's focuses: fire attacks, ice defends, water heals and shields, nature heals, light blesses, the void curses
        add(Cinderspire.create());
        add(PyreCodex.create());
        add(Rimeheart.create());
        add(FrostboundGrimoire.create());
        add(Tidecaller.create());
        add(CodexOfTides.create());
        add(Thornwake.create());
        add(VerdantAlmanac.create());
        add(Sunwarden.create());
        add(LumenPsalter.create());
        add(Gloamreach.create());
        add(UmbralCodex.create());
        // the two ultimate weapons, forged from all twenty-five on the Worldforge
        add(com.fantasyweapons.weapons.obliteration.GreatSwordOfObliteration.create());
        add(com.fantasyweapons.weapons.domination.SpellBookOfDomination.create());
    }

    /** The 13 legendary blades, without the mage's focuses. */
    public static List<WeaponDefinition> blades() {
        return legendaries().stream().filter(d -> !d.weaponClass().magic()).toList();
    }

    /** The mage's staffs and spellbooks. */
    public static List<WeaponDefinition> focuses() {
        return legendaries().stream().filter(d -> d.weaponClass().magic()).toList();
    }

    /** The 25 legendary weapons: everything but the two ultimate weapons. */
    public static List<WeaponDefinition> legendaries() {
        return all().stream().filter(d -> !d.rarity().ultimate()).toList();
    }

    /** The Great Sword of Obliteration and the Spell Book of Absolute Domination. */
    public static List<WeaponDefinition> ultimates() {
        return all().stream().filter(d -> d.rarity().ultimate()).toList();
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
