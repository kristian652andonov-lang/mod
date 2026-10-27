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
 * flickering speed lines, the last held longest, then the snap back; any other ability a quick two-frame flash. The
 * ultimate weapons' powers play one far longer sequence, once, the moment each lands; for a power made of light its
 * middle stills are drawn in the power's own colours.
 * <p>
 * Drawn by the post chain {@code shaders/post/impact.json} over the finished frame (world and hand) just before the HUD.
 */
public final class ImpactFrames {
    private static final int NEGATIVE = 1, INK = 2, LIGHT = 3, TINT_INK = 4, TINT_LIGHT = 5;

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
     * The ultimate weapons' world-breaking blows, played once, the moment each lands: a little over a second of stills,
     * the picture frozen afresh every few so the destruction is seen moving between them, pushing in harder, the last
     * held longest.
     */
    private static final Still[] CATACLYSM = {new Still(80, NEGATIVE), new Still(73, INK), new Still(73, LIGHT), new Still(93, NEGATIVE, true),
            new Still(93, INK), new Still(87, LIGHT), new Still(100, NEGATIVE, true), new Still(107, INK), new Still(100, LIGHT, true),
            new Still(107, NEGATIVE), new Still(253, INK)};
    /** The same for a power made of light (a beam, a black hole's fire, the void): its middle stills in its own colours. */
    private static final Still[] CATACLYSM_TINTED = {new Still(80, NEGATIVE), new Still(73, INK), new Still(73, LIGHT),
            new Still(100, TINT_LIGHT, true), new Still(100, TINT_INK), new Still(107, TINT_LIGHT), new Still(107, TINT_INK, true),
            new Still(93, NEGATIVE), new Still(100, INK), new Still(93, LIGHT, true), new Still(253, INK)};
    /** How long a cataclysm's picture takes to snap back (ms). */
    private static final int CATACLYSM_SNAP = 280;
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
    /** The power's colour and the colour of its light, for tinted stills (0 to 1 each). */
    private static final float[] tint = {1, 1, 1}, glow = {1, 1, 1};
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
        if (active() && (stills == ULTIMATE || cataclysmic()) && !ultimate) return;
        if (active() && cataclysmic()) return;
        begin(ultimate ? ULTIMATE : ACTIVE, ultimate ? ULTIMATE_SNAP : ACTIVE_SNAP, strength, at, ultimate ? 0.12f : 0.06f);
    }

    /**
     * The long, hard sequence of the ultimate weapons' blows (cuts short anything else playing).
     *
     * @param tint the power's colour for the middle stills (0: all black and white)
     * @param light the colour of its light there
     */
    public static void cataclysm(float strength, @Nullable Vec3 at, int tint, int light) {
        if (!ClientConfig.get(ClientConfig.IMPACT_FRAMES, true)) return;
        rgb(tint == 0 ? 0xFFFFFF : tint, ImpactFrames.tint);
        rgb(light == 0 ? 0xFFFFFF : light, glow);
        begin(tint == 0 ? CATACLYSM : CATACLYSM_TINTED, CATACLYSM_SNAP, strength, at, 0.18f);
    }

    private static boolean cataclysmic() {
        return stills == CATACLYSM || stills == CATACLYSM_TINTED;
    }

    private static void rgb(int c, float[] out) {
        out[0] = (c >> 16 & 255) / 255f;
        out[1] = (c >> 8 & 255) / 255f;
        out[2] = (c & 255) / 255f;
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
        boolean longSequence = stills == ULTIMATE || cataclysmic();
        float pushIn = cataclysmic() ? 0.16f : ULTIMATE_ZOOM;
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
        post.setUniform("TintR", tint[0]);
        post.setUniform("TintG", tint[1]);
        post.setUniform("TintB", tint[2]);
        post.setUniform("GlowR", glow[0]);
        post.setUniform("GlowG", glow[1]);
        post.setUniform("GlowB", glow[2]);
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

    /**
     * Loads the shader ahead of need (in the world, before any blow lands), so the first impact frame does not stall the
     * game while it compiles.
     */
    public static void warm(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (chain == null && !broken && mc.level != null && ClientConfig.get(ClientConfig.IMPACT_FRAMES, true)) chain(mc);
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
