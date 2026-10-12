package com.fantasyweapons.client.render.material;

import com.fantasyweapons.client.render.GeoBounds;
import com.fantasyweapons.material.Material;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.EnumMap;
import java.util.Map;

/**
 * How an ore's model is fitted to its block. The artist's stone is a little larger than a block (its crystals break out of
 * every face) and some ores have fragments floating above it: the stone and its crystals are scaled to fill exactly one
 * block, like any other block, so neighbouring ores no longer overlap and the block outline shows; the fragments float
 * above.
 */
public final class OreFit {
    private static final Map<Material, OreFit> FITS = new EnumMap<>(Material.class);

    /** Scale, and the model point (in blocks, model space) brought to the block's centre. */
    public final float scale, centerX, centerY, centerZ;

    private OreFit(GeoBounds body) {
        this.scale = 16f / body.maxSize();
        this.centerX = body.centerX() / 16f;
        this.centerY = body.centerY() / 16f;
        this.centerZ = body.centerZ() / 16f;
    }

    public static OreFit of(Material m) {
        return FITS.computeIfAbsent(m, k -> new OreFit(body(k)));
    }

    /** The ore's stone and crystals, without the fragments floating above. */
    public static GeoBounds body(Material m) {
        return GeoBounds.of("geo/block/" + m.oreAsset() + ".geo.json", bone -> !isFragments(bone));
    }

    static boolean isFragments(String bone) {
        return bone.endsWith("_fragments");
    }

    /** Shows or hides the floating fragments (the model is shared, so every renderer sets this before drawing). */
    static void fragmentsHidden(BakedGeoModel model, boolean hidden) {
        for (GeoBone bone : model.topLevelBones()) fragmentsHidden(bone, hidden);
    }

    private static void fragmentsHidden(GeoBone bone, boolean hidden) {
        if (isFragments(bone.getName())) bone.setHidden(hidden);
        for (GeoBone child : bone.getChildBones()) fragmentsHidden(child, hidden);
    }
}
