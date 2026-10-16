package com.fantasyweapons.client.hud;

import com.fantasyweapons.client.vfx.Colors;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cinematic overlay of the ultimate weapons' abilities, like a broadcast from a weapon in orbit: corner brackets,
 * an uplink line naming the weapon and the spell, a status that changes as the spell unfolds, the target's
 * coordinates and distance, and letterboxed title cards ("SOMETHING IS COMING", "[ IMPACT CONFIRMED ]") with a line of
 * numbers beneath them. Only one plays at a time; a new one replaces it.
 */
public final class CinematicHud {
    /** A title card, or (when {@code status}) the line in the top right corner, from {@code at} for {@code ms}. */
    public record Cue(long at, long ms, String title, String sub, int color, boolean status) {
        public static Cue card(long at, long ms, String title, String sub, int color) {
            return new Cue(at, ms, title, sub, color, false);
        }

        public static Cue status(long at, String text, int color) {
            return new Cue(at, Long.MAX_VALUE / 4, text, "", color, true);
        }
    }

    private static String uplink = "";
    private static Vec3 target = Vec3.ZERO;
    private static int accent = 0xFFFFFF;
    private static long start = -1, total;
    private static final List<Cue> cues = new ArrayList<>();

    private CinematicHud() {
    }

    /**
     * @param uplink the top-left line, e.g. "UPLINK // DOMINION-01 > THE BEAM OF GRANT EXECUTION"
     * @param total  how long the overlay stays (ms)
     */
    public static void play(String uplink, Vec3 target, int accent, long total, List<Cue> list) {
        CinematicHud.uplink = uplink.toUpperCase(Locale.ROOT);
        CinematicHud.target = target;
        CinematicHud.accent = accent;
        CinematicHud.total = total;
        start = Util.getMillis();
        cues.clear();
        cues.addAll(list);
    }

    public static boolean active() {
        return start >= 0 && Util.getMillis() - start < total;
    }

    public static void clear() {
        start = -1;
        cues.clear();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (start < 0) return;
        long t = Util.getMillis() - start;
        if (t >= total) {
            start = -1;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float in = Math.min(1, t / 400f), out = Math.min(1, (total - t) / 600f);
        float a = in * out;
        // glitchy flicker as it comes up
        if (t < 500 && (t / 45) % 3 == 1) a *= 0.35f;
        int white = Colors.alpha(0.9f * a, 0xF4F4F4), dim = Colors.alpha(0.6f * a, 0xC8C8C8);

        // corner brackets
        float m = 12, l = 22;
        bracket(g, m, m, l, 1, 1, white);
        bracket(g, w - m, m, l, -1, 1, white);
        bracket(g, m, h - m, l, 1, -1, white);
        bracket(g, w - m, h - m, l, -1, -1, white);

        // uplink and status
        UiDraw.text(g, uplink, m + 6, m + 4, white, 0.55f, false);
        Cue status = null;
        for (Cue c : cues) if (c.status() && t >= c.at()) status = c;
        if (status != null) {
            boolean blink = (t / 350) % 2 == 0;
            UiDraw.textRight(g, (blink ? "● " : "  ") + status.title(), w - m - 6, m + 4, Colors.alpha(0.95f * a, status.color()), 0.55f, false);
        }
        // the target
        double d = mc.player.position().distanceTo(target);
        UiDraw.text(g, String.format(Locale.ROOT, "TARGET  X %d  Y %d  Z %d", (int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z)),
                m + 6, h - m - 10, dim, 0.5f, false);
        UiDraw.textRight(g, String.format(Locale.ROOT, "RANGE  %,.0f M", d), w - m - 6, h - m - 10, dim, 0.5f, false);

        // title cards
        for (Cue c : cues) {
            if (c.status() || t < c.at() || t > c.at() + c.ms()) continue;
            long k = t - c.at();
            float ca = Math.min(1, k / 120f) * Math.min(1, (c.at() + c.ms() - t) / 250f);
            if (k < 260 && (k / 40) % 2 == 1) ca *= 0.4f;
            card(g, w, h, c, ca * in, k);
        }
    }

    private static void bracket(GuiGraphics g, float x, float y, float l, int sx, int sy, int argb) {
        float t = 1;
        UiDraw.rect(g, Math.min(x, x + sx * l), Math.min(y, y + sy * t), Math.max(x, x + sx * l), Math.max(y, y + sy * t), argb);
        UiDraw.rect(g, Math.min(x, x + sx * t), Math.min(y, y + sy * l), Math.max(x, x + sx * t), Math.max(y, y + sy * l), argb);
    }

    /** A letterboxed title in spaced capitals on a black band edged in white, with its line of numbers below. */
    private static void card(GuiGraphics g, int w, int h, Cue c, float a, long k) {
        float scale = 1.6f;
        String title = c.title().toUpperCase(Locale.ROOT);
        float spacing = 2.2f;
        float tw = 0;
        for (char ch : title.toCharArray()) tw += (UiDraw.font().width(String.valueOf(ch)) + spacing) * scale;
        tw -= spacing * scale;
        float cx = w / 2f, cy = h * 0.22f;
        float bw = tw + 26, bh = 9 * scale + 10;
        float open = Math.min(1, k / 160f);
        float half = bw / 2 * (0.3f + 0.7f * open);
        UiDraw.rect(g, cx - half, cy - bh / 2, cx + half, cy + bh / 2, Colors.alpha(0.82f * a, 0x000000));
        UiDraw.border(g, cx - half, cy - bh / 2, cx + half, cy + bh / 2, 1, Colors.alpha(0.9f * a, 0xEDEDED));
        UiDraw.rect(g, cx - half + 2, cy - bh / 2 + 2, cx + half - 2, cy - bh / 2 + 2.5f, Colors.alpha(0.35f * a, 0xEDEDED));
        if (open >= 0.95f) {
            float x = cx - tw / 2;
            int col = Colors.alpha(a, c.color());
            for (char ch : title.toCharArray()) {
                String s = String.valueOf(ch);
                UiDraw.text(g, s, x, cy - 4 * scale, col, scale, true);
                x += (UiDraw.font().width(s) + spacing) * scale;
            }
        }
        if (!c.sub().isEmpty()) {
            UiDraw.textCentered(g, spaced(c.sub()), cx, cy + bh / 2 + 7, Colors.alpha(0.85f * a, 0xE8E8E8), 0.55f, false);
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toUpperCase(Locale.ROOT).toCharArray()) b.append(ch).append(ch == ' ' ? "  " : " ");
        return b.toString().trim();
    }
}
