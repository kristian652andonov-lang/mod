package com.fantasyweapons.datagen;

import com.fantasyweapons.client.render.GeoBounds;
import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Item models for items drawn by GeckoLib ({@code builtin/entity}). Each model's display transforms are fitted to the
 * real size of its geo model, so every material sits centred and the same size in the inventory, the hand and the world.
 */
public class FWItemModels extends ItemModelProvider {
    public FWItemModels(PackOutput output, ExistingFileHelper files) {
        super(output, FantasyWeapons.MOD_ID, files);
    }

    @Override
    protected void registerModels() {
        for (Material m : Material.values()) {
            // ores are held like blocks (and look like blocks in the inventory, without their floating fragments); the
            // gems a little larger and higher, like vanilla held items
            geoItem(m.oreId(), com.fantasyweapons.client.render.material.OreFit.body(m), 30, 225, 8f, 0f);
            geoItem(m.rawId(), GeoBounds.of("geo/item/" + m.rawAsset() + ".geo.json"), 20, 225, 10f, 3.5f);
            geoItem(m.refinedId(), GeoBounds.of("geo/item/" + m.refinedAsset() + ".geo.json"), 20, 225, 10f, 3.5f);
        }
        geoItem("arcane_refinery", GeoBounds.of("geo/block/arcane_refinery.geo.json"), 30, 225, 9f, 0f);
        geoItem("legendary_crafting_table", GeoBounds.of("geo/block/legendary_crafting_table.geo.json"), 30, 225, 9f, 0f);
        // armor pieces: each fitted to its own part of the set's model, shown from the front
        for (var set : com.fantasyweapons.armor.ArmorSet.values()) {
            for (var type : com.fantasyweapons.armor.ArmorSet.PIECES) {
                GeoBounds bounds = GeoBounds.of("geo/armor/" + set.asset() + ".geo.json",
                        com.fantasyweapons.armor.ArmorSet.bones(type));
                geoItem(set.itemId(type), bounds, 15, 200, 9f, 2f);
            }
        }
    }

    /**
     * How big every model is drawn in the inventory: its outline on screen, turned as it is shown there, spans this many
     * pixels - about what a vanilla block does - so all of them come out the same size, whatever their shape.
     */
    static final float GUI_SIZE = 15f;

    /**
     * @param handFit  size in pixels the model's largest side is scaled to in first person
     * @param handLift how far it is raised in first person (pixels)
     */
    void geoItem(String name, GeoBounds b, float guiPitch, float guiYaw, float handFit, float handLift) {
        ItemModelBuilder model = getBuilder(name).parent(new ModelFile.UncheckedModelFile("builtin/entity")).guiLight(BlockModel.GuiLight.SIDE);
        var t = model.transforms();
        float size = b.maxSize();
        gui(t, b, guiPitch, guiYaw);
        place(t, ItemDisplayContext.FIXED, b, 11f / size, 0, 180, 0, 0);
        place(t, ItemDisplayContext.GROUND, b, 7f / size, 0, 0, 0, 1.5f);
        place(t, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, b, 7f / size, 75, 45, 0, 2.5f);
        place(t, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, b, handFit / size, 0, 45, 0, handLift);
        place(t, ItemDisplayContext.HEAD, b, 10f / size, 0, 180, 0, 12f);
    }

    /** The inventory view: scaled so the model's outline on screen spans {@link #GUI_SIZE}, centred on the slot. */
    private static void gui(ItemModelBuilder.TransformsBuilder t, GeoBounds b, float pitch, float yaw) {
        float[] p = b.projected(pitch, yaw, 0);
        float s = GUI_SIZE / Math.max(p[0], p[1]);
        t.transform(ItemDisplayContext.GUI).rotation(pitch, yaw, 0).translation(round(-p[2] * s), round(-p[3] * s), round(-p[4] * s)).scale(round(s)).end();
    }

    /** Scales the model by {@code s}, turns it and moves its centre to the middle of the slot (plus {@code lift}). */
    private static void place(ItemModelBuilder.TransformsBuilder t, ItemDisplayContext ctx, GeoBounds b, float s, float pitch, float yaw, float roll,
                              float lift) {
        Quaternionf rot = new Quaternionf().rotationXYZ((float) Math.toRadians(pitch), (float) Math.toRadians(yaw), (float) Math.toRadians(roll));
        // GeckoLib draws the model with its origin at the transform's pivot; bring the model's centre there instead
        Vector3f c = rot.transform(new Vector3f(b.centerX(), b.centerY(), b.centerZ()).mul(s));
        t.transform(ctx).rotation(pitch, yaw, roll).translation(round(-c.x), round(-c.y + lift), round(-c.z)).scale(round(s)).end();
    }

    private static float round(float v) {
        return Math.round(v * 1000) / 1000f;
    }
}
