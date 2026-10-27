package com.fantasyweapons.client.perf;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashSet;
import java.util.Set;

/**
 * The game stitches every texture under {@code textures/block} and {@code textures/item} into its block atlas, and
 * every animated one there is redrawn every tick (a smoothly blended one is blended anew each tick, pixel by pixel). The
 * mod's models are drawn by GeckoLib from their own textures, so of the mod's well over a hundred animated textures in
 * the atlas none is drawn on a model there; the ores' are the specks that fly when one is broken. They are paused here:
 * each stays on its first frame.
 */
public final class AtlasAnimations {
    /** The mod's atlas textures some baked model uses (their animations run as ever); empty until the models are baked. */
    private static volatile Set<ResourceLocation> seen = Set.of();
    private static volatile boolean known;

    private AtlasAnimations() {
    }

    /** After the models are baked: which of the mod's textures they use. */
    public static void onModelsBaked(ModelEvent.BakingCompleted event) {
        Set<ResourceLocation> used = new HashSet<>();
        RandomSource random = RandomSource.create(0);
        boolean[] unsure = {false};
        event.getModels().forEach((id, model) -> {
            // (a model's particle texture is only the specks that fly from it when it is broken: those may stand still)
            if (!id.id().getNamespace().equals(FantasyWeapons.MOD_ID)) return;
            for (Direction side : new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                try {
                    for (BakedQuad q : model.getQuads(null, side, random, ModelData.EMPTY, null)) note(used, q.getSprite());
                } catch (RuntimeException e) {
                    // a model that cannot be asked without a block state: which textures it uses is unknown
                    unsure[0] = true;
                    return;
                }
            }
        });
        seen = Set.copyOf(used);
        // unsure what some model uses: nothing is paused
        known = !unsure[0];
        FantasyWeapons.LOGGER.debug("{} of the mod's atlas textures are used by its models", used.size());
    }

    private static void note(Set<ResourceLocation> used, TextureAtlasSprite sprite) {
        if (sprite != null && sprite.contents().name().getNamespace().equals(FantasyWeapons.MOD_ID)) used.add(sprite.contents().name());
    }

    /** Whether the sprite's animation is paused this tick: one of the mod's block-atlas textures no model uses. */
    static boolean paused(TextureAtlasSprite sprite) {
        if (!known || !Optimisations.pauseUnseenAnimations()) return false;
        ResourceLocation name = sprite.contents().name();
        return name.getNamespace().equals(FantasyWeapons.MOD_ID) && sprite.atlasLocation().equals(InventoryMenu.BLOCK_ATLAS) && !seen.contains(name);
    }

    /** A sprite's animation, which does nothing while {@link #paused}. */
    public static TextureAtlasSprite.Ticker wrap(TextureAtlasSprite sprite, TextureAtlasSprite.Ticker ticker) {
        if (!sprite.contents().name().getNamespace().equals(FantasyWeapons.MOD_ID)) return ticker;
        return new TextureAtlasSprite.Ticker() {
            @Override
            public void tickAndUpload() {
                if (!paused(sprite)) ticker.tickAndUpload();
            }

            @Override
            public void close() {
                ticker.close();
            }
        };
    }

    /** How many of the mod's atlas textures are in use by models (for tests). */
    public static int seenCount() {
        return seen.size();
    }
}
