package com.aigirlfriend.companion;

import net.minecraft.nbt.CompoundTag;

public final class CompanionRequest {
    public RequestType type;
    public long issuedTick;
    public long expiresTick;
    public boolean accepted;
    public float progress;
    public String detail = "";
    public double x, y, z;

    public CompanionRequest(RequestType type, long issuedTick, long expiresTick) {
        this.type = type;
        this.issuedTick = issuedTick;
        this.expiresTick = expiresTick;
    }

    public float fraction() {
        return Math.min(1f, progress / Math.max(0.0001f, type.target));
    }

    public boolean isDone() {
        return progress >= type.target;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        t.putLong("issued", issuedTick);
        t.putLong("expires", expiresTick);
        t.putBoolean("accepted", accepted);
        t.putFloat("progress", progress);
        t.putString("detail", detail);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        return t;
    }

    public static CompanionRequest load(CompoundTag t) {
        CompanionRequest r = new CompanionRequest(RequestType.byName(t.getString("type")), t.getLong("issued"), t.getLong("expires"));
        r.accepted = t.getBoolean("accepted");
        r.progress = t.getFloat("progress");
        r.detail = t.getString("detail");
        r.x = t.getDouble("x");
        r.y = t.getDouble("y");
        r.z = t.getDouble("z");
        return r;
    }
}
