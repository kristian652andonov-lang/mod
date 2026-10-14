package com.fantasyweapons.client.guide;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.guide.WayfarersCodexItem;
import net.minecraft.resources.ResourceLocation;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

import java.util.ArrayList;
import java.util.List;

/**
 * The Codex's model. The artist's animations give every part of the book its whole pose (the cover's keyframes repeat the
 * book's own movement, a turning leaf runs from the right page's angle to the left page's), as if all the parts hung
 * from the root; so the parts are hung from the root when the model loads, and are then animated exactly as authored.
 * A page being turned in the reader is a leaf swung about the spine from one page to the other.
 */
public final class CodexModel extends DefaultedItemGeoModel<WayfarersCodexItem> {
    static final String ROOT = "book", RIGHT_PAGE = "wayfarers_codex_codex", LEFT_PAGE = "wayfarers_codex_cover", LEAF = "wayfarers_codex_leaf_0";

    private BakedGeoModel flattened;
    /** The small stains the artist dabbed on the paper's writing area (they would show through the words), by page. */
    private final java.util.Map<GeoBone, List<software.bernie.geckolib.cache.object.GeoCube>> stains = new java.util.HashMap<>();
    private boolean stainsAway;

    public CodexModel() {
        super(FantasyWeapons.id("wayfarers_codex"));
    }

    @Override
    public BakedGeoModel getBakedModel(ResourceLocation location) {
        BakedGeoModel model = super.getBakedModel(location);
        if (model != flattened) {
            flatten(model);
            findStains(model);
            flattened = model;
        }
        return model;
    }

    /** Hangs every part under the book's root (their pivots are in model space, so nothing moves at rest). */
    private static void flatten(BakedGeoModel model) {
        GeoBone root = model.getBone(ROOT).orElse(null);
        if (root == null) return;
        List<GeoBone> parts = new ArrayList<>();
        for (GeoBone child : new ArrayList<>(root.getChildBones())) collect(child, parts);
        for (GeoBone part : parts) {
            part.getChildBones().clear();
            if (!root.getChildBones().contains(part)) root.getChildBones().add(part);
        }
    }

    /** The pages' stains: their small, thin cubes lying over the writing area (see {@link CodexPages}). */
    private void findStains(BakedGeoModel model) {
        stains.clear();
        stainsAway = false;
        for (String page : new String[]{RIGHT_PAGE, LEFT_PAGE}) {
            GeoBone bone = model.getBone(page).orElse(null);
            if (bone == null) continue;
            List<software.bernie.geckolib.cache.object.GeoCube> found = new ArrayList<>();
            for (var cube : bone.getCubes()) {
                if (cube.size().x * cube.size().y > 2 || cube.size().z > 0.3) continue;
                float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z = 0;
                for (var quad : cube.quads()) {
                    if (quad == null) continue;
                    for (var v : quad.vertices()) {
                        x0 = Math.min(x0, v.position().x);
                        x1 = Math.max(x1, v.position().x);
                        y0 = Math.min(y0, v.position().y);
                        y1 = Math.max(y1, v.position().y);
                        z = Math.max(z, Math.abs(v.position().z));
                    }
                }
                if (z < 0.13f / 16 && x1 > -12.46f / 16 && x0 < -1.75f / 16 && y1 > -7.26f / 16 && y0 < 7.24f / 16) found.add(cube);
            }
            if (!found.isEmpty()) stains.put(bone, found);
        }
    }

    /** Lays the stains aside while the book is read, and back when it is shut (the model's files are not touched). */
    private void stainsAway(boolean away) {
        if (away == stainsAway) return;
        stainsAway = away;
        stains.forEach((bone, cubes) -> {
            if (away) bone.getCubes().removeAll(cubes);
            else for (var cube : cubes) if (!bone.getCubes().contains(cube)) bone.getCubes().add(cube);
        });
    }

    private static void collect(GeoBone bone, List<GeoBone> into) {
        into.add(bone);
        for (GeoBone child : bone.getChildBones()) collect(child, into);
    }

    private software.bernie.geckolib.animation.Animation close, closeFrom;

    /** The closing is the artist's opening backwards: made here from it, so the arms take the book down the way they brought it. */
    @Override
    public software.bernie.geckolib.animation.Animation getAnimation(WayfarersCodexItem animatable, String name) {
        if (!WayfarersCodexItem.CLOSE_ANIMATION.equals(name)) return super.getAnimation(animatable, name);
        var open = super.getAnimation(animatable, "animation.wayfarers_codex.open");
        if (open == null) return null;
        if (open != closeFrom) {
            close = reversed(open, name);
            closeFrom = open;
        }
        return close;
    }

    private static software.bernie.geckolib.animation.Animation reversed(software.bernie.geckolib.animation.Animation anim, String name) {
        var bones = new software.bernie.geckolib.animation.keyframe.BoneAnimation[anim.boneAnimations().length];
        for (int i = 0; i < bones.length; i++) {
            var b = anim.boneAnimations()[i];
            bones[i] = new software.bernie.geckolib.animation.keyframe.BoneAnimation(b.boneName(), reversed(b.rotationKeyFrames(), anim.length()),
                    reversed(b.positionKeyFrames(), anim.length()), reversed(b.scaleKeyFrames(), anim.length()));
        }
        return new software.bernie.geckolib.animation.Animation(name, anim.length(), software.bernie.geckolib.animation.Animation.LoopType.HOLD_ON_LAST_FRAME,
                bones, new software.bernie.geckolib.animation.Animation.Keyframes(new software.bernie.geckolib.animation.keyframe.event.data.SoundKeyframeData[0],
                new software.bernie.geckolib.animation.keyframe.event.data.ParticleKeyframeData[0],
                new software.bernie.geckolib.animation.keyframe.event.data.CustomInstructionKeyframeData[0]));
    }

    private static software.bernie.geckolib.animation.keyframe.KeyframeStack<software.bernie.geckolib.animation.keyframe.Keyframe<software.bernie.geckolib.loading.math.MathValue>> reversed(
            software.bernie.geckolib.animation.keyframe.KeyframeStack<software.bernie.geckolib.animation.keyframe.Keyframe<software.bernie.geckolib.loading.math.MathValue>> stack, double length) {
        return new software.bernie.geckolib.animation.keyframe.KeyframeStack<>(reversed(stack.xKeyframes(), length), reversed(stack.yKeyframes(), length),
                reversed(stack.zKeyframes(), length));
    }

    /**
     * One axis' keyframes played backwards over {@code length} (in ticks): each span runs from its end to its start, in
     * reverse order, with its easing turned round; a pause before the first keyframe becomes one after the last.
     */
    private static List<software.bernie.geckolib.animation.keyframe.Keyframe<software.bernie.geckolib.loading.math.MathValue>> reversed(
            List<software.bernie.geckolib.animation.keyframe.Keyframe<software.bernie.geckolib.loading.math.MathValue>> frames, double length) {
        List<software.bernie.geckolib.animation.keyframe.Keyframe<software.bernie.geckolib.loading.math.MathValue>> out = new ArrayList<>();
        if (frames.isEmpty()) return out;
        double total = 0;
        for (var f : frames) total += f.length();
        // the opening holds its last value from `total` to the end: closing starts there
        if (length > total) out.add(new software.bernie.geckolib.animation.keyframe.Keyframe<>(length - total, frames.get(frames.size() - 1).endValue(),
                frames.get(frames.size() - 1).endValue()));
        for (int i = frames.size() - 1; i >= 0; i--) {
            var f = frames.get(i);
            out.add(new software.bernie.geckolib.animation.keyframe.Keyframe<>(f.length(), f.endValue(), f.startValue(), turned(f.easingType()), f.easingArgs()));
        }
        return out;
    }

    private static software.bernie.geckolib.animation.EasingType turned(software.bernie.geckolib.animation.EasingType easing) {
        var types = software.bernie.geckolib.animation.EasingType.EASING_TYPES;
        String name = null;
        for (var e : types.entrySet()) if (e.getValue() == easing) name = e.getKey();
        if (name == null || name.startsWith("easeinout")) return easing;
        if (name.startsWith("easein")) return types.getOrDefault("easeout" + name.substring(6), easing);
        if (name.startsWith("easeout")) return types.getOrDefault("easein" + name.substring(7), easing);
        return easing;
    }

    @Override
    public void setCustomAnimations(WayfarersCodexItem animatable, long instanceId, AnimationState<WayfarersCodexItem> state) {
        if (instanceId != CodexRenderer.FIRST_PERSON) return;
        // the drifting motes would sit over the words: they rest while the book is read
        for (int i = 0; i < 8; i++) {
            GeoBone mote = getAnimationProcessor().getBone("wayfarers_codex_mote_" + i);
            if (mote != null) mote.setHidden(Codex.reading());
        }
        // and the stains would show through the words
        stainsAway(Codex.reading());
        poseLeaf();
    }

    /**
     * Lays the turning leaf where it is in its turn: swung about the spine from the right page to the left through the
     * reader's side, curling (narrower) as it passes upright, so it never towers up before the reader's eyes. Called on
     * every frame (the animations themselves move on only once a tick).
     */
    public void poseLeaf() {
        float t = Codex.flipProgress();
        GeoBone right = getAnimationProcessor().getBone(RIGHT_PAGE), left = getAnimationProcessor().getBone(LEFT_PAGE),
                leaf = getAnimationProcessor().getBone(LEAF);
        if (right == null || left == null || leaf == null) return;
        if (t < 0) {
            // no page turning: the leaf is put away at once (the animations would only tuck it back in on the next tick,
            // leaving a blank page standing in the book meanwhile)
            if (Codex.reading()) leaf.setScaleX(0.001f);
            return;
        }
        // eased: the leaf lifts slowly, sweeps, and settles
        float e = t * t * (3 - 2 * t);
        Matrix3f from = rotation(right), to = rotation(left);
        // the turn from the right page to the left, about the spine, swung through the side the reader sees
        AxisAngle4f turn = new AxisAngle4f().set(new Quaternionf().setFromNormalized(new Matrix3f(from).transpose().mul(to)));
        Vector3f axis = new Vector3f(turn.x, turn.y, turn.z);
        Vector3f edge = new Vector3f(-7 / 16f, 0, 0).rotate(new Quaternionf().fromAxisAngleRad(axis, turn.angle / 2));
        if (edge.z > 0) {
            axis.negate();
            turn.angle = (float) (2 * Math.PI) - turn.angle;
        }
        float angle = turn.angle * (Codex.flipForward() ? e : 1 - e);
        Matrix3f pose = new Matrix3f(from).mul(new Matrix3f().rotation(angle, axis));
        Vector3f euler = pose.getEulerAnglesZYX(new Vector3f());
        leaf.setRotX(euler.x);
        leaf.setRotY(euler.y);
        leaf.setRotZ(euler.z);
        leaf.setPosX(right.getPosX());
        leaf.setPosY(right.getPosY() + 0.0688f);
        leaf.setPosZ(right.getPosZ() + 0.0217f);
        leaf.setScaleX(1 - 0.6f * (float) Math.sin(Math.PI * e));
        leaf.setScaleY(1);
        leaf.setScaleZ(1);
    }

    /** A part's rotation as GeckoLib applies it (about z, then y, then x). */
    private static Matrix3f rotation(GeoBone bone) {
        return new Matrix3f().rotateZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
    }
}
