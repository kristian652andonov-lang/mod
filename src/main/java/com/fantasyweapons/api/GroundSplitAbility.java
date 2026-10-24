package com.fantasyweapons.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Objects;

/**
 * Hook for the future <b>ground split</b> system (Monolith). Monolith's abilities call
 * {@link #execute(Context)} with the line of the split; this class deliberately does <b>not</b> modify terrain itself.
 * <p>
 * Integration points (pick either):
 * <ul>
 *   <li>{@link #setHandler(Handler)}: install the implementation that actually splits the ground.</li>
 *   <li>Listen to {@link GroundSplitEvent} on the NeoForge event bus (fired first, cancellable).</li>
 * </ul>
 * The default handler does nothing, so the weapon's combat, damage and visuals work without any terrain changes.
 */
public final class GroundSplitAbility {
    /**
     * @param origin       point on the ground where the split starts (where the sword was driven in)
     * @param direction    horizontal unit direction of the split (for radial splits: one call per spoke)
     * @param length       length of the split in blocks
     * @param width        half-width of the split in blocks
     * @param depth        suggested depth in blocks
     * @param abilityId    which Monolith ability caused it
     * @param abilityLevel level of that ability
     */
    public record Context(ServerPlayer player, ServerLevel level, ItemStack weapon, Vec3 origin, Vec3 direction, double length, double width,
                          double depth, String abilityId, int abilityLevel) {
    }

    @FunctionalInterface
    public interface Handler {
        void execute(Context context);
    }

    /** Posted before the handler runs; cancel to suppress the split (e.g. protected regions). */
    public static final class GroundSplitEvent extends Event implements ICancellableEvent {
        private final Context context;

        public GroundSplitEvent(Context context) {
            this.context = context;
        }

        public Context context() {
            return context;
        }
    }

    /** The default: does nothing (the ground split system is implemented separately). */
    public static final Handler NONE = context -> {
    };

    private static Handler handler = NONE;

    private GroundSplitAbility() {
    }

    public static void setHandler(Handler newHandler) {
        handler = Objects.requireNonNull(newHandler);
    }

    /** Called by Monolith's abilities. Fires {@link GroundSplitEvent}, then the installed handler unless cancelled. */
    public static void execute(Context context) {
        GroundSplitEvent event = NeoForge.EVENT_BUS.post(new GroundSplitEvent(context));
        if (!event.isCanceled()) handler.execute(context);
    }
}
