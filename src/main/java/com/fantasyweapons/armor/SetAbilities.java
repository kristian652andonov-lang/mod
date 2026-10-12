package com.fantasyweapons.armor;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.ProjectileEffect;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.HitTracker;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.fantasyweapons.weapons.infernochain.Infernochain;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The full-set abilities: what wearing all four pieces of a legendary set does. They work with any weapon, except where
 * the ability names one (Infernochain's chain attacks, switching a weapon's mode for the Eclipse Reaper).
 */
public final class SetAbilities {
    private SetAbilities() {
    }

    // ---- Voidfang: Master of the Void ----
    /** After a teleport, the next strike tears a rift within this many ticks... */
    static final int RIFT_WINDOW = 100;
    /** ...which echoes the hit this many ticks later, for this share of it (half that to enemies beside the rift). */
    static final int RIFT_DELAY = 8;
    static final float RIFT_ECHO = 0.7f;
    static final double RIFT_RADIUS = 2.5;
    // ---- Solaris: Crown of Dawn ----
    /** Attack strength that counts as a fully charged strike. */
    static final float DAWN_STRENGTH = 0.98f;
    static final double DAWN_RADIUS = 3.5;
    static final float DAWN_DAMAGE = 0.35f;
    // ---- Frostrend: Heart of Winter ----
    static final int CHILL_TICKS = 80;
    /** Frostbite slows by this much per stack (up to five stacks from repeated hits). */
    static final float CHILL_SLOW = 0.12f;
    // ---- Doomcleaver: Blood Frenzy ----
    static final double FRENZY_ATTACK_SPEED = 0.4;
    static final float FRENZY_LIFE_STEAL = 0.2f;
    // ---- Stormbreaker: Eye of the Storm ----
    static final int STORM_HITS = 4;
    static final float STORM_DAMAGE = 0.8f;
    static final double STORM_RADIUS = 2.5;
    // ---- Gravebite: Legion of the Grave ----
    static final float RISE_CHANCE = 0.35f;
    static final int MAX_ALLIES = 3;
    static final int ALLY_LIFE = 160;
    static final float ALLY_DAMAGE = 0.6f;
    // ---- Soulreaper: Harvester of Souls ----
    public static final int MAX_SOULS = 5;
    /** Ability damage per stored soul. */
    static final float SOUL_BONUS = 0.15f;
    static final int SOUL_EMPOWER_TICKS = 100;
    // ---- Bloomfall: Heart of the Grove ----
    public static final int GROVE_STILL = 40;
    static final float GROVE_HEAL = 1f;
    static final double GROVE_RADIUS = 5;
    // ---- Eclipse Reaper: Totality ----
    static final int TOTALITY_TICKS = 80;
    static final int TOTALITY_COOLDOWN = 200;
    static final double TOTALITY_RADIUS = 5.5;
    static final float TOTALITY_DAMAGE = 0.8f;
    // ---- Starforge: Gravity Well ----
    static final double WELL_RADIUS = 6;
    // ---- Aetherlance: Perfect Precision ----
    static final float BOLT_DAMAGE = 0.5f;
    static final double BOLT_RANGE = 18;
    // ---- Monolith: Immovable ----
    static final double SHOCK_RADIUS = 4.5;
    static final float SHOCK_DAMAGE = 0.5f;
    // ---- Infernochain: Hellforged ----
    static final int TRAIL_TICKS = 60;
    static final int TRAIL_COOLDOWN = 8;

    private static final ResourceLocation FRENZY_SPEED_ID = FantasyWeapons.id("set_blood_frenzy");
    private static final ResourceLocation IMMOVABLE_ID = FantasyWeapons.id("set_immovable");

    /** The numbers behind a set's ability, for its tooltip. */
    public static String details(ArmorSet set) {
        return switch (set) {
            case VOIDFANG -> "Within " + RIFT_WINDOW / 20 + "s of a teleport · the echo deals " + pct(RIFT_ECHO) + " of the hit";
            case SOLARIS -> "Full-strength hits and fully charged abilities · " + pct(DAWN_DAMAGE) + " of the hit around the target, and burns";
            case FROSTREND -> "Frostbite slows " + pct(CHILL_SLOW) + " per hit, up to five, for " + CHILL_TICKS / 20 + "s";
            case DOOMCLEAVER -> "+" + pct((float) FRENZY_ATTACK_SPEED) + " attack speed and +" + pct(FRENZY_LIFE_STEAL) + " life steal";
            case STORMBREAKER -> "Every " + STORM_HITS + "th hit · " + pct(STORM_DAMAGE) + " of the hit, and staggers";
            case GRAVEBITE -> pct(RISE_CHANCE) + " chance · up to " + MAX_ALLIES + " allies for " + ALLY_LIFE / 20 + "s";
            case SOULREAPER -> "Up to " + MAX_SOULS + " souls · +" + pct(SOUL_BONUS) + " ability damage each";
            case BLOOMFALL -> "After " + GROVE_STILL / 20 + "s still · heals half a heart a second, roots within " + (int) GROVE_RADIUS + " blocks";
            case ECLIPSE_REAPER -> "Once every " + TOTALITY_COOLDOWN / 20 + "s · both modes for " + TOTALITY_TICKS / 20 + "s";
            case STARFORGE -> "Pulls enemies within " + (int) WELL_RADIUS + " blocks of the impact";
            case AETHERLANCE -> "The bolt deals " + pct(BOLT_DAMAGE) + " of the hit and pierces";
            case MONOLITH -> "The shockwave deals " + pct(SHOCK_DAMAGE) + " of the hit around the impact";
            case INFERNOCHAIN -> "The Infernochain's chain form and abilities · burns for " + TRAIL_TICKS / 20 + "s";
        };
    }

    private static String pct(float f) {
        return Math.round(f * 100) + "%";
    }

    // ------------------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------------------

    public static SetState state(Player p) {
        return p.getData(ModAttachments.SET_STATE);
    }

    static boolean has(LivingEntity e, ArmorSet set) {
        return FantasyArmorItem.fullSet(e, set);
    }

    static long now(ServerPlayer p) {
        return AbilityService.now(p);
    }

    static void sync(ServerPlayer p) {
        p.syncData(ModAttachments.SET_STATE);
    }

    /** The held fantasy weapon, or empty. */
    static ItemStack weapon(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return s.getItem() instanceof FantasyWeaponItem ? s : ItemStack.EMPTY;
    }

    static UUID weaponId(ServerPlayer p) {
        ItemStack s = weapon(p);
        return s.isEmpty() ? AbilityRuntime.NIL : FantasyWeaponItem.data(s).idOrNil();
    }

    /** The wearer's strength for set effects that don't come from a hit: the held weapon's damage, else their attack damage. */
    static float power(ServerPlayer p) {
        ItemStack s = weapon(p);
        if (s.getItem() instanceof FantasyWeaponItem w) {
            WeaponData data = FantasyWeaponItem.data(s);
            WeaponForm form = w.definition().form(data);
            return ProgressionMath.weaponDamage(w.definition(), data) * (form != null ? form.damageFactor() : 1f);
        }
        return (float) Math.max(1, p.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    private static void setModifier(LivingEntity e, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier have = inst.getModifier(id);
        if (amount == 0) {
            if (have != null) inst.removeModifier(id);
        } else if (have == null || have.amount() != amount) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static long seed(ServerPlayer p) {
        return p.getRandom().nextLong();
    }

    // ------------------------------------------------------------------------------------------------------------
    // melee
    // ------------------------------------------------------------------------------------------------------------

    /**
     * After a melee swing landed on {@code target}.
     *
     * @param damage   the swing's damage before the target's defences
     * @param strength how far the attack was charged (1 = fully)
     */
    public static void onMelee(ServerPlayer p, ItemStack stack, WeaponDefinition def, LivingEntity target, float damage, float strength,
                               boolean crit, boolean heavy) {
        ArmorSet set = FantasyArmorItem.fullSet(p);
        if (set == null) return;
        switch (set) {
            case VOIDFANG -> riftEcho(p, stack, target, damage);
            case SOLARIS -> {
                if (strength >= DAWN_STRENGTH) dawnBurst(p, stack, target.getBoundingBox().getCenter(), damage * DAWN_DAMAGE, DAWN_RADIUS, target);
            }
            case STORMBREAKER -> storm(p, stack, target, damage);
            case STARFORGE -> {
                if (heavy) gravityWell(p, target);
            }
            case AETHERLANCE -> {
                if (crit) precisionBolt(p, stack, target, damage);
            }
            case MONOLITH -> {
                if (heavy) shockwave(p, stack, target, damage);
            }
            case INFERNOCHAIN -> {
                if (chainForm(def, stack)) hellfireTrail(p, target);
            }
            default -> {
            }
        }
    }

    /** After an ability of {@code weapon} dealt damage. */
    public static void onAbilityHit(ServerPlayer p, ItemStack weapon, LivingEntity target) {
        if (weapon.getItem() instanceof FantasyWeaponItem w && w.definition().id().equals("infernochain") && has(p, ArmorSet.INFERNOCHAIN)) {
            hellfireTrail(p, target);
        }
    }

    // ---- Voidfang ----

    /** A teleport (an ability's, an ender pearl's or a chorus fruit's) primes the next strike. */
    public static void onTeleport(ServerPlayer p) {
        if (!has(p, ArmorSet.VOIDFANG)) return;
        state(p).riftUntil = now(p) + RIFT_WINDOW;
        sync(p);
        Fx.tracking(p, FxPayload.of(FxIds.SET_RIFT_PRIMED).caster(p.getId()).pos(p.position()).power(RIFT_WINDOW).build());
    }

    static void riftEcho(ServerPlayer p, ItemStack stack, LivingEntity target, float damage) {
        SetState s = state(p);
        if (s.riftUntil <= now(p)) return;
        s.riftUntil = 0;
        sync(p);
        ServerLevel level = p.serverLevel();
        Vec3 at = target.getBoundingBox().getCenter();
        int targetId = target.getId();
        float echo = damage * RIFT_ECHO;
        Kit.fx(level, FxPayload.of(FxIds.SET_RIFT_ECHO).caster(p.getId()).pos(at).dir(p.getLookAngle()).power(RIFT_DELAY).scale((float) RIFT_RADIUS)
                .entities(targetId).seed(seed(p)).build());
        Kit.sound(level, at, ModSounds.RIFT_OPEN.get(), 1f, 1.4f);
        Delayed.schedule(p, weaponId(p), RIFT_DELAY, (o, w) -> {
            ServerLevel l = o.serverLevel();
            // the rift stays where the hit landed: it echoes on the target if the target is still at it
            if (l.getEntity(targetId) instanceof LivingEntity t && t.isAlive() && t.getBoundingBox().getCenter().distanceToSqr(at) < 9) {
                FWDamage.deal(o, w, t, echo, FWDamage.Kind.ABILITY, Element.VOID, FWDamage.FLAG_CRIT);
            }
            for (LivingEntity e : Targeting.inRadius(l, o, at, RIFT_RADIUS, FWDamage.Kind.ABILITY)) {
                if (e.getId() != targetId) FWDamage.deal(o, w, e, echo * 0.5f, FWDamage.Kind.ABILITY, Element.VOID, 0);
            }
            Kit.sound(l, at, ModSounds.RIFT_CLOSE.get(), 1.1f, 1.2f);
        });
    }

    // ---- Solaris ----

    static void dawnBurst(ServerPlayer p, ItemStack stack, Vec3 center, float damage, double radius, @Nullable LivingEntity spared) {
        ServerLevel level = p.serverLevel();
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(level, p, center, radius, FWDamage.Kind.ABILITY)) {
            if (e != spared) FWDamage.deal(p, stack, e, damage, FWDamage.Kind.ABILITY, Element.SOLAR, 0);
            if (e.isAlive()) {
                e.igniteForSeconds(3);
                StatusService.apply(e, StatusType.SOLAR_BURN, 60, 1, damage * 0.06f, p.getUUID());
            }
            ids.add(e.getId());
        }
        Kit.fx(level, FxPayload.of(FxIds.SET_DAWN_BURST).caster(p.getId()).pos(center).scale((float) radius).entities(ids).seed(seed(p)).build());
        Kit.sound(level, center, ModSounds.SOLAR_BURST.get(), 0.9f, 1.5f);
    }

    // ---- Stormbreaker ----

    static void storm(ServerPlayer p, ItemStack stack, LivingEntity target, float damage) {
        SetState s = state(p);
        s.stormHits++;
        if (s.stormHits < STORM_HITS) {
            sync(p);
            return;
        }
        s.stormHits = 0;
        sync(p);
        ServerLevel level = p.serverLevel();
        Vec3 foot = target.position();
        // the bolt falls from the sky, or from the ceiling in a cave
        Vec3 top = new Vec3(foot.x, target.getBoundingBox().maxY + 0.2, foot.z);
        HitResult roof = level.clip(new ClipContext(top, top.add(0, 18, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        Vec3 sky = roof.getType() == HitResult.Type.MISS ? top.add(0, 18, 0) : roof.getLocation();
        List<Integer> ids = new ArrayList<>();
        FWDamage.deal(p, stack, target, damage * STORM_DAMAGE, FWDamage.Kind.ABILITY, Element.LIGHTNING, FWDamage.FLAG_CRIT);
        if (target.isAlive()) StatusService.apply(target, StatusType.STAGGERED, 15, 1, 0f, p.getUUID());
        ids.add(target.getId());
        for (LivingEntity e : Targeting.inRadius(level, p, foot, STORM_RADIUS, FWDamage.Kind.ABILITY)) {
            if (e == target) continue;
            FWDamage.deal(p, stack, e, damage * STORM_DAMAGE * 0.5f, FWDamage.Kind.ABILITY, Element.LIGHTNING, 0);
            ids.add(e.getId());
        }
        Kit.fx(level, FxPayload.of(FxIds.SET_STORM_BOLT).caster(p.getId()).pos(foot).point(sky).scale((float) STORM_RADIUS).entities(ids)
                .seed(seed(p)).build());
        Kit.sound(level, foot, ModSounds.LIGHTNING.get(), 1.3f, 1.1f);
    }

    // ---- Starforge ----

    static void gravityWell(ServerPlayer p, LivingEntity target) {
        ServerLevel level = p.serverLevel();
        Vec3 c = target.position();
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(level, p, c, WELL_RADIUS, FWDamage.Kind.ABILITY)) {
            if (e == target) continue;
            Kit.pull(e, c.add(0, 0.5, 0), 1.1);
            StatusEffects.Instance g = StatusService.apply(e, StatusType.GRAVITY_BOUND, 20, 1, 0.45f, p.getUUID());
            if (g != null) {
                g.px = c.x;
                g.py = c.y;
                g.pz = c.z;
            }
            ids.add(e.getId());
        }
        Kit.fx(level, FxPayload.of(FxIds.SET_GRAVITY_WELL).caster(p.getId()).pos(c).scale((float) WELL_RADIUS).entities(ids).seed(seed(p)).build());
        Kit.sound(level, c, ModSounds.GRAVITY.get(), 1f, 1.3f);
    }

    // ---- Aetherlance ----

    static void precisionBolt(ServerPlayer p, ItemStack stack, LivingEntity target, float damage) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 start = p.getEyePosition().add(look.scale(0.7)).subtract(0, 0.25, 0);
        Vec3 vel = look.scale(1.8);
        float dealt = damage * BOLT_DAMAGE;
        long seed = seed(p);
        ProjectileEffect bolt = new ProjectileEffect(level, p, weaponId(p), start, vel, 0.6, BOLT_RANGE, 4, (o, t, pr) -> {
            FWDamage.deal(o, stack, t, dealt, FWDamage.Kind.ABILITY, Element.ENERGY, FWDamage.FLAG_CRIT);
            return false;
        }).ignore(target).onEnd((o, pos, hit, pr) -> Kit.fx(o.serverLevel(), FxPayload.of(FxIds.SET_PRECISION_BOLT_END).caster(o.getId()).pos(pos)
                .seed(seed).build()));
        AreaEffectManager.add(bolt);
        Kit.fx(level, FxPayload.of(FxIds.SET_PRECISION_BOLT).caster(p.getId()).pos(start).dir(vel).power((float) BOLT_RANGE).seed(seed).build());
        Kit.sound(level, start, ModSounds.ENERGY_FIRE.get(), 0.8f, 1.7f);
    }

    // ---- Monolith ----

    static void shockwave(ServerPlayer p, ItemStack stack, LivingEntity target, float damage) {
        ServerLevel level = p.serverLevel();
        Vec3 c = Kit.ground(level, target.position(), 6);
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(level, p, c, SHOCK_RADIUS, FWDamage.Kind.ABILITY)) {
            if (e == target) continue;
            FWDamage.deal(p, stack, e, damage * SHOCK_DAMAGE, FWDamage.Kind.ABILITY, Element.EARTH, 0);
            Kit.knock(e, c, 0.7, 0.35);
            if (e.isAlive()) StatusService.apply(e, StatusType.STAGGERED, 20, 1, 0f, p.getUUID());
            ids.add(e.getId());
        }
        Kit.fx(level, FxPayload.of(FxIds.SET_SHOCKWAVE).caster(p.getId()).pos(c).scale((float) SHOCK_RADIUS).entities(ids).seed(seed(p)).build());
        Kit.sound(level, c, ModSounds.EARTH_IMPACT.get(), 1.2f, 0.9f);
    }

    // ---- Infernochain ----

    static boolean chainForm(WeaponDefinition def, ItemStack stack) {
        if (!def.id().equals("infernochain")) return false;
        WeaponForm form = def.form(FantasyWeaponItem.data(stack));
        return form != null && form.id().equals(Infernochain.CHAINBLADE);
    }

    static void hellfireTrail(ServerPlayer p, LivingEntity target) {
        SetState s = state(p);
        long now = now(p);
        if (now < s.trailReadyAt) return;
        s.trailReadyAt = now + TRAIL_COOLDOWN;
        ServerLevel level = p.serverLevel();
        Vec3 a = Kit.feet(p);
        Vec3 b = Kit.ground(level, target.position(), 6);
        if (a.distanceToSqr(b) < 1) b = a.add(Targeting.flatLook(p).scale(2));
        AreaEffectManager.add(new HellfireTrail(level, p, weaponId(p), a, b, TRAIL_TICKS, power(p) * 0.04f));
        Kit.fx(level, FxPayload.of(FxIds.SET_HELLFIRE_TRAIL).caster(p.getId()).pos(a).point(a).point(b).power(TRAIL_TICKS).seed(seed(p)).build());
        Kit.sound(level, b, ModSounds.FIRE_WHOOSH.get(), 0.9f, 0.9f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // abilities and modes
    // ------------------------------------------------------------------------------------------------------------

    /** Before an ability runs: stored souls are spent on it. */
    public static void beforeCast(ServerPlayer p) {
        SetState s = state(p);
        s.empowered = 0;
        if (s.souls > 0 && has(p, ArmorSet.SOULREAPER)) {
            s.empowered = s.souls;
            s.empoweredUntil = now(p) + SOUL_EMPOWER_TICKS;
            s.souls = 0;
        }
    }

    /** After an ability ran ({@code ok}: it was cast; {@code fullCharge}: released fully charged). */
    public static void afterCast(ServerPlayer p, boolean ok, boolean fullCharge) {
        SetState s = state(p);
        if (s.empowered > 0) {
            if (ok) {
                Fx.tracking(p, FxPayload.of(FxIds.SET_SOUL_RELEASE).caster(p.getId()).pos(p.position()).level(s.empowered).seed(seed(p)).build());
                Kit.sound(p.serverLevel(), p.position(), ModSounds.SOUL_PROJECTILE.get(), 1.2f, 0.7f);
            } else {
                s.souls = s.empowered; // a failed cast keeps its souls
                s.empowered = 0;
            }
            sync(p);
        }
        if (ok && fullCharge && has(p, ArmorSet.SOLARIS)) {
            dawnBurst(p, weapon(p), Kit.feet(p).add(0, 1, 0), power(p) * 0.5f, DAWN_RADIUS + 0.5, null);
        }
    }

    /** Multiplier on ability damage: the souls being spent on the current cast. */
    public static float abilityDamage(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer p)) return 1f;
        SetState s = p.getExistingDataOrNull(ModAttachments.SET_STATE);
        if (s == null || s.empowered <= 0 || now(p) > s.empoweredUntil) return 1f;
        return 1f + SOUL_BONUS * s.empowered;
    }

    /** After a weapon's mode switched: the eclipse wave and totality. */
    public static void onFormSwitch(ServerPlayer p) {
        if (!has(p, ArmorSet.ECLIPSE_REAPER)) return;
        SetState s = state(p);
        long now = now(p);
        if (now < s.totalityReadyAt) return;
        s.totalityUntil = now + TOTALITY_TICKS;
        s.totalityReadyAt = now + TOTALITY_COOLDOWN;
        sync(p);
        ServerLevel level = p.serverLevel();
        Vec3 c = Kit.feet(p);
        float damage = power(p) * TOTALITY_DAMAGE;
        List<Integer> ids = Kit.burst(p, weapon(p), c.add(0, 1, 0), TOTALITY_RADIUS, damage, Element.CELESTIAL, 0, 0.7, 0.25, e -> {
            if (!e.isAlive()) return;
            StatusService.apply(e, StatusType.ECLIPSE_LIGHT, 100, 1, 0f, p.getUUID());
            StatusService.apply(e, StatusType.ECLIPSE_DARKNESS, 100, 1, damage * 0.03f, p.getUUID());
        });
        Kit.fx(level, FxPayload.of(FxIds.SET_ECLIPSE_WAVE).caster(p.getId()).pos(c).scale((float) TOTALITY_RADIUS).power(TOTALITY_TICKS).entities(ids)
                .seed(seed(p)).build());
        Kit.sound(level, c, ModSounds.SOLAR_BURST.get(), 1.2f, 0.7f);
        Kit.sound(level, c, ModSounds.RIFT_OPEN.get(), 1f, 0.8f);
    }

    /** Whether {@code e} is in totality: both modes' bonuses apply. */
    public static boolean totality(LivingEntity e) {
        if (!(e instanceof Player p)) return false;
        SetState s = p.getExistingDataOrNull(ModAttachments.SET_STATE);
        return s != null && s.totalityUntil > p.level().getGameTime() && has(p, ArmorSet.ECLIPSE_REAPER);
    }

    /** A form's damage factor; in totality the best of all the weapon's forms. */
    public static float formDamageFactor(LivingEntity e, WeaponDefinition def, @Nullable WeaponForm form) {
        float f = form != null ? form.damageFactor() : 1f;
        if (form != null && totality(e)) for (WeaponForm other : def.forms()) f = Math.max(f, other.damageFactor());
        return f;
    }

    /** A form's extra reach; in totality the best of all the weapon's forms. */
    public static float formReach(LivingEntity e, WeaponDefinition def, @Nullable WeaponForm form) {
        float r = form != null ? form.reachBonus() : 0f;
        if (form != null && totality(e)) for (WeaponForm other : def.forms()) r = Math.max(r, other.reachBonus());
        return r;
    }

    // ------------------------------------------------------------------------------------------------------------
    // damage taken, kills
    // ------------------------------------------------------------------------------------------------------------

    /** After {@code victim} took damage. */
    public static void onDamaged(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayer p) || amount <= 0 || !has(p, ArmorSet.FROSTREND)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p || !attacker.isAlive()) return;
        if (attacker instanceof Player other && !p.canHarmPlayer(other)) return;
        StatusService.apply(attacker, StatusType.FROSTBITE, CHILL_TICKS, 1, CHILL_SLOW, p.getUUID());
        ServerLevel level = p.serverLevel();
        Kit.fx(level, FxPayload.of(FxIds.SET_WINTER_CHILL).caster(p.getId()).pos(attacker.getBoundingBox().getCenter()).point(p.getBoundingBox().getCenter())
                .scale(attacker.getBbHeight()).entities(attacker.getId()).seed(seed(p)).build());
        Kit.sound(level, attacker.position(), ModSounds.ICE_BURST.get(), 0.7f, 1.6f);
    }

    /** A kill by a set wearer: the slain may rise (Gravebite), its soul is stored (Soulreaper). */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player) return;
        ServerPlayer killer = killer(level, victim, event.getSource());
        if (killer == null || killer == victim) return;
        ArmorSet set = FantasyArmorItem.fullSet(killer);
        if (set == ArmorSet.GRAVEBITE && victim instanceof Mob) rise(killer, victim);
        else if (set == ArmorSet.SOULREAPER) harvest(killer, victim);
    }

    @Nullable
    static ServerPlayer killer(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        HitTracker t = victim.getExistingDataOrNull(ModAttachments.HIT_TRACKER);
        long now = level.getServer().overworld().getGameTime();
        if (t != null && t.player != null && now - t.gameTime <= ServerConfig.KILL_CREDIT_WINDOW_TICKS.getOrDefault()) {
            return level.getServer().getPlayerList().getPlayer(t.player);
        }
        return null;
    }

    static void rise(ServerPlayer p, LivingEntity victim) {
        if (state(p).allies >= MAX_ALLIES || p.getRandom().nextFloat() >= RISE_CHANCE) return;
        raise(p, victim);
    }

    static void raise(ServerPlayer p, LivingEntity victim) {
        SetState s = state(p);
        s.allies++;
        sync(p);
        float size = Math.max(0.6f, Math.min(1.4f, victim.getBbHeight() * 0.55f));
        AreaEffectManager.add(new SpectralAlly(p.serverLevel(), p, weaponId(p), victim.getBoundingBox().getCenter(), ALLY_LIFE,
                power(p) * ALLY_DAMAGE, size, seed(p)));
        Kit.sound(p.serverLevel(), victim.position(), ModSounds.SOUL_PROJECTILE.get(), 1.1f, 0.55f);
    }

    /** A spectral ally ended. */
    static void allyGone(@Nullable ServerPlayer owner) {
        if (owner == null) return;
        SetState s = state(owner);
        s.allies = Math.max(0, s.allies - 1);
        sync(owner);
    }

    static void harvest(ServerPlayer p, LivingEntity victim) {
        SetState s = state(p);
        if (s.souls >= MAX_SOULS) return;
        s.souls++;
        sync(p);
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.SET_SOUL_HARVEST).caster(p.getId()).pos(victim.getBoundingBox().getCenter()).level(s.souls)
                .seed(seed(p)).build());
        Kit.sound(p.serverLevel(), victim.position(), ModSounds.SOUL_PROJECTILE.get(), 0.7f, 1.6f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // every tick
    // ------------------------------------------------------------------------------------------------------------

    public static void tick(ServerPlayer p) {
        ArmorSet set = FantasyArmorItem.fullSet(p);
        SetState s = state(p);

        // Monolith: no knockback at all
        setModifier(p, Attributes.KNOCKBACK_RESISTANCE, IMMOVABLE_ID, set == ArmorSet.MONOLITH ? 1.0 : 0, AttributeModifier.Operation.ADD_VALUE);

        // Doomcleaver: below half health, a blood frenzy
        boolean frenzy = set == ArmorSet.DOOMCLEAVER && p.isAlive() && p.getHealth() <= p.getMaxHealth() * 0.5f;
        setModifier(p, Attributes.ATTACK_SPEED, FRENZY_SPEED_ID, frenzy ? FRENZY_ATTACK_SPEED : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (frenzy != s.frenzy) {
            s.frenzy = frenzy;
            sync(p);
            if (frenzy) {
                Fx.tracking(p, FxPayload.of(FxIds.SET_FRENZY).caster(p.getId()).pos(p.position()).seed(seed(p)).build());
                Kit.sound(p.serverLevel(), p.position(), ModSounds.BLOOD_RAGE.get(), 1f, 1.2f);
            }
        }
        if (frenzy && p.tickCount % 20 == 0) Fx.tracking(p, FxPayload.of(FxIds.SET_FRENZY_PULSE).caster(p.getId()).pos(p.position()).seed(seed(p)).build());

        // Bloomfall: standing still grows the grove
        grove(p, s, set == ArmorSet.BLOOMFALL);

        // a set taken off loses what it was holding
        boolean changed = false;
        if (set != ArmorSet.SOULREAPER && s.souls > 0) {
            s.souls = 0;
            changed = true;
        }
        if (set != ArmorSet.STORMBREAKER && s.stormHits > 0) {
            s.stormHits = 0;
            changed = true;
        }
        if (set != ArmorSet.VOIDFANG && s.riftUntil > 0) {
            s.riftUntil = 0;
            changed = true;
        }
        if (changed) sync(p);
    }

    static void grove(ServerPlayer p, SetState s, boolean wearing) {
        double dx = p.getX() - s.lastX, dz = p.getZ() - s.lastZ;
        s.lastX = p.getX();
        s.lastZ = p.getZ();
        boolean still = wearing && p.isAlive() && p.onGround() && dx * dx + dz * dz < 1e-4;
        s.stillTicks = still ? s.stillTicks + 1 : 0;
        boolean active = s.stillTicks >= GROVE_STILL;
        ServerLevel level = p.serverLevel();
        if (active != s.grove) {
            s.grove = active;
            sync(p);
            if (active) {
                Fx.tracking(p, FxPayload.of(FxIds.SET_GROVE).caster(p.getId()).pos(Kit.feet(p)).scale((float) GROVE_RADIUS).seed(seed(p)).build());
                Kit.sound(level, p.position(), ModSounds.NATURE_GROWTH.get(), 1f, 1.1f);
            }
        }
        if (!active) return;
        int t = s.stillTicks - GROVE_STILL;
        if (t % 20 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(GROVE_HEAL);
        if (t % 40 == 0) {
            List<Integer> ids = new ArrayList<>();
            for (LivingEntity e : Targeting.inRadius(level, p, p.position(), GROVE_RADIUS, FWDamage.Kind.ABILITY)) {
                StatusService.apply(e, StatusType.ROOTED, 30, 1, 0f, p.getUUID());
                ids.add(e.getId());
            }
            Fx.tracking(p, FxPayload.of(FxIds.SET_GROVE_PULSE).caster(p.getId()).pos(Kit.feet(p)).scale((float) GROVE_RADIUS).entities(ids)
                    .seed(seed(p)).build());
            if (!ids.isEmpty()) Kit.sound(level, p.position(), ModSounds.NATURE_GROWTH.get(), 0.8f, 1.4f);
        }
    }

    /**
     * Operator test tool ({@code /fw setfx}): sets off {@code set}'s ability at once against the enemy nearest the
     * player, as if its condition had just been met.
     *
     * @return false if there is no enemy within 10 blocks to set it off against
     */
    public static boolean demo(ServerPlayer p, ArmorSet set) {
        SetState s = state(p);
        if (set == ArmorSet.DOOMCLEAVER) {
            p.setHealth(p.getMaxHealth() * 0.4f);
            return true;
        }
        if (set == ArmorSet.BLOOMFALL) {
            s.stillTicks = GROVE_STILL - 1;
            return true;
        }
        if (set == ArmorSet.ECLIPSE_REAPER) {
            s.totalityReadyAt = 0;
            onFormSwitch(p);
            return true;
        }
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : Targeting.inRadius(p.serverLevel(), p, p.position(), 10, FWDamage.Kind.ABILITY)) {
            if (e.distanceToSqr(p) < best) {
                best = e.distanceToSqr(p);
                target = e;
            }
        }
        if (target == null) return false;
        ItemStack w = weapon(p);
        float damage = power(p);
        switch (set) {
            case VOIDFANG -> {
                s.riftUntil = now(p) + RIFT_WINDOW;
                riftEcho(p, w, target, damage);
            }
            case SOLARIS -> dawnBurst(p, w, target.getBoundingBox().getCenter(), damage * DAWN_DAMAGE, DAWN_RADIUS, target);
            case FROSTREND -> onDamaged(p, p.damageSources().mobAttack(target), 1);
            case STORMBREAKER -> {
                s.stormHits = STORM_HITS - 1;
                storm(p, w, target, damage);
            }
            case GRAVEBITE -> raise(p, target);
            case SOULREAPER -> {
                harvest(p, target);
                beforeCast(p);
                afterCast(p, true, false);
            }
            case STARFORGE -> gravityWell(p, target);
            case AETHERLANCE -> precisionBolt(p, w, target, damage);
            case MONOLITH -> shockwave(p, w, target, damage);
            case INFERNOCHAIN -> {
                s.trailReadyAt = 0;
                hellfireTrail(p, target);
            }
            default -> {
            }
        }
        return true;
    }

    /** Doomcleaver: extra melee life steal in a blood frenzy. */
    public static float lifeSteal(LivingEntity attacker) {
        if (!(attacker instanceof Player p)) return 0f;
        SetState s = p.getExistingDataOrNull(ModAttachments.SET_STATE);
        return s != null && s.frenzy ? FRENZY_LIFE_STEAL : 0f;
    }
}
