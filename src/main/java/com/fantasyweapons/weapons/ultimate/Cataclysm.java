package com.fantasyweapons.weapons.ultimate;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

/** What the two ultimate weapons' abilities share: annihilating damage, far-reaching effects and where a cast lands. */
public final class Cataclysm {
    /** How far away players still see (and hear) a cataclysm. */
    public static final double SEEN = 720;

    private Cataclysm() {
    }

    /** Sends an effect to every player in the level within {@link #SEEN} of it, and always to the caster. */
    public static void fx(ServerLevel level, ServerPlayer caster, FxPayload payload) {
        double r2 = SEEN * SEEN;
        for (ServerPlayer p : level.players()) {
            if (p == caster || p.position().distanceToSqr(payload.pos()) <= r2) Fx.only(p, payload);
        }
    }

    /**
     * Kills: deals the ability's damage, or the target's whole health several times over if that is more. Bosses lose a
     * third of their health per blow instead. Players are still spared when PvP is off.
     */
    public static float annihilate(ServerPlayer owner, ItemStack weapon, LivingEntity target, float damage, Element element) {
        float whole = target.getMaxHealth() + target.getAbsorptionAmount();
        boolean boss = target.getType().is(Tags.EntityTypes.BOSSES);
        float amount = Math.max(damage, boss ? whole * 0.34f : whole * 4f);
        target.invulnerableTime = 0;
        return FWDamage.deal(owner, weapon, target, amount, FWDamage.Kind.ABILITY, element, FWDamage.FLAG_EXECUTE);
    }

    public static float annihilate(AbilityContext ctx, LivingEntity target, Element element) {
        return annihilate(ctx.player(), ctx.stack(), target, ctx.damage(), element);
    }

    /**
     * Erases everything inside a vertical column from the bottom of the world to the top: creatures (the caster may
     * harm) are annihilated, everything else that is not a player - items, orbs, falling blocks, carts, arrows - is
     * deleted outright.
     *
     * @return the creatures struck
     */
    public static List<LivingEntity> eraseColumn(ServerPlayer owner, ItemStack weapon, ServerLevel level, Vec3 center, double radius, float damage, Element element) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center.x - radius, level.getMinBuildHeight() - 16, center.z - radius,
                center.x + radius, level.getMaxBuildHeight() + 64, center.z + radius);
        List<LivingEntity> hit = new java.util.ArrayList<>();
        double r2 = radius * radius;
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box, x -> true)) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            if (dx * dx + dz * dz > r2) continue;
            if (e instanceof LivingEntity le) {
                if (FWDamage.canHarm(owner, le, FWDamage.Kind.ABILITY)) {
                    annihilate(owner, weapon, le, damage, element);
                    hit.add(le);
                }
            } else if (!(e instanceof net.minecraft.world.entity.player.Player) && !(e instanceof net.neoforged.neoforge.entity.PartEntity<?>)
                    && !e.isRemoved()) {
                e.discard();
            }
        }
        return hit;
    }

    /**
     * Impact frames for the blow landing at {@code at}, for the caster and everyone within {@code seen} blocks of it.
     *
     * @param cataclysm the long, hard sequence, or the ordinary ultimate one
     */
    public static void frames(ServerLevel level, ServerPlayer caster, Vec3 at, double seen, boolean cataclysm) {
        FxPayload p = FxPayload.of(com.fantasyweapons.network.FxIds.CATACLYSM_FRAME).caster(caster.getId()).pos(at).scale((float) seen)
                .level(cataclysm ? 1 : 0).build();
        double r2 = seen * seen;
        for (ServerPlayer pl : level.players()) if (pl == caster || pl.position().distanceToSqr(at) <= r2) Fx.only(pl, p);
    }

    /** Every creature the caster may harm within {@code radius} of {@code center}. */
    public static List<LivingEntity> victims(ServerLevel level, ServerPlayer caster, Vec3 center, double radius) {
        return Targeting.inRadius(level, caster, center, radius, FWDamage.Kind.ABILITY);
    }

    /**
     * Where a cast lands: the point picked on the targeting map, else where the player looks (up to {@code range}),
     * always set on the ground (the highest solid block of the column when its chunk is loaded).
     */
    public static Vec3 landing(AbilityContext ctx, double range) {
        Vec3 t = ctx.target();
        if (t == null) return Kit.aimGround(ctx.player(), range);
        return surface(ctx.level(), t);
    }

    /** The top of the ground at a point's column (unchanged if its chunk is not loaded). */
    public static Vec3 surface(ServerLevel level, Vec3 at) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return at;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(at.x, y, at.z);
    }
}
