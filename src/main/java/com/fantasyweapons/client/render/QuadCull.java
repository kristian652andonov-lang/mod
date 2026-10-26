package com.fantasyweapons.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which faces of the mod's models are worth drawing, worked out once per face from its textures: a face whose patch of
 * the texture is wholly clear (in every frame of an animated one) is never drawn, and the glow passes draw only the
 * faces whose patch of the glowmask has something in it (on a model like the Weapon of God's, most of its faces have
 * none). Every face is otherwise drawn exactly as before. See {@link ModelCulling}.
 */
public final class QuadCull {
    static final byte SEEN = 1, GLOWS = 2;

    /** For each texture: whether each pixel is clear, as a summed-area table over "not clear", or null if not readable. */
    private static final Map<ResourceLocation, Mask> MASKS = new HashMap<>();
    /** For each texture, each face's flags (faces are kept by identity: a model's faces live as long as it). */
    private static final Map<ResourceLocation, IdentityHashMap<GeoQuad, Byte>> FLAGS = new HashMap<>();
    /** For each texture, each cube's faces drawn: the seen ones in the low half, the glowing ones in the high half. */
    private static final Map<ResourceLocation, IdentityHashMap<GeoCube, Long>> CUBE_FACES = new HashMap<>();
    /** The texture last asked about and its cubes (a model's cubes come one after another, all with one texture). */
    @Nullable
    private static ResourceLocation lastTexture;
    @Nullable
    private static IdentityHashMap<GeoCube, Long> lastCubes;

    private QuadCull() {
    }

    /** Forgets everything (textures or models were reloaded). */
    public static void clear() {
        MASKS.clear();
        FLAGS.clear();
        CUBE_FACES.clear();
        lastTexture = null;
        lastCubes = null;
    }

    /** {@link #SEEN} and {@link #GLOWS} for {@code quad} of a model drawn with {@code texture}. */
    static byte flags(ResourceLocation texture, GeoQuad quad) {
        IdentityHashMap<GeoQuad, Byte> known = FLAGS.computeIfAbsent(texture, t -> new IdentityHashMap<>());
        Byte f = known.get(quad);
        if (f != null) return f;
        // the frames are cut as the game draws them: a strip of the artist's is cut by its model (see FrameStripTextures),
        // and the glow is cut into the same frames as the texture it glows on, as GeckoLib does
        int[] frame = drawnFrame(texture);
        Mask base = mask(texture, false, frame), glow = mask(AutoGlowingTexture.getEmissiveResource(texture), true, frame);
        byte flags = 0;
        // a texture that cannot be read is drawn in full; a missing glowmask has nothing to glow
        if (base == null || base.any(quad)) flags |= SEEN;
        if (glow != null && glow.any(quad)) flags |= GLOWS;
        known.put(quad, flags);
        return flags;
    }

    /** The faces of {@code cube} drawn (bit {@code i} for its {@code i}th face): those seen, or in the glow layer those that glow. */
    static int faces(ResourceLocation texture, GeoCube cube, boolean glow) {
        IdentityHashMap<GeoCube, Long> known;
        if (texture == lastTexture || texture.equals(lastTexture)) {
            known = lastCubes;
        } else {
            known = CUBE_FACES.computeIfAbsent(texture, t -> new IdentityHashMap<>());
            lastTexture = texture;
            lastCubes = known;
        }
        Long f = known.get(cube);
        if (f == null) {
            long seen = 0, glows = 0;
            GeoQuad[] quads = cube.quads();
            for (int i = 0; i < quads.length && i < 31; i++) {
                if (quads[i] == null) continue;
                byte b = flags(texture, quads[i]);
                if ((b & SEEN) != 0) seen |= 1L << i;
                if ((b & GLOWS) != 0) glows |= 1L << i;
            }
            f = seen | (glows << 32);
            known.put(cube, f);
        }
        return (int) (glow ? f >>> 32 : f);
    }

    /**
     * Where {@code texture} has something. A texture's pixel counts if it is not clear; a glowmask's if it is not wholly
     * zero, as GeckoLib reads one: a pixel clear but coloured still makes the texture glow there, at full strength.
     */
    @Nullable
    private static Mask mask(ResourceLocation texture, boolean glowmask, @Nullable int[] frame) {
        if (MASKS.containsKey(texture)) return MASKS.get(texture);
        Mask m = null;
        var res = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (res.isPresent()) {
            try (var in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                // an animated texture's frames are laid out in the image: a face is drawn if its patch has something in
                // any frame
                m = frame != null ? new Mask(img, frame[0], frame[1], glowmask) : new Mask(img, img.getWidth(), img.getHeight(), glowmask);
            } catch (Exception e) {
                // drawn in full, as if it could not be read
                m = Mask.ALL;
            }
        }
        MASKS.put(texture, m);
        return m;
    }

    /** The size of the frames {@code texture} is drawn in ({width, height}), or null if it is not animated. */
    @Nullable
    private static int[] drawnFrame(ResourceLocation texture) {
        var drawn = Minecraft.getInstance().getTextureManager().getTexture(texture, null);
        if (drawn instanceof FrameStripTextures.StripTexture strip) return new int[]{strip.frameWidth(), strip.frameHeight()};
        var res = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (res.isEmpty()) return null;
        try (var in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            var animation = res.get().metadata().getSection(net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER);
            if (animation.isEmpty()) return null;
            var frame = animation.get().calculateFrameSize(img.getWidth(), img.getHeight());
            return new int[]{frame.width(), frame.height()};
        } catch (Exception e) {
            return null;
        }
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

        /**
         * A frame of {@code fw} by {@code fh}: a pixel counts if it is not clear in any of the image's frames (or, for a
         * glowmask, not wholly zero).
         */
        Mask(NativeImage img, int fw, int fh, boolean anyChannel) {
            w = Math.max(1, Math.min(fw, img.getWidth()));
            h = Math.max(1, Math.min(fh, img.getHeight()));
            int across = Math.max(1, img.getWidth() / w), down = Math.max(1, img.getHeight() / h);
            sum = new int[(w + 1) * (h + 1)];
            for (int y = 0; y < h; y++) {
                int row = 0;
                for (int x = 0; x < w; x++) {
                    boolean any = false;
                    for (int fy = 0; fy < down && !any; fy++) {
                        for (int fx = 0; fx < across && !any; fx++) {
                            int c = img.getPixelRGBA(fx * w + x, fy * h + y);
                            any = anyChannel ? c != 0 : ((c >>> 24) & 255) != 0;
                        }
                    }
                    if (any) row++;
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
