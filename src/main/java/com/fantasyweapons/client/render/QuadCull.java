package com.fantasyweapons.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.texture.GeoAbstractTexture;
import software.bernie.geckolib.resource.GeoGlowingTextureMeta;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Which faces of the mod's models are worth drawing, worked out once per face from its textures: a face whose patch of
 * the texture is wholly clear (in every frame of an animated one) is never drawn, and the glow passes draw only the
 * faces whose patch of the glowmask has something in it (on a model like the Weapon of God's, most of its faces have
 * none). Every face is otherwise drawn exactly as before. See {@link ModelCulling}.
 * <p>
 * A texture is read once, on a worker thread, the first time a model is drawn with it: until it has been read the model
 * is drawn whole, so a model seen for the first time costs the game's drawing nothing (reading a large texture on the
 * drawing thread, as it was read once per face, froze the game for seconds the first time a big model showed).
 */
public final class QuadCull {
    /** For each texture, where it and its glowmask have something: read once, on a worker thread (see {@link #masks}). */
    private static final Map<ResourceLocation, CompletableFuture<Masks>> MASKS = new HashMap<>();
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
        CUBE_FACES.clear();
        lastTexture = null;
        lastCubes = null;
    }

    /**
     * The faces of {@code cube} drawn (bit {@code i} for its {@code i}th face): those seen, or in the glow layer those that
     * glow; all of them while the texture is still being read.
     */
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
            Masks m = masks(texture);
            if (m == null) return -1;
            long seen = 0, glows = 0;
            GeoQuad[] quads = cube.quads();
            for (int i = 0; i < quads.length && i < 31; i++) {
                if (quads[i] == null) continue;
                // a texture that cannot be read is drawn in full; one without a glowmask has nothing to glow
                if (m.base() == null || m.base().any(quads[i])) seen |= 1L << i;
                if (m.glow() != null && m.glow().any(quads[i])) glows |= 1L << i;
            }
            f = seen | (glows << 32);
            known.put(cube, f);
        }
        return (int) (glow ? f >>> 32 : f);
    }

    /** Where {@code texture} and its glowmask have something, or null while they are still being read. */
    @Nullable
    private static Masks masks(ResourceLocation texture) {
        CompletableFuture<Masks> read = MASKS.get(texture);
        if (read == null) {
            Minecraft mc = Minecraft.getInstance();
            // a strip of the artist's is cut into the frames its model draws (see FrameStripTextures); asked here, where
            // the game's textures are kept
            int[] strip = mc.getTextureManager().getTexture(texture, null) instanceof FrameStripTextures.StripTexture s
                    ? new int[]{s.frameWidth(), s.frameHeight()} : null;
            ResourceManager resources = mc.getResourceManager();
            read = CompletableFuture.supplyAsync(() -> read(resources, texture, strip), Util.backgroundExecutor())
                    .exceptionally(e -> Masks.ALL);
            MASKS.put(texture, read);
        }
        return read.getNow(null);
    }

    /**
     * Reads where the texture and its glowmask have something, cut into the frames the game draws them in: the texture's
     * own (a strip of the artist's by its model, an animated texture by its animation), and the glow into the same frames
     * as the texture it glows on, as GeckoLib does. A texture's pixel counts if it is not clear; a glowmask's if it is not
     * wholly zero, as GeckoLib reads one: a pixel clear but coloured still makes the texture glow there, at full strength.
     */
    private static Masks read(ResourceManager resources, ResourceLocation texture, @Nullable int[] strip) {
        try {
            int[] frame = strip;
            Mask base = null;
            boolean glowsByMeta = false;
            var res = resources.getResource(texture);
            if (res.isPresent()) {
                try (var in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    if (frame == null) {
                        var animation = res.get().metadata().getSection(AnimationMetadataSection.SERIALIZER);
                        if (animation.isPresent()) {
                            var size = animation.get().calculateFrameSize(img.getWidth(), img.getHeight());
                            frame = new int[]{size.width(), size.height()};
                        }
                    }
                    base = new Mask(img, frame, false);
                }
                // GeckoLib can also make the glow from the texture's own metadata, without a glowmask
                glowsByMeta = res.get().metadata().getSection(GeoGlowingTextureMeta.DESERIALIZER).isPresent();
            }
            Mask glow = null;
            var glowRes = resources.getResource(GeoAbstractTexture.appendToPath(texture, "_glowmask"));
            if (glowRes.isPresent()) {
                try (var in = glowRes.get().open(); NativeImage img = NativeImage.read(in)) {
                    glow = new Mask(img, frame, true);
                }
            } else if (glowsByMeta) {
                glow = Mask.ALL;
            }
            return new Masks(base, glow);
        } catch (Exception e) {
            // drawn in full, as if it could not be read
            return Masks.ALL;
        }
    }

    /** Where a texture has something (null if it is not there: drawn in full) and where its glowmask does (null: nowhere). */
    private record Masks(@Nullable Mask base, @Nullable Mask glow) {
        /** A texture drawn in full, glow and all. */
        static final Masks ALL = new Masks(Mask.ALL, Mask.ALL);
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
         * A frame ({width, height}, or the whole image if null): a pixel counts if it is not clear in any of the image's
         * frames (or, for a glowmask, not wholly zero).
         */
        Mask(NativeImage img, @Nullable int[] frame, boolean anyChannel) {
            w = Math.max(1, Math.min(frame != null ? frame[0] : img.getWidth(), img.getWidth()));
            h = Math.max(1, Math.min(frame != null ? frame[1] : img.getHeight(), img.getHeight()));
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
