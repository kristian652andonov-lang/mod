package com.fantasyweapons.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player ability state owned by the server. Clients only ever receive copies of it.
 * <ul>
 *   <li>Cooldowns are keyed by weapon instance + ability, persisted, and survive relogging/death.</li>
 *   <li>Charge state records which ability is charging and since when (server ticks).</li>
 *   <li>A thrown weapon (scythes) is tracked so the weapon can't attack/cast while in flight.</li>
 * </ul>
 */
public final class AbilityRuntime {
    public static final UUID NIL = new UUID(0, 0);

    public record Cooldown(long end, int total) {
        static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(Cooldown::end),
                Codec.INT.fieldOf("total").forGetter(Cooldown::total)
        ).apply(i, Cooldown::new));
    }

    public static final Codec<AbilityRuntime> CODEC = Codec.unboundedMap(Codec.STRING, Cooldown.CODEC)
            .xmap(AbilityRuntime::new, r -> r.cooldowns);

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityRuntime> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityRuntime decode(RegistryFriendlyByteBuf buf) {
            AbilityRuntime r = new AbilityRuntime();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) r.cooldowns.put(buf.readUtf(), new Cooldown(buf.readVarLong(), buf.readVarInt()));
            r.chargingAbility = buf.readUtf();
            r.chargingWeapon = UUIDUtil.STREAM_CODEC.decode(buf);
            r.chargeStart = buf.readVarLong();
            r.chargeTicks = buf.readVarInt();
            r.thrownWeapon = UUIDUtil.STREAM_CODEC.decode(buf);
            r.activeAbility = buf.readUtf();
            r.activeUntil = buf.readVarLong();
            return r;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AbilityRuntime r) {
            buf.writeVarInt(r.cooldowns.size());
            r.cooldowns.forEach((k, v) -> {
                buf.writeUtf(k);
                buf.writeVarLong(v.end());
                buf.writeVarInt(v.total());
            });
            buf.writeUtf(r.chargingAbility);
            UUIDUtil.STREAM_CODEC.encode(buf, r.chargingWeapon);
            buf.writeVarLong(r.chargeStart);
            buf.writeVarInt(r.chargeTicks);
            UUIDUtil.STREAM_CODEC.encode(buf, r.thrownWeapon);
            buf.writeUtf(r.activeAbility);
            buf.writeVarLong(r.activeUntil);
        }
    };

    private final Map<String, Cooldown> cooldowns;
    private String chargingAbility = "";
    private UUID chargingWeapon = NIL;
    private long chargeStart;
    private int chargeTicks;
    private UUID thrownWeapon = NIL;
    private String activeAbility = "";
    private long activeUntil;

    /** Server-side only: inventory slot the charge started from, and packet rate limiting. */
    public transient int chargingSlot = -1;
    public transient long rateWindowStart;
    public transient int rateWindowCount;

    public AbilityRuntime() {
        this.cooldowns = new HashMap<>();
    }

    private AbilityRuntime(Map<String, Cooldown> cooldowns) {
        this.cooldowns = new HashMap<>(cooldowns);
    }

    public static String cooldownKey(UUID weapon, String abilityId) {
        return weapon + "|" + abilityId;
    }

    // ---- cooldowns ----

    public long cooldownRemaining(UUID weapon, String abilityId, long now) {
        Cooldown c = cooldowns.get(cooldownKey(weapon, abilityId));
        return c == null ? 0 : Math.max(0, c.end() - now);
    }

    public int cooldownTotal(UUID weapon, String abilityId) {
        Cooldown c = cooldowns.get(cooldownKey(weapon, abilityId));
        return c == null ? 0 : c.total();
    }

    public void setCooldown(UUID weapon, String abilityId, long now, int ticks) {
        if (ticks <= 0) cooldowns.remove(cooldownKey(weapon, abilityId));
        else cooldowns.put(cooldownKey(weapon, abilityId), new Cooldown(now + ticks, ticks));
    }

    /** Drops expired cooldowns so the synced map stays small. */
    public boolean pruneCooldowns(long now) {
        return cooldowns.values().removeIf(c -> c.end() <= now);
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    // ---- charging ----

    public boolean isCharging() {
        return !chargingAbility.isEmpty();
    }

    public String chargingAbility() {
        return chargingAbility;
    }

    public UUID chargingWeapon() {
        return chargingWeapon;
    }

    public long chargeStart() {
        return chargeStart;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    public float chargeFraction(long now) {
        if (!isCharging()) return 0;
        if (chargeTicks <= 0) return 1;
        return Math.min(1f, Math.max(0f, (now - chargeStart) / (float) chargeTicks));
    }

    public void startCharge(String abilityId, UUID weapon, int slot, long now, int ticks) {
        this.chargingAbility = abilityId;
        this.chargingWeapon = weapon;
        this.chargingSlot = slot;
        this.chargeStart = now;
        this.chargeTicks = ticks;
    }

    public void clearCharge() {
        this.chargingAbility = "";
        this.chargingWeapon = NIL;
        this.chargingSlot = -1;
        this.chargeStart = 0;
        this.chargeTicks = 0;
    }

    // ---- active window (an ability that keeps acting for a while, e.g. Void Dimension) ----

    public String activeAbility() {
        return activeAbility;
    }

    public long activeUntil() {
        return activeUntil;
    }

    public boolean isActive(long now) {
        return !activeAbility.isEmpty() && now < activeUntil;
    }

    public void setActive(String abilityId, long until) {
        this.activeAbility = abilityId;
        this.activeUntil = until;
    }

    // ---- thrown weapon ----

    public UUID thrownWeapon() {
        return thrownWeapon;
    }

    public boolean isThrown(UUID weapon) {
        return !NIL.equals(thrownWeapon) && thrownWeapon.equals(weapon);
    }

    public void setThrown(UUID weapon) {
        this.thrownWeapon = weapon == null ? NIL : weapon;
        this.thrownSince = -1;
    }

    /** Server only: game time the throw was first seen by the watchdog (-1 = not yet). */
    public long thrownSince = -1;

    /** Copy of the raw cooldown map for the client mirror. */
    public Map<String, Cooldown> cooldowns() {
        return cooldowns;
    }
}
