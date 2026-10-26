package com.fantasyweapons.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only presentation settings ({@code config/fantasyweapons-client.toml}).
 */
public final class ClientConfig {
    /** A choice in the settings menu with a name of its own (rather than its constant's, spelled out). */
    public interface Named {
        /** Its name in English (the language file's key). */
        String englishName();
    }

    /**
     * How much the effects draw. Every tier scales their detail ({@link #density()}); the lower tiers also show fewer
     * effects at once, fewer blocks flying from the destruction, and land the destruction a little at a time instead
     * of all in one frame, so a weak computer does not freeze when a great power lands.
     */
    public enum VfxQuality implements Named {
        LOW_END("Low-end PC", 0.2f, 128, 120, 48),
        LOW("Low", 0.35f, 256, 260, 160),
        MEDIUM("Medium", 0.6f, 512, 450, 480),
        HIGH("High", 1.0f, Integer.MAX_VALUE, 700, 0),
        ULTRA("Ultra", 1.5f, Integer.MAX_VALUE, 900, 0);

        private final String name;
        private final float density;
        private final int effects, debris, sectionsPerTick;

        VfxQuality(String name, float density, int effects, int debris, int sectionsPerTick) {
            this.name = name;
            this.density = density;
            this.effects = effects;
            this.debris = debris;
            this.sectionsPerTick = sectionsPerTick;
        }

        @Override
        public String englishName() {
            return name;
        }

        /** Multiplier for segment counts / sub-element counts in VFX. */
        public float density() {
            return density;
        }

        /** The most effects shown at once (the "Effect limit" setting can lower it further). */
        public int effects() {
            return effects;
        }

        /** The most blocks of land a power's destruction throws through the air at once. */
        public int debris() {
            return debris;
        }

        /** The most rewritten sections of land put into the world in a frame's tick (0: all of them as they come). */
        public int sectionsPerTick() {
            return sectionsPerTick;
        }
    }

    public enum HudAnchor { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;
    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ModConfigSpec.IntValue MAX_VFX_INSTANCES;
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES;
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES_ALL;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<HudAnchor> HUD_ANCHOR;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.BooleanValue CROSSHAIR_CHARGE_RING;
    public static final ModConfigSpec.BooleanValue LEVEL_UP_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue UNLOCK_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue EXP_POPUPS;
    public static final ModConfigSpec.BooleanValue DAMAGE_NUMBERS;
    public static final ModConfigSpec.BooleanValue CONFIRM_UPGRADES;

    public static final ModConfigSpec.BooleanValue PUNCHY_CODEX_OWN_ANIMATION;

    public static final ModConfigSpec SPEC;

    static {
        B.translation("Visual effects").push("vfx");
        VFX_QUALITY = B.comment("How much the effects draw. Low-end PC is made for weak computers: the fewest and simplest effects, the fewest flying blocks, and the destruction of the great powers landing a little at a time so the game does not freeze. Low, Medium, High and Ultra draw more and more")
                .translation("Effect quality").defineEnum("quality", VfxQuality.HIGH);
        SCREEN_SHAKE = B.comment("How hard the camera shakes at heavy impacts (0 = off)").translation("Screen shake").defineInRange("screen_shake", 1.0, 0.0, 3.0);
        MAX_VFX_INSTANCES = B.comment("The most effects shown at once").translation("Effect limit").defineInRange("max_instances", 512, 16, 8192);
        IMPACT_FRAMES = B.comment("Anime impact frames when an ability's blow lands: the picture freezes for an instant as a stark black-and-white still with speed lines")
                .translation("Impact frames").define("impact_frames", true);
        IMPACT_FRAMES_ALL = B.comment("Impact frames for every ability's hit (a quick flash), not only for ultimates").translation("Impact frames for every ability").define("impact_frames_every_ability", true);
        B.pop();

        B.translation("Interface").push("hud");
        HUD_ENABLED = B.comment("The panel with the held weapon's level, abilities and cooldowns").translation("Weapon HUD").define("enabled", true);
        HUD_ANCHOR = B.comment("The corner of the screen the weapon HUD sits in").translation("HUD corner").defineEnum("anchor", HudAnchor.BOTTOM_RIGHT);
        HUD_SCALE = B.comment("Makes the weapon HUD bigger or smaller, on top of the game's interface size").translation("HUD size").defineInRange("scale", 1.0, 0.5, 2.0);
        HUD_OFFSET_X = B.comment("Moves the weapon HUD sideways from its corner (pixels)").translation("HUD shift sideways").defineInRange("offset_x", 0, -2000, 2000);
        HUD_OFFSET_Y = B.comment("Moves the weapon HUD up or down from its corner (pixels)").translation("HUD shift up or down").defineInRange("offset_y", 0, -2000, 2000);
        CROSSHAIR_CHARGE_RING = B.comment("A ring around the crosshair that fills while an ability charges").translation("Charge ring").define("crosshair_charge_ring", true);
        LEVEL_UP_NOTIFICATIONS = B.comment("A banner when a weapon gains a level").translation("Level-up banners").define("level_up_notifications", true);
        UNLOCK_NOTIFICATIONS = B.comment("A banner when a weapon awakens a new ability").translation("Ability banners").define("unlock_notifications", true);
        EXP_POPUPS = B.comment("Small +EXP numbers when a weapon gains experience").translation("EXP popups").define("exp_popups", true);
        DAMAGE_NUMBERS = B.comment("Floating numbers for the damage of your hits").translation("Damage numbers").define("damage_numbers", true);
        CONFIRM_UPGRADES = B.comment("Ask once more before mastery points are spent").translation("Confirm upgrades").define("confirm_upgrades", true);
        B.pop();

        B.translation("Compatibility").push("compat");
        PUNCHY_CODEX_OWN_ANIMATION = B.comment("With Punchy installed: put the Wayfarer's Codex on Punchy's item blacklist, so it lifts, opens and reads with",
                        "its own animation (in Punchy's hand it stays shut). Weapons and tools keep Punchy's animations either way.")
                .translation("Punchy: the Codex keeps its own animation").define("punchy_codex_own_animation", true);
        B.pop();

        SPEC = B.build();
    }

    private ClientConfig() {
    }

    public static VfxQuality quality() {
        return SPEC.isLoaded() ? VFX_QUALITY.get() : VfxQuality.HIGH;
    }

    public static boolean get(ModConfigSpec.BooleanValue value, boolean def) {
        return SPEC.isLoaded() ? value.get() : def;
    }

    public static double get(ModConfigSpec.DoubleValue value, double def) {
        return SPEC.isLoaded() ? value.get() : def;
    }

    public static int get(ModConfigSpec.IntValue value, int def) {
        return SPEC.isLoaded() ? value.get() : def;
    }
}
