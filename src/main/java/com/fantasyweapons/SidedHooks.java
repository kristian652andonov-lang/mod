package com.fantasyweapons;

import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.network.ProgressionEventPayload;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Bridge from common code to client-only code. The default implementation is a no-op (dedicated server); the client
 * replaces {@link #INSTANCE} during client setup. Keeps client classes from ever being referenced on a server.
 */
public interface SidedHooks {
    SidedHooks NOOP = new SidedHooks() {
    };

    /** Replaced by the client during mod construction. */
    class Holder {
        public static SidedHooks instance = NOOP;
    }

    static SidedHooks get() {
        return Holder.instance;
    }

    /** A living entity holding a fantasy weapon swung its arm (called on the client for every observed swing). */
    default void onWeaponSwing(LivingEntity entity, ItemStack stack, InteractionHand hand) {
    }

    /** Supplies the GeckoLib render provider for a weapon item (physical client only). */
    default void provideRenderer(FantasyWeaponItem item, Consumer<Object> consumer) {
    }

    /** Supplies the GeckoLib render provider for a material, armor or block item (physical client only). */
    default void provideItemRenderer(net.minecraft.world.item.Item item, Consumer<Object> consumer) {
    }

    /** Supplies the GeckoLib render provider (worn model and inventory icon) for an armor piece. */
    default void provideArmorRenderer(com.fantasyweapons.armor.FantasyArmorItem item, Consumer<Object> consumer) {
    }

    /** The local player used a Wayfarer's Codex: it is lifted into view and opened (client only). */
    default void openCodex(InteractionHand hand) {
    }

    /** Where the local player's Codex is in its reading (see {@link com.fantasyweapons.guide.WayfarersCodexItem}'s phases). */
    default int codexPhase() {
        return 0;
    }

    /** The local player on a client (null on a dedicated server). */
    default net.minecraft.world.entity.player.Player clientPlayer() {
        return null;
    }

    /** A custom effect arrived from the server. */
    default void handleFx(FxPayload payload) {
    }

    /** A thrown weapon entity appeared on the client (spawns its trail visuals). */
    default void onThrownWeapon(com.fantasyweapons.entity.ThrownWeaponEntity entity) {
    }

    /** A progression notification arrived from the server. */
    default void handleProgression(ProgressionEventPayload payload) {
    }

    /** The player joined the world for the first time: the class menu, once the world is on screen. */
    default void handleOpenClasses() {
    }

    /** One of the player's quests is done: its toast. */
    default void handleQuestCompleted(String quest) {
    }

    /** The player earned one of the mod's deeds (advancements): its toast and fanfare. */
    default void handleDeed(com.fantasyweapons.network.DeedPayload payload) {
    }
}
