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
 * <p>
 * Two habits of GeckoLib's controller would still tear the weapon apart, so this one changes them:
 * <ul>
 *   <li>When a triggered animation ends, the controller spends one frame stopped before the idle takes over, and in
 *   that frame GeckoLib pulls every bone towards the model's rest pose. The scythes rest half a turn from the way
 *   their animations hold them, so at the end of every cast and every catch some parts flipped round for a frame and
 *   swung back. Here the next animation takes over in the same frame, from where the weapon is.</li>
 *   <li>Between the stages of a chained animation (charge, cast, impact) GeckoLib blends from the pose saved when
 *   the chain began, not from where the weapon is, and shows the next stage's first pose for a frame before that.
 *   Here each stage blends from the pose the last one ended in.</li>
 * </ul>
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
        State before = animationState;
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        // a triggered animation has just ended: start the next one now rather than leave the bones to GeckoLib's pull
        // towards the rest pose for a frame (the second pass starts the blend from where the bones are)
        if (animationState == State.STOPPED && before != State.STOPPED && hasAnimationFinished()) {
            super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        }
        lastSeekTime = seekTime;
        if (animationState != State.TRANSITIONING || currentAnimation == null) {
            recordSegments();
            restated = null;
            return;
        }
        if (currentAnimation != restated) {
            restated = currentAnimation;
            boolean nextStage = before == State.RUNNING && startedOnKeyframes();
            for (BoneAnimation anim : currentAnimation.animation().boneAnimations()) {
                GeoBone bone = bones.get(anim.boneName());
                BoneAnimationQueue queue = boneAnimationQueues.get(anim.boneName());
                if (bone == null || queue == null) continue;
                if (nextStage && snapshots.get(anim.boneName()) != null) boneSnapshots.put(anim.boneName(), BoneSnapshot.copy(snapshots.get(anim.boneName())));
                BoneSnapshot snap = boneSnapshots.get(anim.boneName());
                AnimationPoint px = queue.rotationXQueue().peekLast(), py = queue.rotationYQueue().peekLast(), pz = queue.rotationZQueue().peekLast();
                if (snap == null || px == null || py == null || pz == null) continue;
                BoneSnapshot init = bone.getInitialSnapshot();
                // where the blend is heading: the next animation's first pose
                double[] from = closestEuler(snap.getRotX() - init.getRotX(), snap.getRotY() - init.getRotY(), snap.getRotZ() - init.getRotZ(),
                        nextStage ? valueNow(px) : px.animationEndValue(), nextStage ? valueNow(py) : py.animationEndValue(), nextStage ? valueNow(pz) : pz.animationEndValue());
                snap.updateRotation((float) from[0] + init.getRotX(), (float) from[1] + init.getRotY(), (float) from[2] + init.getRotZ());
                if (nextStage) hold(queue, snap, init);
            }
        }
        recordSegments();
    }

    /** The seek time of the last frame processed, in ticks. */
    private double lastSeekTime;

    /**
     * Whether this frame's points were queued straight from the current animation's keyframes: GeckoLib moved on to
     * the next stage of a chain and posed its first keyframe (a blend's points carry no keyframe).
     */
    private boolean startedOnKeyframes() {
        for (BoneAnimationQueue queue : boneAnimationQueues.values()) {
            AnimationPoint p = queue.rotationXQueue().peekLast();
            if (p != null) return p.keyFrame() != null;
        }
        return false;
    }

    /** The value a queued point gives this frame. */
    private static double valueNow(AnimationPoint p) {
        if (p.transitionLength() <= 0) return p.animationEndValue();
        double f = Math.min(1, Math.max(0, p.currentTick() / p.transitionLength()));
        return p.animationStartValue() + (p.animationEndValue() - p.animationStartValue()) * f;
    }

    /** Replaces this frame's points of a bone with its pose in {@code snap}: it stays where it is until the blend begins. */
    private static void hold(BoneAnimationQueue queue, BoneSnapshot snap, BoneSnapshot init) {
        if (!queue.rotationXQueue().isEmpty()) {
            queue.rotationXQueue().clear();
            queue.rotationYQueue().clear();
            queue.rotationZQueue().clear();
            double x = snap.getRotX() - init.getRotX(), y = snap.getRotY() - init.getRotY(), z = snap.getRotZ() - init.getRotZ();
            queue.addRotationXPoint(null, 0, 0, x, x);
            queue.addRotationYPoint(null, 0, 0, y, y);
            queue.addRotationZPoint(null, 0, 0, z, z);
        }
        if (!queue.positionXQueue().isEmpty()) {
            queue.positionXQueue().clear();
            queue.positionYQueue().clear();
            queue.positionZQueue().clear();
            queue.addPosXPoint(null, 0, 0, snap.getOffsetX(), snap.getOffsetX());
            queue.addPosYPoint(null, 0, 0, snap.getOffsetY(), snap.getOffsetY());
            queue.addPosZPoint(null, 0, 0, snap.getOffsetZ(), snap.getOffsetZ());
        }
        if (!queue.scaleXQueue().isEmpty()) {
            queue.scaleXQueue().clear();
            queue.scaleYQueue().clear();
            queue.scaleZQueue().clear();
            queue.addScaleXPoint(null, 0, 0, snap.getScaleX(), snap.getScaleX());
            queue.addScaleYPoint(null, 0, 0, snap.getScaleY(), snap.getScaleY());
            queue.addScaleZPoint(null, 0, 0, snap.getScaleZ(), snap.getScaleZ());
        }
    }

    /** The full name of the animation playing (or being blended into), "" if none. */
    public String currentName() {
        return currentAnimation == null ? "" : currentAnimation.animation().name();
    }

    /** How far the playing animation has got, 0 to 1 (0 while it is still being blended into). */
    public double progress() {
        if (currentAnimation == null || animationState != State.RUNNING) return 0;
        double length = currentAnimation.animation().length();
        return length <= 0 ? 1 : Math.min(1, Math.max(0, (lastSeekTime - tickOffset) * animationSpeedModifier.apply(animatable) / length));
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
