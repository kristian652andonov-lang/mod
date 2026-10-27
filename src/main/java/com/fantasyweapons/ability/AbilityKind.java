package com.fantasyweapons.ability;

import com.fantasyweapons.text.Tr;
public enum AbilityKind {
    /** Charged (or instant) ability activated with the ability key. */
    ACTIVE("Active"),
    /** Always-on effect once unlocked; upgrades strengthen it. */
    PASSIVE("Passive"),
    /** The weapon's capstone ability: long charge, long cooldown, devastating effect. */
    ULTIMATE("Ultimate");

    private final String displayName;

    AbilityKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return Tr.s(displayName);
    }

    public boolean castable() {
        return this != PASSIVE;
    }
}
