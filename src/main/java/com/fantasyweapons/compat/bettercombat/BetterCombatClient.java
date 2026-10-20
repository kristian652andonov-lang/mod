package com.fantasyweapons.compat.bettercombat;

import com.fantasyweapons.client.anim.WeaponPoses;
import com.fantasyweapons.client.fx.GenericFx;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.bettercombat.client.compat.FirstPersonAnimationCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the Better Combat support: its attack animations and swing trails (in the weapon's colours) play in
 * place of the weapons' own swings, and the weapons add only what those cannot show. Only loaded once Better Combat is
 * known to be installed.
 */
public final class BetterCombatClient {
    /** Ticks for a swing to take the weapon's grip over, and to hand it back. */
    private static final float EASE_IN = 3f, EASE_OUT = 4f;
    /** Entity id -> {how far the grip is taken over (0..1), when that was last updated (ticks)}. */
    private static final Map<Integer, float[]> TAKEN = new HashMap<>();

    private BetterCombatClient() {
    }

    public static void init() {
        BetterCombatClientEvents.ATTACK_START.register((player, hand) -> {
            if (!(hand.itemStack().getItem() instanceof FantasyWeaponItem)) return;
            // Better Combat may leave the first-person view still (its setting, or a camera mod): the weapon then
            // plays its own first-person attack, so the swing is still seen
            boolean stillFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson()
                    && FirstPersonAnimationCompatibility.firstPersonMode() == FirstPersonMode.NONE;
            GenericFx.onBetterCombatSwing(player, hand.itemStack(), stillFirstPerson);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BetterCombatClient::onTooltip);
        WeaponPoses.gripKeep = BetterCombatClient::gripKeep;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> TAKEN.clear());
    }

    /**
     * The weapons tilt in the hand to suit their own swings; Better Combat's swings hold them as they are. So while one
     * plays (any playerAnimator animation on the arms) the grip eases out, and back in once it has wound down.
     */
    private static double gripKeep(LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) return 1;
        boolean animating;
        try {
            animating = PlayerAnimationAccess.getPlayerAnimLayer(player).isActive();
        } catch (IllegalArgumentException e) {
            return 1;
        }
        float now = entity.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float[] state = TAKEN.get(entity.getId());
        if (state == null) {
            if (!animating) return 1;
            TAKEN.put(entity.getId(), state = new float[]{0, now});
        }
        float dt = Mth.clamp(now - state[1], 0, 5);
        state[1] = now;
        state[0] = animating ? Math.min(1, state[0] + dt / EASE_IN) : Math.max(0, state[0] - dt / EASE_OUT);
        if (state[0] <= 0) TAKEN.remove(entity.getId());
        float t = state[0];
        return 1 - t * t * (3 - 2 * t);
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
