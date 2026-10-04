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
     * @param ground position on the ground (the blast centre is lifted slightly)
     * @param power  0 = small, 1 = medium, 2 = huge
     */
    public static void explode(Vec3 ground, float radius, Palette pal, long seed, int power, ResourceLocation embers) {
        Vec3 c = ground.add(0, Math.min(1.2, radius * 0.25), 0);
        VfxManager.add(new SphereVfx(c, radius * 0.1f, radius * 0.85f, Colors.argb(120, pal.main()), 10 + power * 3, SphereVfx.Mode.GROW));
        VfxManager.add(new SphereVfx(c, radius * 0.05f, radius * 0.4f, Colors.argb(160, pal.core()), 7 + power * 2, SphereVfx.Mode.GROW));
        VfxManager.add(new FlashVfx(c, radius * 0.5f, radius * 1.6f, Colors.argb(170, pal.main()), 10 + power * 2).energy());
        VfxManager.add(new FlashVfx(c, radius * 0.3f, radius * 1.1f, Colors.argb(255, pal.core()), 6, VfxTextures.FLASH));
        VfxManager.add(new ShockwaveVfx(ground.add(0, 0.06, 0), new Vec3(0, 1, 0), 0.3f, radius * 1.35f, 0.5f + 0.15f * power,
                Colors.argb(230, pal.main()), 14 + power * 3).energy());
        if (power >= 1) {
            FxScheduler.after(3, () -> VfxManager.add(new ShockwaveVfx(ground.add(0, 0.08, 0), new Vec3(0, 1, 0), 0.3f, radius * 1.0f, 0.4f,
                    Colors.argb(200, pal.core()), 18)));
        }
        VfxManager.add(new DecalVfx(ground.add(0, 0.03, 0), new Vec3(0, 1, 0), radius * 0.8f, Colors.argb(220, pal.deep()), VfxTextures.CRACK,
                50 + power * 30).timing(0.04f, 0.4f).translucent());
        VfxManager.add(new DecalVfx(ground.add(0, 0.04, 0), new Vec3(0, 1, 0), radius * 0.7f, Colors.argb(200, pal.main()), VfxTextures.GLOW,
                20 + power * 10).timing(0.05f, 0.7f));
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
