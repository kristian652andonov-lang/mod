package com.fantasyweapons.client.fx;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Impact frames, as anime draws a blow landing: for a few frames the picture freezes and is redrawn as a stark
 * black-and-white still - a negative, black ink on white paper, white light on black - with speed lines bursting from
 * where the blow lands, the view punched in; then it snaps back, the colour flooding back in, as the camera springs out.
 * An ultimate gets the long sequence - five stills in half a second, pushing in, each landing with a jolt and fresh,
 * flickering speed lines, the last held longest, then the snap back; any other ability a quick two-frame flash.
 * <p>
 * Drawn by the post chain {@code shaders/post/impact.json} over the finished frame (world and hand) just before the HUD.
 */
public final class ImpactFrames {
    private static final int NEGATIVE = 1, INK = 2, LIGHT = 3;

    /** One still: how long it is held (ms), how it is drawn, and whether it freezes a fresh picture of the world. */
    private record Still(int ms, int mode, boolean fresh) {
        Still(int ms, int mode) {
            this(ms, mode, false);
        }
    }

    /** About three quarters of a second in all: each still on screen long enough to read, the last held longest. */
    private static final Still[] ULTIMATE = {new Still(85, NEGATIVE), new Still(85, INK), new Still(85, LIGHT), new Still(95, NEGATIVE),
            new Still(150, INK)};
    private static final Still[] ACTIVE = {new Still(55, INK), new Still(50, NEGATIVE)};
    /**
     * The ultimate weapons' world-breaking blows: about a second and a half of stills, the picture frozen afresh every
     * few stills so the destruction is seen moving between them, pushing in harder, the last held longest.
     */
    private static final Still[] CATACLYSM = {new Still(90, NEGATIVE), new Still(80, INK), new Still(80, LIGHT), new Still(100, NEGATIVE, true),
            new Still(110, INK), new Still(90, LIGHT), new Still(120, NEGATIVE, true), new Still(130, INK), new Still(110, LIGHT, true),
            new Still(240, INK)};
    private static final int ULTIMATE_SNAP = 260, ACTIVE_SNAP = 180;
    /** How far an ultimate's stills push in over the sequence, and how often their speed lines flicker (ms). */
    private static final float ULTIMATE_ZOOM = 0.07f;
    private static final int FLICKER_MS = 70;

    @Nullable
    private static PostChain chain;
    private static boolean broken;
    private static int width, height;

    private static Still[] stills;
    private static int snapMs;
    private static long start = -1;
    private static float strength, centerX, centerY, seed, punch;
    private static int lastIndex = -1;
    /** The first frame of a sequence captures the picture the stills are drawn from. */
    private static boolean capture;

    /** DEVELOPMENT ONLY (screenshot director): pins the sequence at this many milliseconds in (null: real time). */
    public static Long debugElapsed;

    private ImpactFrames() {
    }

    private static long elapsed() {
        return debugElapsed != null ? debugElapsed : Util.getMillis() - start;
    }

    /**
     * Plays impact frames.
     *
     * @param ultimate the full sequence, or a quick flash
     * @param strength 0..1, how strongly the stills replace the picture
     * @param at       where the blow lands (its speed lines burst from there), or null for the middle of the view
     */
    public static void play(boolean ultimate, float strength, @Nullable Vec3 at) {
        if (!ClientConfig.get(ClientConfig.IMPACT_FRAMES, true)) return;
        if (!ultimate && !ClientConfig.get(ClientConfig.IMPACT_FRAMES_ALL, true)) return;
        // a quick flash never cuts short an ultimate's sequence still playing, nor anything a cataclysm's
        if (active() && (stills == ULTIMATE || stills == CATACLYSM) && !ultimate) return;
        if (active() && stills == CATACLYSM) return;
        begin(ultimate ? ULTIMATE : ACTIVE, ultimate ? ULTIMATE_SNAP : ACTIVE_SNAP, strength, at, ultimate ? 0.12f : 0.06f);
    }

    /** The long, hard sequence of the ultimate weapons' blows (cuts short anything else playing). */
    public static void cataclysm(float strength, @Nullable Vec3 at) {
        if (!ClientConfig.get(ClientConfig.IMPACT_FRAMES, true)) return;
        begin(CATACLYSM, 320, strength, at, 0.18f);
    }

    private static void begin(Still[] sequence, int snap, float strength, @Nullable Vec3 at, float punch) {
        stills = sequence;
        snapMs = snap;
        lastIndex = -1;
        start = Util.getMillis();
        capture = true;
        ImpactFrames.strength = Math.max(0, Math.min(1, strength));
        float[] c = at == null ? new float[]{0.5f, 0.5f} : screen(at);
        centerX = c[0];
        centerY = c[1];
        seed = (float) (Math.random() * 100);
        ImpactFrames.punch = punch;
    }

    private static boolean active() {
        return start >= 0 && Util.getMillis() - start < frozenMs() + snapMs;
    }

    /** Whether a still is on screen right now (screen tints stay off then, so the still is black and white). */
    public static boolean holding() {
        return start >= 0 && stills != null && !broken && elapsed() < frozenMs();
    }

    private static int frozenMs() {
        int total = 0;
        for (Still s : stills) total += s.ms();
        return total;
    }

    /** Draws the current frame of the sequence over the finished picture (before the HUD). */
    public static void render(RenderGuiEvent.Pre event) {
        if (start < 0 || broken) return;
        long t = elapsed();
        int frozen = frozenMs();
        if (t >= frozen + snapMs) {
            start = -1;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PostChain post = chain(mc);
        if (post == null) return;
        int mode = 0, index = 0;
        float fade = 0;
        if (t < frozen) {
            long acc = 0;
            for (int i = 0; i < stills.length; i++) {
                acc += stills[i].ms();
                if (t < acc) {
                    mode = stills[i].mode();
                    index = i;
                    break;
                }
            }
        } else {
            fade = 1 - (t - frozen) / (float) snapMs;
        }
        boolean longSequence = stills == ULTIMATE || stills == CATACLYSM;
        float pushIn = stills == CATACLYSM ? 0.16f : ULTIMATE_ZOOM;
        // a fresh still freezes a new picture of the world, so the destruction moves on between the stills
        if (mode != 0 && index != lastIndex && stills[index].fresh()) capture = true;
        lastIndex = index;
        // an ultimate's stills push slowly in about the burst, and each new one lands with a small jolt
        float zoom = longSequence && mode != 0 ? 1 + pushIn * (float) Math.sqrt(t / (float) frozen) : 1;
        float joltX = 0, joltY = 0;
        if (longSequence && mode != 0 && index > 0) {
            joltX = (hash(seed + index * 3.1f) - 0.5f) * 0.012f;
            joltY = (hash(seed + index * 5.7f) - 0.5f) * 0.012f;
        }
        // fresh speed lines for every still; on the long holds they flicker
        float lines = seed + index * 7.31f + (longSequence ? (t / FLICKER_MS) * 3.17f : 0);
        // the stills are all drawn from the one frozen picture; snapping back, the picture is live again
        post.setUniform("Capture", capture || mode == 0 ? 1f : 0f);
        capture = false;
        post.setUniform("Mode", mode);
        post.setUniform("Strength", mode == 0 ? strength * fade : strength);
        post.setUniform("Fade", fade);
        post.setUniform("Lines", 1f);
        post.setUniform("CenterX", centerX);
        post.setUniform("CenterY", 1 - centerY);
        post.setUniform("Seed", lines);
        post.setUniform("Zoom", zoom);
        post.setUniform("ShiftX", joltX);
        post.setUniform("ShiftY", joltY);
        try {
            post.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    /** The view is punched in on the impact and springs back out as the picture snaps back. */
    public static void fov(ViewportEvent.ComputeFov event) {
        if (start < 0 || stills == null) return;
        long t = elapsed();
        int frozen = frozenMs();
        float k;
        if (t < frozen) k = 1;
        else if (t < frozen + snapMs) {
            float u = (t - frozen) / (float) snapMs;
            k = (1 - u) * (1 - u);
        } else return;
        event.setFOV(event.getFOV() * (1 - punch * k));
    }

    private static float hash(float n) {
        double v = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    @Nullable
    private static PostChain chain(Minecraft mc) {
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), FantasyWeapons.id("shaders/post/impact.json"));
                chain.resize(w, h);
                width = w;
                height = h;
            } catch (Exception e) {
                FantasyWeapons.LOGGER.error("Impact frames could not be loaded; they are turned off", e);
                broken = true;
                return null;
            }
        } else if (w != width || h != height) {
            chain.resize(w, h);
            width = w;
            height = h;
        }
        return chain;
    }

    /** Throws the shader away after a resource reload, so it is loaded afresh. */
    public static void reset() {
        if (chain != null) chain.close();
        chain = null;
        broken = false;
        start = -1;
    }

    /** Where a point in the world falls on the screen (0..1 from the top left), or the middle if it is behind the view. */
    private static float[] screen(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 d = at.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double z = d.x * look.x() + d.y * look.y() + d.z * look.z();
        if (z < 0.1) return new float[]{0.5f, 0.5f};
        double fov = Math.toRadians(mc.options.fov().get());
        double tan = Math.tan(fov / 2);
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double x = -(d.x * left.x() + d.y * left.y() + d.z * left.z()) / z / (tan * aspect);
        double y = (d.x * up.x() + d.y * up.y() + d.z * up.z()) / z / tan;
        return new float[]{(float) Math.max(0, Math.min(1, x * 0.5 + 0.5)), (float) Math.max(0, Math.min(1, 0.5 - y * 0.5))};
    }
}
