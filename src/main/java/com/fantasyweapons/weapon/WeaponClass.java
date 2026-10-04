package com.fantasyweapons.weapon;

/**
 * Physical archetype of a weapon. Controls melee feel: attack speed, reach, how wide the swing cleaves,
 * knockback and the first-person swing style.
 *
 * @param attackSpeed   vanilla attack-speed attribute value (attacks per second at full charge)
 * @param reachBonus    extra entity interaction range in blocks
 * @param cleaveAngle   total horizontal arc (degrees) of secondary targets hit by a full-strength swing; 0 = single target
 * @param cleaveRange   max distance for secondary cleave targets
 * @param knockback     base knockback strength
 * @param twoHanded     held with both hands in third person
 */
public enum WeaponClass {
    LONGSWORD("Longsword", 1.4f, 0.75f, 70f, 3.6f, 0.45f, false, SwingStyle.SLASH),
    GREATSWORD("Greatsword", 0.95f, 1.25f, 110f, 4.2f, 0.8f, true, SwingStyle.HEAVY_SLASH),
    BATTLEAXE("Battle Axe", 0.9f, 0.75f, 90f, 3.8f, 0.9f, true, SwingStyle.CHOP),
    SCYTHE("Scythe", 1.05f, 1.75f, 150f, 4.6f, 0.4f, true, SwingStyle.REAP),
    WARHAMMER("Warhammer", 0.75f, 0.75f, 360f, 3.2f, 1.4f, true, SwingStyle.SLAM),
    LANCE("Lance", 1.15f, 3.0f, 0f, 6.5f, 0.7f, true, SwingStyle.THRUST),
    COLOSSAL("Colossal Greatsword", 0.45f, 1.75f, 140f, 5.0f, 2.2f, true, SwingStyle.SLAM),
    CHAINBLADE("Transforming Chainblade", 1.3f, 0.75f, 60f, 3.6f, 0.4f, false, SwingStyle.SLASH);

    public enum SwingStyle { SLASH, HEAVY_SLASH, CHOP, REAP, SLAM, THRUST }

    private final String displayName;
    private final float attackSpeed;
    private final float reachBonus;
    private final float cleaveAngle;
    private final float cleaveRange;
    private final float knockback;
    private final boolean twoHanded;
    private final SwingStyle swingStyle;

    WeaponClass(String displayName, float attackSpeed, float reachBonus, float cleaveAngle, float cleaveRange,
                float knockback, boolean twoHanded, SwingStyle swingStyle) {
        this.displayName = displayName;
        this.attackSpeed = attackSpeed;
        this.reachBonus = reachBonus;
        this.cleaveAngle = cleaveAngle;
        this.cleaveRange = cleaveRange;
        this.knockback = knockback;
        this.twoHanded = twoHanded;
        this.swingStyle = swingStyle;
    }

    public String displayName() {
        return displayName;
    }

    public float attackSpeed() {
        return attackSpeed;
    }

    public float reachBonus() {
        return reachBonus;
    }

    public float cleaveAngle() {
        return cleaveAngle;
    }

    public float cleaveRange() {
        return cleaveRange;
    }

    public float knockback() {
        return knockback;
    }

    public boolean twoHanded() {
        return twoHanded;
    }

    public SwingStyle swingStyle() {
        return swingStyle;
    }
}
