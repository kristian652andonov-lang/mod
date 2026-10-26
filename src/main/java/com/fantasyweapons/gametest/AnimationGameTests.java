package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.fantasyweapons.weapon.Weapons;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The weapons' animations as the client will play them, read from the same animation files: every animation a
 * trigger plays in every form exists, and a form with its own versions of the animations (Eclipse Reaper's dark
 * form) starts and ends each of them with every part where its idle holds it, so nothing jumps when one hands over
 * to the next.
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimationGameTests {
    private static final String ARENA = "arena";

    private AnimationGameTests() {
    }

    @GameTest(template = ARENA)
    public static void everyTriggeredAnimationExists(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        for (WeaponDefinition def : Weapons.all()) {
            Map<String, JsonObject> anims = animations(def);
            if (anims.isEmpty()) {
                missing.add(def.id() + ": no animation file");
                continue;
            }
            List<WeaponForm> forms = new ArrayList<>(def.forms());
            if (forms.isEmpty()) forms.add(null);
            for (WeaponForm form : forms) {
                List<String> names = new ArrayList<>();
                FantasyWeaponItem.triggers(def).values().forEach(names::addAll);
                names.add("idle");
                if (form != null) {
                    names.add(form.attackAnimation());
                    names.replaceAll(form::animation);
                    names.set(names.indexOf(form.animation("idle")), form.idleAnimation());
                    if (form.enterAnimation() != null) names.add(form.enterAnimation());
                }
                for (String n : names) {
                    String full = def.anim(n);
                    if (!anims.containsKey(full)) missing.add(def.id() + (form == null ? "" : "/" + form.id()) + ": " + full);
                }
            }
        }
        if (!missing.isEmpty()) h.fail(missing.size() + " animations are missing, the first: " + missing.getFirst());
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void formsHandOverWithoutJumps(GameTestHelper h) {
        List<String> jumps = new ArrayList<>();
        int checked = 0;
        for (WeaponDefinition def : Weapons.all()) {
            WeaponForm plain = def.forms().stream().filter(f -> f.animationVariant() == null).findFirst().orElse(null);
            if (plain == null) continue;
            Map<String, JsonObject> anims = animations(def);
            Map<String, float[]> pivots = pivots(def);
            String grip = grip(def);
            if (grip == null) continue;
            for (WeaponForm form : def.forms()) {
                if (form.animationVariant() == null) continue;
                JsonObject plainIdle = anims.get(def.anim(plain.idleAnimation())), formIdle = anims.get(def.anim(form.idleAnimation()));
                List<String> names = new ArrayList<>();
                FantasyWeaponItem.triggers(def).values().forEach(names::addAll);
                names.add(form.attackAnimation());
                for (String n : names.stream().distinct().toList()) {
                    JsonObject made = anims.get(def.anim(n)), own = anims.get(def.anim(form.animation(n)));
                    if (made == null || own == null) continue;
                    float length = made.get("animation_length").getAsFloat();
                    for (String bone : own.getAsJsonObject("bones").keySet()) {
                        if (!pivots.containsKey(bone)) continue;
                        for (float t : new float[]{0, length}) {
                            // where the animation as made meets its form's idle, the form's own version meets this
                            // one's: every part where it is, as GeckoLib blends them (each part on its own), and so
                            // also where it sits against the grip the hand holds still
                            Matrix4f madePose = pose(made, bone, t, pivots), plainPose = pose(plainIdle, bone, 0, pivots);
                            if (madePose == null || plainPose == null || !close(madePose, plainPose)) continue;
                            checked++;
                            Matrix4f ownPose = pose(own, bone, t, pivots), idlePose = pose(formIdle, bone, 0, pivots);
                            Matrix4f ownRel = relative(own, grip, bone, t, pivots), idleRel = relative(formIdle, grip, bone, 0, pivots);
                            if (ownPose == null || idlePose == null || ownRel == null || idleRel == null || !close(ownPose, idlePose) || !close(ownRel, idleRel)) {
                                jumps.add(def.id() + "/" + form.id() + " " + form.animation(n) + " " + bone + (t == 0 ? " at the start" : " at the end"));
                            }
                        }
                    }
                }
            }
        }
        if (checked == 0) h.fail("no form with its own animations was checked");
        if (!jumps.isEmpty()) h.fail(jumps.size() + " parts jump between a form's animations and its idle, the first: " + jumps.getFirst());
        h.succeed();
    }

    private static boolean close(Matrix4f a, Matrix4f b) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 3; r++) {
                // angles to a hundredth (about half a degree), positions to a sixth of a pixel
                if (Math.abs(a.get(c, r) - b.get(c, r)) > (c == 3 ? 0.16f : 0.01f)) return false;
            }
        }
        return true;
    }

    /** The bone's transform in the grip's frame (the hand holds the grip still, see WeaponGeoModel#anchorGrip). */
    private static Matrix4f relative(JsonObject anim, String grip, String bone, float t, Map<String, float[]> pivots) {
        Matrix4f g = pose(anim, grip, t, pivots), b = pose(anim, bone, t, pivots);
        return g == null || b == null ? null : g.invert().mul(b);
    }

    /** A bone's transform as GeckoLib poses it from the file's values: position, then rotation and scale about the pivot. */
    private static Matrix4f pose(JsonObject anim, String bone, float t, Map<String, float[]> pivots) {
        JsonObject ch = anim.getAsJsonObject("bones").getAsJsonObject(bone);
        float[] rot = value(ch == null ? null : ch.get("rotation"), t, 0), pos = value(ch == null ? null : ch.get("position"), t, 0),
                scale = value(ch == null ? null : ch.get("scale"), t, 1), p = pivots.get(bone);
        if (rot == null || pos == null || scale == null || p == null) return null;
        return new Matrix4f().translation(pos[0], pos[1], pos[2]).translate(p[0], p[1], p[2])
                .rotateZ((float) Math.toRadians(-rot[2])).rotateY((float) Math.toRadians(rot[1])).rotateX((float) Math.toRadians(-rot[0]))
                .scale(scale[0], scale[1], scale[2]).translate(-p[0], -p[1], -p[2]);
    }

    /** A channel's value at {@code t}, keyframes joined by straight lines; null if it is not plain numbers. */
    private static float[] value(JsonElement channel, float t, float none) {
        if (channel == null) return new float[]{none, none, none};
        if (channel.isJsonArray()) return vector(channel.getAsJsonArray());
        JsonObject obj = channel.getAsJsonObject();
        if (obj.has("post") || obj.has("vector")) return vector(obj.has("post") ? obj.get("post") : obj.get("vector"));
        TreeMap<Float, float[]> frames = new TreeMap<>();
        for (var e : obj.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonObject()) v = v.getAsJsonObject().has("post") ? v.getAsJsonObject().get("post") : v.getAsJsonObject().get("vector");
            float[] vec = vector(v);
            if (vec == null) return null;
            frames.put(Float.parseFloat(e.getKey()), vec);
        }
        var lo = frames.floorEntry(t);
        var hi = frames.ceilingEntry(t);
        if (lo == null) return hi.getValue();
        if (hi == null || hi.getKey().equals(lo.getKey())) return lo.getValue();
        float f = (t - lo.getKey()) / (hi.getKey() - lo.getKey());
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) out[i] = lo.getValue()[i] + (hi.getValue()[i] - lo.getValue()[i]) * f;
        return out;
    }

    private static float[] vector(JsonElement v) {
        if (v == null || !v.isJsonArray()) return null;
        JsonArray a = v.getAsJsonArray();
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            JsonElement e = a.get(Math.min(i, a.size() - 1));
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
            out[i] = e.getAsFloat();
        }
        return out;
    }

    /** All of a weapon's animations, by full name (its file and the generated extras). */
    private static Map<String, JsonObject> animations(WeaponDefinition def) {
        Map<String, JsonObject> out = new HashMap<>();
        for (String file : new String[]{def.geoName() + ".animation.json", def.geoName() + "_extra.animation.json"}) {
            JsonObject json = read("/assets/fantasyweapons/animations/item/" + file);
            if (json == null) continue;
            for (var e : json.getAsJsonObject("animations").entrySet()) out.put(e.getKey(), e.getValue().getAsJsonObject());
        }
        return out;
    }

    private static Map<String, float[]> pivots(WeaponDefinition def) {
        Map<String, float[]> out = new HashMap<>();
        JsonObject json = read("/assets/fantasyweapons/geo/item/" + def.geoName() + ".geo.json");
        if (json == null) return out;
        for (JsonElement b : json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            JsonObject bone = b.getAsJsonObject();
            float[] pivot = bone.has("pivot") ? vector(bone.get("pivot")) : new float[3];
            out.put(bone.get("name").getAsString(), pivot);
        }
        return out;
    }

    /** The bone the hand holds, found as WeaponGeoModel#findGrip finds it. */
    private static String grip(WeaponDefinition def) {
        JsonObject json = read("/assets/fantasyweapons/geo/item/" + def.geoName() + ".geo.json");
        if (json == null) return null;
        Map<String, String> parents = new HashMap<>();
        for (JsonElement b : json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            JsonObject bone = b.getAsJsonObject();
            parents.put(bone.get("name").getAsString(), bone.has("parent") ? bone.get("parent").getAsString() : null);
        }
        for (String key : new String[]{"grip", "handle", "haft", "shaft"}) {
            for (var e : parents.entrySet()) {
                String n = e.getKey(), parent = e.getValue();
                if (n.contains(key) && !n.contains("pommel") && parent != null && parents.get(parent) == null) return n;
            }
        }
        return null;
    }

    private static JsonObject read(String file) {
        try (InputStream in = AnimationGameTests.class.getResourceAsStream(file)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
