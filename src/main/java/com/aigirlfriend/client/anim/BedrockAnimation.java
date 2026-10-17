package com.aigirlfriend.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal Bedrock (Blockbench) animation reader for the PLAYER model. Supports static values, keyframe maps with
 * linear / catmullrom / step interpolation, pre/post values, loop / hold_on_last_frame. Molang strings that are plain
 * numbers are accepted; other expressions evaluate to 0.
 * <p>
 * Values are kept in Bedrock convention (degrees and pixels, +Y up, +Z forward) and converted when applied.
 */
public final class BedrockAnimation {
    public enum LoopMode { ONCE, LOOP, HOLD }

    public final String name;
    public float length;
    public final LoopMode loop;
    public final Map<String, Bone> bones = new HashMap<>();
    /** Pivot for the "root" bone, in pixels (Bedrock space). */
    public final Vector3f rootPivot = new Vector3f();

    public BedrockAnimation(String name, float length, LoopMode loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    public static final class Keyframe {
        final float time;
        final Vector3f pre;
        final Vector3f post;
        final boolean catmull;
        final boolean step;

        Keyframe(float time, Vector3f pre, Vector3f post, boolean catmull, boolean step) {
            this.time = time;
            this.pre = pre;
            this.post = post;
            this.catmull = catmull;
            this.step = step;
        }
    }

    public static final class Channel {
        final List<Keyframe> frames = new ArrayList<>();

        public Vector3f sample(float t) {
            if (frames.isEmpty()) return new Vector3f();
            if (frames.size() == 1 || t <= frames.get(0).time) return new Vector3f(frames.get(0).post);
            Keyframe last = frames.get(frames.size() - 1);
            if (t >= last.time) return new Vector3f(last.post);
            for (int i = 0; i < frames.size() - 1; i++) {
                Keyframe a = frames.get(i), b = frames.get(i + 1);
                if (t >= a.time && t < b.time) {
                    if (a.step) return new Vector3f(a.post);
                    float f = (t - a.time) / Math.max(1e-4f, b.time - a.time);
                    if (a.catmull || b.catmull) {
                        Vector3f p0 = i > 0 ? frames.get(i - 1).post : a.post;
                        Vector3f p3 = i + 2 < frames.size() ? frames.get(i + 2).pre : b.pre;
                        return new Vector3f(catmull(p0.x, a.post.x, b.pre.x, p3.x, f), catmull(p0.y, a.post.y, b.pre.y, p3.y, f), catmull(p0.z, a.post.z, b.pre.z, p3.z, f));
                    }
                    return new Vector3f(Mth.lerp(f, a.post.x, b.pre.x), Mth.lerp(f, a.post.y, b.pre.y), Mth.lerp(f, a.post.z, b.pre.z));
                }
            }
            return new Vector3f(last.post);
        }

        private static float catmull(float p0, float p1, float p2, float p3, float t) {
            float t2 = t * t, t3 = t2 * t;
            return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
    }

    public static final class Bone {
        public Channel rotation;
        public Channel position;
    }

    /** Animation time in seconds for an elapsed time, honouring the loop mode. */
    public float timeAt(float seconds) {
        if (length <= 0) return 0;
        return switch (loop) {
            case LOOP -> seconds % length;
            case HOLD, ONCE -> Math.min(seconds, length);
        };
    }

    public static String normalizeBone(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    public static BedrockAnimation parse(String name, JsonObject json) {
        float length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : 0f;
        LoopMode loop = LoopMode.ONCE;
        if (json.has("loop")) {
            JsonElement l = json.get("loop");
            if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) loop = l.getAsBoolean() ? LoopMode.LOOP : LoopMode.ONCE;
            else if ("hold_on_last_frame".equals(l.getAsString())) loop = LoopMode.HOLD;
        }
        BedrockAnimation anim = new BedrockAnimation(name, length, loop);
        if (json.has("root_pivot")) {
            Vector3f p = vec(json.get("root_pivot"));
            anim.rootPivot.set(p);
        }
        JsonObject bones = json.has("bones") ? json.getAsJsonObject("bones") : new JsonObject();
        float maxTime = 0;
        for (Map.Entry<String, JsonElement> e : bones.entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            Bone bone = new Bone();
            if (b.has("rotation")) bone.rotation = channel(b.get("rotation"));
            if (b.has("position")) bone.position = channel(b.get("position"));
            for (Channel c : new Channel[]{bone.rotation, bone.position}) {
                if (c != null && !c.frames.isEmpty()) maxTime = Math.max(maxTime, c.frames.get(c.frames.size() - 1).time);
            }
            anim.bones.put(normalizeBone(e.getKey()), bone);
        }
        if (length <= 0 && maxTime > 0) anim.length = maxTime;
        return anim;
    }

    private static Channel channel(JsonElement el) {
        Channel c = new Channel();
        if (el.isJsonArray() || el.isJsonPrimitive()) {
            Vector3f v = vec(el);
            c.frames.add(new Keyframe(0, v, v, false, false));
            return c;
        }
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("pre") || obj.has("post") || obj.has("vector")) {
            Vector3f v = vec(obj.has("post") ? obj.get("post") : obj.has("vector") ? obj.get("vector") : obj.get("pre"));
            c.frames.add(new Keyframe(0, v, v, false, false));
            return c;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                JsonObject k = v.getAsJsonObject();
                Vector3f post = vec(k.has("post") ? k.get("post") : k.has("vector") ? k.get("vector") : k.get("pre"));
                Vector3f pre = k.has("pre") ? vec(k.get("pre")) : post;
                String mode = k.has("lerp_mode") ? k.get("lerp_mode").getAsString() : "linear";
                c.frames.add(new Keyframe(time, pre, post, "catmullrom".equals(mode), "step".equals(mode)));
            } else {
                Vector3f val = vec(v);
                c.frames.add(new Keyframe(time, val, val, false, false));
            }
        }
        c.frames.sort((a, b) -> Float.compare(a.time, b.time));
        return c;
    }

    private static Vector3f vec(JsonElement el) {
        if (el == null) return new Vector3f();
        if (el.isJsonPrimitive()) {
            float f = num(el);
            return new Vector3f(f, f, f);
        }
        JsonArray a = el.getAsJsonArray();
        return new Vector3f(a.size() > 0 ? num(a.get(0)) : 0, a.size() > 1 ? num(a.get(1)) : 0, a.size() > 2 ? num(a.get(2)) : 0);
    }

    private static float num(JsonElement e) {
        try {
            if (e.getAsJsonPrimitive().isNumber()) return e.getAsFloat();
            return Float.parseFloat(e.getAsString().trim());
        } catch (Exception ex) {
            return 0f;
        }
    }
}
