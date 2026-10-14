package com.fantasyweapons.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A player's quests: the tallies of counted objectives, the quests claimed and those already announced as done. */
public final class QuestData {
    private final Map<String, List<Integer>> counts = new HashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private final Set<String> announced = new HashSet<>();

    public static final Codec<QuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("counts", Map.of()).forGetter(d -> d.counts),
            Codec.STRING.listOf().optionalFieldOf("claimed", List.of()).forGetter(d -> List.copyOf(d.claimed)),
            Codec.STRING.listOf().optionalFieldOf("announced", List.of()).forGetter(d -> List.copyOf(d.announced))
    ).apply(i, (counts, claimed, announced) -> {
        QuestData d = new QuestData();
        counts.forEach((k, v) -> d.counts.put(k, new ArrayList<>(v)));
        d.claimed.addAll(claimed);
        d.announced.addAll(announced);
        return d;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())), d -> new HashMap<>(d.counts),
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), d -> List.copyOf(d.claimed),
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), d -> List.copyOf(d.announced),
            (counts, claimed, announced) -> {
                QuestData d = new QuestData();
                counts.forEach((k, v) -> d.counts.put(k, new ArrayList<>(v)));
                d.claimed.addAll(claimed);
                d.announced.addAll(announced);
                return d;
            });

    /** The tally of objective {@code index} of quest {@code id}. */
    public int count(String id, int index) {
        List<Integer> c = counts.get(id);
        return c == null || index >= c.size() ? 0 : c.get(index);
    }

    /** Sets a tally; whether it changed. */
    boolean setCount(String id, int index, int value) {
        List<Integer> c = counts.computeIfAbsent(id, k -> new ArrayList<>());
        while (c.size() <= index) c.add(0);
        if (c.get(index) == value) return false;
        c.set(index, value);
        return true;
    }

    public boolean claimed(String id) {
        return claimed.contains(id);
    }

    void claim(String id) {
        claimed.add(id);
        counts.remove(id);
    }

    boolean announced(String id) {
        return announced.contains(id);
    }

    void announce(String id) {
        announced.add(id);
    }
}
