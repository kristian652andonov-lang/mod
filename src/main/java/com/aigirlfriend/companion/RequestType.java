package com.aigirlfriend.companion;

/** Optional things she may ask for. Completing them builds trust; ignoring them only causes mild disappointment. */
public enum RequestType {
    BRING_FOOD(1f, 6f, 3f),
    EXPLORE_TOGETHER(120f, 10f, 4f),
    REACH_SAFETY(1f, 6f, 6f),
    TALK_TOGETHER(3f, 6f, 3f),
    PREFERRED_GIFT(1f, 12f, 4f),
    HOME_BEFORE_NIGHT(1f, 8f, 6f),
    PROTECT_ME(3f, 10f, 7f),
    SIT_BY_FIRE(30f, 8f, 3f),
    COMFORT_ME(1f, 8f, 4f);

    /** Amount of progress needed (items, blocks, mobs, seconds...). */
    public final float target;
    public final float affectionReward;
    public final float trustReward;

    RequestType(float target, float affectionReward, float trustReward) {
        this.target = target;
        this.affectionReward = affectionReward;
        this.trustReward = trustReward;
    }

    public String key() {
        return "aigirlfriend.request." + name().toLowerCase();
    }

    public static RequestType byName(String s) {
        try {
            return valueOf(s);
        } catch (Exception e) {
            return TALK_TOGETHER;
        }
    }
}
