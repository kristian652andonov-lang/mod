package com.aigirlfriend.companion;

public enum MemoryType {
    FIRST_MEETING(1.0f),
    CONVERSATION(0.35f),
    GIFT(0.5f),
    PREFERENCE(0.6f),
    ADVENTURE(0.55f),
    PROTECTED(0.8f),
    DANGER(0.5f),
    MILESTONE(1.0f),
    DISAPPOINTMENT(0.6f),
    RESOLVED_CONFLICT(0.7f),
    FAVORITE_ACTIVITY(0.6f),
    FAVORITE_PLACE(0.65f),
    PROMISE(0.75f),
    AFFECTION(0.45f),
    HURT(0.55f);

    public final float baseImportance;

    MemoryType(float baseImportance) {
        this.baseImportance = baseImportance;
    }

    public static MemoryType byName(String s) {
        try {
            return valueOf(s);
        } catch (Exception e) {
            return CONVERSATION;
        }
    }
}
