package com.fantasyweapons.client.render;

import com.fantasyweapons.FantasyWeapons;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The artist's animated textures are strips of frames that are not square - a refinery frame is 128x64, a raw or refined
 * material's 32x16 - but their {@code .mcmeta} files don't give the frame size, and Minecraft (and GeckoLib after it)
 * then takes the frames to be squares: every face is drawn from the wrong part of the strip. Without changing the files,
 * each such texture is registered as a GeckoLib animated texture whose frame height comes from its model's texture size.
 * The glow layers follow the corrected frames, since GeckoLib builds them from the base texture.
 */
public final class FrameStripTextures implements ResourceManagerReloadListener {
    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        Map<ResourceLocation, Resource> pngs = resources.listResources("textures",
                l -> l.getNamespace().equals(FantasyWeapons.MOD_ID) && l.getPath().endsWith(".png") && !l.getPath().endsWith("_glowmask.png"));
        int fixed = 0;
        for (var e : pngs.entrySet()) {
            ResourceLocation location = e.getKey();
            try {
                Optional<AnimationMetadataSection> anim = e.getValue().metadata().getSection(AnimationMetadataSection.SERIALIZER);
                if (anim.isEmpty()) continue;
                int[] size = pngSize(e.getValue());
                Integer frameHeight = modelFrameHeight(resources, location, size[0]);
                if (frameHeight == null || frameHeight <= 0 || size[1] % frameHeight != 0) continue;
                FrameSize assumed = anim.get().calculateFrameSize(size[0], size[1]);
                if (assumed.width() == size[0] && assumed.height() == frameHeight) continue;
                // already corrected (a reload keeps the texture, and the glow layer built on it)
                if (textures.getTexture(location, null) instanceof StripTexture) continue;
                textures.register(location, new StripTexture(location, size[0], frameHeight));
                fixed++;
            } catch (IOException | RuntimeException ex) {
                FantasyWeapons.LOGGER.warn("Could not read the frames of {}", location, ex);
            }
        }
        if (fixed > 0) FantasyWeapons.LOGGER.debug("Corrected the frame size of {} animated textures", fixed);
    }

    /** Width and height from the PNG header. */
    private static int[] pngSize(Resource png) throws IOException {
        try (InputStream in = png.open()) {
            byte[] h = in.readNBytes(24);
            if (h.length < 24) throw new IOException("not a PNG");
            int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
            int ht = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
            return new int[]{w, ht};
        }
    }

    /**
     * One frame's height for a strip {@code width} pixels wide, from the texture size of the model that uses it:
     * {@code textures/<kind>/<name>.png} belongs to {@code geo/<kind>/<name>.geo.json}.
     */
    @Nullable
    private static Integer modelFrameHeight(ResourceManager resources, ResourceLocation texture, int width) throws IOException {
        String path = texture.getPath(); // textures/<kind>/<name>.png
        String rest = path.substring("textures/".length(), path.length() - ".png".length());
        ResourceLocation geo = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "geo/" + rest + ".geo.json");
        Optional<Resource> model = resources.getResource(geo);
        if (model.isEmpty()) return null;
        try (InputStreamReader reader = new InputStreamReader(model.get().open(), StandardCharsets.UTF_8)) {
            JsonObject description = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonObject("description");
            int tw = description.get("texture_width").getAsInt(), th = description.get("texture_height").getAsInt();
            if (tw <= 0 || (width * th) % tw != 0) return null;
            return width * th / tw;
        }
    }

    /** A GeckoLib animated texture read with an explicit frame size. */
    static final class StripTexture extends AnimatableTexture {
        private final int frameWidth, frameHeight;

        StripTexture(ResourceLocation location, int frameWidth, int frameHeight) {
            super(location);
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }

        @Override
        public void load(ResourceManager manager) throws IOException {
            super.load(new FrameSized(manager, this.location, this.frameWidth, this.frameHeight));
        }
    }

    /** A view of the resources in which one texture's animation metadata carries a frame size. */
    private record FrameSized(ResourceManager delegate, ResourceLocation texture, int frameWidth, int frameHeight) implements ResourceManager {
        @Override
        public Optional<Resource> getResource(ResourceLocation location) {
            Optional<Resource> found = delegate.getResource(location);
            if (!location.equals(texture) || found.isEmpty()) return found;
            Resource original = found.get();
            return Optional.of(new Resource(original.source(), original::open, () -> sized(original.metadata())));
        }

        private ResourceMetadata sized(ResourceMetadata metadata) {
            return new ResourceMetadata() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> serializer) {
                    Optional<T> section = metadata.getSection(serializer);
                    if (serializer != AnimationMetadataSection.SERIALIZER || section.isEmpty()) return section;
                    AnimationMetadataSection anim = (AnimationMetadataSection) section.get();
                    List<AnimationFrame> frames = new ArrayList<>();
                    anim.forEachFrame((index, time) -> frames.add(new AnimationFrame(index, time)));
                    return Optional.of((T) new AnimationMetadataSection(frames, frameWidth, frameHeight, anim.getDefaultFrameTime(),
                            anim.isInterpolatedFrames()));
                }
            };
        }

        @Override
        public Set<String> getNamespaces() {
            return delegate.getNamespaces();
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation location) {
            return delegate.getResourceStack(location);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
            return delegate.listResources(path, filter);
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
            return delegate.listResourceStacks(path, filter);
        }

        @Override
        public Stream<PackResources> listPacks() {
            return delegate.listPacks();
        }
    }
}
