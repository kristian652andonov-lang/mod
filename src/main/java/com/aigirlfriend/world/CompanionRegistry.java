package com.aigirlfriend.world;

import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-level record of every companion: who owns her, where she was last seen, her generation, and a
 * snapshot of her identity. This lets the mod find her in unloaded chunks, prevent duplicates, and restore
 * her identity if the entity is ever lost.
 */
public final class CompanionRegistry extends SavedData {
    private static final String NAME = "aigirlfriend_registry";

    public enum Status { ACTIVE, LOST, DEAD }

    public static final class Entry {
        public UUID companionId;
        public UUID owner;
        public UUID entityUuid;
        public String name = "";
        public String dimension = "minecraft:overworld";
        public BlockPos lastPos = BlockPos.ZERO;
        public int generation;
        public Status status = Status.ACTIVE;
        public CompoundTag snapshot = new CompoundTag();
        public long updatedTick;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", companionId);
            if (owner != null) t.putUUID("owner", owner);
            if (entityUuid != null) t.putUUID("entity", entityUuid);
            t.putString("name", name);
            t.putString("dim", dimension);
            t.put("pos", NbtUtils.writeBlockPos(lastPos));
            t.putInt("gen", generation);
            t.putString("status", status.name());
            t.put("snapshot", snapshot);
            t.putLong("updated", updatedTick);
            return t;
        }

        static Entry load(CompoundTag t) {
            Entry e = new Entry();
            e.companionId = t.getUUID("id");
            e.owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
            e.entityUuid = t.hasUUID("entity") ? t.getUUID("entity") : null;
            e.name = t.getString("name");
            e.dimension = t.getString("dim");
            e.lastPos = NbtUtils.readBlockPos(t, "pos").orElse(BlockPos.ZERO);
            e.generation = t.getInt("gen");
            try { e.status = Status.valueOf(t.getString("status")); } catch (Exception ex) { e.status = Status.ACTIVE; }
            e.snapshot = t.getCompound("snapshot");
            e.updatedTick = t.getLong("updated");
            return e;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static CompanionRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CompanionRegistry::new, CompanionRegistry::load), NAME);
    }

    private static CompanionRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionRegistry r = new CompanionRegistry();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Entry e = Entry.load(list.getCompound(i));
            r.entries.put(e.companionId, e);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) list.add(e.save());
        tag.put("entries", list);
        return tag;
    }

    @Nullable
    public Entry get(UUID companionId) {
        return entries.get(companionId);
    }

    /** Records the live state of a companion (position, generation, identity snapshot). */
    public void update(GirlfriendEntity gf, boolean includeSnapshot) {
        Entry e = entries.computeIfAbsent(gf.data().companionId, id -> {
            Entry n = new Entry();
            n.companionId = id;
            return n;
        });
        e.owner = gf.getOwnerUUID();
        e.entityUuid = gf.getUUID();
        e.name = gf.data().name;
        e.dimension = gf.level().dimension().location().toString();
        e.lastPos = gf.blockPosition();
        e.generation = Math.max(e.generation, gf.data().generation);
        e.status = Status.ACTIVE;
        if (includeSnapshot) e.snapshot = gf.data().save();
        e.updatedTick = gf.level().getGameTime();
        setDirty();
    }

    public void markStatus(UUID companionId, Status status) {
        Entry e = entries.get(companionId);
        if (e != null) {
            e.status = status;
            setDirty();
        }
    }

    public void remove(UUID companionId) {
        if (entries.remove(companionId) != null) setDirty();
    }

    public List<Entry> byOwner(UUID owner) {
        List<Entry> list = new ArrayList<>();
        for (Entry e : entries.values()) if (owner.equals(e.owner)) list.add(e);
        return list;
    }

    public int activeCount(UUID owner) {
        int n = 0;
        for (Entry e : entries.values()) if (owner.equals(e.owner) && e.status == Status.ACTIVE) n++;
        return n;
    }

    /** A companion of this owner that can be brought back (dead or lost), most recent first. */
    @Nullable
    public Entry restorable(UUID owner) {
        Entry best = null;
        for (Entry e : entries.values()) {
            if (owner.equals(e.owner) && e.status != Status.ACTIVE && !e.snapshot.isEmpty()) {
                if (best == null || e.updatedTick > best.updatedTick) best = e;
            }
        }
        return best;
    }
}
