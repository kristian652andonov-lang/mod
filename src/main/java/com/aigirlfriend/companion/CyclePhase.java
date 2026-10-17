package com.aigirlfriend.companion;

public enum CyclePhase {
    MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL, DISABLED;

    public String key() {
        return "aigirlfriend.cycle." + name().toLowerCase();
    }

    public static CyclePhase byId(int id) {
        CyclePhase[] v = values();
        return id >= 0 && id < v.length ? v[id] : DISABLED;
    }
}
