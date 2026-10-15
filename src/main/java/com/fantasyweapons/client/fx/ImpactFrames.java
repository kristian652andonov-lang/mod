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
 * Impact frames, as anime draws a blow landing: for a few frames the picture freezes and is redrawn as a stark still -
 * a negative, black ink on white paper, light on black in the weapon's colour - with speed lines bursting from where
 * the blow lands, the view punched in; then it snaps back through a split of colour while the camera springs out.
 * An ultimate gets the full sequence; any other ability a quick two-frame flash.
 * <p>
 * Drawn by the post chain {@code shaders/post/impact.json} over the finished frame (world and hand) just before the HUD.
 */
public final class ImpactFrames {
    private static final int NEGATIVE = 1, INK = 2, LIGHT = 3;

    /** One still: how long it is held (ms) and how it is drawn. */
    private record Still(int ms, int mode) {
    }

    private static final Still[] ULTIMATE = {new Still(70, NEGATIVE), new Still(75, INK), new Still(70, LIGHT), new Still(60, INK)};
    private static final Still[] ACTIVE = {new Still(55, INK), new Still(50, NEGATIVE)};
    private static final int ULTIMATE_SNAP = 320, ACTIVE_SNAP = 180;

    @Nullable
    private static PostChain chain;
    private static boolean broken;
    private static int width, height;

    private static Still[] stills;
    private static int snapMs;
    private static long start = -1;
    private static float strength, tintR, tintG, tintB, centerX, centerY, seed, punch;
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
     * @param tint     the weapon's colour (RGB)
     * @param ultimate the full sequence, or a quick flash
     * @param strength 0..1, how strongly the stills replace the picture
     * @param at       where the blow lands (its speed lines burst from there), or null for the middle of the view
     */
    public static void play(int tint, boolean ultimate, float strength, @Nullable Vec3 at) {
        if (!ClientConfig.get(ClientConfig.IMPACT_FRAMES, true)) return;
        if (!ultimate && !ClientConfig.get(ClientConfig.IMPACT_FRAMES_ALL, true)) return;
        // a quick flash never cuts short an ultimate's sequence still playing
        if (active() && stills == ULTIMATE && !ultimate) return;
        stills = ultimate ? ULTIMATE : ACTIVE;
        snapMs = ultimate ? ULTIMATE_SNAP : ACTIVE_SNAP;
        start = Util.getMillis();
        capture = true;
        ImpactFrames.strength = Math.max(0, Math.min(1, strength));
        tintR = ((tint >> 16) & 255) / 255f;
        tintG = ((tint >> 8) & 255) / 255f;
        tintB = (tint & 255) / 255f;
        float[] c = at == null ? new float[]{0.5f, 0.5f} : screen(at);
        centerX = c[0];
        centerY = c[1];
        seed = (float) (Math.random() * 100);
        punch = ultimate ? 0.12f : 0.06f;
    }

    private static boolean active() {
        return start >= 0 && Util.getMillis() - start < frozenMs() + snapMs;
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
        int mode = 0;
        float fade = 0;
        if (t < frozen) {
            long acc = 0;
            for (Still s : stills) {
                acc += s.ms();
                if (t < acc) {
                    mode = s.mode();
                    break;
                }
            }
        } else {
            fade = 1 - (t - frozen) / (float) snapMs;
        }
        // the stills are all drawn from the one frozen picture; snapping back, the picture is live again
        post.setUniform("Capture", capture || mode == 0 ? 1f : 0f);
        capture = false;
        post.setUniform("Mode", mode);
        post.setUniform("Strength", mode == 0 ? strength * fade : strength);
        post.setUniform("Fade", fade);
        post.setUniform("Lines", 1f);
        post.setUniform("TintR", tintR);
        post.setUniform("TintG", tintG);
        post.setUniform("TintB", tintB);
        post.setUniform("CenterX", centerX);
        post.setUniform("CenterY", 1 - centerY);
        post.setUniform("Seed", seed + (mode * 13.7f));
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
