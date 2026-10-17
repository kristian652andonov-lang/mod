package com.aigirlfriend.client.render;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.entity.GirlfriendAnimations;
import com.aigirlfriend.entity.GirlfriendEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.Collection;

/**
 * Body model (skeleton + skin). Outfits are drawn on top of this skeleton by {@link OutfitLayer}.
 * <p>
 * After the animation controllers run, this applies: head tracking (added on top of the animation), eye shift,
 * the cloth/hair rules from the character pack (skirt hems follow the thighs, hair swings clear when she leans),
 * and a per-entity anti-snap filter that limits how fast any bone may jump, so controller switches and gesture
 * endings blend instead of popping.
 */
public class GirlfriendModel extends GeoModel<GirlfriendEntity> {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "geo/entity/girlfriend.geo.json");
    public static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "textures/entity/girlfriend/skin.png");
    /** Max bone speed for the anti-snap filter: degrees and pixels per tick. */
    private static final float MAX_DEG_PER_TICK = 28f;
    private static final float MAX_PX_PER_TICK = 1.6f;

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(GirlfriendEntity animatable) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(GirlfriendEntity animatable) {
        return SKIN;
    }

    @Override
    public ResourceLocation getAnimationResource(GirlfriendEntity animatable) {
        return GirlfriendAnimations.ANIMATION_FILE;
    }

    @Override
    public void setCustomAnimations(GirlfriendEntity gf, long instanceId, AnimationState<GirlfriendEntity> state) {
        GeoBone head = bone("head");
        EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        boolean free = !gf.isSleeping() && !gf.isFainted() && !gf.isPassenger();
        if (head != null && look != null && free) {
            float yaw = Mth.clamp(look.netHeadYaw(), -75f, 75f);
            float pitch = Mth.clamp(look.headPitch(), -45f, 45f);
            head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD * 0.85f);
            head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD * 0.85f);
        }

        // cloth & hair rules (Bedrock degrees, +X tips a part forward)
        float waist = pitch("waist");
        float relR = pitch("right_leg") - waist, relL = pitch("left_leg") - waist;
        setPitch("skirt_front", Math.min(0, Math.min(relR, relL)));
        setPitch("skirt_back", Math.max(0, Math.max(relR, relL)));
        float lean = pitch("chest");
        setPitch("hair_front", -0.35f * Math.max(0, lean));
        setPitch("hair_back", 0.6f * Math.max(0, -lean));
        GeoBone hairFront = bone("hair_front");
        if (hairFront != null) hairFront.setPosZ(hairFront.getPosZ() + gf.getOutfitType().hairFrontPush);

        antiSnap(gf, state.getAnimationTick());
        // Our edits are per-frame overlays. Clear the "changed" markers they set, otherwise GeckoLib treats every
        // bone as animated and never eases bones the current clips leave alone back to rest (e.g. the body would
        // stay rotated after a lying clip ends).
        for (GeoBone b : getAnimationProcessor().getRegisteredBones()) b.resetStateChanges();
    }

    private void antiSnap(GirlfriendEntity gf, double tick) {
        GirlfriendAnimations.ClientState cs = gf.animState;
        double dt = tick - cs.lastPoseTick;
        cs.lastPoseTick = tick;
        Collection<GeoBone> bones = getAnimationProcessor().getRegisteredBones();
        boolean reset = dt <= 0 || dt > 10 || cs.lastPose == null;
        if (reset) cs.lastPose = new java.util.HashMap<>();
        float maxRot = (float) (MAX_DEG_PER_TICK * Mth.DEG_TO_RAD * Math.max(dt, 0.05));
        float maxPos = (float) (MAX_PX_PER_TICK * Math.max(dt, 0.05));
        for (GeoBone b : bones) {
            float[] prev = cs.lastPose.get(b.getName());
            float[] cur = {b.getRotX(), b.getRotY(), b.getRotZ(), b.getPosX(), b.getPosY(), b.getPosZ()};
            if (prev != null && !reset) {
                for (int i = 0; i < 6; i++) {
                    float limit = i < 3 ? maxRot : maxPos;
                    cur[i] = prev[i] + Mth.clamp(cur[i] - prev[i], -limit, limit);
                }
                b.setRotX(cur[0]);
                b.setRotY(cur[1]);
                b.setRotZ(cur[2]);
                b.setPosX(cur[3]);
                b.setPosY(cur[4]);
                b.setPosZ(cur[5]);
            }
            cs.lastPose.put(b.getName(), cur);
        }
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
