package com.terrahaul.client.render;

import com.terrahaul.config.TerrahaulConfig;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Turns off distance fog when {@code distanceFog} is false in the client config.
 * <p>
 * Only the terrain fog seen from the open air is pushed out of reach; the sky keeps its haze at the
 * horizon, and the fog under water or lava, in powder snow and from blindness or darkness stays,
 * because it is part of play. Sodium takes its fog distances from the same render state, so it
 * follows; a shader pack draws its own fog. The option applies from the next frame.
 */
public final class FogControl {
    /** Far beyond any render distance, so no terrain is ever fogged. */
    private static final float OUT_OF_REACH = 1.0E6F;

    private FogControl() {
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (TerrahaulConfig.distanceFog() || event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        if (event.getCamera().getEntity() instanceof LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        event.setNearPlaneDistance(OUT_OF_REACH);
        event.setFarPlaneDistance(OUT_OF_REACH);
        // the event's distances are only used when it is cancelled
        event.setCanceled(true);
    }
}
