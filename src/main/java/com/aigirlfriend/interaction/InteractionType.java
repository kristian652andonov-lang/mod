package com.aigirlfriend.interaction;

import com.aigirlfriend.companion.Emotion;
import com.aigirlfriend.companion.LogKey;
import com.aigirlfriend.companion.RelationshipStage;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinated two-character interactions. Every entry defines its trigger requirements (stage), animation
 * state (her animation + the player's), positioning, duration (-1 = until ended), interruption policy
 * (movement lock, break distance, damage), cooldown and its emotional effect.
 */
public enum InteractionType {
    HOLD_HANDS(RelationshipStage.GROWING_AFFECTION, -1, 60, Positioning.FREE, 0f, false, 7f,
            "interaction.hold_hands", "hold_hands", 1.5f, 0.6f, 6f, LogKey.HAND_HOLD_SECONDS, Emotion.LOVE),
    LEAD_TO_BED(RelationshipStage.ROMANTIC, 1800, 600, Positioning.FREE, 0f, false, 9f,
            "interaction.lead_by_hand", "led_by_hand", 2f, 0.5f, 8f, LogKey.HAND_HOLD_SECONDS, Emotion.LOVE),
    HUG(RelationshipStage.FRIENDSHIP, 70, 160, Positioning.FACE, 0.52f, true, 2.5f,
            "interaction.hug", "hug", 4f, 1.2f, 14f, LogKey.HUGS, Emotion.JOY),
    BACK_HUG(RelationshipStage.ROMANTIC, 90, 260, Positioning.BEHIND, 0.42f, true, 2.5f,
            "interaction.back_hug", "back_hugged", 5f, 1.2f, 16f, LogKey.HUGS, Emotion.LOVE),
    CUDDLE(RelationshipStage.ROMANTIC, 170, 320, Positioning.FACE, 0.40f, true, 2.5f,
            "interaction.cuddle", "cuddle", 6f, 1.5f, 22f, LogKey.CUDDLES, Emotion.LOVE),
    KISS(RelationshipStage.ROMANTIC, 54, 240, Positioning.FACE, 0.40f, true, 2.5f,
            "interaction.kiss", "kiss", 6f, 1.2f, 18f, LogKey.KISSES, Emotion.LOVE),
    KISS_CHEEK(RelationshipStage.GROWING_AFFECTION, 36, 200, Positioning.FACE, 0.46f, true, 2.5f,
            "interaction.kiss_cheek", "kiss_cheek_receive", 4f, 0.8f, 12f, LogKey.KISSES, Emotion.EMBARRASSMENT),
    FOREHEAD_KISS(RelationshipStage.ROMANTIC, 46, 220, Positioning.FACE, 0.44f, true, 2.5f,
            "interaction.forehead_kiss", "forehead_kiss", 5f, 2f, 14f, LogKey.KISSES, Emotion.LOVE),
    LEAN_ON(RelationshipStage.GROWING_AFFECTION, 220, 300, Positioning.SIDE, 0.50f, false, 2.0f,
            "interaction.lean", "lean_support", 3f, 1f, 12f, LogKey.CUDDLES, Emotion.LOVE),
    SIT_TOGETHER(RelationshipStage.FRIENDSHIP, -1, 40, Positioning.SEATED, 0f, false, 3f,
            "interaction.sit_together", "sit_together", 1f, 0.6f, 8f, LogKey.NEAR_OWNER_SECONDS, Emotion.JOY),
    HEAD_ON_SHOULDER(RelationshipStage.ROMANTIC, -1, 160, Positioning.SEATED, 0f, false, 3f,
            "interaction.head_on_shoulder", "arm_around", 3f, 1f, 14f, LogKey.CUDDLES, Emotion.LOVE),
    CUDDLE_SITTING(RelationshipStage.ROMANTIC, -1, 200, Positioning.SEATED, 0f, false, 3f,
            "interaction.cuddle_sitting", "cuddle_sitting", 4f, 1.2f, 18f, LogKey.CUDDLES, Emotion.LOVE),
    CARRY(RelationshipStage.GROWING_AFFECTION, -1, 100, Positioning.CARRIED, 0f, false, 64f,
            "interaction.carried", "carry", 3f, 2f, 12f, LogKey.CUDDLES, Emotion.JOY),
    CUDDLE_BED(RelationshipStage.ROMANTIC, -1, 200, Positioning.BED, 0f, false, 4f,
            "interaction.cuddle_bed", "cuddle_bed", 5f, 1.5f, 20f, LogKey.CUDDLES, Emotion.LOVE),
    SLEEP_TOGETHER(RelationshipStage.ROMANTIC, -1, 0, Positioning.BED, 0f, false, 6f,
            "interaction.sleep_together", null, 6f, 3f, 20f, LogKey.SLEPT_TOGETHER, Emotion.LOVE),
    PET_HAIR(RelationshipStage.FRIENDSHIP, 90, 160, Positioning.FACE, 0.55f, true, 2.5f,
            "interaction.pet_hair", "pet_hair", 3f, 1.2f, 12f, LogKey.HUGS, Emotion.JOY),
    PET_CHEEK(RelationshipStage.GROWING_AFFECTION, 80, 200, Positioning.FACE, 0.48f, true, 2.5f,
            "interaction.pet_cheek", "pet_cheek", 4f, 1.2f, 14f, LogKey.CUDDLES, Emotion.LOVE),
    LAP_SIT(RelationshipStage.ROMANTIC, -1, 200, Positioning.LAP, 0f, false, 4f,
            "interaction.lap_sit", "lap_sit", 5f, 1.5f, 20f, LogKey.CUDDLES, Emotion.LOVE);

    public final RelationshipStage stage;
    public final int duration;
    public final int cooldown;
    public final Positioning positioning;
    public final float distance;
    public final boolean locksPlayer;
    public final float breakDistance;
    public final String animation;
    @Nullable public final String playerAnimation;
    public final float affection;
    public final float trust;
    public final float attention;
    public final LogKey logKey;
    public final Emotion emotion;

    InteractionType(RelationshipStage stage, int duration, int cooldown, Positioning positioning, float distance, boolean locksPlayer,
                    float breakDistance, String animation, @Nullable String playerAnimation, float affection, float trust, float attention,
                    LogKey logKey, Emotion emotion) {
        this.stage = stage;
        this.duration = duration;
        this.cooldown = cooldown;
        this.positioning = positioning;
        this.distance = distance;
        this.locksPlayer = locksPlayer;
        this.breakDistance = breakDistance;
        this.animation = animation;
        this.playerAnimation = playerAnimation;
        this.affection = affection;
        this.trust = trust;
        this.attention = attention;
        this.logKey = logKey;
        this.emotion = emotion;
    }

    public boolean isToggle() {
        return duration < 0;
    }

    public boolean isRomantic() {
        return stage.isRomantic();
    }

    public boolean isSeated() {
        return positioning == Positioning.SEATED;
    }

    public String key() {
        return "aigirlfriend.interaction." + name().toLowerCase();
    }

    public static InteractionType byId(int id) {
        InteractionType[] v = values();
        return id >= 0 && id < v.length ? v[id] : null;
    }
}
