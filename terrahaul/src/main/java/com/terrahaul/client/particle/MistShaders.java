package com.terrahaul.client.particle;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.terrahaul.Terrahaul;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * The two core shaders of the mist ({@code assets/terrahaul/shaders/core}), loaded with Minecraft's
 * own shaders and again whenever resource packs are reloaded:
 * <ul>
 *   <li>{@code mist}: Minecraft's particle shader without its cut-off at an opacity of 0.1. Mist
 *       is faint (a puff is a few tenths opaque at most, and fades in and out), so that cut-off
 *       took away its soft edges and made fading puffs vanish all at once;</li>
 *   <li>{@code mist_depth}: writes the depth of the visible mist that is in front of the water,
 *       for Fabulous graphics (see {@link MistParticle#drawDepth}).</li>
 * </ul>
 * If they cannot be loaded (a resource pack replaced them with something broken), mist falls back to
 * Minecraft's particle shader and the depth pass is skipped.
 */
public final class MistShaders {
    @Nullable
    private static ShaderInstance colour;
    @Nullable
    private static ShaderInstance depth;

    private MistShaders() {
    }

    @Nullable
    static ShaderInstance colour() {
        return colour;
    }

    @Nullable
    static ShaderInstance depth() {
        return depth;
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Terrahaul.id("mist"), DefaultVertexFormat.PARTICLE),
                shader -> colour = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Terrahaul.id("mist_depth"), DefaultVertexFormat.PARTICLE),
                shader -> depth = shader);
        } catch (IOException e) {
            Terrahaul.LOGGER.error("Could not load the mist shaders; mist is drawn with Minecraft's particle shader", e);
            colour = null;
            depth = null;
        }
    }
}
