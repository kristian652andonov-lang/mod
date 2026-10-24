package com.fantasyweapons.weapons.cinderspire;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Cinderspire spell. */
public final class CinderspireAbilities {
    private CinderspireAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Kindling (passive): marks laid by every fire, combusting at five
    // ------------------------------------------------------------------------------------------------------------

    public static void onHit(MeleeContext ctx) {
        kindle(ctx.player(), ctx.stack(), ctx.target(), 1);
    }

    /** Lays {@code marks} Kindle marks on {@code target}; at the count it combusts in a burst of flame. */
    static void kindle(ServerPlayer owner, ItemStack stack, LivingEntity target, int marks) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item) || !target.isAlive()) return;
        AbilityDefinition k = item.definition().ability(Cinderspire.KINDLING);
        int lvl = FantasyWeaponItem.data(stack).abilityLevel(k);
        if (lvl <= 0) return;
        int need = (int) k.param("marks");
        StatusEffects.Instance inst = Spells.status(owner, stack, target, StatusType.KINDLED, 160, marks, 0);
        if (inst.stacks < need) return;
        StatusService.remove(target, StatusType.KINDLED);
        float weaponDamage = ProgressionMath.weaponDamage(item.definition(), FantasyWeaponItem.data(stack));
        float damage = (float) (weaponDamage * Spells.scaled(k, lvl, "combust", "combust_per_level"));
        Vec3 at = target.getBoundingBox().getCenter();
        List<Integer> hit = Kit.burst(owner, stack, at, k.param("radius"), damage, Element.FIRE, FWDamage.FLAG_HEAVY, 0.6, 0.35, null);
        Kit.fx(owner.serverLevel(), FxPayload.of(FxIds.CINDER_COMBUST).caster(owner.getId()).pos(at).scale((float) k.param("radius"))
                .entities(hit).seed(Kit.seed(owner.serverLevel())).build());
        Kit.sound(owner.serverLevel(), at, ModSounds.EXPLOSION.get(), 1.1f, 1.4f);
    }

    private static void sear(AbilityContext ctx, LivingEntity e, double fraction, int marks) {
        Spells.status(ctx, e, StatusType.SEARED, 80, 1, (float) (ctx.weaponDamage() * fraction));
        kindle(ctx.player(), ctx.stack(), e, marks);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Meteor Brand
    // ------------------------------------------------------------------------------------------------------------

    public static boolean meteorBrand(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 at = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int delay = (int) ctx.param("delay");
        float damage = ctx.damage();
        long seed = Kit.seed(ctx.level());
        // the meteor comes in at a slant from behind the caster's shoulder
        Vec3 flat = Targeting.flatLook(p);
        Vec3 sky = at.add(flat.scale(-14)).add(0, 26, 0);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.CINDER_BRAND).caster(p.getId()).pos(at).point(sky).scale((float) radius).power(delay)
                .level(ctx.abilityLevel()).seed(seed).build());
        Kit.sound(ctx.level(), at, ModSounds.FIRE_WHOOSH.get(), 1.2f, 0.6f);
        SpellEffect.later(ctx, at, delay, (o, w, s) -> {
            List<Integer> hit = Kit.falloffBurst(o, w, at.add(0, 0.8, 0), radius, damage, 0.5f, Element.FIRE, FWDamage.FLAG_HEAVY, 1.2, 0.55,
                    e -> sear(ctx, e, ctx.param("sear"), 2));
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.CINDER_METEOR).caster(o.getId()).pos(at).point(sky).scale((float) radius).entities(hit).seed(seed).build());
            Kit.sound(o.serverLevel(), at, ModSounds.METEOR_IMPACT.get(), 2.2f, 0.9f);
            Kit.sound(o.serverLevel(), at, ModSounds.EXPLOSION.get(), 1.8f, 0.8f);
        });
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Flame Geysers
    // ------------------------------------------------------------------------------------------------------------

    public static boolean flameGeysers(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        int count = (int) Math.floor(ctx.scaled("count", "count_per_level"));
        double spacing = ctx.param("spacing"), radius = ctx.param("radius"), lift = ctx.param("lift");
        float damage = ctx.damage();
        // across the line of sight, erupting from the middle outwards
        Vec3 across = Targeting.flatLook(p).cross(new Vec3(0, 1, 0)).normalize();
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int k = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1);
            spots.add(Kit.groundAt(ctx.level(), center.add(across.scale(k * spacing)), center));
        }
        for (int i = 0; i < spots.size(); i++) {
            Vec3 at = spots.get(i);
            SpellEffect.later(ctx, at, 1 + i * 3, (o, w, s) -> {
                List<Integer> hit = new ArrayList<>();
                for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, at.add(0, 1, 0), radius, FWDamage.Kind.ABILITY)) {
                    if (!s.once(e.getId())) continue;
                    ctx.deal(e, damage, 0);
                    e.push(0, lift, 0);
                    e.hurtMarked = true;
                    sear(ctx, e, 0.05, 1);
                    hit.add(e.getId());
                }
                Kit.fx(o.serverLevel(), FxPayload.of(FxIds.CINDER_GEYSER).caster(o.getId()).pos(at).scale((float) radius).entities(hit)
                        .seed(Kit.seed(o.serverLevel())).build());
                Kit.sound(o.serverLevel(), at, ModSounds.FIRE_WHOOSH.get(), 1.3f, 0.7f + o.getRandom().nextFloat() * 0.2f);
            });
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Heart of the Volcano (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean heartOfTheVolcano(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int interval = Math.max(2, (int) ctx.param("interval"));
        int bombs = (int) ctx.param("bombs");
        double bombRadius = ctx.param("bomb_radius");
        float total = ctx.damage();
        float bombDamage = (float) (total * ctx.param("bomb_fraction"));
        float lava = (float) (total * ctx.param("lava_fraction"));
        float eruption = (float) (total * ctx.param("eruption_fraction"));
        ServerLevel level = ctx.level();
        FieldEffect field = new FieldEffect(ctx, center, radius, duration, 10)
                .onPulse((o, weapon, f, inside) -> {
                    if (f.age() < 15) return; // the ground is still splitting
                    for (LivingEntity e : inside) {
                        FWDamage.deal(o, weapon, e, lava, FWDamage.Kind.ABILITY, Element.FIRE, 0);
                        Spells.status(o, weapon, e, StatusType.SEARED, 40, 1, (float) (ctx.weaponDamage() * 0.04));
                    }
                })
                .onTick((o, weapon, f, inside) -> {
                    if (f.age() < 15 || f.age() % interval != 0) return;
                    // magma bombs flung up out of the lake, falling back somewhere across it (on its enemies, mostly)
                    List<Vec3> targets = new ArrayList<>();
                    for (LivingEntity e : inside) if (targets.size() < bombs && o.getRandom().nextFloat() < 0.6f) targets.add(e.position());
                    while (targets.size() < bombs) {
                        double a = o.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(o.getRandom().nextDouble()) * radius;
                        targets.add(Kit.groundAt(o.serverLevel(), center.add(Math.cos(a) * r, 0, Math.sin(a) * r), center));
                    }
                    for (Vec3 t : targets) {
                        Vec3 land = Kit.groundAt(o.serverLevel(), t, center);
                        double a = o.getRandom().nextDouble() * Math.PI * 2;
                        Vec3 from = center.add(Math.cos(a) * radius * 0.25, 0.3, Math.sin(a) * radius * 0.25);
                        int flight = 14 + o.getRandom().nextInt(8);
                        Kit.fx(o.serverLevel(), FxPayload.of(FxIds.CINDER_BOMB).caster(o.getId()).pos(from).point(land).power(flight)
                                .scale((float) bombRadius).seed(o.getRandom().nextLong()).build());
                        AreaEffectManager.add(new SpellEffect(level, o, ctx.data().idOrNil(), land, flight).onEnd((oo, w, s) -> {
                            Kit.burst(oo, w, land.add(0, 0.8, 0), bombRadius, bombDamage, Element.FIRE, 0, 0.5, 0.4, e -> kindle(oo, w, e, 1));
                            Kit.sound(oo.serverLevel(), land, ModSounds.EXPLOSION.get(), 1.0f, 1.2f + oo.getRandom().nextFloat() * 0.3f);
                        }));
                    }
                })
                .onEnd((o, weapon, f) -> {
                    List<Integer> hit = Kit.falloffBurst(o, weapon, center.add(0, 1, 0), radius, eruption, 0.45f, Element.FIRE, FWDamage.FLAG_HEAVY,
                            1.2, 1.4, e -> sear(ctx, e, 0.12, 3));
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.CINDER_ERUPTION).caster(o.getId()).pos(center).scale((float) radius).entities(hit)
                            .seed(o.getRandom().nextLong()).build());
                    Kit.sound(o.serverLevel(), center, ModSounds.EXPLOSION.get(), 3f, 0.5f);
                    Kit.sound(o.serverLevel(), center, ModSounds.METEOR_IMPACT.get(), 3f, 0.6f);
                });
        AreaEffectManager.add(field);
        Kit.active(ctx, duration);
        Kit.fx(level, FxPayload.of(FxIds.CINDER_VOLCANO).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(level)).build());
        Kit.sound(level, center, ModSounds.EARTH_IMPACT.get(), 2.5f, 0.5f);
        return true;
    }
}
