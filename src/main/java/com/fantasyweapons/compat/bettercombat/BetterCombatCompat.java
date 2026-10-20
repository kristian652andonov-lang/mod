package com.fantasyweapons.compat.bettercombat;

import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Optional Better Combat support. With Better Combat installed the blades swing in its combos (their attacks are the
 * {@code weapon_attributes} data, see {@code FWBetterCombat}): it picks the targets in each swing's hitbox and strikes
 * them one after another through {@code Player.attack}, which still lands in {@code MeleeHandler}.
 * <p>
 * Nothing here touches Better Combat's classes unless it is loaded; those calls live in {@link BetterCombatHooks}.
 */
public final class BetterCombatCompat {
    public static final String MOD_ID = "bettercombat";
    /** Better Combat's transient attack damage modifier while it deals a swing: the combo, dual-wield and sweep multipliers. */
    private static final ResourceLocation SWING_DAMAGE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "temp_attack");
    /** Players whose Better Combat swings are switched off by us (their weapon is in flight). */
    private static final Set<ServerPlayer> GROUNDED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Boolean loaded;

    private BetterCombatCompat() {
    }

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded(MOD_ID);
        return loaded;
    }

    /** Whether Better Combat swings this weapon for this player (and so picks the targets of each swing itself). */
    public static boolean drivesSwing(Player player, ItemStack stack) {
        return loaded() && BetterCombatHooks.drivesSwing(player, stack);
    }

    /** The damage factor of the Better Combat swing being dealt (1 outside one). */
    public static float swingDamageFactor(Player player) {
        if (!loaded()) return 1f;
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeModifier swing = damage == null ? null : damage.getModifier(SWING_DAMAGE);
        return swing == null ? 1f : (float) Math.max(0, 1 + swing.amount());
    }

    /** Server tick: while the held weapon is thrown, Better Combat's swings are off, so the empty hand does not swing combos. */
    public static void tick(ServerPlayer player) {
        if (!loaded()) return;
        ItemStack held = player.getMainHandItem();
        boolean thrown = held.getItem() instanceof FantasyWeaponItem
                && player.getData(ModAttachments.ABILITY_RUNTIME).isThrown(FantasyWeaponItem.data(held).idOrNil());
        if (thrown == GROUNDED.contains(player)) return;
        if (thrown) GROUNDED.add(player);
        else GROUNDED.remove(player);
        BetterCombatHooks.setAttacksDisabled(player, thrown);
    }
}
