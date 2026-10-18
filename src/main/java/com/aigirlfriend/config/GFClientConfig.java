package com.aigirlfriend.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;
import java.util.regex.Pattern;

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
    public static final ModConfigSpec.EnumValue<VoiceMode> VOICE_MODE;
    public static final ModConfigSpec.DoubleValue VOICE_VOLUME;
    public static final ModConfigSpec.DoubleValue VOICE_PITCH;
    public static final ModConfigSpec.DoubleValue VOICE_RATE;
    public static final ModConfigSpec.ConfigValue<String> VOICE_NAME;
    public static final ModConfigSpec.ConfigValue<String> NEURAL_VOICE;
    public static final ModConfigSpec.BooleanValue NEURAL_AUTO_DOWNLOAD;
    public static final ModConfigSpec.IntValue NEURAL_THREADS;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.DoubleValue CARRY_FORWARD;
    public static final ModConfigSpec.DoubleValue CARRY_HEIGHT;
    public static final ModConfigSpec.DoubleValue CARRY_YAW;
    public static final ModConfigSpec.DoubleValue LAP_FORWARD;
    public static final ModConfigSpec.DoubleValue LAP_HEIGHT;
    public static final ModConfigSpec.DoubleValue LAP_YAW;
    public static final ModConfigSpec.DoubleValue SEAT_HEIGHT;

    /** Kokoro voice ids, optionally weighted ("af_heart:0.7+af_sarah:0.3"); kept in step with the voice downloads. */
    private static final String NEURAL_VOICE_ID = "(?:af_(?:heart|sarah|bella|nicole|sky|nova|jessica|aoede|kore|river|alloy)|bf_(?:emma|isabella|alice|lily))(?:\\s*[:*]\\s*\\d{1,2}(?:\\.\\d+)?)?";
    private static final Pattern NEURAL_VOICE_SPEC = Pattern.compile("\\s*" + NEURAL_VOICE_ID + "(?:\\s*\\+\\s*" + NEURAL_VOICE_ID + ")*\\s*");

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

        b.comment("Her voice: she speaks her lines out loud from where she stands (uses the Voice/Speech volume slider).").push("sound");
        VOICE_ENABLED = b.comment("Let her speak out loud.").define("speakAloud", true);
        VOICE_MODE = b.comment("AUTO = her natural voice (a neural voice that runs on your computer, downloaded once, about 420 MB) as soon as it is ready;",
                "until then, or if your computer cannot run it, your system's text-to-speech shaped into her voice, or her built-in synthesized voice.",
                "NEURAL = only her natural voice (downloads it even if neuralVoiceAutoDownload is off; silent until it is ready);",
                "SPEECH = system text-to-speech only; SYNTH = built-in synthesized voice only; BLIPS = soft blips per word; OFF = silent.")
                .defineEnum("voiceMode", VoiceMode.AUTO);
        VOICE_VOLUME = b.defineInRange("voiceVolume", 0.8, 0.0, 1.0);
        VOICE_PITCH = b.comment("Her voice pitch (1.0 = her normal voice).").defineInRange("voicePitchScale", 1.0, 0.5, 2.0);
        VOICE_RATE = b.comment("Her speaking speed (1.0 = normal).").defineInRange("voiceRate", 1.0, 0.5, 2.0);
        VOICE_NAME = b.comment("Preferred system voice (name or part of it, e.g. Zira, Samantha, en-us+f3, slt). Empty = pick a female voice automatically.")
                .define("systemVoice", "");
        NEURAL_VOICE = b.comment("Her natural voice: one Kokoro voice, or a blend with weights joined by '+'. American: af_heart, af_sarah, af_bella,",
                        "af_nicole, af_sky, af_nova, af_jessica, af_aoede, af_kore, af_river, af_alloy. British: bf_emma, bf_isabella, bf_alice, bf_lily.",
                        "Default: af_heart:0.7+af_sarah:0.3 (warm and young; plain af_heart is almost the same).")
                .define("neuralVoice", "af_heart:0.7+af_sarah:0.3", GFClientConfig::validNeuralVoice);
        NEURAL_AUTO_DOWNLOAD = b.comment("In AUTO mode, download her natural voice automatically (one time, about 420 MB, from Hugging Face, GitHub and Maven Central).")
                .define("neuralVoiceAutoDownload", true);
        NEURAL_THREADS = b.comment("CPU threads for her natural voice. 0 = automatic (a quarter of your processor threads, 1 to 4). More threads = she starts talking sooner.")
                .defineInRange("neuralVoiceThreads", 0, 0, 16);
        SOUNDS = b.comment("Interaction sound effects (kiss, hug, gifts).").define("interactionSounds", true);
        b.pop();

        b.comment("Fine-tune where her model sits relative to you, to match your own model/animations (visual only).").push("poseTuning");
        CARRY_FORWARD = b.comment("Carried: blocks in front of you.").defineInRange("carryForward", 0.42, -1.0, 2.0);
        CARRY_HEIGHT = b.comment("Carried: height of her model origin above your feet (the carried clip itself lifts her body 8 px).").defineInRange("carryHeight", -0.12, -1.0, 2.5);
        CARRY_YAW = b.comment("Carried: her facing relative to yours, in degrees (90 = across your arms).").defineInRange("carryYaw", 90.0, -180.0, 180.0);
        LAP_FORWARD = b.comment("Lap sitting: blocks in front of you.").defineInRange("lapForward", 0.28, -1.0, 2.0);
        LAP_HEIGHT = b.comment("Lap sitting: height of her model origin above yours (you are seated); 0.42 rests her thighs on yours.").defineInRange("lapHeight", 0.42, -1.0, 2.0);
        LAP_YAW = b.comment("Lap sitting: her facing relative to yours (0 = same way, 90 = sideways).").defineInRange("lapYaw", 90.0, -180.0, 180.0);
        SEAT_HEIGHT = b.comment("Seats: how far below the seat surface her feet origin sits (the sitting clip already lowers her hips).").defineInRange("seatHeight", 0.24, 0.0, 1.5);
        b.pop();
        SPEC = b.build();
    }

    private GFClientConfig() {}

    private static boolean validNeuralVoice(Object value) {
        return value instanceof String s && NEURAL_VOICE_SPEC.matcher(s.toLowerCase(Locale.ROOT)).matches();
    }

    public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public enum VoiceMode { AUTO, NEURAL, SPEECH, SYNTH, BLIPS, OFF }
}
