package com.fantasyweapons.magic;

import com.fantasyweapons.playerclass.ClassGear;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/** Hooks the mage's magic into the game: healing rules, Spring Eternal's reprieve, kill passives and held auras. */
public final class MagicEvents {
    private MagicEvents() {
    }

    public static void register(IEventBus bus) {
        // Spring Eternal hears of a death before anything else does, so a reprieve undoes it cleanly
        bus.addListener(EventPriority.HIGHEST, MagicStatus::saveFromDeath);
        bus.addListener(MagicStatus::onHeal);
    }

    /** After a death has stood: Wildfire and Contagion. */
    public static void onDeath(LivingDeathEvent event) {
        com.fantasyweapons.weapons.pyrecodex.PyreCodexAbilities.onDeath(event);
        com.fantasyweapons.weapons.umbralcodex.UmbralCodexAbilities.onDeath(event);
    }

    /** Every second: the auras of a held Rimeheart and Sunwarden, the Almanac's sunlight. */
    public static void tick(ServerPlayer p) {
        if (p.tickCount % 20 != 0) return;
        aura(p, p.getMainHandItem());
        if (p.getOffhandItem() != p.getMainHandItem()) aura(p, p.getOffhandItem());
    }

    private static void aura(ServerPlayer p, ItemStack stack) {
        if (!(stack.getItem() instanceof FantasyWeaponItem item) || !ClassGear.canUse(p, stack)) return;
        switch (item.definition().id()) {
            case "rimeheart" -> com.fantasyweapons.weapons.rimeheart.RimeheartAbilities.aura(p, stack);
            case "sunwarden" -> com.fantasyweapons.weapons.sunwarden.SunwardenAbilities.aura(p, stack);
            case "verdant_almanac" -> com.fantasyweapons.weapons.verdantalmanac.VerdantAlmanacAbilities.sunlight(p, stack);
            default -> {
            }
        }
    }
}
