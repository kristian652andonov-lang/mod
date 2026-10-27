package com.fantasyweapons.playerclass;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The classes at work: a class's attribute changes laid on the player (and taken off when it changes), its perks in combat
 * (the assassin's backstab, wounded-strike and vanish, the berserker's rage and bloodthirst, the paladin's smite, aegis
 * and blessing), and the choosing itself, with a wait between changes.
 */
public final class ClassService {
    /** How long a player waits before changing class again (ticks): twenty minutes. The first choice is free. */
    public static final long CHANGE_WAIT = 20 * 60 * 20;
    /** When each paladin was last hurt (game time), for their blessing out of combat. */
    private static final Map<UUID, Long> lastHurt = new HashMap<>();

    private ClassService() {
    }

    public static PlayerClass of(Player player) {
        return player.getData(ModAttachments.PLAYER_CLASS).playerClass();
    }

    /** Ticks left before the player may change class again (0 when they may). */
    public static long waitLeft(Player player) {
        ClassData d = player.getData(ModAttachments.PLAYER_CLASS);
        if (d.playerClass() == PlayerClass.NONE || d.chosenAt() < 0) return 0;
        return Math.max(0, d.chosenAt() + CHANGE_WAIT - player.level().getGameTime());
    }

    /** The player asks for a class from the class menu. */
    public static void choose(ServerPlayer player, PlayerClass chosen) {
        if (!chosen.available() || chosen == PlayerClass.NONE || chosen == of(player)) return;
        long wait = waitLeft(player);
        if (wait > 0) {
            player.displayClientMessage(Tr.c("You can change your class again in %s minutes.", String.valueOf(wait / 20 / 60 + 1)).withColor(0xFF8A6A), true);
            return;
        }
        player.setData(ModAttachments.PLAYER_CLASS, new ClassData(chosen, player.level().getGameTime()));
        apply(player);
        player.syncData(ModAttachments.PLAYER_CLASS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.3f);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
        player.displayClientMessage(Tr.c(chosen == PlayerClass.ASSASSIN ? "You are now an %s." : "You are now a %s.",
                Tr.c(chosen.englishName()).withColor(chosen.color())).withColor(0xCFC3A6), false);
    }

    /**
     * Operator tool: sets the player's class (any, even one still being made) with no ceremony; {@code wait} starts the
     * wait before the next change, otherwise the player may change at once.
     */
    public static void set(ServerPlayer player, PlayerClass c, boolean wait) {
        player.setData(ModAttachments.PLAYER_CLASS, new ClassData(c, wait ? player.level().getGameTime() : -1));
        apply(player);
        player.syncData(ModAttachments.PLAYER_CLASS);
    }

    private static ResourceLocation modifierId(PlayerClass.Mod mod) {
        return FantasyWeapons.id("class/" + mod.attribute().unwrapKey().map(k -> k.location().getPath()).orElse("attribute"));
    }

    /** Lays the player's class on them: every class's changes taken off, theirs put on (kept with the player). */
    public static void apply(ServerPlayer player) {
        PlayerClass current = of(player);
        for (PlayerClass c : PlayerClass.values()) {
            for (PlayerClass.Mod mod : c.modifiers()) {
                AttributeInstance a = player.getAttribute(mod.attribute());
                if (a != null) a.removeModifier(modifierId(mod));
            }
        }
        for (PlayerClass.Mod mod : current.modifiers()) {
            AttributeInstance a = player.getAttribute(mod.attribute());
            if (a != null) a.addPermanentModifier(new AttributeModifier(modifierId(mod), mod.amount(), mod.operation()));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        // armor of another class comes off
        ClassGear.stripArmor(player);
    }

    /** Lays the class on again, and the first time a player joins the world, puts the class menu before them. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        apply(player);
        ClassData d = player.getData(ModAttachments.PLAYER_CLASS);
        if (!d.offered()) {
            player.setData(ModAttachments.PLAYER_CLASS, d.withOffered());
            player.syncData(ModAttachments.PLAYER_CLASS);
            if (d.playerClass() == PlayerClass.NONE) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.fantasyweapons.network.QuestPayloads.OpenClasses());
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }

    /** The perks that change damage: backstab, wounded-strike, rage, smite (dealt) and aegis (taken). */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof ServerPlayer hurt) {
            lastHurt.put(hurt.getUUID(), hurt.level().getGameTime());
            if (of(hurt) == PlayerClass.PALADIN && event.getSource().getEntity() instanceof LivingEntity attacker && attacker.isInvertedHealAndHarm()) {
                event.setAmount(event.getAmount() * 0.75f);
            }
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player == target) return;
        float amount = event.getAmount();
        switch (of(player)) {
            case ASSASSIN -> {
                // from behind: the target faces away from the assassin
                Vec3 facing = target.getViewVector(1).multiply(1, 0, 1).normalize();
                Vec3 toAttacker = player.position().subtract(target.position()).multiply(1, 0, 1).normalize();
                if (facing.dot(toAttacker) < -0.3) amount *= 1.6f;
                if (target.getHealth() < target.getMaxHealth() / 2) amount *= 1.25f;
            }
            case BERSERKER -> {
                float missing = 1 - player.getHealth() / player.getMaxHealth();
                amount *= 1 + 0.5f * Math.min(1, missing / 0.75f);
            }
            case PALADIN -> {
                if (target.isInvertedHealAndHarm()) amount *= 1.5f;
            }
            case MAGE -> {
                // spells, and the bolts of a staff or spellbook
                if (event.getSource().is(com.fantasyweapons.combat.FWDamage.ABILITY) || com.fantasyweapons.magic.MageBolts.isFocus(player.getMainHandItem())) {
                    amount *= 1.15f;
                }
            }
            default -> {
            }
        }
        event.setAmount(amount);
    }

    /** Kills: the assassin vanishes, the berserker drinks. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player) return;
        switch (of(player)) {
            case ASSASSIN -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false, true));
                player.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
            }
            case BERSERKER -> {
                player.heal(2);
                player.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.4, 0.3, 0.1);
            }
            default -> {
            }
        }
    }

    /** Every second: the paladin's blessing heals them once they have been out of combat a while. */
    public static void tick(ServerPlayer player) {
        // armor put on some other way (or kept from creative mode) comes off if the class cannot wear it
        if (player.tickCount % 20 == 0) ClassGear.stripArmor(player);
        if (player.tickCount % 60 != 0) return;
        long since = player.level().getGameTime() - lastHurt.getOrDefault(player.getUUID(), 0L);
        // the mage's Arcane Ward: a shield of two hearts gathers once out of combat a while
        if (of(player) == PlayerClass.MAGE && since > 160 && player.getAbsorptionAmount() < 4) {
            com.fantasyweapons.magic.Heals.barrierFlat(player, player, 4, 20 * 30);
        }
        if (of(player) != PlayerClass.PALADIN) return;
        if (since > 160 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.3, 0.4, 0.3, 0);
        }
    }

    public static void onLogout(ServerPlayer player) {
        lastHurt.remove(player.getUUID());
        ClassGear.forget(player.getUUID());
    }
}
