package com.aigirlfriend.world;

import com.aigirlfriend.entity.GirlfriendEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loaded companions by identity (server side). Used to stop duplicates (e.g. chunk/portal dupes) and to find
 * an owner's companions without scanning the world.
 */
public final class ActiveCompanions {
    private static final Map<UUID, GirlfriendEntity> BY_ID = new HashMap<>();

    private ActiveCompanions() {}

    /**
     * Registers a companion entering a level. Returns false if this copy must be discarded.
     * Copies loaded from disk lose against a live copy; freshly transferred copies (portal, restore) replace it.
     */
    public static boolean onJoin(GirlfriendEntity gf, boolean loadedFromDisk, @Nullable CompanionRegistry registry) {
        UUID id = gf.data().companionId;
        if (registry != null) {
            CompanionRegistry.Entry e = registry.get(id);
            if (e != null && e.generation > gf.data().generation) return false; // stale copy of a restored companion
        }
        GirlfriendEntity existing = BY_ID.get(id);
        if (existing != null && existing != gf && !existing.isRemoved()) {
            if (loadedFromDisk) return false;
            existing.discard();
        }
        BY_ID.put(id, gf);
        return true;
    }

    public static void onLeave(GirlfriendEntity gf) {
        BY_ID.remove(gf.data().companionId, gf);
    }

    @Nullable
    public static GirlfriendEntity get(UUID companionId) {
        GirlfriendEntity g = BY_ID.get(companionId);
        return g != null && !g.isRemoved() ? g : null;
    }

    public static List<GirlfriendEntity> ownedBy(UUID owner) {
        List<GirlfriendEntity> list = new ArrayList<>();
        for (GirlfriendEntity g : BY_ID.values()) if (!g.isRemoved() && owner.equals(g.getOwnerUUID())) list.add(g);
        return list;
    }

    public static void clear() {
        BY_ID.clear();
    }
}
