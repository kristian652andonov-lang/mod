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

    /** A custom effect arrived from the server. */
    default void handleFx(FxPayload payload) {
    }

    /** A thrown weapon entity appeared on the client (spawns its trail visuals). */
    default void onThrownWeapon(com.fantasyweapons.entity.ThrownWeaponEntity entity) {
    }

    /** A progression notification arrived from the server. */
    default void handleProgression(ProgressionEventPayload payload) {
    }
}
