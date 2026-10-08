package com.fantasyweapons.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only presentation settings ({@code config/fantasyweapons-client.toml}).
 */
public final class ClientConfig {
    public enum VfxQuality {
        LOW(0.35f), MEDIUM(0.6f), HIGH(1.0f), ULTRA(1.5f);

        private final float density;

        VfxQuality(float density) {
            this.density = density;
        }

        /** Multiplier for segment counts / sub-element counts in VFX. */
        public float density() {
            return density;
        }
    }

    public enum HudAnchor { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;
    public static final ModConfigSpec.BooleanValue DISTORTION;
    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ModConfigSpec.IntValue MAX_VFX_INSTANCES;

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

    public static final ModConfigSpec SPEC;

    static {
        B.push("vfx");
        VFX_QUALITY = B.comment("Custom VFX detail: LOW, MEDIUM, HIGH or ULTRA").defineEnum("quality", VfxQuality.HIGH);
        DISTORTION = B.comment("Screen-space distortion behind rifts / shockwaves (HIGH and ULTRA only)").define("distortion", true);
        SCREEN_SHAKE = B.comment("Camera shake strength for heavy impacts (0 = off)").defineInRange("screen_shake", 1.0, 0.0, 3.0);
        MAX_VFX_INSTANCES = B.comment("Upper bound of simultaneously alive VFX objects").defineInRange("max_instances", 512, 16, 8192);
        B.pop();

        B.push("hud");
        HUD_ENABLED = B.define("enabled", true);
        HUD_ANCHOR = B.comment("Corner the weapon HUD is anchored to").defineEnum("anchor", HudAnchor.BOTTOM_RIGHT);
        HUD_SCALE = B.comment("Extra HUD scale on top of the GUI scale").defineInRange("scale", 1.0, 0.5, 2.0);
        HUD_OFFSET_X = B.defineInRange("offset_x", 0, -2000, 2000);
        HUD_OFFSET_Y = B.defineInRange("offset_y", 0, -2000, 2000);
        CROSSHAIR_CHARGE_RING = B.comment("Show the radial charge ring around the crosshair").define("crosshair_charge_ring", true);
        LEVEL_UP_NOTIFICATIONS = B.comment("Show weapon level-up banners").define("level_up_notifications", true);
        UNLOCK_NOTIFICATIONS = B.comment("Show ability-unlock banners").define("unlock_notifications", true);
        EXP_POPUPS = B.comment("Show small +EXP popups").define("exp_popups", true);
        DAMAGE_NUMBERS = B.comment("Show floating damage numbers for your hits").define("damage_numbers", true);
        CONFIRM_UPGRADES = B.comment("Ask for confirmation before spending mastery points").define("confirm_upgrades", true);
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
