package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.DecalVfx;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.ShardBurstVfx;
import com.fantasyweapons.client.vfx.effects.ShockwaveVfx;
import com.fantasyweapons.client.vfx.effects.SphereVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Shared layered explosion (sphere, flash, ground ring, scorch, embers, camera shake) tinted by an element palette. */
public final class Blast {
    public record Palette(int core, int main, int deep) {
    }

    private Blast() {
    }

    /**
     * @param at    where it goes off: on the ground, or - a projectile bursting at the end of its flight - in the air,
     *              in which case only the air burst plays and nothing is drawn on the ground (the blast centre is lifted
     *              slightly off the ground)
     * @param power 0 = small, 1 = medium, 2 = huge
     */
    public static void explode(Vec3 at, float radius, Palette pal, long seed, int power, ResourceLocation embers) {
        // on the ground only if there is ground right under it; an air burst draws no rings, scorch or cracks
        Vec3 floor = FrostrendFx.groundOrNull(at, 2.0);
        Vec3 ground = floor != null ? floor : at;
        Vec3 c = floor != null ? ground.add(0, Math.min(1.2, radius * 0.25), 0) : at;
        VfxManager.add(new FlashVfx(c, radius * 0.4f, radius * 1.1f, Colors.argb(140, pal.main()), 10 + power * 2).energy());
        VfxManager.add(new FlashVfx(c, radius * 0.3f, radius * 1.1f, Colors.argb(255, pal.core()), 6, VfxTextures.FLASH));
        if (floor != null) {
            VfxManager.add(new ShockwaveVfx(ground.add(0, 0.06, 0), new Vec3(0, 1, 0), 0.3f, radius * 1.35f, 0.5f + 0.15f * power,
                    Colors.argb(230, pal.main()), 14 + power * 3).energy());
            if (power >= 1) {
                FxScheduler.after(3, () -> VfxManager.add(new ShockwaveVfx(ground.add(0, 0.08, 0), new Vec3(0, 1, 0), 0.3f, radius * 1.0f, 0.4f,
                        Colors.argb(200, pal.core()), 18)));
            }
            // the ground breaks: fracture cracks, buckled plates, thrown chunks and dust of the real terrain
            GroundShatter.impact(ground, radius * 0.8f, 0.6f + 0.45f * power, seed * 13 + 7);
            VfxManager.add(new DecalVfx(ground.add(0, 0.04, 0), new Vec3(0, 1, 0), radius * 0.7f, Colors.argb(200, pal.main()), VfxTextures.GLOW,
                    20 + power * 10).timing(0.05f, 0.7f));
        } else {
            // in the open air the blast is a ball of light and a shell of shock spreading out in every direction
            VfxManager.add(new SphereVfx(c, radius * 0.2f, radius * 1.2f, Colors.argb(150, pal.main()), 10 + power * 3, SphereVfx.Mode.GROW));
        }
        int n = 24 + power * 20;
        VfxManager.add(new ShardBurstVfx(c, new Vec3(0, 1, 0), 0.9f, 0.3f + 0.12f * power + radius * 0.02f, n, 0.25f + 0.08f * power,
                Colors.argb(255, pal.core()), Colors.argb(0, pal.deep()), 24 + power * 6, seed).texture(embers, false).physics(0.012f, 0.92f));
        VfxManager.add(new ShardBurstVfx(c, new Vec3(0, 1, 0), 1f, 0.06f, 10 + power * 6, 0.9f + radius * 0.08f,
                Colors.argb(150, pal.main()), Colors.argb(0, pal.deep()), 30 + power * 8, seed * 31).texture(VfxTextures.MIST, false).physics(-0.003f, 0.93f));
        CameraShake.add(ground, 0.5f + 0.4f * power, radius * 3 + 8);
        var mc = Minecraft.getInstance();
        if (power >= 2 && mc.player != null && mc.player.position().distanceTo(ground) < radius * 2.5) {
            ScreenFx.flash(pal.core(), 0.25f, 10);
        }
    }
}
