package com.aigirlfriend.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything that makes her "her". Saved inside the entity NBT and mirrored into the world-level
 * {@link com.aigirlfriend.world.CompanionRegistry} so identity can be recovered if the entity is lost.
 */
public final class CompanionData {
    public static final int DATA_VERSION = 1;

    public UUID companionId = UUID.randomUUID();
    /** Incremented whenever the companion is re-created (dimension change, restore) so stale copies can be discarded. */
    public int generation = 0;
    public String name = "Aria";
    public int outfit = 0;
    public long createdDay = 0;

    public Personality personality = new Personality();
    public Preferences preferences = new Preferences();
    public final Needs needs = new Needs();
    public final MoodState mood = new MoodState();
    public final Relationship relationship = new Relationship();
    public final MemoryBank memory = new MemoryBank();
    public final CycleState cycle = new CycleState();
    public final ConversationHistory history = new ConversationHistory();
    public final DailyLog today = new DailyLog();
    public final DailyLog yesterday = new DailyLog();

    @Nullable public CompanionRequest request;
    public long lastRequestTick = -100000L;
    public long requestDay = -1;
    public int requestsToday = 0;

    @Nullable public BlockPos home;
    public String homeDimension = "minecraft:overworld";

    /** Game time the owner was last nearby; drives "I missed you" greetings. */
    public long lastOwnerNearTick = 0L;
    public long lastInteractionTick = 0L;
    public long lastProtectedTick = -100000L;
    public long lastGiftTick = -100000L;
    public long lastFrightTick = -100000L;
    public long lastFedTick = -100000L;

    public void initialize(RandomSource random, String chosenName, int outfitCount, long day) {
        name = chosenName;
        personality = Personality.generate(random);
        preferences = Preferences.generate(random, personality);
        outfit = 0;
        createdDay = day;
        mood.wellbeing = 15f + random.nextFloat() * 20f;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("version", DATA_VERSION);
        t.putUUID("companionId", companionId);
        t.putInt("generation", generation);
        t.putString("name", name);
        t.putInt("outfit", outfit);
        t.putLong("createdDay", createdDay);
        t.put("personality", personality.save());
        t.put("preferences", preferences.save());
        t.put("needs", needs.save());
        t.put("mood", mood.save());
        t.put("relationship", relationship.save());
        t.put("memory", memory.save());
        t.put("cycle", cycle.save());
        t.put("history", history.save());
        t.put("today", today.save());
        t.put("yesterday", yesterday.save());
        if (request != null) t.put("request", request.save());
        t.putLong("lastRequestTick", lastRequestTick);
        t.putLong("requestDay", requestDay);
        t.putInt("requestsToday", requestsToday);
        if (home != null) t.put("home", NbtUtils.writeBlockPos(home));
        t.putString("homeDim", homeDimension);
        t.putLong("lastOwnerNear", lastOwnerNearTick);
        t.putLong("lastInteraction", lastInteractionTick);
        t.putLong("lastProtected", lastProtectedTick);
        t.putLong("lastGift", lastGiftTick);
        t.putLong("lastFright", lastFrightTick);
        t.putLong("lastFed", lastFedTick);
        return t;
    }

    public void load(CompoundTag t) {
        if (t.hasUUID("companionId")) companionId = t.getUUID("companionId");
        generation = t.getInt("generation");
        name = t.getString("name");
        outfit = t.getInt("outfit");
        createdDay = t.getLong("createdDay");
        personality.load(t.getCompound("personality"));
        preferences.load(t.getCompound("preferences"));
        needs.load(t.getCompound("needs"));
        mood.load(t.getCompound("mood"));
        relationship.load(t.getCompound("relationship"));
        memory.load(t.getList("memory", Tag.TAG_COMPOUND));
        cycle.load(t.getCompound("cycle"));
        history.load(t.getList("history", Tag.TAG_COMPOUND));
        today.load(t.getCompound("today"));
        yesterday.load(t.getCompound("yesterday"));
        request = t.contains("request") ? CompanionRequest.load(t.getCompound("request")) : null;
        lastRequestTick = t.getLong("lastRequestTick");
        requestDay = t.getLong("requestDay");
        requestsToday = t.getInt("requestsToday");
        home = NbtUtils.readBlockPos(t, "home").orElse(null);
        homeDimension = t.contains("homeDim") ? t.getString("homeDim") : "minecraft:overworld";
        lastOwnerNearTick = t.getLong("lastOwnerNear");
        lastInteractionTick = t.getLong("lastInteraction");
        lastProtectedTick = t.getLong("lastProtected");
        lastGiftTick = t.getLong("lastGift");
        lastFrightTick = t.getLong("lastFright");
        lastFedTick = t.getLong("lastFed");
    }
}
