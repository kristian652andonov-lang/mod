package com.fantasyweapons.client.guide;

import com.fantasyweapons.guide.WayfarersCodexItem;
import com.fantasyweapons.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The local player's Wayfarer's Codex as it is read: lifted and opened (the artist's five-second opening, cut short by
 * using it again), then held open with its pages shown on the book while {@link CodexScreen} takes the mouse and keys;
 * which spread lies open, the page turning between spreads and the way back.
 */
public final class Codex {
    /** When the pages can be read: as the opened spread lights up. */
    public static final long READ_AT_MS = 4600;
    /** How long a page takes to turn. */
    public static final long FLIP_MS = 420;

    private static long openedAt = -1, closedAt = -1;
    private static boolean reading;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static CameraType cameraBefore;

    private static int spread;
    private static final Deque<Integer> back = new ArrayDeque<>();
    private static long flipAt = -1;
    private static boolean flipForward;
    private static int flipFrom;
    private static CodexBook book;

    private Codex() {
    }

    public static int phase() {
        return reading ? WayfarersCodexItem.READING_PHASE : openedAt >= 0 ? WayfarersCodexItem.OPENING_PHASE : WayfarersCodexItem.CLOSED_PHASE;
    }

    public static boolean reading() {
        return reading;
    }

    /** The codex was used: lift and open it, or, while it is opening, go straight to reading. */
    public static void use(InteractionHand used) {
        Minecraft mc = Minecraft.getInstance();
        if (reading) return;
        if (openedAt >= 0) {
            read(mc);
            return;
        }
        hand = used;
        openedAt = Util.getMillis();
        // the book is read in the hands, from the player's own eyes
        if (!mc.options.getCameraType().isFirstPerson()) {
            cameraBefore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        play(ModSounds.CODEX_OPEN.get(), 1f);
    }

    /** Opens the screen once the opening has played (and gives up if the book is put away meanwhile). */
    public static void tick(Minecraft mc) {
        if (openedAt < 0) return;
        if (!holding(mc.player)) {
            if (reading && mc.screen instanceof CodexScreen) mc.setScreen(null);
            closed();
            return;
        }
        if (!reading && Util.getMillis() - openedAt >= READ_AT_MS) read(mc);
    }

    private static void read(Minecraft mc) {
        if (mc.screen != null && !(mc.screen instanceof CodexScreen)) return;
        reading = true;
        book = new CodexBook(mc);
        spread = Math.min(spread, book.spreads() - 1);
        mc.setScreen(new CodexScreen());
    }

    /** The reading screen closed: the book shuts. */
    public static void closed() {
        if (openedAt >= 0) {
            play(ModSounds.CODEX_CLOSE.get(), 1f);
            closedAt = Util.getMillis();
        }
        reading = false;
        openedAt = -1;
        flipAt = -1;
        if (cameraBefore != null) {
            Minecraft.getInstance().options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
    }

    /**
     * How far the book is brought up before the eyes, 0 (held at the side) to 1 (held open for reading): it comes up
     * when it is opened and goes back down once it has shut.
     */
    public static float raised() {
        long now = Util.getMillis();
        if (openedAt >= 0) return ease(Math.min(1, (now - openedAt) / 250f));
        if (closedAt >= 0) return ease(1 - Math.min(1, (now - closedAt) / 450f));
        return 0;
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    private static boolean holding(Player player) {
        return player != null && player.getItemInHand(hand).getItem() instanceof WayfarersCodexItem;
    }

    public static CodexBook book() {
        return book;
    }

    public static int spread() {
        return spread;
    }

    /** Turns to {@code target} (a spread), remembering where the reader came from when {@code remember}. */
    public static void turnTo(int target, boolean remember) {
        if (book == null) return;
        target = Math.max(0, Math.min(book.spreads() - 1, target));
        if (target == spread) return;
        if (remember) back.push(spread);
        flipFrom = spread;
        flipForward = target > spread;
        spread = target;
        flipAt = Util.getMillis();
        play(ModSounds.CODEX_PAGE.get(), 0.9f + (float) Math.random() * 0.2f);
    }

    public static void next() {
        turnTo(spread + 1, false);
    }

    public static void previous() {
        turnTo(spread - 1, false);
    }

    public static boolean canGoBack() {
        return !back.isEmpty();
    }

    /** Back to where the last link was followed from. */
    public static void goBack() {
        if (!back.isEmpty()) turnTo(back.pop(), false);
    }

    /** How far the turning page has gone (0 to 1), or -1 when no page is turning. */
    public static float flipProgress() {
        if (flipAt < 0) return -1;
        float t = (Util.getMillis() - flipAt) / (float) FLIP_MS;
        if (t >= 1) {
            flipAt = -1;
            return -1;
        }
        return t;
    }

    /** The spread a turning page is leaving. */
    public static int flippedFrom() {
        return flipFrom;
    }

    public static boolean flipForward() {
        return flipForward;
    }

    /**
     * The spread shown on one side while a page turns: the turning leaf lifts the old right page off the new one and
     * covers the old left page as it lands, so the new left page appears halfway.
     */
    public static int shownSpread(boolean left) {
        float t = flipProgress();
        if (t < 0) return spread;
        if (flipForward) return left && t < 0.5f ? flipFrom : spread;
        return !left && t < 0.5f ? flipFrom : spread;
    }

    private static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.8f));
    }
}
