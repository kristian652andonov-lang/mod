package com.fantasyweapons.weapon;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.BoneAnimationQueue;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

/**
 * An animation controller whose blends between animations keep the weapon in one piece.
 * <p>
 * GeckoLib blends each bone's three Euler angles separately, from where the bone is to where the next animation
 * starts. Many of the artist's animations hold every part of the weapon at {@code (-180, 0, 180)}: the same thing
 * as a half turn about the weapon's long axis, so on their own they look fine. Blended from the rest pose angle by
 * angle, though, the parts pass through {@code (-90, 0, 90)}, a quarter turn sideways - and each part turns about
 * its own pivot, so the head of a hammer and its haft swing apart and the head floats off beside the wielder for the
 * length of the blend. Every orientation has a second set of Euler angles, {@code (x + 180, 180 - y, z + 180)};
 * at the start of each blend the controller restates every bone's starting angles in whichever form lies closest
 * to where the blend is heading, so it turns the short way round about a single axis and the parts stay together.
 */
public class RigidAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
    private AnimationProcessor.QueuedAnimation restated;
    /**
     * The keyframe segment each animated bone is in this frame, captured before GeckoLib consumes it:
     * {rotation start x,y,z, rotation end x,y,z, position start x,y,z, position end x,y,z, rotation tick, rotation
     * length, position tick, position length}; a channel the animation leaves alone is NaN. Read by the weapon model
     * to keep pieces that move with the grip on it (see WeaponGeoModel#weldToGrip).
     */
    public final Map<String, double[]> segments = new java.util.HashMap<>();

    public RigidAnimationController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    @Override
    public void process(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones, Map<String, BoneSnapshot> snapshots, double seekTime,
                        boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        recordSegments();
        if (animationState != State.TRANSITIONING || currentAnimation == null) {
            restated = null;
            return;
        }
        if (currentAnimation == restated) return;
        restated = currentAnimation;
        for (BoneAnimation anim : currentAnimation.animation().boneAnimations()) {
            BoneSnapshot snap = boneSnapshots.get(anim.boneName());
            GeoBone bone = bones.get(anim.boneName());
            BoneAnimationQueue queue = boneAnimationQueues.get(anim.boneName());
            if (snap == null || bone == null || queue == null) continue;
            AnimationPoint px = queue.rotationXQueue().peekLast(), py = queue.rotationYQueue().peekLast(), pz = queue.rotationZQueue().peekLast();
            if (px == null || py == null || pz == null) continue;
            BoneSnapshot init = bone.getInitialSnapshot();
            double[] from = closestEuler(snap.getRotX() - init.getRotX(), snap.getRotY() - init.getRotY(), snap.getRotZ() - init.getRotZ(),
                    px.animationEndValue(), py.animationEndValue(), pz.animationEndValue());
            snap.updateRotation((float) from[0] + init.getRotX(), (float) from[1] + init.getRotY(), (float) from[2] + init.getRotZ());
        }
    }

    private void recordSegments() {
        segments.clear();
        for (Map.Entry<String, BoneAnimationQueue> e : boneAnimationQueues.entrySet()) {
            BoneAnimationQueue q = e.getValue();
            AnimationPoint rx = q.rotationXQueue().peekLast(), ry = q.rotationYQueue().peekLast(), rz = q.rotationZQueue().peekLast();
            AnimationPoint px = q.positionXQueue().peekLast(), py = q.positionYQueue().peekLast(), pz = q.positionZQueue().peekLast();
            double[] seg = new double[16];
            java.util.Arrays.fill(seg, Double.NaN);
            if (rx != null && ry != null && rz != null) {
                seg[0] = rx.animationStartValue(); seg[1] = ry.animationStartValue(); seg[2] = rz.animationStartValue();
                seg[3] = rx.animationEndValue(); seg[4] = ry.animationEndValue(); seg[5] = rz.animationEndValue();
                seg[12] = rx.currentTick(); seg[13] = rx.transitionLength();
            }
            if (px != null && py != null && pz != null) {
                seg[6] = px.animationStartValue(); seg[7] = py.animationStartValue(); seg[8] = pz.animationStartValue();
                seg[9] = px.animationEndValue(); seg[10] = py.animationEndValue(); seg[11] = pz.animationEndValue();
                seg[14] = px.currentTick(); seg[15] = px.transitionLength();
            }
            segments.put(e.getKey(), seg);
        }
    }

    /**
     * Euler angles (applied Z, then Y, then X, as GeckoLib does) for the same orientation as {@code (x, y, z)}, as
     * close as possible to {@code (tx, ty, tz)}: either set, each angle moved by whole turns.
     */
    public static double[] closestEuler(double x, double y, double z, double tx, double ty, double tz) {
        double[] a = {near(x, tx), near(y, ty), near(z, tz)};
        double[] b = {near(x + Math.PI, tx), near(Math.PI - y, ty), near(z + Math.PI, tz)};
        return dist(a, tx, ty, tz) <= dist(b, tx, ty, tz) ? a : b;
    }

    /** {@code a} moved by whole turns to within half a turn of {@code target}. */
    private static double near(double a, double target) {
        double twoPi = Math.PI * 2;
        return a - twoPi * Math.floor((a - target + Math.PI) / twoPi);
    }

    private static double dist(double[] v, double tx, double ty, double tz) {
        double dx = v[0] - tx, dy = v[1] - ty, dz = v[2] - tz;
        return dx * dx + dy * dy + dz * dz;
    }
}
