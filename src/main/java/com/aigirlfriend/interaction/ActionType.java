package com.aigirlfriend.interaction;

/** Options of the interaction wheel. The server decides which ones are currently valid. */
public enum ActionType {
    // inner ring: everyday commands (never locked behind relationship progress)
    TALK(true), FOLLOW(true), WAIT(true), SIT(true), STAND_UP(true), STAY_SAFE(true), RETURN_HOME(true), SET_HOME(true), STATUS(true),
    // outer ring: interactions
    HUG(false), CUDDLE(false), KISS(false), KISS_CHEEK(false), FOREHEAD_KISS(false), BACK_HUG(false),
    HOLD_HANDS(false), LET_GO(false), LEAN(false), SIT_TOGETHER(false), HEAD_ON_SHOULDER(false), CUDDLE_SITTING(false),
    PICK_UP(false), PUT_DOWN(false), GIVE_GIFT(false), FEED(false), SLEEP_TOGETHER(false), CUDDLE_BED(false), STOP(false),
    PET_HAIR(false), PET_CHEEK(false), LAP_SIT(false), GET_UP(false);

    public final boolean command;

    ActionType(boolean command) {
        this.command = command;
    }

    public String key() {
        return "aigirlfriend.action." + name().toLowerCase();
    }

    public static ActionType byId(int id) {
        ActionType[] v = values();
        return id >= 0 && id < v.length ? v[id] : null;
    }
}
