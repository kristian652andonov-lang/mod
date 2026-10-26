package com.fantasyweapons.compat.bettercombat;

import com.fantasyweapons.client.fx.GenericFx;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.bettercombat.client.compat.FirstPersonAnimationCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client side of the Better Combat support: its attack animations and swing trails (in the weapon's colours) play in
 * place of the weapons' own swings, and the weapons add only what those cannot show. Only loaded once Better Combat is
 * known to be installed.
 * <p>
 * In first person a weapon's attack is not drawn on the player model, as Better Combat does with other weapons (see
 * FirstPersonModeMixin): the model's arm ended each swing far from where the first-person hand holds the weapon, so the
 * weapon vanished and then jumped back to rest, shaking as it rose. It stays in the first-person hand and plays the same
 * attack there, the model's animation laid on the hand, from rest back to rest (see BetterCombatFirstPerson); Better
 * Combat still runs the combo, picks the targets and lands the blows, and plays its animations on the model everyone
 * else sees.
 */
public final class BetterCombatClient {
    private BetterCombatClient() {
    }

    /** Whether the local player's attack is drawn in the first-person hand, not on the model: one of the weapons held. */
    public static boolean swingsInHand() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().getItem() instanceof FantasyWeaponItem weapon && !weapon.definition().weaponClass().magic();
    }

    /**
     * Lays the weapon's Better Combat attack on the first-person hand ({@code pose}, at {@code hand} in the view), as the
     * player model plays it (see BetterCombatFirstPerson); returns whether an attack is playing.
     */
    public static boolean firstPersonAttack(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Vector3f hand, net.minecraft.world.entity.LivingEntity entity,
                                            float partialTick) {
        return entity instanceof net.minecraft.client.player.AbstractClientPlayer player && BetterCombatFirstPerson.apply(pose, hand, player, partialTick);
    }

    public static void init() {
        BetterCombatClientEvents.ATTACK_START.register((player, hand) -> {
            if (!(hand.itemStack().getItem() instanceof FantasyWeaponItem)) return;
            // Better Combat leaves the first-person view still (always for the weapons, see swingsInHand; also its
            // setting, or a camera mod): the weapon then plays its own first-person attack, so the swing is still seen
            boolean stillFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson()
                    && FirstPersonAnimationCompatibility.firstPersonMode() == FirstPersonMode.NONE;
            GenericFx.onBetterCombatSwing(player, hand.itemStack(), stillFirstPerson);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BetterCombatClient::onTooltip);
    }

    /**
     * Better Combat adds an attack range line under the name. For these weapons it counts only the player's base reach
     * (theirs comes with the weapon) and breaks into the illuminated page, so it is taken out again.
     */
    private static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof FantasyWeaponItem)) return;
        event.getToolTip().removeIf(BetterCombatClient::isAttackRangeLine);
    }

    private static boolean isAttackRangeLine(Component line) {
        for (Component part : line.getSiblings()) {
            if (!(part.getContents() instanceof TranslatableContents modifier) || !modifier.getKey().startsWith("attribute.modifier.equals.")) continue;
            for (Object arg : modifier.getArgs()) {
                if (arg instanceof Component name && name.getContents() instanceof TranslatableContents key
                        && "attribute.name.generic.attack_range".equals(key.getKey())) return true;
            }
        }
        return false;
    }
}
