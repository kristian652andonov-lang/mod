package com.terrahaul.client.wind;

import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.worldgen.noise.SimplexNoise;
import com.terrahaul.worldgen.noise.TMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Client-side wind shared by every Terrahaul animation (falling leaves, petals, dust and
 * vegetation sway).
 * <p>
 * The wind is purely visual and computed independently on each client, so it needs no network
 * traffic. Direction wanders slowly; strength combines a slow base swell, short gusts, the weather
 * (rain and thunder) and the camera's altitude, and is scaled by the {@code windStrength} option.
 * Dimensions without sky light (Nether, End) have no wind. Values are kept for the previous and
 * the current tick so renderers can interpolate smoothly between ticks.
 */
public final class WindSystem {
    private static final SimplexNoise NOISE = new SimplexNoise(0x7E77A4A0L);

    private static long ticks;
    private static float strength;
    private static float prevStrength;
    private static float gust;
    private static float prevGust;
    private static float angle;
    private static float prevAngle;

    private WindSystem() {
    }

    /** Advances the wind by one client tick. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        prevStrength = strength;
        prevGust = gust;
        prevAngle = angle;
        if (level == null || mc.isPaused()) {
            return;
        }
        ticks++;
        double t = ticks / 20.0;

        // direction: a slow random walk over the full circle
        float targetAngle = (float) (NOISE.noise2(t / 420.0, 3.7) * Math.PI * 1.6 + 0.6);
        angle = targetAngle;

        double base = 0.45 + 0.35 * NOISE.noise2(t / 140.0, 11.3);
        double gusts = Math.max(0.0, NOISE.noise2(t / 7.0, 27.1) * 1.3 + NOISE.noise2(t / 2.3, 41.9) * 0.4 - 0.2);
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        double weather = 1.0 + rain * 0.55 + thunder * 0.9;
        double altitude = 1.0;
        if (mc.gameRenderer.getMainCamera().isInitialized()) {
            double y = mc.gameRenderer.getMainCamera().getPosition().y;
            altitude = 1.0 + TMath.clamp((y - 90.0) / 320.0, 0.0, 1.0) * 0.9;
        }
        double target = level.dimensionType().hasSkyLight() ? base * weather * altitude * TerrahaulConfig.windStrength() : 0.0;
        gust = (float) (gusts * weather);
        // ease towards the target so weather changes never pop
        strength = (float) Mth.lerp(0.08, strength, target);
    }

    /** Seconds of wind animation, interpolated within the tick. */
    public static double time(float partialTick) {
        return (ticks + partialTick) / 20.0;
    }

    /** Sustained wind strength; about 0.3 on a calm day, 1 in a breeze and 2-3 in a storm. */
    public static float strength(float partialTick) {
        return Mth.lerp(partialTick, prevStrength, strength);
    }

    /** Gust intensity on top of the sustained wind, 0 when still. */
    public static float gust(float partialTick) {
        return Mth.lerp(partialTick, prevGust, gust) * strength(partialTick);
    }

    /** Effective wind including gusts. */
    public static float total(float partialTick) {
        return strength(partialTick) + gust(partialTick) * 0.6F;
    }

    public static float dirX(float partialTick) {
        return Mth.cos(Mth.rotLerp(partialTick, prevAngle * Mth.RAD_TO_DEG, angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
    }

    public static float dirZ(float partialTick) {
        return Mth.sin(Mth.rotLerp(partialTick, prevAngle * Mth.RAD_TO_DEG, angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
    }

    /** Resets the wind when the player leaves a world. */
    public static void reset() {
        ticks = 0;
        strength = prevStrength = 0.0F;
        gust = prevGust = 0.0F;
    }
}
