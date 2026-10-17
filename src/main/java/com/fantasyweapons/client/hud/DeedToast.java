package com.fantasyweapons.client.hud;

import com.fantasyweapons.advancement.Deeds;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.network.DeedPayload;
import com.fantasyweapons.sound.ModSounds;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

/**
 * One of the mod's deeds, earned: in place of vanilla's advancement toast, a dark netherite plaque in a gold frame, the
 * deed's icon on a medallion haloed in its tier's colour (rays turning behind a legend's), its rank, its title in its own
 * colour and what it was for, a gleam sweeping across and sparkles glinting as it arrives, and the tier's fanfare. The title
 * and then what it was for are typed out letter by letter, a key clicking for each, behind a blinking caret.
 */
public final class DeedToast implements Toast {
    private static final int W = 192, H = 42;
    private static final net.minecraft.resources.ResourceLocation SPARKLE = com.fantasyweapons.FantasyWeapons.id("textures/gui/fantasy/sparkle.png");
    private final DeedPayload deed;
    private final AdvancementType type;
    private boolean played;
    /** How many letters have been typed so far (their clicks played). */
    private int typed;
    private long typeEnd = -1;
    /** When typing starts (ms into the toast), and how fast the title and the description are typed (letters a second). */
    private static final float TYPE_FROM = 0.35f, TITLE_RATE = 22, DESC_RATE = 42, PAUSE = 0.15f;

    /** Another heading in place of the rank's (a quest's "QUEST COMPLETE"), or null. */
    private final String headerOverride;

    public DeedToast(DeedPayload deed) {
        this(deed, null);
    }

    public DeedToast(DeedPayload deed, String header) {
        this.deed = deed;
        this.headerOverride = header;
        AdvancementType[] types = AdvancementType.values();
        this.type = types[Math.max(0, Math.min(types.length - 1, deed.tier()))];
    }

    /**
     * Sized like the rest of the HUD (see {@link UiScale#hud}), but never under one of the game's toast rows (32 GUI units),
     * which would leave gaps between the toasts stacked down the screen.
     */
    private static float scale() {
        return Math.max(UiScale.hud(), 31f / H);
    }

    @Override
    public int width() {
        return Math.round(W * scale());
    }

    @Override
    public int height() {
        return Math.round(H * scale());
    }

    private long duration() {
        // its time to be read once it is all written
        long read = switch (type) {
            case TASK -> 3500;
            case GOAL -> 4500;
            case CHALLENGE -> 6500;
        };
        return (typeEnd < 0 ? 4000 : typeEnd) + read;
    }

    private String header() {
        if (headerOverride != null) return headerOverride;
        return switch (type) {
            case TASK -> "DEED EARNED";
            case GOAL -> "FEAT ACCOMPLISHED";
            case CHALLENGE -> "LEGEND FORGED";
        };
    }

    private SoundEvent sound() {
        return switch (type) {
            case TASK -> ModSounds.DEED_TASK.get();
            case GOAL -> ModSounds.DEED_GOAL.get();
            case CHALLENGE -> ModSounds.DEED_CHALLENGE.get();
        };
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        if (!played) {
            played = true;
            toasts.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(sound(), 1f, 0.9f));
        }
        float hud = scale();
        g.pose().pushPose();
        g.pose().scale(hud, hud, 1);
        try {
            return draw(g, toasts, time);
        } finally {
            g.pose().popPose();
        }
    }

    private Visibility draw(GuiGraphics g, ToastComponent toasts, long time) {
        float t = time / 1000f;
        int color = Deeds.color(type);
        boolean legend = type == AdvancementType.CHALLENGE;

        UiDraw.style(UiDraw.NETHERITE, -1, 0xF2C46B, 0xFFE7A8, 0xB9AC92);
        try {
            UiDraw.panel(g, 2, 2, W - 4, H - 4, color, 1f);
        } finally {
            UiDraw.resetTheme();
        }
        // a flash of the tier's colour as it arrives, then a steady glow along the frame
        float flash = Math.max(0, 1 - t / 0.5f);
        UiDraw.glowBorder(g, 1, 1, W - 1, H - 1, color, 0.25f + 0.6f * flash + (legend ? 0.12f * (float) Math.sin(t * 4) : 0), 3);

        // the medallion and its halo
        float cx = 21, cy = H / 2f;
        UiDraw.glow(g, cx, cy, 44 + 6 * (float) Math.sin(t * 3), Colors.alpha(legend ? 0.7f : 0.5f, color));
        if (type != AdvancementType.TASK) {
            int rays = legend ? 12 : 8;
            for (int i = 0; i < rays; i++) {
                float a = (float) (i * Math.PI * 2 / rays + t * (legend ? 0.8f : 0.4f));
                UiDraw.arc(g, cx, cy, 15, legend ? 24 : 20, a - 0.07f, a + 0.07f, Colors.alpha(legend ? 0.35f : 0.22f, color), 2);
            }
        }
        UiDraw.medallion(g, cx, cy, 15, 0xFFFFFFFF);
        g.renderFakeItem(deed.icon(), (int) cx - 8, (int) cy - 8);

        // rank, title and what it was for
        float x = 42;
        UiDraw.text(g, Deeds.mark(type), x - 0.5f, 6, 0xFFFFFFFF, 0.85f, true);
        UiDraw.text(g, header(), x + 8.5f, 8, Colors.alpha(1f, color), 0.6f, true);
        Component title = deed.title();
        String titleText = title.getString();
        float titleScale = UiDraw.font().width(title) > (W - x - 8) / 0.9f ? 0.75f : 0.9f;
        String description = deed.description().getString();
        float descScale = 0.55f;
        int room = (int) ((W - x - 8) / descScale);
        if (UiDraw.font().width(description) > room) description = UiDraw.font().plainSubstrByWidth(description, room - UiDraw.font().width("...")) + "...";
        // typed out: the title, a breath, then what it was for
        float since = Math.max(0, t - TYPE_FROM), titleTime = titleText.length() / TITLE_RATE;
        int titleShown = Math.min(titleText.length(), (int) (since * TITLE_RATE));
        int descShown = since < titleTime + PAUSE ? 0 : Math.min(description.length(), (int) ((since - titleTime - PAUSE) * DESC_RATE));
        type(toasts, titleText, description, titleShown, descShown, time);
        UiDraw.text(g, Component.literal(titleText.substring(0, titleShown)).withStyle(title.getStyle()), x, 17, 0xFFFFFFFF, titleScale, true);
        UiDraw.text(g, description.substring(0, descShown), x, 29, Colors.alpha(1f, 0xC9BDA2), descScale, false);
        // the caret, after the letter being typed, blinking a while once all is written
        boolean writingTitle = titleShown < titleText.length() || descShown == 0;
        float caretX = writingTitle ? x + UiDraw.font().width(titleText.substring(0, titleShown)) * titleScale + 0.6f
                : x + UiDraw.font().width(description.substring(0, descShown)) * descScale + 0.5f;
        float caretY = writingTitle ? 16.5f : 28.5f, caretH = writingTitle ? 8 * titleScale : 8 * descScale;
        boolean done = descShown >= description.length();
        if (t >= TYPE_FROM && (!done || (time - typeEnd < 1600 && (time / 260) % 2 == 0))) {
            UiDraw.rect(g, caretX, caretY, caretX + (writingTitle ? 1 : 0.7f), caretY + caretH, Colors.alpha(0.9f, color));
        }

        // a gleam sweeping across, and a legend's sparks
        if (t > 0.2f && t < 1.4f) {
            float p = (t - 0.2f) / 1.2f, gx = -24 + p * (W + 48);
            UiDraw.additive(g, VfxTextures.GLOW, gx - 14, 2, 28, H - 4, Colors.alpha(0.35f * (float) Math.sin(p * Math.PI), 0xFFF4D8));
        }
        // sparkles glint across the plaque: a few as a deed arrives, more for a feat, a legend's never stop
        int sparkles = legend ? 6 : type == AdvancementType.GOAL ? 4 : 3;
        for (int i = 0; i < sparkles; i++) {
            float phase = t * (legend ? 1.1f : 1.6f) - i * 0.37f;
            if (!legend && (phase < 0 || phase > 1)) continue;
            float tw = (float) Math.pow(Math.max(0, Math.sin((phase - Math.floor(phase)) * Math.PI)), 2);
            float px = 44 + ((i * 53 + 17) % (W - 56)), py = 5 + ((i * 23 + 7) % (H - 10));
            sparkle(g, px, py, 5 + 7 * tw, t * 1.5f + i, Colors.alpha(0.9f * tw, Colors.lerpRgb(color, 0xFFFFFF, 0.55f)));
        }
        return time >= duration() ? Visibility.HIDE : Visibility.SHOW;
    }

    /** Clicks a key for each letter newly typed (not for spaces), and notes when the typing ends. */
    private void type(ToastComponent toasts, String title, String description, int titleShown, int descShown, long time) {
        int now = titleShown + descShown;
        if (now > typed) {
            int i = now - 1;
            char c = i < title.length() ? title.charAt(i) : description.charAt(Math.min(description.length() - 1, i - title.length()));
            if (c != ' ') {
                float pitch = 0.92f + (float) ((i * 7919 % 23) / 23.0 * 0.22);
                toasts.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DEED_TYPE.get(), pitch, i < title.length() ? 0.5f : 0.32f));
            }
            typed = now;
        }
        if (typeEnd < 0 && descShown >= description.length() && titleShown >= title.length()) typeEnd = time;
    }

    /** The four-rayed sparkle, turning slowly, {@code size} across. */
    private static void sparkle(GuiGraphics g, float x, float y, float size, float angle, int argb) {
        if ((argb >>> 24) < 4) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle * 0.25f));
        UiDraw.additive(g, SPARKLE, -size / 2, -size / 2, size, size, argb);
        g.pose().popPose();
    }
}
