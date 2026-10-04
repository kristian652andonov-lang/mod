package com.fantasyweapons.ability;

/** Presentation state of an ability, as shown by the HUD and menu. */
public enum AbilityState {
    LOCKED("LOCKED"),
    UNLOCKED("PASSIVE"),
    READY("READY"),
    CHARGING("CHARGING"),
    FULLY_CHARGED("FULL CHARGE"),
    ACTIVE("ACTIVE"),
    COOLDOWN("COOLDOWN");

    private final String label;

    AbilityState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
