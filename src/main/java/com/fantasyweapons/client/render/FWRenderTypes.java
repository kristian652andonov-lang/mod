package com.fantasyweapons.client.render;

import com.fantasyweapons.client.vfx.VfxTextures;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Custom render types for the VFX system. Extends RenderType only to reach the protected render-state shards.
 * <ul>
 *   <li>{@link #additive}: soft additive glow (alpha-weighted, so faded vertices fade out)</li>
 *   <li>{@link #energy}: additive + animated noise breakup shader</li>
 *   <li>{@link #voidInterior}: alpha-blended dark void with swirling nebula (can actually darken the scene)</li>
 *   <li>{@link #translucent}: alpha-blended textured geometry (dark smoke, shadows, solid-looking fragments)</li>
 *   <li>{@link #solid}: like translucent but writes depth, for modelled props that must hide their own back faces</li>
 * </ul>
 * Apart from {@link #solid} none of them write depth, so effects overlap without sorting artefacts but are still
 * hidden behind terrain.
 */
public final class FWRenderTypes extends RenderType {
    private FWRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    private static final TransparencyStateShard ADDITIVE_ALPHA = new TransparencyStateShard("fw_additive_alpha", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    private static final ShaderStateShard ADDITIVE_SHADER = new ShaderStateShard(() -> FWShaders.vfxAdditive);
    private static final ShaderStateShard ENERGY_SHADER = new ShaderStateShard(() -> FWShaders.vfxEnergy);
    private static final ShaderStateShard VOID_SHADER = new ShaderStateShard(() -> FWShaders.vfxVoid);
    private static final ShaderStateShard CUTOUT_SHADER = new ShaderStateShard(() -> FWShaders.vfxCutout);

    private static final Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(tex -> create("fw_vfx_additive",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(ADDITIVE_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(ADDITIVE_ALPHA)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(tex -> create("fw_vfx_translucent",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(ADDITIVE_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> SOLID = Util.memoize(tex -> create("fw_vfx_solid",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(ADDITIVE_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> CUTOUT = Util.memoize(tex -> create("fw_vfx_cutout",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(CUTOUT_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> ENERGY = Util.memoize(tex -> create("fw_vfx_energy",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(ENERGY_SHADER)
                    .setTextureState(MultiTextureStateShard.builder().add(tex, true, false).add(VfxTextures.NOISE, true, false).build())
                    .setTransparencyState(ADDITIVE_ALPHA)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> VOID = Util.memoize(tex -> create("fw_vfx_void",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(VOID_SHADER)
                    .setTextureState(MultiTextureStateShard.builder().add(tex, true, false).add(VfxTextures.NOISE, true, false).build())
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    // ---- GUI (screen-space) variants, using the vanilla position-tex-colour shader (no fog) ----
    private static final ShaderStateShard GUI_TEX_COLOR = new ShaderStateShard(GameRenderer::getPositionTexColorShader);

    private static final Function<ResourceLocation, RenderType> GUI_ADDITIVE = Util.memoize(tex -> create("fw_gui_additive",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(GUI_TEX_COLOR)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(ADDITIVE_ALPHA)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> GUI_TEXTURED = Util.memoize(tex -> create("fw_gui_textured",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(GUI_TEX_COLOR)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    public static RenderType guiAdditive(ResourceLocation texture) {
        return GUI_ADDITIVE.apply(texture);
    }

    public static RenderType guiTextured(ResourceLocation texture) {
        return GUI_TEXTURED.apply(texture);
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    /** Opaque-looking textured geometry that writes depth (vines, thorns, petals, chain links) so it occludes itself. */
    public static RenderType solid(ResourceLocation texture) {
        return SOLID.apply(texture);
    }

    /** Opaque geometry whose outline is cut out by its texture's alpha (petals, leaves). */
    public static RenderType cutout(ResourceLocation texture) {
        return CUTOUT.apply(texture);
    }

    public static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT.apply(texture);
    }

    public static RenderType energy(ResourceLocation texture) {
        return ENERGY.apply(texture);
    }

    public static RenderType voidInterior(ResourceLocation mask) {
        return VOID.apply(mask);
    }
}
