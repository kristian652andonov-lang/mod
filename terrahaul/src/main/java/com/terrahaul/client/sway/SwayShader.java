package com.terrahaul.client.sway;

import com.mojang.blaze3d.shaders.Uniform;
import com.terrahaul.client.wind.WindSystem;
import com.terrahaul.config.TerrahaulConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Feeds the wind to the cutout core shader once per frame, before terrain is drawn.
 * <p>
 * {@code TerrahaulWind} is (direction x, direction z, strength, time in seconds) and
 * {@code TerrahaulCamera} the camera position wrapped to a 1024-block period, which is exactly the
 * period of the shader's wave pattern, so the wrap is seamless and float precision stays high far
 * from the world origin. If a resource pack replaces the shader the uniforms are simply missing and
 * nothing happens.
 */
public final class SwayShader {
    private static final double PERIOD = 1024.0;

    private SwayShader() {
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        ShaderInstance shader = GameRenderer.getRendertypeCutoutShader();
        if (shader == null) {
            return;
        }
        Uniform wind = shader.getUniform("TerrahaulWind");
        Uniform camera = shader.getUniform("TerrahaulCamera");
        if (wind == null || camera == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean enabled = TerrahaulConfig.vegetationSway() && TerrahaulConfig.animationQuality() != TerrahaulConfig.AnimationQuality.OFF
            && Minecraft.getInstance().level != null;
        float strength = enabled ? WindSystem.strength(partial) + WindSystem.gust(partial) * 0.5F : 0.0F;
        wind.set(WindSystem.dirX(partial), WindSystem.dirZ(partial), strength, (float) (WindSystem.time(partial) % 3600.0));
        Vec3 pos = event.getCamera().getPosition();
        camera.set((float) wrap(pos.x), (float) pos.y, (float) wrap(pos.z));
    }

    private static double wrap(double v) {
        return v - Math.floor(v / PERIOD) * PERIOD;
    }
}
