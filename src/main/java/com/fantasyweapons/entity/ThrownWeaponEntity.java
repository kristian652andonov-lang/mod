package com.fantasyweapons.entity;

import com.fantasyweapons.SidedHooks;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.registry.ModEntities;
import com.fantasyweapons.weapon.Element;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The actual weapon in flight (Soulreaper / Eclipse Reaper throws). Server-driven: flies out, orbits or hunts a list
 * of targets, then returns to its owner's hand. Clients render the real item model spinning. While it exists the
 * owner's runtime marks the weapon as thrown (hidden in hand, no melee, no other abilities).
 */
public class ThrownWeaponEntity extends Entity {
    public enum Mode { OUT, ORBIT, HUNT, RETURN }

    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(ThrownWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ThrownWeaponEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(ThrownWeaponEntity.class, EntityDataSerializers.BYTE);

    /** Called for each entity the weapon cuts; returns the damage actually dealt. */
    public interface HitHandler {
        float onHit(ServerPlayer owner, ItemStack weapon, LivingEntity target, ThrownWeaponEntity thrown);
    }

    // server-only flight plan
    private UUID ownerUuid;
    private UUID weaponId;
    private Vec3 dir = Vec3.ZERO;
    private double speed = 1.0;
    private double range = 12;
    private double travelled;
    private double radius = 1.3;
    private int orbitTicks;
    private double orbitRadius = 3.5;
    private double orbitAngle;
    private int orbitAge;
    private final Deque<Integer> huntTargets = new ArrayDeque<>();
    private int rehitTicks = 1000;
    private final Map<Integer, Integer> lastHit = new HashMap<>();
    @Nullable
    private HitHandler onHit;
    @Nullable
    private Runnable onCaught;
    private int life;
    private int returnTicks;
    /** Client only: the stack drawn for this entity (own animation instance, see ThrownWeaponRenderer). */
    public ItemStack displayStack;
    private boolean clientInit;

    public ThrownWeaponEntity(EntityType<? extends ThrownWeaponEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ThrownWeaponEntity create(ServerPlayer owner, ItemStack stack, UUID weaponId, Vec3 start) {
        ThrownWeaponEntity e = new ThrownWeaponEntity(ModEntities.THROWN_WEAPON.get(), owner.level());
        e.setPos(start);
        e.ownerUuid = owner.getUUID();
        e.weaponId = weaponId;
        e.entityData.set(STACK, stack.copyWithCount(1));
        e.entityData.set(OWNER, owner.getId());
        return e;
    }

    // ---- flight plan builders (server) ----

    public ThrownWeaponEntity out(Vec3 direction, double speed, double range) {
        this.dir = direction.normalize();
        this.speed = speed;
        this.range = range;
        setMode(Mode.OUT);
        return this;
    }

    public ThrownWeaponEntity orbit(int ticks, double radius, double speed) {
        this.orbitTicks = ticks;
        this.orbitRadius = radius;
        this.speed = speed;
        setMode(Mode.ORBIT);
        return this;
    }

    private static double smooth(double k) {
        return k * k * (3 - 2 * k);
    }

    public ThrownWeaponEntity hunt(List<? extends Entity> targets, double speed) {
        huntTargets.clear();
        for (Entity t : targets) huntTargets.add(t.getId());
        this.speed = speed;
        setMode(Mode.HUNT);
        return this;
    }

    public ThrownWeaponEntity hits(double radius, int rehitTicks, HitHandler handler) {
        this.radius = radius;
        this.rehitTicks = rehitTicks;
        this.onHit = handler;
        return this;
    }

    public ThrownWeaponEntity onCaught(Runnable r) {
        this.onCaught = r;
        return this;
    }

    // ---- synced state ----

    public ItemStack stack() {
        return entityData.get(STACK);
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    public Mode mode() {
        byte b = entityData.get(MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, b))];
    }

    private void setMode(Mode m) {
        entityData.set(MODE, (byte) m.ordinal());
        lastHit.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STACK, ItemStack.EMPTY);
        builder.define(OWNER, -1);
        builder.define(MODE, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!clientInit) {
                clientInit = true;
                SidedHooks.get().onThrownWeapon(this);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        ServerPlayer owner = ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null || owner.level() != level || !owner.isAlive() || ++life > 400) {
            finish(owner);
            return;
        }
        Vec3 from = position();
        Vec3 to = switch (mode()) {
            case OUT -> {
                Vec3 next = from.add(dir.scale(speed));
                HitResult hit = level.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                travelled += speed;
                if (hit.getType() != HitResult.Type.MISS || travelled >= range) {
                    setMode(Mode.RETURN);
                    yield hit.getType() != HitResult.Type.MISS ? hit.getLocation() : next;
                }
                yield next;
            }
            case ORBIT -> {
                // spirals out from the hand, circles at full reach, then spirals back in and is caught: no sharp turns
                Vec3 c = owner.position().add(0, 1.1, 0);
                if (orbitAge == 0) orbitAngle = Math.atan2(from.z - c.z, from.x - c.x);
                orbitAge++;
                double in = smooth(Math.min(1, orbitAge / 10.0));
                double out = smooth(Math.min(1, orbitTicks / 12.0));
                double r = 0.8 + (orbitRadius - 0.8) * in * out;
                orbitAngle += speed / Math.max(1.0, orbitRadius) * (0.6 + 0.4 * in);
                Vec3 at = c.add(Math.cos(orbitAngle) * r, Math.sin(orbitAngle * 2) * 0.25 * in * out, Math.sin(orbitAngle) * r);
                if (--orbitTicks <= 0) finish(owner);
                yield at;
            }
            case HUNT -> {
                Entity t = null;
                while (!huntTargets.isEmpty()) {
                    t = level.getEntity(huntTargets.peek());
                    if (t != null && t.isAlive()) break;
                    huntTargets.poll();
                    t = null;
                }
                if (t == null) {
                    setMode(Mode.RETURN);
                    yield from;
                }
                Vec3 goal = t.getBoundingBox().getCenter();
                Vec3 d = goal.subtract(from);
                if (d.length() <= speed + 0.6) {
                    huntTargets.poll();
                    lastHit.remove(t.getId());
                    yield goal;
                }
                yield from.add(d.normalize().scale(speed));
            }
            case RETURN -> {
                // flies home smoothly: eases in from a slow start, never jumps the last stretch
                returnTicks++;
                Vec3 goal = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
                Vec3 d = goal.subtract(from);
                double dist = d.length();
                if (dist <= 0.9) {
                    finish(owner);
                    yield goal;
                }
                double cruise = Math.max(1.0, speed * 1.1) + returnTicks * 0.03; // keeps gaining so it always catches up
                double s = Math.min(dist, Math.min(cruise, 0.3 + returnTicks * 0.1));
                yield from.add(d.normalize().scale(s));
            }
        };
        setPos(to);
        if (isRemoved() || onHit == null) return;
        ItemStack weapon = weaponStack(owner);
        for (LivingEntity e : Targeting.alongPath(level, owner, from, to, radius, FWDamage.Kind.ABILITY)) {
            Integer last = lastHit.get(e.getId());
            if (last != null && life - last < rehitTicks) continue;
            lastHit.put(e.getId(), life);
            onHit.onHit(owner, weapon, e, this);
        }
    }

    public ItemStack weaponStack(ServerPlayer owner) {
        ItemStack s = ExpService.findWeapon(owner, weaponId);
        return s.isEmpty() ? stack() : s;
    }

    private void finish(@Nullable ServerPlayer owner) {
        if (owner != null) {
            AbilityRuntime rt = owner.getData(ModAttachments.ABILITY_RUNTIME);
            if (rt.isThrown(weaponId)) {
                rt.setThrown(null);
                owner.syncData(ModAttachments.ABILITY_RUNTIME);
            }
            if (onCaught != null) onCaught.run();
        }
        discard();
    }

    /**
     * However the entity goes away — caught, /kill, chunk unload, dimension change — the weapon must never stay
     * "in flight", or its abilities and form switch would be locked forever.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && ownerUuid != null && level() instanceof ServerLevel sl) {
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                AbilityRuntime rt = owner.getData(ModAttachments.ABILITY_RUNTIME);
                if (rt.isThrown(weaponId)) {
                    rt.setThrown(null);
                    owner.syncData(ModAttachments.ABILITY_RUNTIME);
                }
            }
        }
        super.remove(reason);
    }

    /** Element of the hits (for helpers that need it). */
    public Element element() {
        return Element.SOUL;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
