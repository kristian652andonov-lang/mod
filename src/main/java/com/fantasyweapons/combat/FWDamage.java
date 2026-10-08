package com.fantasyweapons.combat;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Centralised, server-authoritative damage application for every weapon hit, ability, AoE, chain and DoT.
 * Handles friendly-fire rules, status amplification, kill credit, life steal and damage numbers.
 */
public final class FWDamage {
    public static final ResourceKey<DamageType> WEAPON = ResourceKey.create(Registries.DAMAGE_TYPE, FantasyWeapons.id("weapon"));
    public static final ResourceKey<DamageType> ABILITY = ResourceKey.create(Registries.DAMAGE_TYPE, FantasyWeapons.id("ability"));

    public enum Kind { MELEE, ABILITY, DOT }

    /** Flags carried in the damage-number effect. */
    public static final int FLAG_CRIT = 1, FLAG_HEAVY = 2, FLAG_ABILITY = 4, FLAG_DOT = 8, FLAG_EXECUTE = 16;

    private FWDamage() {
    }

    public static DamageSource source(ServerLevel level, Kind kind, Entity attacker) {
        ResourceKey<DamageType> key = kind == Kind.MELEE ? WEAPON : ABILITY;
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker, attacker);
    }

    /** Resolves multipart entities (ender dragon parts) to the entity that actually takes damage. */
    @Nullable
    public static LivingEntity resolve(Entity e) {
        if (e instanceof PartEntity<?> part) e = part.getParent();
        return e instanceof LivingEntity le ? le : null;
    }

    /** Whether {@code attacker}'s abilities may damage {@code target}. Melee on the crosshair target is always allowed by vanilla rules instead. */
    public static boolean canHarm(Player attacker, Entity target, Kind kind) {
        if (target == attacker || !target.isAlive() || target.isSpectator() || target.isInvulnerable()) return false;
        if (!(target instanceof LivingEntity) && !(target instanceof PartEntity<?>)) return false;
        if (target instanceof ArmorStand && kind != Kind.MELEE) return false;
        if (target instanceof Player p) {
            if (kind != Kind.MELEE && !ServerConfig.ABILITIES_HURT_PLAYERS.getOrDefault()) return false;
            return attacker.canHarmPlayer(p);
        }
        if (attacker.isAlliedTo(target)) return false;
        if (target instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            if (owned.getOwnerUUID().equals(attacker.getUUID())) return false;
            if (kind != Kind.MELEE && !ServerConfig.ABILITIES_HURT_TAMED.getOrDefault()) return false;
        }
        return true;
    }

    /**
     * Deals damage on behalf of a weapon. Returns the health actually removed (0 if the hit was blocked/immune).
     *
     * @param weapon the weapon stack credited for the hit (may be empty for ownerless effects)
     */
    public static float deal(ServerPlayer attacker, ItemStack weapon, Entity rawTarget, float amount, Kind kind,
                             @Nullable Element element, int flags) {
        LivingEntity target = resolve(rawTarget);
        if (target == null || amount <= 0) return 0;
        if (!canHarm(attacker, rawTarget, kind)) return 0;
        ServerLevel level = attacker.serverLevel();

        float scaled = amount * StatusService.incomingMultiplier(target, element);
        float before = target.getHealth() + target.getAbsorptionAmount();
        boolean hurt = rawTarget.hurt(source(level, kind, attacker), scaled);
        if (!hurt) return 0;
        float after = target.isAlive() ? target.getHealth() + target.getAbsorptionAmount() : 0;
        float dealt = Math.max(0, before - after);

        if (!weapon.isEmpty() && weapon.getItem() instanceof FantasyWeaponItem) {
            recordHit(attacker, weapon, target);
        }
        Fx.only(attacker, FxPayload.of(FxIds.DAMAGE_NUMBER).caster(attacker.getId()).pos(target.position().add(0, target.getBbHeight(), 0))
                .power(dealt > 0 ? dealt : scaled).level(flags | (kind == Kind.ABILITY ? FLAG_ABILITY : 0) | (kind == Kind.DOT ? FLAG_DOT : 0))
                .entities(target.getId()).build());
        return dealt;
    }

    /** Heals the attacker by a fraction of damage dealt. */
    public static void lifesteal(ServerPlayer attacker, float dealt, float fraction) {
        if (fraction <= 0 || dealt <= 0 || !attacker.isAlive()) return;
        attacker.heal(dealt * fraction);
    }

    public static void recordHit(ServerPlayer attacker, ItemStack weapon, LivingEntity target) {
        HitTracker t = target.getData(ModAttachments.HIT_TRACKER);
        WeaponData data = FantasyWeaponItem.data(weapon);
        t.player = attacker.getUUID();
        t.weapon = data.idOrNil();
        t.gameTime = attacker.serverLevel().getServer().overworld().getGameTime();
    }
}
