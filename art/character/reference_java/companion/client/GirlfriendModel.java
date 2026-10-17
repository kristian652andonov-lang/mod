package com.example.companion.client;

import com.example.companion.entity.GirlfriendEntity;
import com.example.companion.outfit.Outfit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Body model + the procedural rules that keep cloth and hair off the body in every animation.
 * The same rules run in "Character Viewer.html" (viewer/bedrock.js#procedural).
 */
public class GirlfriendModel extends GeoModel<GirlfriendEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Outfit.MODID, "geo/entity/girlfriend.geo.json");
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(Outfit.MODID, "textures/entity/girlfriend/skin.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(Outfit.MODID, "animations/entity/girlfriend.animation.json");

    @Override public ResourceLocation getModelResource(GirlfriendEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(GirlfriendEntity entity) { return SKIN; }
    @Override public ResourceLocation getAnimationResource(GirlfriendEntity entity) { return ANIMATIONS; }

    @Override
    public void setCustomAnimations(GirlfriendEntity entity, long instanceId, AnimationState<GirlfriendEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);

        GeoBone head = bone("head");
        if (head != null && !entity.isSleeping() && !entity.isPassenger()) {
            EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(head.getRotX() + look.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + look.netHeadYaw() * Mth.DEG_TO_RAD);
        }

        // Angles below use the Bedrock/Blockbench convention of the .animation.json (degrees, +X tips a part forward).
        float waist = pitch("waist");
        float relR = pitch("right_leg") - waist, relL = pitch("left_leg") - waist;
        setPitch("skirt_front", Math.min(0, Math.min(relR, relL)));  // hem front follows the leading thigh
        setPitch("skirt_back", Math.max(0, Math.max(relR, relL)));   // hem back follows the trailing thigh

        float lean = pitch("chest");
        setPitch("hair_front", -0.35f * Math.max(0, lean));  // strands swing clear of the waist when she leans in
        setPitch("hair_back", 0.6f * Math.max(0, -lean));    // back hair swings clear when she leans back

        GeoBone hairFront = bone("hair_front");
        if (hairFront != null) hairFront.setPosZ(entity.getOutfit().hairFrontPush);
    }

    private GeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }

    private float pitch(String name) {
        GeoBone b = bone(name);
        return b == null ? 0 : -(b.getRotX() - b.getInitialSnapshot().getRotX()) * Mth.RAD_TO_DEG;
    }

    private void setPitch(String name, float degrees) {
        GeoBone b = bone(name);
        if (b != null) b.setRotX(b.getInitialSnapshot().getRotX() - degrees * Mth.DEG_TO_RAD);
    }
}
