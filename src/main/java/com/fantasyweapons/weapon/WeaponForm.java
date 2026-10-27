package com.fantasyweapons.weapon;

import org.jetbrains.annotations.Nullable;

/**
 * A switchable form/mode of a weapon (e.g. Infernochain SWORD / CHAINBLADE, Eclipse Reaper LIGHT / DARK).
 *
 * @param id              stable id stored in weapon data
 * @param displayName     shown in HUD and menu
 * @param label           "FORM" or "MODE"
 * @param themePrimary    UI/VFX primary colour while in this form
 * @param themeSecondary  UI/VFX secondary colour while in this form
 * @param enterAnimation  GeckoLib animation played when switching INTO this form (exact name from the asset pack)
 * @param idleAnimation   looping animation while in this form
 * @param attackAnimation animation used for normal attacks in this form
 * @param reachBonus      extra reach while in this form (added to the class reach)
 * @param damageFactor    multiplier on melee damage in this form
 * @param animationVariant suffix of this form's own versions of the weapon's other animations (generated, see
 *                         tools/gen_extra_animations.py), or null when the form plays the weapon's animations as made
 */
public record WeaponForm(String id, String displayName, String label, int themePrimary, int themeSecondary,
                         @Nullable String enterAnimation, String idleAnimation, String attackAnimation,
                         float reachBonus, float damageFactor, float attackSpeedFactor, @Nullable String animationVariant) {
    /** The form's name in the player's language. */
    @Override
    public String displayName() {
        return com.fantasyweapons.text.Tr.s(displayName);
    }

    @Override
    public String label() {
        return com.fantasyweapons.text.Tr.s(label);
    }

    /**
     * The animation this form plays for one of the weapon's animations: its own version when it has them (the
     * weapon's parts laid out the way this form holds them), else the animation itself. The form's idle and the
     * animation that brings it in are its own already.
     */
    public String animation(String name) {
        if (animationVariant == null || name.equals(idleAnimation) || name.equals(enterAnimation)) return name;
        return name + animationVariant;
    }

    /** Whether {@code fullName} (as GeckoLib names it, "animation.weapon.name") is one of this form's own animations. */
    public boolean owns(String fullName) {
        String name = fullName.substring(fullName.lastIndexOf('.') + 1);
        return name.equals(idleAnimation) || animationVariant != null && name.endsWith(animationVariant);
    }
}
