package com.fantasyweapons.combat;

import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.compat.bettercombat.BetterCombatCompat;
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
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Custom melee pipeline used instead of {@code Player.attack} for fantasy weapons.
 * <p>
 * Vanilla's attack spawns crit, sweep, enchant and (scaled by damage!) heart-shaped damage-indicator particles. At
 * these damage values that would be dozens of vanilla particles per hit, so the vanilla attack is cancelled for our
 * weapons and replaced with this, which uses only the custom VFX system.
 */
public final class MeleeHandler {
    /** Swings must wait for (almost) full recovery; a little slack for client/server timing. */
    /** GameTests only: turns off random critical hits so damage comparisons are deterministic. */
    public static boolean suppressRandomCrits;

    public static final float MIN_STRENGTH = 0.85f;
    /** With Better Combat: its swings land after their wind-up, near full strength; this only stops click spam. */
    public static final float BETTER_COMBAT_MIN_STRENGTH = 0.5f;
    private static final Map<ServerPlayer, Swing> SWINGS = new WeakHashMap<>();

    private MeleeHandler() {
    }

    public static void attack(ServerPlayer player, Entity rawTarget) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FantasyWeaponItem item)) return;
        // a staff or spellbook does not strike: its click looses a bolt
        if (item.definition().weaponClass().magic()) {
            com.fantasyweapons.magic.MageBolts.cast(player);
            return;
        }
        // only the weapon's class can fight with it
        if (!com.fantasyweapons.playerclass.ClassGear.canUse(player, stack)) {
            com.fantasyweapons.playerclass.ClassGear.refuse(player, stack);
            return;
        }
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        AbilityRuntime rt = player.getData(ModAttachments.ABILITY_RUNTIME);
        if (rt.isThrown(data.idOrNil())) return;

        ServerLevel level = player.serverLevel();
        WeaponForm form = def.form(data);
        WeaponClass cls = def.weaponClass();
        // with Better Combat installed it picks the targets in the swing's hitbox and strikes them one by one, all in the
        // same tick: the first one is the swing (recovery, damage, crit, sound), the rest are struck by the same blow
        boolean betterCombat = BetterCombatCompat.drivesSwing(player, stack);
        Swing swing = betterCombat ? SWINGS.get(player) : null;
        if (swing == null || !swing.continuedBy(player, level.getGameTime(), data, form, rawTarget)) {
            float strength = player.getAttackStrengthScale(0.5f);
            // heavy weapons have a real recovery: a swing before the weapon has recovered does nothing at all (Better
            // Combat paces its swings itself, and lands each one after the wind-up, so it only needs a loose check)
            if (strength < (betterCombat ? BETTER_COMBAT_MIN_STRENGTH : MIN_STRENGTH)) return;
            player.resetAttackStrengthTicker();

            boolean heavy = player.isShiftKeyDown() && strength >= ServerConfig.HEAVY_ATTACK_MIN_STRENGTH.getOrDefault();
            boolean falling = player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater();
            boolean crit = strength > 0.9f && (falling || !suppressRandomCrits && player.getRandom().nextFloat() < def.critChance());

            float base = ProgressionMath.weaponDamage(def, data) * com.fantasyweapons.armor.SetAbilities.formDamageFactor(player, def, form);
            float damage = base * (0.2f + strength * strength * 0.8f);
            if (heavy) damage *= def.heavyMultiplier();
            if (crit) damage *= def.critMultiplier();
            damage *= com.fantasyweapons.armor.ArmorEffects.meleeMultiplier(player, crit, heavy);
            damage *= StatusService.outgoingMultiplier(player);
            // the combo step's multiplier (a finisher hits harder) and Better Combat's sweep penalty for crowds
            if (betterCombat) damage *= BetterCombatCompat.swingDamageFactor(player);
            swing = new Swing(level.getGameTime(), player.position(), data.idOrNil(), form, strength, heavy, crit, damage);
            if (betterCombat) SWINGS.put(player, swing);
        } else {
            betterCombatFollowUp(player, stack, def, data, cls, swing, rawTarget);
            return;
        }
        float strength = swing.strength;
        boolean heavy = swing.heavy, crit = swing.crit;
        float damage = swing.damage;

        int flags = swing.flags();
        List<Integer> hitIds = new ArrayList<>();
        float totalDealt = 0;

        LivingEntity primary = FWDamage.resolve(rawTarget);
        if (primary == null) {
            // non-living targets (end crystals, item frames...) take a plain hit
            rawTarget.hurt(FWDamage.source(level, FWDamage.Kind.MELEE, player), damage);
            return;
        }
        swing.struck = true;
        swing.targets.add(rawTarget.getId());
        float dealt = FWDamage.deal(player, stack, rawTarget, damage, FWDamage.Kind.MELEE, def.element(), flags);
        if (dealt > 0 || primary.isAlive()) {
            totalDealt += dealt;
            hitIds.add(primary.getId());
            applyKnockback(player, primary, cls.knockback() * (heavy ? 1.6f : 1f) * strength);
            runHook(def, new MeleeContext(player, stack, def, data, primary, dealt, heavy, crit, true));
            com.fantasyweapons.armor.SetAbilities.onMelee(player, stack, def, primary, damage, strength, crit, heavy);
        }

        // Cleave: full-strength swings also hit enemies inside the weapon's arc (Better Combat's hitbox does this itself)
        if (!betterCombat && strength > 0.9f && cls.cleaveAngle() > 0) {
            float reach = cls.cleaveRange() + com.fantasyweapons.armor.SetAbilities.formReach(player, def, form);
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

        lifesteal(player, def, totalDealt);

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

    /**
     * A further target in the hitbox of the Better Combat swing already dealt this tick: it takes the same blow (and
     * the weapon's on-hit effects, as a cleave target would), without a second recovery, sound or crit burst.
     */
    private static void betterCombatFollowUp(ServerPlayer player, ItemStack stack, WeaponDefinition def, WeaponData data, WeaponClass cls,
                                             Swing swing, Entity rawTarget) {
        // Better Combat winds the recovery back before each target; the swing's last strike starts it again
        player.resetAttackStrengthTicker();
        swing.targets.add(rawTarget.getId());
        LivingEntity target = FWDamage.resolve(rawTarget);
        if (target == null) {
            rawTarget.hurt(FWDamage.source(player.serverLevel(), FWDamage.Kind.MELEE, player), swing.damage);
            return;
        }
        // the first living target is the swing's main one, even when Better Combat listed something else before it
        boolean main = !swing.struck;
        swing.struck = true;
        float dealt = FWDamage.deal(player, stack, rawTarget, swing.damage, FWDamage.Kind.MELEE, def.element(), swing.flags());
        if (dealt <= 0 && !target.isAlive()) return;
        applyKnockback(player, target, cls.knockback() * (main ? (swing.heavy ? 1.6f : 1f) : 0.6f) * swing.strength);
        runHook(def, new MeleeContext(player, stack, def, data, target, dealt, swing.heavy, swing.crit, main));
        if (main) {
            com.fantasyweapons.armor.SetAbilities.onMelee(player, stack, def, target, swing.damage, swing.strength, swing.crit, swing.heavy);
            player.setLastHurtMob(target);
        }
        lifesteal(player, def, dealt);
        player.awardStat(Stats.DAMAGE_DEALT, Math.round(dealt * 10f));
        // only the sparks on this target: the crit star and heavy shockwave came with the swing's first hit
        Fx.tracking(player, FxPayload.of(FxIds.MELEE_HIT).caster(player.getId())
                .pos(target.getBoundingBox().getCenter()).dir(player.getLookAngle())
                .power(swing.strength).level(main ? swing.flags() : 0).entities(List.of(target.getId())).seed(player.getRandom().nextLong()).build());
    }

    private static void lifesteal(ServerPlayer player, WeaponDefinition def, float dealt) {
        if (def.lifesteal() > 0) FWDamage.lifesteal(player, dealt, def.lifesteal() * StatusService.lifestealMultiplier(player));
        FWDamage.lifesteal(player, dealt, com.fantasyweapons.armor.ArmorEffects.lifeSteal(player));
    }

    /** One swing's blow, kept for the tick while Better Combat strikes the rest of its targets. */
    private static final class Swing {
        final long tick;
        final Vec3 from;
        final java.util.UUID weapon;
        final WeaponForm form;
        final float strength;
        final boolean heavy, crit;
        final float damage;
        /** Entity ids struck so far. */
        final java.util.Set<Integer> targets = new java.util.HashSet<>();
        /** A living target has taken the main hit. */
        boolean struck;

        Swing(long tick, Vec3 from, java.util.UUID weapon, WeaponForm form, float strength, boolean heavy, boolean crit, float damage) {
            this.tick = tick;
            this.from = from;
            this.weapon = weapon;
            this.form = form;
            this.strength = strength;
            this.heavy = heavy;
            this.crit = crit;
            this.damage = damage;
        }

        /**
         * Whether a strike belongs to this swing: within one Better Combat swing every target is struck once, in the
         * same tick, by the same weapon in the same form, from the same spot. Anything else is a swing of its own.
         */
        boolean continuedBy(ServerPlayer player, long now, WeaponData data, WeaponForm form, Entity target) {
            return now == tick && data.idOrNil().equals(weapon) && form == this.form && !targets.contains(target.getId())
                    && player.position().distanceToSqr(from) < 1e-4;
        }

        int flags() {
            return (crit ? FWDamage.FLAG_CRIT : 0) | (heavy ? FWDamage.FLAG_HEAVY : 0);
        }
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
