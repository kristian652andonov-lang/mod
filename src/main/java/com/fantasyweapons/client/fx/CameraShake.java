package com.fantasyweapons.client.fx;

import com.fantasyweapons.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Decaying camera shake for heavy impacts (strength falls off with distance from the impact). */
public final class CameraShake {
    private static float intensity;
    private static float prevIntensity;

    private CameraShake() {
    }

    public static void add(Vec3 source, float strength, double radius) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = p.position().distanceTo(source);
        if (d > radius) return;
        float s = strength * (float) (1 - d / radius) * (float) ClientConfig.get(ClientConfig.SCREEN_SHAKE, 1.0);
        intensity = Math.min(3f, Math.max(intensity, s));
    }

    public static void tick() {
        prevIntensity = intensity;
        intensity *= 0.82f;
        if (intensity < 0.01f) intensity = 0;
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        float i = prevIntensity + (intensity - prevIntensity) * partial;
        if (i <= 0) return;
        double t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + partial;
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 2.7) * 1.4 * i));
        event.setPitch(event.getPitch() + (float) (Math.cos(t * 3.3) * 1.2 * i));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 4.1) * 0.9 * i));
    }
}
