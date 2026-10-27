package com.fantasyweapons.world;

import com.fantasyweapons.progression.ExpService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A lingering, server-side gameplay effect (rift, travelling slash, field...). Not an entity: it has no rendering of
 * its own and costs nothing on the client. Clients get a one-shot {@code FxPayload} and simulate the visuals.
 */
public abstract class AreaEffect {
    protected final ServerLevel level;
    protected final UUID ownerId;
    protected final UUID weaponId;
    protected Vec3 pos;
    protected int age;
    protected final int lifetime;
    protected boolean finished;
    /** Entities already hit by one-hit-per-entity effects. */
    protected final Set<Integer> hitOnce = new HashSet<>();

    protected AreaEffect(ServerLevel level, ServerPlayer owner, UUID weaponId, Vec3 pos, int lifetime) {
        this.level = level;
        this.ownerId = owner.getUUID();
        this.weaponId = weaponId;
        this.pos = pos;
        this.lifetime = lifetime;
    }

    /** @return false when the effect should be removed */
    final boolean tickInternal() {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.level() != level) {
            onEnd(null);
            return false;
        }
        age++;
        tick(owner);
        if (finished || age >= lifetime) {
            onEnd(owner);
            return false;
        }
        return true;
    }

    protected abstract void tick(ServerPlayer owner);

    /** Called once when the effect ends (owner is null if they left). */
    protected void onEnd(@Nullable ServerPlayer owner) {
    }

    /** The weapon stack that created this effect (for kill credit), or empty if it left the inventory. */
    protected ItemStack weapon(ServerPlayer owner) {
        return ExpService.findWeapon(owner, weaponId);
    }

    public Vec3 pos() {
        return pos;
    }
}
