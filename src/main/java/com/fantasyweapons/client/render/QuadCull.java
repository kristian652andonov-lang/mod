package com.fantasyweapons.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which faces of a weapon's model are worth drawing, worked out once per face from its textures: a face whose patch of
 * the texture is wholly clear is never drawn, and the glow passes draw only the faces whose patch of the glowmask has
 * something in it (on a model like the Weapon of God's, most of its faces have none). Every face is otherwise drawn
 * exactly as before.
 */
public final class QuadCull {
    static final byte SEEN = 1, GLOWS = 2;

    /** For each texture: whether each pixel is clear, as a summed-area table over "not clear", or null if not readable. */
    private static final Map<ResourceLocation, Mask> MASKS = new HashMap<>();
    /** For each texture, each face's flags (faces are kept by identity: a model's faces live as long as it). */
    private static final Map<ResourceLocation, IdentityHashMap<GeoQuad, Byte>> FLAGS = new HashMap<>();

    private QuadCull() {
    }

    /** Forgets everything (textures or models were reloaded). */
    public static void clear() {
        MASKS.clear();
        FLAGS.clear();
    }

    /** {@link #SEEN} and {@link #GLOWS} for {@code quad} of a model drawn with {@code texture}. */
    static byte flags(ResourceLocation texture, GeoQuad quad) {
        IdentityHashMap<GeoQuad, Byte> known = FLAGS.computeIfAbsent(texture, t -> new IdentityHashMap<>());
        Byte f = known.get(quad);
        if (f != null) return f;
        Mask base = mask(texture), glow = mask(AutoGlowingTexture.getEmissiveResource(texture));
        byte flags = 0;
        // a texture that cannot be read is drawn in full; a missing glowmask has nothing to glow
        if (base == null || base.any(quad)) flags |= SEEN;
        if (glow != null && glow.any(quad)) flags |= GLOWS;
        known.put(quad, flags);
        return flags;
    }

    @Nullable
    private static Mask mask(ResourceLocation texture) {
        if (MASKS.containsKey(texture)) return MASKS.get(texture);
        Mask m = null;
        var manager = Minecraft.getInstance().getResourceManager();
        var res = manager.getResource(texture);
        // an animated texture's frames are stacked down the image, so its faces cannot be matched to it: all are drawn
        if (res.isPresent() && manager.getResource(texture.withPath(texture.getPath() + ".mcmeta")).isPresent()) {
            MASKS.put(texture, Mask.ALL);
            return Mask.ALL;
        }
        if (res.isPresent()) {
            try (var in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                m = new Mask(img);
            } catch (Exception e) {
                m = null;
            }
        }
        MASKS.put(texture, m);
        return m;
    }

    /** Where a texture is not clear, for asking quickly whether a rectangle of it has anything in it. */
    private static final class Mask {
        /** A texture whose faces are all drawn. */
        static final Mask ALL = new Mask();
        private final int w, h;
        private final int[] sum;

        private Mask() {
            w = h = 0;
            sum = null;
        }

        Mask(NativeImage img) {
            w = img.getWidth();
            h = img.getHeight();
            sum = new int[(w + 1) * (h + 1)];
            for (int y = 0; y < h; y++) {
                int row = 0;
                for (int x = 0; x < w; x++) {
                    if (((img.getPixelRGBA(x, y) >>> 24) & 255) != 0) row++;
                    sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
                }
            }
        }

        /** Whether the face's patch of the texture has any pixel that is not clear. */
        boolean any(GeoQuad quad) {
            if (sum == null) return true;
            float u0 = 1, u1 = 0, v0 = 1, v1 = 0;
            for (GeoVertex v : quad.vertices()) {
                u0 = Math.min(u0, v.texU());
                u1 = Math.max(u1, v.texU());
                v0 = Math.min(v0, v.texV());
                v1 = Math.max(v1, v.texV());
            }
            int x0 = clamp((int) Math.floor(u0 * w + 1e-4), w - 1), y0 = clamp((int) Math.floor(v0 * h + 1e-4), h - 1);
            int x1 = Math.max(x0 + 1, clamp((int) Math.ceil(u1 * w - 1e-4), w)), y1 = Math.max(y0 + 1, clamp((int) Math.ceil(v1 * h - 1e-4), h));
            int n = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0];
            return n > 0;
        }

        private static int clamp(int v, int max) {
            return Math.max(0, Math.min(max, v));
        }
    }
}
