package com.aigirlfriend.companion;

/** Primary daily moods. Valence/energy are used by dialogue, expressions and mood evaluation. */
public enum Mood {
    HAPPY(0.8f, 0.6f, Expression.HAPPY, 0xFFE07A),
    AFFECTIONATE(0.9f, 0.5f, Expression.AFFECTION, 0xFF8FB1),
    PLAYFUL(0.7f, 0.85f, Expression.HAPPY, 0xFFB86B),
    SHY(0.3f, 0.35f, Expression.EMBARRASSED, 0xF5A3C7),
    RELAXED(0.5f, 0.3f, Expression.NEUTRAL, 0x9EE6C4),
    SLEEPY(0.1f, 0.05f, Expression.SLEEPY, 0xA9A6E8),
    LONELY(-0.5f, 0.25f, Expression.SAD, 0x8CA6D9),
    SAD(-0.7f, 0.2f, Expression.SAD, 0x6F8FC9),
    IRRITABLE(-0.4f, 0.6f, Expression.ANGRY, 0xE36B5B),
    WORRIED(-0.3f, 0.5f, Expression.FEAR, 0xC9B26F),
    FRIGHTENED(-0.6f, 0.8f, Expression.FEAR, 0xB07CC9),
    EMBARRASSED(0.1f, 0.55f, Expression.EMBARRASSED, 0xFF9DA6),
    ENERGETIC(0.6f, 1.0f, Expression.HAPPY, 0xFFD24A),
    NEEDY(-0.1f, 0.5f, Expression.AFFECTION, 0xE59AD9),
    INDEPENDENT(0.4f, 0.6f, Expression.NEUTRAL, 0x7FC8E8);

    public final float valence;
    public final float energy;
    public final Expression expression;
    public final int color;

    Mood(float valence, float energy, Expression expression, int color) {
        this.valence = valence;
        this.energy = energy;
        this.expression = expression;
        this.color = color;
    }

    public boolean isNegative() {
        return valence < 0f;
    }

    public String key() {
        return "aigirlfriend.mood." + name().toLowerCase();
    }

    public String word() {
        return name().toLowerCase();
    }

    public static Mood byId(int id) {
        Mood[] v = values();
        return id >= 0 && id < v.length ? v[id] : RELAXED;
    }
}
