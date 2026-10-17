package com.aigirlfriend.dialogue;

import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.companion.CompanionRequest;
import com.aigirlfriend.companion.ConversationHistory;
import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.Emotion;
import com.aigirlfriend.companion.MemoryEntry;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.NeedsSystem;
import com.aigirlfriend.companion.Personality;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.util.WorldClock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A compact, immutable snapshot of everything relevant for one line of dialogue: time, weather, needs,
 * mood, relationship, recent events, fear, cycle phase, personality and recorded memories. Built on the
 * server thread, then safely handed to the AI worker or the local generator.
 */
public final class DialogueContext {
    public final String name;
    public final String playerName;
    public final DialogueTrigger trigger;
    public final Map<String, String> vars;
    public final long day;
    public final String partOfDay;
    public final String clock;
    public final boolean night;
    public final String weather;
    public final String biome;
    public final Mood mood;
    public final List<String> moodCauses;
    public final Emotion emotion;
    public final float hunger, energy, attention, healthFraction, fear;
    public final RelationshipStage stage;
    public final float trust;
    public final int daysTogether;
    public final Personality personality;
    public final String archetype;
    @Nullable public final String threat;
    public final boolean protectedRecently, giftRecently, fedRecently, sick, scaredRecently;
    public final CyclePhase cycle;
    @Nullable public final String favoriteRevealed;
    public final String favoriteActivity;
    public final boolean hasHome;
    public final List<String> memories;
    @Nullable public final MemoryEntry reminisce;
    public final List<String> openPromises;
    @Nullable public final String request;
    public final List<ConversationHistory.Line> history;
    public final boolean ownerNear;

    public DialogueContext(GirlfriendEntity gf, @Nullable Player player, DialogueTrigger trigger, Map<String, String> vars) {
        CompanionData d = gf.data();
        Level level = gf.level();
        long now = level.getGameTime();
        this.name = d.name;
        Player owner = player != null ? player : gf.getOwner();
        this.playerName = owner != null ? owner.getGameProfile().getName() : "you";
        this.trigger = trigger;
        this.vars = vars;
        this.day = WorldClock.day(level);
        this.partOfDay = WorldClock.partOfDay(level);
        this.clock = WorldClock.clock(level);
        this.night = WorldClock.isNight(level);
        this.weather = level.isThundering() ? "thunderstorm" : level.isRaining() ? "rain" : "clear";
        this.biome = gf.controller().biomeName();
        this.mood = d.mood.primary;
        this.moodCauses = List.copyOf(d.mood.causes);
        this.emotion = d.mood.activeEmotion(now);
        this.hunger = d.needs.hunger;
        this.energy = d.needs.energy;
        this.attention = d.needs.attention;
        this.healthFraction = gf.getHealth() / gf.getMaxHealth();
        this.fear = gf.controller().fear.level;
        this.stage = d.relationship.stage();
        this.trust = d.relationship.trust;
        this.daysTogether = d.relationship.daysTogether;
        this.personality = d.personality;
        this.archetype = d.personality.archetype();
        this.threat = gf.controller().fear.mainThreat != null && gf.controller().fear.uneasy()
                ? com.aigirlfriend.companion.CompanionController.mobName(gf.controller().fear.mainThreat) : null;
        this.protectedRecently = now - d.lastProtectedTick < 2400;
        this.giftRecently = now - d.lastGiftTick < 2400;
        this.fedRecently = now - d.lastFedTick < 1200;
        this.scaredRecently = now - d.lastFrightTick < 2400;
        boolean ill = false;
        for (MobEffectInstance e : gf.getActiveEffects()) {
            if (!e.getEffect().value().isBeneficial()) ill = true;
        }
        this.sick = ill;
        this.cycle = d.cycle.phase(NeedsSystem.cycleClock(level));
        this.favoriteRevealed = d.preferences.isRevealed(d.preferences.favorite) ? d.preferences.favorite.word() : null;
        this.favoriteActivity = d.preferences.favoriteActivity;
        this.hasHome = d.home != null;
        List<String> mem = new ArrayList<>();
        for (MemoryEntry e : d.memory.relevant(GFConfig.AI_CONTEXT_MEMORIES.get(), day)) {
            long ago = day - e.day;
            mem.add(e.summary + (ago <= 0 ? " (today)" : ago == 1 ? " (yesterday)" : " (" + ago + " days ago)"));
        }
        this.memories = mem;
        this.reminisce = trigger == DialogueTrigger.REMINISCE ? d.memory.pickReminiscence(day, gf.getRandom()) : null;
        if (reminisce != null) reminisce.mentioned = true;
        List<String> promises = new ArrayList<>();
        for (MemoryEntry e : d.memory.openPromises()) promises.add(e.summary);
        this.openPromises = promises;
        CompanionRequest r = d.request;
        this.request = r == null ? null : r.type.name();
        this.history = d.history.recent(24);
        this.ownerNear = gf.controller().isOwnerNear();
    }

    public String var(String key, String fallback) {
        String v = vars.get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    public boolean flag(String key) {
        return "1".equals(vars.get(key));
    }

    /** The memory this line may reference: an explicitly chosen one, or the reminiscence pick. Never invented. */
    @Nullable
    public String reminisceSummary() {
        String v = vars.get("memory");
        if (v != null && !v.isEmpty()) return v;
        return reminisce != null ? reminisce.summary : null;
    }

    public boolean romantic() {
        return stage.isRomantic();
    }

    public String hungerWord() {
        return hunger >= 85 ? "starving" : hunger >= 55 ? "hungry" : hunger >= 30 ? "a little peckish" : "full";
    }

    public String energyWord() {
        return energy <= 12 ? "exhausted" : energy <= 35 ? "tired" : energy >= 80 ? "full of energy" : "fine";
    }

    public String attentionWord() {
        return attention < GFConfig.LONELY_THRESHOLD.get() ? "lonely and ignored" : attention > GFConfig.AFFECTIONATE_THRESHOLD.get() ? "cherished" : "okay";
    }
}
