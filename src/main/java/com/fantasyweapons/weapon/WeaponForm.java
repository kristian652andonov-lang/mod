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
 */
public record WeaponForm(String id, String displayName, String label, int themePrimary, int themeSecondary,
                         @Nullable String enterAnimation, String idleAnimation, String attackAnimation,
                         float reachBonus, float damageFactor, float attackSpeedFactor) {
}
