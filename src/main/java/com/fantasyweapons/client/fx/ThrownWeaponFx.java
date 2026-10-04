package com.fantasyweapons.client.fx;

import com.fantasyweapons.client.render.ThrownWeaponRenderer;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxManager;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.client.vfx.effects.FlashVfx;
import com.fantasyweapons.client.vfx.effects.FollowTrailVfx;
import com.fantasyweapons.entity.ThrownWeaponEntity;
import net.minecraft.world.phys.Vec3;

/** Trails of a spinning thrown weapon: a broad energy wake plus two ribbons traced by the spinning blade tips. */
public final class ThrownWeaponFx {
    private ThrownWeaponFx() {
    }

    public static void attach(ThrownWeaponEntity e) {
        Themes.Theme theme = Themes.of(e.stack());
        int primary = theme.primary(), secondary = theme.secondary();
        VfxManager.add(new FollowTrailVfx(() -> e.isRemoved() ? null : e.getPosition(1f).add(0, 0.25, 0), 1.6f,
                Colors.argb(170, primary), Colors.argb(0, secondary), 12, 600).texture(VfxTextures.STREAK, true));
        for (int k = 0; k < 2; k++) {
            double offset = k * Math.PI;
            VfxManager.add(new FollowTrailVfx(() -> {
                if (e.isRemoved()) return null;
                double a = Math.toRadians((e.tickCount) * ThrownWeaponRenderer.SPIN) + offset;
                double r = ThrownWeaponRenderer.SCALE * 0.48;
                return e.position().add(Math.cos(a) * r, 0.25, Math.sin(a) * r);
            }, 0.35f, Colors.argb(240, theme.light()), Colors.argb(0, primary), 7, 600).texture(VfxTextures.LIGHTNING, false));
        }
        VfxManager.add(new FlashVfx(Vec3.ZERO, 1.2f, 1.8f, Colors.argb(120, primary), 600).follow(e, new Vec3(0, 0.25, 0)).peak(0.02f).energy());
    }
}
