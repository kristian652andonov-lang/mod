package com.fantasyweapons.client.render;

import com.fantasyweapons.FantasyWeapons;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** The mod's custom core shaders (assets/fantasyweapons/shaders/core). */
public final class FWShaders {
    public static ShaderInstance vfxAdditive;
    public static ShaderInstance vfxEnergy;
    public static ShaderInstance vfxVoid;
    public static ShaderInstance vfxCutout;

    private FWShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FantasyWeapons.id("vfx_additive"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> vfxAdditive = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FantasyWeapons.id("vfx_energy"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> vfxEnergy = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FantasyWeapons.id("vfx_void"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> vfxVoid = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), FantasyWeapons.id("vfx_cutout"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> vfxCutout = s);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Fantasy Weapons shaders", e);
        }
    }
}
