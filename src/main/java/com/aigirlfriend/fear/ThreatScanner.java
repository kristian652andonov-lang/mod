package com.aigirlfriend.fear;

import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Threat assessment: weighs nearby hostile mobs by type, distance, whether they target her, line of sight,
 * and her own health and courage. Runs every 10 ticks, never every tick.
 */
public final class ThreatScanner {
    private ThreatScanner() {}

    public static float typeWeight(LivingEntity e) {
        if (e instanceof Warden || e instanceof WitherBoss || e instanceof EnderDragon) return 3.0f;
        if (e instanceof Ravager) return 2.2f;
        if (e instanceof Creeper c) return c.isIgnited() ? 2.5f : 1.6f;
        if (e instanceof Vindicator || e instanceof Evoker || e instanceof PiglinBrute) return 1.7f;
        if (e instanceof WitherSkeleton) return 1.5f;
        if (e instanceof Blaze || e instanceof Ghast || e instanceof Hoglin) return 1.3f;
        if (e instanceof Witch) return 1.2f;
        if (e instanceof AbstractSkeleton) return 1.1f;
        if (e instanceof CaveSpider) return 1.1f;
        if (e instanceof Spider) return 1.0f;
        if (e instanceof Zombie) return 0.9f;
        if (e instanceof Phantom) return 0.9f;
        if (e instanceof Slime s) return 0.25f * s.getSize();
        if (e instanceof Silverfish || e instanceof Endermite) return 0.4f;
        return 1.0f;
    }

    public static boolean isThreat(GirlfriendEntity gf, LivingEntity e) {
        if (!e.isAlive() || !(e instanceof Enemy)) return false;
        if (e instanceof EnderMan em) return em.isCreepy() || em.getTarget() != null;
        if (e instanceof NeutralMob nm && e instanceof Mob m) return nm.isAngry() || m.getTarget() != null;
        if (e instanceof Mob m && m.isNoAi()) return false;
        return true;
    }

    public static void scan(GirlfriendEntity gf, FearState state) {
        double radius = GFConfig.FEAR_RADIUS.get();
        AABB box = gf.getBoundingBox().inflate(radius, radius * 0.5, radius);
        List<Mob> mobs = gf.level().getEntitiesOfClass(Mob.class, box, m -> isThreat(gf, m));
        Player owner = gf.getOwner();
        float total = 0f;
        float best = 0f;
        LivingEntity main = null;
        Vec3 center = Vec3.ZERO;
        float weightSum = 0f;
        for (Mob m : mobs) {
            double dist = m.distanceTo(gf);
            if (dist > radius) continue;
            float proximity = (float) Mth.clamp(1.0 - dist / radius, 0.0, 1.0);
            float w = typeWeight(m) * proximity * proximity * 1.25f + typeWeight(m) * 0.08f;
            if (m.getTarget() == gf) w *= 1.8f;
            else if (owner != null && m.getTarget() == owner) w *= 1.25f;
            if (!gf.hasLineOfSight(m)) w *= 0.45f;
            total += w;
            weightSum += w;
            center = center.add(m.position().scale(w));
            if (w > best) {
                best = w;
                main = m;
            }
        }
        float courage = gf.data().personality.bravery();
        float mult = 1.25f - courage * 0.6f;
        if (gf.getHealth() < gf.getMaxHealth() * 0.5f) mult *= 1.35f;
        if (owner != null) {
            double od = owner.distanceTo(gf);
            if (od < 5) mult *= 0.7f;
            else if (od > 16) mult *= 1.2f;
        } else {
            mult *= 1.2f;
        }
        state.target = Mth.clamp(total * mult, 0f, 1f);
        state.mainThreat = main;
        state.threatCount = mobs.size();
        state.threatCenter = weightSum > 0 ? center.scale(1.0 / weightSum) : null;
        float rate = state.target > state.level ? 0.55f : 0.08f;
        state.level += (state.target - state.level) * rate;
        if (state.level < 0.01f) state.level = 0f;
        state.peak = Math.max(state.peak, state.level);
    }
}
