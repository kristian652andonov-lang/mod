package com.fantasyweapons.status;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom internal status effects. These are not vanilla MobEffects: no vanilla potion particles, no vanilla icons.
 * They are shown by the custom HUD and visualised by the custom VFX system.
 */
public enum StatusType {
    VOID_MARK("Void Mark", 0x9B4DFF, true, 10),
    SOLAR_BURN("Solar Burn", 0xFFB627, true, 1),
    FROSTBITE("Frostbite", 0x8FE3FF, true, 5),
    FROZEN("Frozen", 0xDDF8FF, true, 1),
    BERSERKER("Berserker", 0xE0213A, false, 1),
    SOUL_DRAIN("Soul Drain", 0x5A8CFF, true, 1),
    NATURE_POISON("Nature Poison", 0x6BE36F, true, 5),
    ECLIPSE_LIGHT("Eclipse Light", 0xFFE7A0, true, 3),
    ECLIPSE_DARKNESS("Eclipse Darkness", 0x8B3DFF, true, 3),
    GRAVITY_BOUND("Gravity Bound", 0x8A6CFF, true, 1),
    INFERNO_OVERHEAT("Inferno Overheat", 0xFF5A1F, false, 5),
    ROOTED("Rooted", 0x4CAF50, true, 1),
    STAGGERED("Staggered", 0xB08A5A, true, 1),
    SEARED("Seared", 0xFF3A10, true, 5),
    // ---- the mage's spells (see com.fantasyweapons.magic.MagicStatus) ----
    /** Fire marks laid by the Cinderspire's Kindling: at five the target combusts. */
    KINDLED("Kindled", 0xFF7A2A, true, 5),
    /** Drenched by water spells: slowed. */
    SOAKED("Soaked", 0x4FB4FF, true, 1),
    /** A shield of absorption (potency = its health); cast by ice, water and light. */
    BARRIER("Barrier", 0xBFE6FF, false, 1),
    /** The Rimeheart's crystal ward: whoever strikes its bearer is frostbitten. */
    GLACIAL_WARD("Glacial Ward", 0x9FE8FF, false, 1),
    /** The Codex of Tides' bubble: when it bursts or fades it washes over its bearer, healing them. */
    BUBBLE("Bubble Aegis", 0x6FD0FF, false, 1),
    /** Less damage taken (potency = fraction). */
    FROST_WARD("Frost Ward", 0xCFF4FF, false, 1),
    /** The aura of a Rimeheart's bearer: less damage taken, more against frostbitten attackers. */
    PERMAFROST("Permafrost", 0xBFEFFF, false, 1),
    /** The Frostbound Grimoire's armor of rime: less damage taken per stack. */
    RIME_ARMOR("Rime Armor", 0xA8E0FF, false, 3),
    /** Ice shards circling their bearer, each turning one blow aside entirely. */
    SHATTERGUARD("Shatterguard", 0xDDF8FF, false, 3),
    /** More damage dealt (potency = fraction). */
    VALOR("Valor", 0xFFC94A, false, 1),
    /** The aura of a Sunwarden's bearer: more damage dealt. */
    RADIANCE("Radiance", 0xFFE9A8, false, 1),
    /** The Dawnbringer's blessing: much more damage dealt. */
    DAWN("Dawn", 0xFFE58A, false, 1),
    /** The Hymn of Swiftness: faster attacks (potency = fraction). */
    SWIFT("Swiftness", 0xFFF0B0, false, 1),
    /** The Lumen Psalter's choir: more damage dealt per stack. */
    CHOIR("Choir", 0xFFF6D8, false, 5),
    /** A halo of fortitude: no knockback. */
    HALO("Halo", 0xFFEBA0, false, 1),
    /** Every blow of an ascended ally calls a ray of light down on its target (potency = the ray's damage). */
    ASCENDED("Ascended", 0xFFFDE0, false, 1),
    /** Spring Eternal: healing every second, and saved once from death. */
    SPRING("Spring Eternal", 0x8CFF7A, false, 1),
    /** Healing over time from the Verdant Almanac's vines (potency = health per pulse). */
    MENDING("Mending", 0x7DE36A, false, 1),
    /** The Gloamreach's curse: damage over time that grows every pulse, and slowness. */
    WITHERING("Withering", 0x7A3DB8, true, 1),
    /** The Gloamreach's miasma: damage over time per stack, and weakness. */
    ROT("Rot", 0x6A4A9A, true, 10),
    /** Dirge of Ruin: heavy damage over time, slowness, and no healing at all. */
    RUIN("Ruin", 0x4A1A7A, true, 1),
    /** Cursed by the Gloamreach's Decay: more damage taken, half the healing. */
    DECAY("Decay", 0x8A5AC8, true, 1),
    /** The Umbral Codex's hex: its life drained into whoever laid it. */
    LEECH("Leeching Hex", 0x9B4DFF, true, 1),
    /** Bane seeds buried in the flesh: damage over time, then they bloom. */
    BANE("Bane", 0xB070FF, true, 3),
    /** The Umbral Plague: damage over time and weakness, spreading to enemies close by. */
    PLAGUE("Umbral Plague", 0x5E2A8A, true, 1);

    /** The curses of the void mages (Decay's vulnerability and Contagion's spread act on any of them). */
    public boolean curse() {
        return this == WITHERING || this == ROT || this == RUIN || this == LEECH || this == BANE || this == PLAGUE;
    }

    private final String displayName;
    private final int color;
    private final boolean harmful;
    private final int maxStacks;

    StatusType(String displayName, int color, boolean harmful, int maxStacks) {
        this.displayName = displayName;
        this.color = color;
        this.harmful = harmful;
        this.maxStacks = maxStacks;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public boolean harmful() {
        return harmful;
    }

    public int maxStacks() {
        return maxStacks;
    }

    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(FantasyWeapons.MOD_ID, "textures/gui/status/" + name().toLowerCase() + ".png");
    }
}
