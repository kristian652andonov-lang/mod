package com.aigirlfriend.companion;

/**
 * Relationship progression. A stage needs enough affection AND trust AND days spent together,
 * so it cannot be rushed by spamming one interaction.
 */
public enum RelationshipStage {
    ACQUAINTANCE(0, 0, 0),
    FRIENDSHIP(120, 20, 0),
    GROWING_AFFECTION(340, 35, 1),
    ROMANTIC(700, 50, 2),
    DEEP_TRUST(1150, 70, 4),
    PARTNERSHIP(1750, 85, 7);

    public final float affection;
    public final float trust;
    public final int days;

    RelationshipStage(float affection, float trust, int days) {
        this.affection = affection;
        this.trust = trust;
        this.days = days;
    }

    public boolean atLeast(RelationshipStage other) {
        return ordinal() >= other.ordinal();
    }

    public RelationshipStage next() {
        RelationshipStage[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : this;
    }

    public boolean isRomantic() {
        return atLeast(ROMANTIC);
    }

    public String key() {
        return "aigirlfriend.stage." + name().toLowerCase();
    }

    public static RelationshipStage byId(int id) {
        RelationshipStage[] v = values();
        return id >= 0 && id < v.length ? v[id] : ACQUAINTANCE;
    }
}
