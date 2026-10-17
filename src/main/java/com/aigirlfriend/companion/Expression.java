package com.aigirlfriend.companion;

/** Facial expressions. Each maps to an "expression.<name>" animation and optional face texture. */
public enum Expression {
    NEUTRAL, HAPPY, SAD, FEAR, EMBARRASSED, AFFECTION, ANGRY, SLEEPY, SURPRISED;

    public String id() {
        return name().toLowerCase();
    }

    public static Expression byId(int id) {
        Expression[] v = values();
        return id >= 0 && id < v.length ? v[id] : NEUTRAL;
    }
}
