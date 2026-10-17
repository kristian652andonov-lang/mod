package com.aigirlfriend.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Purely visual / per-player preferences. */
public final class GFClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.EnumValue<HudCorner> HUD_CORNER;
    public static final ModConfigSpec.BooleanValue SPEECH_BUBBLES;
    public static final ModConfigSpec.BooleanValue SHOW_CYCLE;
    public static final ModConfigSpec.BooleanValue MINIMAL_UI;
    public static final ModConfigSpec.BooleanValue TOOLTIPS;
    public static final ModConfigSpec.DoubleValue ANIMATION_SPEED;
    public static final ModConfigSpec.BooleanValue PLAYER_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue LOCK_MOVEMENT;
    public static final ModConfigSpec.BooleanValue HAND_PULL;
    public static final ModConfigSpec.DoubleValue HAND_PULL_STRENGTH;
    public static final ModConfigSpec.BooleanValue VOICE_ENABLED;
    public static final ModConfigSpec.DoubleValue VOICE_VOLUME;
    public static final ModConfigSpec.DoubleValue VOICE_PITCH;
    public static final ModConfigSpec.BooleanValue SOUNDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("interface");
        SHOW_HUD = b.comment("Small status widget while she is nearby.").define("showHud", true);
        HUD_CORNER = b.defineEnum("hudCorner", HudCorner.TOP_LEFT);
        SPEECH_BUBBLES = b.define("speechBubbles", true);
        SHOW_CYCLE = b.comment("Show her cycle phase in the status screen (only if the server allows it).").define("showCycleStatus", false);
        MINIMAL_UI = b.comment("Hide nonessential interface elements (HUD details, decorations).").define("minimalUi", false);
        TOOLTIPS = b.define("tooltips", true);
        b.pop();

        b.push("animation");
        ANIMATION_SPEED = b.defineInRange("animationSpeed", 1.0, 0.25, 3.0);
        PLAYER_ANIMATIONS = b.comment("Animate the player model during shared interactions.").define("playerAnimations", true);
        LOCK_MOVEMENT = b.comment("Hold still during hugs/kisses so both characters stay aligned (sneak to cancel).").define("lockMovementDuringInteractions", true);
        HAND_PULL = b.comment("When she leads you by the hand you feel a gentle pull.").define("handHoldPull", true);
        HAND_PULL_STRENGTH = b.defineInRange("handHoldPullStrength", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Optional voice: soft voice blips while she talks. Independent from text dialogue.").push("sound");
        VOICE_ENABLED = b.define("voiceEnabled", false);
        VOICE_VOLUME = b.defineInRange("voiceVolume", 0.5, 0.0, 1.0);
        VOICE_PITCH = b.defineInRange("voicePitch", 1.35, 0.5, 2.0);
        SOUNDS = b.comment("Interaction sound effects (kiss, hug, gifts).").define("interactionSounds", true);
        b.pop();
        SPEC = b.build();
    }

    private GFClientConfig() {}

    public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
}
