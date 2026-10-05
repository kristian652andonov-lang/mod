package com.fantasyweapons.combat;

import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponAnimations;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom melee pipeline used instead of {@code Player.attack} for fantasy weapons.
 * <p>
 * Vanilla's attack spawns crit, sweep, enchant and (scaled by damage!) heart-shaped damage-indicator particles. At
 * these damage values that would be dozens of vanilla particles per hit, so the vanilla attack is cancelled for our
 * weapons and replaced with this, which uses only the custom VFX system.
 */
public final class MeleeHandler {
    /** Swings must wait for (almost) full recovery; a little slack for client/server timing. */
    public static final float MIN_STRENGTH = 0.85f;

    private MeleeHandler() {
    }

    public static void attack(ServerPlayer player, Entity rawTarget) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        AbilityRuntime rt = player.getData(ModAttachments.ABILITY_RUNTIME);
        if (rt.isThrown(data.idOrNil())) return;

        float strength = player.getAttackStrengthScale(0.5f);
        // heavy weapons have a real recovery: a swing before the weapon has recovered does nothing at all
        if (strength < MIN_STRENGTH) return;
        player.resetAttackStrengthTicker();
        ServerLevel level = player.serverLevel();

        WeaponForm form = def.form(data);
        WeaponClass cls = def.weaponClass();
        boolean heavy = player.isShiftKeyDown() && strength >= ServerConfig.HEAVY_ATTACK_MIN_STRENGTH.getOrDefault();
        boolean falling = player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater();
        boolean crit = strength > 0.9f && (falling || player.getRandom().nextFloat() < def.critChance());

        float base = ProgressionMath.weaponDamage(def, data) * (form != null ? form.damageFactor() : 1f);
        float damage = base * (0.2f + strength * strength * 0.8f);
        if (heavy) damage *= def.heavyMultiplier();
        if (crit) damage *= def.critMultiplier();
        damage *= StatusService.outgoingMultiplier(player);

        int flags = (crit ? FWDamage.FLAG_CRIT : 0) | (heavy ? FWDamage.FLAG_HEAVY : 0);
        List<Integer> hitIds = new ArrayList<>();
        float totalDealt = 0;

        LivingEntity primary = FWDamage.resolve(rawTarget);
        if (primary == null) {
            // non-living targets (end crystals, item frames...) take a plain hit
            rawTarget.hurt(FWDamage.source(level, FWDamage.Kind.MELEE, player), damage);
            return;
        }
        float dealt = FWDamage.deal(player, stack, rawTarget, damage, FWDamage.Kind.MELEE, def.element(), flags);
        if (dealt > 0 || primary.isAlive()) {
            totalDealt += dealt;
            hitIds.add(primary.getId());
            applyKnockback(player, primary, cls.knockback() * (heavy ? 1.6f : 1f) * strength);
            runHook(def, new MeleeContext(player, stack, def, data, primary, dealt, heavy, crit, true));
        }

        // Cleave: full-strength swings also hit enemies inside the weapon's arc.
        if (strength > 0.9f && cls.cleaveAngle() > 0) {
            float reach = cls.cleaveRange() + (form != null ? form.reachBonus() : 0f);
            float cleaveFactor = heavy ? 0.85f : 0.6f;
            Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
            for (LivingEntity other : Targeting.inCone(level, player, origin, player.getLookAngle(), reach, cls.cleaveAngle(), FWDamage.Kind.MELEE)) {
                if (other == primary || other instanceof net.minecraft.world.entity.player.Player) continue;
                float d = FWDamage.deal(player, stack, other, damage * cleaveFactor, FWDamage.Kind.MELEE, def.element(), flags);
                if (d > 0) {
                    totalDealt += d;
                    hitIds.add(other.getId());
                    applyKnockback(player, other, cls.knockback() * 0.6f * strength);
                    runHook(def, new MeleeContext(player, stack, def, data, other, d, heavy, crit, false));
                }
            }
        }

        if (def.lifesteal() > 0) FWDamage.lifesteal(player, totalDealt, def.lifesteal() * StatusService.lifestealMultiplier(player));

        player.setLastHurtMob(primary);
        player.causeFoodExhaustion(0.1f);
        player.awardStat(Stats.DAMAGE_DEALT, Math.round(totalDealt * 10f));

        Fx.tracking(player, FxPayload.of(FxIds.MELEE_HIT).caster(player.getId())
                .pos(primary.getBoundingBox().getCenter()).dir(player.getLookAngle())
                .power(strength).level(flags).entities(hitIds).seed(player.getRandom().nextLong()).build());
        level.playSound(null, primary.getX(), primary.getY(), primary.getZ(),
                heavy ? ModSounds.HEAVY_IMPACT.get() : ModSounds.WEAPON_HIT.get(), SoundSource.PLAYERS, heavy ? 1.2f : 0.9f,
                0.9f + player.getRandom().nextFloat() * 0.2f);
        if (heavy) WeaponAnimations.trigger(player, stack, "heavy_attack");
    }

    private static void runHook(WeaponDefinition def, MeleeContext ctx) {
        if (def.meleeHook() != null) def.meleeHook().onHit(ctx);
    }

    public static void applyKnockback(Entity attacker, LivingEntity target, float strength) {
        if (strength <= 0) return;
        float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
        target.knockback(strength, Mth.sin(yaw), -Mth.cos(yaw));
        target.hurtMarked = true;
    }
}
