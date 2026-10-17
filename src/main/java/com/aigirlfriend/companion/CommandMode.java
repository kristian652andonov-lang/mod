package com.aigirlfriend.companion;

/** Standing orders from the owner. Survival commands are never locked behind relationship progress. */
public enum CommandMode {
    FOLLOW, WAIT, SIT, STAY_SAFE, HOME;

    public String key() {
        return "aigirlfriend.command." + name().toLowerCase();
    }

    public static CommandMode byId(int id) {
        CommandMode[] v = values();
        return id >= 0 && id < v.length ? v[id] : FOLLOW;
    }
}
