package com.aigirlfriend.network;

import com.aigirlfriend.companion.CompanionData;
import com.aigirlfriend.companion.CompanionRequest;
import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.GiftCategory;
import com.aigirlfriend.companion.MemoryEntry;
import com.aigirlfriend.companion.MoodState;
import com.aigirlfriend.companion.NeedsSystem;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the status screen / HUD shows. Built on the server for the owner only, so detailed
 * needs, cycle data and memories are never broadcast to other players.
 */
public record StatusSnapshot(
        String name, int mood, int emotion, int expression, List<String> moodCauses,
        float hunger, float energy, float attention, float health, float maxHealth,
        float affection, float trust, float stageProgress, int stage, int daysTogether,
        int activity, int command,
        int cyclePhase, int cycleDay,
        int requestType, boolean requestAccepted, float requestProgress, int requestTicksLeft, String requestDetail,
        String archetype, int favoriteRevealed, String favoriteActivity,
        List<String> memories, int[] moodHistory,
        int outfit, int outfitCount, boolean hasHome, int interaction) {

    public static final StreamCodec<FriendlyByteBuf, StatusSnapshot> STREAM_CODEC = StreamCodec.of((buf, s) -> s.write(buf), StatusSnapshot::read);

    public static StatusSnapshot of(GirlfriendEntity gf, ServerPlayer viewer, boolean full) {
        CompanionData d = gf.data();
        MoodState m = d.mood;
        long now = gf.level().getGameTime();
        int phase = -1, cycleDay = 0;
        if (GFConfig.CYCLE_ENABLED.get() && GFConfig.CYCLE_SHOW_IN_STATUS.get() && gf.isOwnedBy(viewer)) {
            long clock = NeedsSystem.cycleClock(gf.level());
            CyclePhase p = d.cycle.phase(clock);
            if (p != CyclePhase.DISABLED) {
                phase = p.ordinal();
                cycleDay = d.cycle.dayOfCycle(clock);
            }
        }
        CompanionRequest r = d.request;
        int fav = -1;
        if (d.preferences.isRevealed(d.preferences.favorite)) fav = d.preferences.favorite.ordinal();
        List<String> memories = new ArrayList<>();
        if (full) {
            List<MemoryEntry> rel = d.memory.relevant(8, com.aigirlfriend.util.WorldClock.day(gf.level()));
            for (MemoryEntry e : rel) memories.add(e.summary);
        }
        int[] history = full ? m.history.stream().limit(7).mapToInt(h -> h.mood().ordinal()).toArray() : new int[0];
        var cur = gf.interactions().current();
        return new StatusSnapshot(
                d.name, m.primary.ordinal(), m.activeEmotion(now).ordinal(), gf.getExpression().ordinal(), List.copyOf(m.causes),
                d.needs.hunger, d.needs.energy, d.needs.attention, gf.getHealth(), gf.getMaxHealth(),
                d.relationship.affection, d.relationship.trust, d.relationship.progressToNext(), d.relationship.stage().ordinal(), d.relationship.daysTogether,
                gf.getActivity().ordinal(), gf.getCommand().ordinal(),
                phase, cycleDay,
                r == null ? -1 : r.type.ordinal(), r != null && r.accepted, r == null ? 0f : r.fraction(), r == null ? 0 : (int) Math.max(0, r.expiresTick - now),
                r == null ? "" : r.detail,
                d.personality.archetype(), fav, d.preferences.favoriteActivity,
                memories, history,
                d.outfit, GFConfig.OUTFIT_COUNT.get(), d.home != null, cur == null ? -1 : cur.ordinal());
    }

    public GiftCategory favoriteCategory() {
        return favoriteRevealed < 0 ? null : GiftCategory.byId(favoriteRevealed);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, 64);
        buf.writeVarInt(mood);
        buf.writeVarInt(emotion);
        buf.writeVarInt(expression);
        buf.writeCollection(moodCauses, (b, s) -> b.writeUtf(s, 256));
        buf.writeFloat(hunger);
        buf.writeFloat(energy);
        buf.writeFloat(attention);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeFloat(affection);
        buf.writeFloat(trust);
        buf.writeFloat(stageProgress);
        buf.writeVarInt(stage);
        buf.writeVarInt(daysTogether);
        buf.writeVarInt(activity);
        buf.writeVarInt(command);
        buf.writeVarInt(cyclePhase);
        buf.writeVarInt(cycleDay);
        buf.writeVarInt(requestType);
        buf.writeBoolean(requestAccepted);
        buf.writeFloat(requestProgress);
        buf.writeVarInt(requestTicksLeft);
        buf.writeUtf(requestDetail, 64);
        buf.writeUtf(archetype, 32);
        buf.writeVarInt(favoriteRevealed);
        buf.writeUtf(favoriteActivity, 64);
        buf.writeCollection(memories, (b, s) -> b.writeUtf(s, 256));
        buf.writeVarIntArray(moodHistory);
        buf.writeVarInt(outfit);
        buf.writeVarInt(outfitCount);
        buf.writeBoolean(hasHome);
        buf.writeVarInt(interaction);
    }

    private static StatusSnapshot read(FriendlyByteBuf buf) {
        return new StatusSnapshot(
                buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readList(b -> b.readUtf(256)),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(), buf.readUtf(64),
                buf.readUtf(32), buf.readVarInt(), buf.readUtf(64),
                buf.readList(b -> b.readUtf(256)), buf.readVarIntArray(16),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
    }
}
