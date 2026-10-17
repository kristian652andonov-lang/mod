package com.aigirlfriend.companion;

/** What she is currently doing; synced to clients for the status UI and animation selection. */
public enum Activity {
    IDLE, FOLLOWING, WAITING, SITTING, SLEEPING, EATING, FLEEING, HIDING, GOING_HOME, AT_HOME,
    INTERACTING, CARRIED, FAINTED, LEADING_TO_BED, STAYING_SAFE, SEEKING_BED, RECOVERING;

    public String key() {
        return "aigirlfriend.activity." + name().toLowerCase();
    }

    public static Activity byId(int id) {
        Activity[] v = values();
        return id >= 0 && id < v.length ? v[id] : IDLE;
    }
}
