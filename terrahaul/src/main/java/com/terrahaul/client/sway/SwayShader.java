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
 * nothing happens. The same values go to Sodium's terrain shader through
 * {@link com.terrahaul.client.sodium.SodiumShaders} when Sodium draws the terrain.
 */
public final class SwayShader {
    private static final double PERIOD = 1024.0;
    /** The values of this frame: wind (direction x, z, strength, time) and wrapped camera (x, y, z). */
    private static final float[] WIND = new float[4];
    private static final float[] CAMERA = new float[3];

    private SwayShader() {
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean enabled = TerrahaulConfig.vegetationSway() && TerrahaulConfig.animationQuality() != TerrahaulConfig.AnimationQuality.OFF
            && Minecraft.getInstance().level != null;
        WIND[0] = WindSystem.dirX(partial);
        WIND[1] = WindSystem.dirZ(partial);
        WIND[2] = enabled ? WindSystem.strength(partial) + WindSystem.gust(partial) * 0.5F : 0.0F;
        WIND[3] = (float) (WindSystem.time(partial) % 3600.0);
        Vec3 pos = event.getCamera().getPosition();
        CAMERA[0] = (float) wrap(pos.x);
        CAMERA[1] = (float) pos.y;
        CAMERA[2] = (float) wrap(pos.z);

        ShaderInstance shader = GameRenderer.getRendertypeCutoutShader();
        if (shader == null) {
            return;
        }
        Uniform wind = shader.getUniform("TerrahaulWind");
        Uniform camera = shader.getUniform("TerrahaulCamera");
        if (wind == null || camera == null) {
            return;
        }
        wind.set(WIND[0], WIND[1], WIND[2], WIND[3]);
        camera.set(CAMERA[0], CAMERA[1], CAMERA[2]);
    }

    /** This frame's wind uniform: direction x, direction z, strength (0 when sway is off), time. */
    public static float[] wind() {
        return WIND;
    }

    /** This frame's camera uniform: x and z wrapped to the wave period, y as is. */
    public static float[] camera() {
        return CAMERA;
    }

    private static double wrap(double v) {
        return v - Math.floor(v / PERIOD) * PERIOD;
    }
}
