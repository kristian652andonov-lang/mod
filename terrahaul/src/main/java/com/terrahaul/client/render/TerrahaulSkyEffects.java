package com.terrahaul.client.render;

import com.terrahaul.Terrahaul;
import com.terrahaul.config.TerrahaulConfig;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Sky effects of the Terrahaul Overworld ({@code terrahaul:overworld}): the vanilla Overworld sky
 * with the cloud layer raised (client option {@code cloudHeight}), because vanilla clouds at Y 192
 * would cut through Terrahaul's hills and forests.
 */
public final class TerrahaulSkyEffects extends DimensionSpecialEffects {
    public TerrahaulSkyEffects() {
        super(DimensionSpecialEffects.OverworldEffects.CLOUD_LEVEL, true, SkyType.NORMAL, false, false);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Terrahaul.id("overworld"), new TerrahaulSkyEffects());
    }

    @Override
    public float getCloudHeight() {
        return (float) TerrahaulConfig.cloudHeight();
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        // same as vanilla OverworldEffects
        return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }
}
