package com.aigirlfriend.companion;

/** Short-lived secondary emotions caused by events during the day. */
public enum Emotion {
    NONE(null),
    JOY(Expression.HAPPY),
    LOVE(Expression.AFFECTION),
    SADNESS(Expression.SAD),
    FEAR(Expression.FEAR),
    EMBARRASSMENT(Expression.EMBARRASSED),
    ANNOYANCE(Expression.ANGRY),
    SURPRISE(Expression.SURPRISED),
    RELIEF(Expression.HAPPY),
    PRIDE(Expression.HAPPY),
    WORRY(Expression.FEAR),
    TIREDNESS(Expression.SLEEPY),
    DISCOMFORT(Expression.SAD);

    public final Expression expression;

    Emotion(Expression expression) {
        this.expression = expression;
    }

    public static Emotion byName(String s) {
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return NONE;
        }
    }

    public static Emotion byId(int id) {
        Emotion[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
