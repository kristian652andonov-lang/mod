package com.aigirlfriend.interaction;

/**
 * One-shot body animations she plays on her own (no partner positioning required).
 * Each maps to an "action.<id>" animation in the companion's animation file.
 */
public enum Gesture {
    NONE(0),
    WAVE(40),
    WAVE_GOODBYE(50),
    YAWN(50),
    STRETCH(60),
    EAT(40),
    DRINK(36),
    LOOK_AT_ANIMAL(80),
    LOOK_AROUND(70),
    STARTLED(26),
    CELEBRATE(60),
    SAD(80),
    REACH_HAND(50),
    GIFT_HAPPY(50),
    SHY(50),
    HURT(12),
    DEFEND(18),
    TALK(40),
    POUT(50),
    BLOW_KISS(36),
    GIGGLE(36),
    WAKE_GREETING(60),
    RELIEVED(50),
    WAKE_UP(52),
    ASK_HELP(50);

    public final int duration;

    Gesture(int duration) {
        this.duration = duration;
    }

    public String animation() {
        return "action." + name().toLowerCase();
    }

    public static Gesture byId(int id) {
        Gesture[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
