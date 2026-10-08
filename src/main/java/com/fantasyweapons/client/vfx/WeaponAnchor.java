package com.fantasyweapons.client.vfx;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Approximate world positions of a holder's weapon hand and blade, used to attach effects to the weapon in both first
 * and third person.
 */
public final class WeaponAnchor {
    private WeaponAnchor() {
    }

    public static boolean isFirstPersonLocal(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player && mc.getCameraEntity() == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    /** World position of the main hand. */
    public static Vec3 hand(LivingEntity e, float partial) {
        if (isFirstPersonLocal(e)) {
            Minecraft mc = Minecraft.getInstance();
            var cam = mc.gameRenderer.getMainCamera();
            Vec3 look = new Vec3(cam.getLookVector());
            Vec3 up = new Vec3(cam.getUpVector());
            Vec3 left = new Vec3(cam.getLeftVector());
            return cam.getPosition().add(look.scale(0.75)).subtract(left.scale(0.42)).subtract(up.scale(0.32));
        }
        float bodyYaw = Mth.lerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 base = e.getPosition(partial);
        double side = -0.36;
        double fwd = 0.28;
        double height = e.getBbHeight() * (e.isCrouching() ? 0.42 : 0.5);
        double x = -Math.sin(bodyYaw) * fwd + Math.cos(bodyYaw) * side * -1;
        double z = Math.cos(bodyYaw) * fwd + Math.sin(bodyYaw) * side * -1;
        return base.add(x, height, z);
    }

    /** Point along the blade at {@code reach} blocks from the hand. */
    public static Vec3 blade(LivingEntity e, float partial, double reach) {
        Vec3 hand = hand(e, partial);
        Vec3 dir;
        if (isFirstPersonLocal(e)) {
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            dir = new Vec3(cam.getLookVector()).add(new Vec3(cam.getUpVector()).scale(0.9)).normalize();
            reach *= 0.45;
        } else {
            Vec3 look = e.getViewVector(partial);
            dir = new Vec3(look.x * 0.6, 0.8, look.z * 0.6).normalize();
        }
        return hand.add(dir.scale(reach));
    }
}
