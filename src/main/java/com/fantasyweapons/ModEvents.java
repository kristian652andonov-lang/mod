package com.fantasyweapons;

import com.fantasyweapons.ability.AbilityService;
import com.fantasyweapons.combat.MeleeHandler;
import com.fantasyweapons.command.FWCommand;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.fantasyweapons.world.DeathDissolve;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game-bus event wiring for common (both sides) logic. */
public final class ModEvents {
    private ModEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ModEvents::onAttack);
        bus.addListener(EventPriority.LOWEST, ModEvents::onDeath);
        bus.addListener(ModEvents::onPlayerTick);
        bus.addListener(ModEvents::onEntityTick);
        bus.addListener(ModEvents::onLevelTick);
        bus.addListener(ModEvents::onLogout);
        bus.addListener(ModEvents::onServerStopped);
        bus.addListener(ModEvents::onCommands);
        bus.addListener(ModEvents::onIncomingDamage);
    }

    /**
     * Fantasy weapons never use vanilla's attack (which spawns vanilla crit/sweep/heart particles). The event fires on
     * both sides; it is cancelled on both and the custom pipeline runs on the server only.
     */
    private static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity().getMainHandItem().getItem() instanceof FantasyWeaponItem)) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            MeleeHandler.attack(player, event.getTarget());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        ExpService.onLivingDeath(event);
        com.fantasyweapons.weapons.bloomfall.BloomfallAbilities.onDeath(event);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) AbilityService.tick(player);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide) return;
        if (living.deathTime >= DeathDissolve.REMOVE_AT && !living.isRemoved()) DeathDissolve.tick(living);
        if (living.hasData(ModAttachments.STATUS)) StatusService.tick(living);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) AreaEffectManager.tick(level);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityService.onLogout(player);
            com.fantasyweapons.weapons.stormbreaker.StormbreakerAbilities.forget(player.getUUID());
            com.fantasyweapons.weapons.gravebite.GravebiteAbilities.forget(player.getUUID());
            com.fantasyweapons.weapons.eclipse.EclipseReaperAbilities.forget(player.getUUID());
            com.fantasyweapons.weapons.aetherlance.AetherlanceAbilities.forget(player.getUUID());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        AreaEffectManager.clear();
    }

    /** Frozen creatures cannot deal damage. */
    private static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && !attacker.level().isClientSide
                && com.fantasyweapons.status.StatusService.stacks(attacker, com.fantasyweapons.status.StatusType.FROZEN) > 0) {
            event.setCanceled(true);
        }
    }

    private static void onCommands(RegisterCommandsEvent event) {
        FWCommand.register(event.getDispatcher());
    }
}
