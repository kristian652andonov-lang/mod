package com.example.companion.outfit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side outfit state for one companion. The only thing a change touches is the synced outfit id,
 * so position, pathfinding, animation controllers, relationship data and the current activity carry on.
 */
public final class Wardrobe {
    private final Entity owner;
    private final EntityDataAccessor<String> data;
    @Nullable private Outfit beforeBed;
    private boolean autoSleepwear = true;

    public Wardrobe(Entity owner, EntityDataAccessor<String> data) {
        this.owner = owner;
        this.data = data;
    }

    public Outfit current() {
        return Outfit.byId(owner.getEntityData().get(data)).orElse(Outfit.EVERYDAY);
    }

    /** Manual pick (GUI, command, interaction). Also cancels the automatic switch back after waking. */
    public void choose(Outfit outfit) {
        beforeBed = null;
        wear(outfit);
    }

    /** Call when she gets into bed (or starts your own night routine). */
    public void onBedtime() {
        if (autoSleepwear && current() != Outfit.SLEEP) {
            beforeBed = current();
            wear(Outfit.SLEEP);
        }
    }

    /** Call when she wakes. Restores what she wore before bed unless the player changed it in the meantime. */
    public void onWake() {
        if (beforeBed != null && current() == Outfit.SLEEP) wear(beforeBed);
        beforeBed = null;
    }

    public boolean autoSleepwear() { return autoSleepwear; }
    public void setAutoSleepwear(boolean on) { autoSleepwear = on; }

    private void wear(Outfit outfit) {
        owner.getEntityData().set(data, outfit.id);
    }

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putString("Current", current().id);
        if (beforeBed != null) t.putString("BeforeBed", beforeBed.id);
        t.putBoolean("AutoSleepwear", autoSleepwear);
        tag.put("Wardrobe", t);
    }

    public void load(CompoundTag tag) {
        CompoundTag t = tag.getCompound("Wardrobe");
        Outfit.byId(t.getString("Current")).ifPresent(this::wear);
        beforeBed = Outfit.byId(t.getString("BeforeBed")).orElse(null);
        autoSleepwear = !t.contains("AutoSleepwear") || t.getBoolean("AutoSleepwear");
    }
}
