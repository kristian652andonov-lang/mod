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
        codex();
        for (Material m : Material.values()) {
            // ores are held like blocks (and look like blocks in the inventory, without their floating fragments); the
            // gems a little larger and higher, like vanilla held items
            geoItem(m.oreId(), com.fantasyweapons.client.render.material.OreFit.body(m), 30, 225, 8f, 0f);
            geoItem(m.rawId(), GeoBounds.of("geo/item/" + m.rawAsset() + ".geo.json"), 20, 225, 10f, 3.5f);
            geoItem(m.refinedId(), GeoBounds.of("geo/item/" + m.refinedAsset() + ".geo.json"), 20, 225, 10f, 3.5f);
        }
        for (Material m : Material.values()) {
            for (var kind : com.fantasyweapons.tool.ToolKind.values()) {
                String name = m.toolPrefix() + "_" + kind.id();
                tool(name, GeoBounds.of("geo/item/" + name + ".geo.json"));
            }
        }
        geoItem("legendary_upgrading_table", GeoBounds.of("geo/block/legendary_upgrading_table.geo.json"), 30, 225, 9f, 0f);
        geoItem("arcane_refinery", GeoBounds.of("geo/block/arcane_refinery.geo.json"), 30, 225, 9f, 0f);
        geoItem("legendary_crafting_table", GeoBounds.of("geo/block/legendary_crafting_table.geo.json"), 30, 225, 9f, 0f);
        geoItem("worldforge", GeoBounds.of("geo/block/worldforge_core.geo.json"), 30, 225, 9f, 0f);
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
     * The Wayfarer's Codex: shut everywhere (its shut pose holds the book 7 pixels off the spine, so the bounds are moved
     * by that), held at the side in first person with its cover turned to the player; it is brought up before the eyes
     * to be read by its renderer.
     */
    void codex() {
        java.util.Set<String> shut = java.util.Set.of("book", "wayfarers_codex_codex", "wayfarers_codex_cover", "wayfarers_codex_spine",
                "wayfarers_codex_clasp", "wayfarers_codex_ribbon");
        GeoBounds b = GeoBounds.of("geo/item/wayfarers_codex.geo.json", shut::contains).shifted(7, 0, 0);
        ItemModelBuilder model = getBuilder("wayfarers_codex").parent(new ModelFile.UncheckedModelFile("builtin/entity")).guiLight(BlockModel.GuiLight.SIDE);
        var t = model.transforms();
        float size = b.maxSize();
        gui(t, b, 20, 200);
        place(t, ItemDisplayContext.FIXED, b, 11f / size, 0, 180, 0, 0);
        place(t, ItemDisplayContext.GROUND, b, 7f / size, 0, 0, 0, 1.5f);
        place(t, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, b, 7f / size, 0, 90, -25, 2.5f);
        place(t, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, b, 7.5f / size, 5, 160, 0, 2.5f);
        place(t, ItemDisplayContext.HEAD, b, 10f / size, 0, 180, 0, 12f);
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
        gui(t, b, pitch, yaw, 0, GUI_SIZE);
    }

    private static void gui(ItemModelBuilder.TransformsBuilder t, GeoBounds b, float pitch, float yaw, float roll, float size) {
        float[] p = b.projected(pitch, yaw, roll);
        float s = size / Math.max(p[0], p[1]);
        t.transform(ItemDisplayContext.GUI).rotation(pitch, yaw, roll).translation(round(-p[2] * s), round(-p[3] * s), round(-p[4] * s)).scale(round(s)).end();
    }

    /** How the legendary tools' models are scaled against a vanilla tool's sprite in the hand. */
    static final float TOOL_SCALE = 0.62f;

    /**
     * A legendary tool, held by its grip (the model's origin) the way a vanilla tool is held: its handle is laid along the
     * diagonal a vanilla tool's sprite is drawn on, with the grip where the sprite's is, and then turned and placed by
     * the vanilla handheld transforms. In the inventory and item frames it lies on that diagonal too, head up to the right,
     * a little turned so its depth shows.
     */
    void tool(String name, GeoBounds b) {
        ItemModelBuilder model = getBuilder(name).parent(new ModelFile.UncheckedModelFile("builtin/entity")).guiLight(BlockModel.GuiLight.FRONT);
        var t = model.transforms();
        float size = b.maxSize();
        gui(t, b, 0, 20, -45, 16.5f);
        held(t, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new Vector3f(0, -90, 55), new Vector3f(0, 4, 0.5f), 0.85f);
        held(t, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new Vector3f(0, -90, 25), new Vector3f(1.13f, 3.2f, 1.13f), 0.68f);
        place(t, ItemDisplayContext.FIXED, b, 13f / size, 0, 180, -45, 0);
        place(t, ItemDisplayContext.GROUND, b, 8f / size, 0, 0, 0, 1.5f);
        place(t, ItemDisplayContext.HEAD, b, 10f / size, 0, 180, 0, 12f);
    }

    /** Where a vanilla tool sprite's grip lies, from the sprite's centre (pixels). */
    private static final Vector3f SPRITE_GRIP = new Vector3f(-5.5f, -5.5f, 0);

    /** A held view: the vanilla handheld transform {@code rot}, {@code move}, {@code scale}, applied to the tool laid on the sprite's diagonal. */
    private static void held(ItemModelBuilder.TransformsBuilder t, ItemDisplayContext ctx, Vector3f rot, Vector3f move, float scale) {
        Quaternionf vanilla = new Quaternionf().rotationXYZ((float) Math.toRadians(rot.x), (float) Math.toRadians(rot.y), (float) Math.toRadians(rot.z));
        Vector3f grip = vanilla.transform(new Vector3f(SPRITE_GRIP)).mul(scale).add(move);
        // the transform turns X, then Y, then Z, applied innermost last: laying the tool on the diagonal first is one more
        // turn about Z
        t.transform(ctx).rotation(rot.x, rot.y, rot.z - 45).translation(round(grip.x), round(grip.y), round(grip.z)).scale(round(scale * TOOL_SCALE)).end();
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
