package com.fantasyweapons.status;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Status effects currently on a living entity (attachment). Server authoritative; synced to trackers.
 */
public final class StatusEffects {
    public static final class Instance {
        public final StatusType type;
        public int stacks;
        public int duration;
        public int maxDuration;
        /** Effect strength, meaning depends on type (damage per pulse, slow fraction, bonus fraction...). */
        public float potency;
        /** Server-only: who applied it (for kill credit / soul drain healing). */
        @Nullable
        public transient UUID source;
        /** Server-only: auxiliary point (e.g. gravity well centre). */
        public transient double px, py, pz;

        public Instance(StatusType type, int stacks, int duration, float potency) {
            this.type = type;
            this.stacks = stacks;
            this.duration = duration;
            this.maxDuration = duration;
            this.potency = potency;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StatusEffects> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StatusEffects decode(RegistryFriendlyByteBuf buf) {
            StatusEffects s = new StatusEffects();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                StatusType t = StatusType.values()[buf.readVarInt()];
                Instance inst = new Instance(t, buf.readVarInt(), buf.readVarInt(), buf.readFloat());
                inst.maxDuration = buf.readVarInt();
                s.active.put(t, inst);
            }
            return s;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, StatusEffects s) {
            buf.writeVarInt(s.active.size());
            for (Instance i : s.active.values()) {
                buf.writeVarInt(i.type.ordinal());
                buf.writeVarInt(i.stacks);
                buf.writeVarInt(i.duration);
                buf.writeFloat(i.potency);
                buf.writeVarInt(i.maxDuration);
            }
        }
    };

    private final Map<StatusType, Instance> active = new EnumMap<>(StatusType.class);

    @Nullable
    public Instance get(StatusType type) {
        return active.get(type);
    }

    public boolean has(StatusType type) {
        return active.containsKey(type);
    }

    public int stacks(StatusType type) {
        Instance i = active.get(type);
        return i == null ? 0 : i.stacks;
    }

    public Collection<Instance> all() {
        return active.values();
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    public void put(Instance instance) {
        active.put(instance.type, instance);
    }

    @Nullable
    public Instance remove(StatusType type) {
        return active.remove(type);
    }

    /** Client-side countdown between syncs. */
    public void clientTick() {
        active.values().removeIf(i -> --i.duration <= 0);
    }
}
