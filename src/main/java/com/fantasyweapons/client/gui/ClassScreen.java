package com.fantasyweapons.client.gui;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.network.QuestPayloads;
import com.fantasyweapons.playerclass.ClassService;
import com.fantasyweapons.playerclass.PlayerClass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The class menu: a card for each class (its emblem before a turning sigil, its name and calling, what it gives in green
 * and what it costs in red) with a button to take it. The class already taken is crowned; the Mage is still being
 * made and sealed with a lock. Taking a class asks once more before it is sent, and after a change the menu shows how
 * long until the next.
 */
public final class ClassScreen extends Screen {
    public static final ResourceLocation EMBLEMS = FantasyWeapons.id("textures/gui/classes.png");
    private static final ResourceLocation LOCK = FantasyWeapons.id("textures/gui/lock.png");
    private static final List<PlayerClass> CARDS = List.of(PlayerClass.ASSASSIN, PlayerClass.BERSERKER, PlayerClass.PALADIN, PlayerClass.MAGE);
    private static final int W = 416, H = 236, CARD_W = 98, CARD_H = 176, GAP = 4, CARDS_TOP = 24, EMBLEM = 48;
    private static final int GOLD = 0xF2C46B, GOLD_LIGHT = 0xFFE7A8, MUTED = 0xB9AC92, GOOD = 0x8FE08A, BAD = 0xF07A62, LINE = 0xB08A3E;
    /** How long a first click on "Choose" waits for the second, confirming one (ticks). */
    private static final int CONFIRM_TICKS = 80;

    /** Ticks until the class menu opens for a newcomer (once the world is showing), or -1. */
    private static int pendingOpen = -1;

    private int left, top;
    private PlayerClass confirming;
    private float confirmUntil;
    private float time;

    public ClassScreen() {
        super(Component.literal("Choose Your Class"));
    }

    /** Opens the menu for a player new to the world, a moment after the world appears. */
    public static void openWhenReady() {
        pendingOpen = 40;
    }

    public static void tickPending(net.minecraft.client.Minecraft mc) {
        if (pendingOpen < 0 || mc.player == null || mc.getOverlay() != null || mc.screen != null) return;
        if (--pendingOpen <= 0) {
            pendingOpen = -1;
            mc.setScreen(new ClassScreen());
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Where a class's emblem sits in the sheet. */
    public static void emblem(GuiGraphics g, PlayerClass c, float x, float y, float size, int argb) {
        int n = PlayerClass.values().length;
        UiDraw.textureUv(g, EMBLEMS, x, y, size, size, c.ordinal() / (float) n, 0, (c.ordinal() + 1) / (float) n, 1, argb);
    }

    private int cardX(int i) {
        return left + (W - (CARDS.size() * CARD_W + (CARDS.size() - 1) * GAP)) / 2 + i * (CARD_W + GAP);
    }

    private int buttonY() {
        return top + CARDS_TOP + CARD_H - 20;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        if (confirming != null && time > confirmUntil) confirming = null;
        PlayerClass current = ClassService.of(minecraft.player);
        UiDraw.style(UiDraw.NETHERITE, -1, GOLD, GOLD_LIGHT, MUTED);
        try {
            UiDraw.panel(g, left, top, W, H, current.color(), 1f);
            UiDraw.title(g, "Choose Your Class", left + W / 2f, top + 9, Colors.alpha(1f, GOLD_LIGHT), 1f);
            // the class already taken, top left
            UiDraw.text(g, "Calling:", left + 12, top + 11, Colors.alpha(1f, MUTED), 0.6f, false);
            UiDraw.text(g, current.displayName(), left + 12 + UiDraw.font().width("Calling: ") * 0.6f, top + 11, Colors.alpha(1f, current.color()), 0.6f, true);
            PlayerClass hovered = null;
            for (int i = 0; i < CARDS.size(); i++) {
                PlayerClass c = CARDS.get(i);
                int x = cardX(i), y = top + CARDS_TOP;
                boolean hover = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
                if (hover) hovered = c;
                card(g, c, x, y, hover, current, mouseX, mouseY);
            }
            footer(g, hovered != null ? hovered : current == PlayerClass.NONE ? null : current, current);
        } finally {
            UiDraw.resetTheme();
        }
    }

    private void card(GuiGraphics g, PlayerClass c, int x, int y, boolean hover, PlayerClass current, int mouseX, int mouseY) {
        boolean chosen = c == current, locked = !c.available();
        int color = c.color();
        float lift = hover && !locked ? 1.5f : 0;
        float yy0 = y - lift;
        UiDraw.rect(g, x, yy0, x + CARD_W, yy0 + CARD_H, Colors.alpha(chosen ? 0.6f : hover ? 0.5f : 0.38f, locked ? 0x0C0C10 : Colors.lerpRgb(color, 0x000000, 0.82f)));
        UiDraw.vGradient(g, x, yy0, x + CARD_W, yy0 + 70, Colors.alpha(locked ? 0.05f : chosen ? 0.32f : 0.18f, color), 0);
        if (chosen) {
            float pulse = 0.55f + 0.25f * (float) Math.sin(time * 0.15f);
            UiDraw.glowBorder(g, x, yy0, x + CARD_W, yy0 + CARD_H, color, pulse, 3);
        } else if (hover && !locked) {
            UiDraw.glowBorder(g, x, yy0, x + CARD_W, yy0 + CARD_H, color, 0.45f, 2);
        } else {
            UiDraw.border(g, x, yy0, x + CARD_W, yy0 + CARD_H, 1, Colors.alpha(locked ? 0.35f : 0.6f, LINE));
        }
        // the emblem before its sigil
        float cx = x + CARD_W / 2f, ey = yy0 + 6;
        UiDraw.sigil(g, cx, ey + EMBLEM / 2f, 27, locked ? 0x5A6070 : color, time, locked ? 0.25f : chosen ? 1f : hover ? 0.8f : 0.5f);
        emblem(g, c, cx - EMBLEM / 2f, ey, EMBLEM, locked ? Colors.alpha(1f, 0x6A6A72) : 0xFFFFFFFF);
        if (locked) UiDraw.texture(g, LOCK, cx + 8, ey + EMBLEM - 20, 16, 16, 0xFFFFFFFF);
        if (chosen) UiDraw.textCentered(g, "★ YOUR CLASS ★", cx, yy0 + 3, Colors.alpha(1f, GOLD_LIGHT), 0.5f, true);
        // name and calling
        UiDraw.title(g, c.displayName(), cx, ey + EMBLEM + 4, Colors.alpha(1f, locked ? 0x9A9AA6 : color), 0.9f);
        UiDraw.textCentered(g, c.tagline(), cx, ey + EMBLEM + 14, Colors.alpha(1f, MUTED), 0.5f, false);
        UiDraw.divider(g, cx, ey + EMBLEM + 23, CARD_W - 18, locked ? 0.4f : 0.85f);
        // what it gives and costs
        float py = ey + EMBLEM + 28;
        for (String perk : c.perks()) {
            boolean drawback = perk.startsWith("-");
            int ink = locked ? 0x8A8A94 : drawback ? BAD : GOOD;
            UiDraw.diamond(g, x + 6, py + 2.3f, 1.4f, Colors.alpha(1f, locked ? 0x6A6A72 : drawback ? 0xA0402E : color));
            int lines = UiDraw.wrap(g, perk, x + 9, py, CARD_W - 13, Colors.alpha(1f, ink), 0.5f, 5);
            py += lines * 5 + 1.5f;
        }
        // the button
        int bx = x + 12, by = buttonY(), bw = CARD_W - 24, bh = 14;
        long wait = ClassService.waitLeft(minecraft.player);
        boolean canTake = !locked && !chosen && wait <= 0;
        boolean overButton = canTake && inside(mouseX, mouseY, bx, by, bw, bh);
        boolean asking = confirming == c;
        UiDraw.button(g, bx, by, bw, bh, canTake ? overButton ? 1 : 0 : 2, canTake ? 1f : 0.55f);
        if (asking) UiDraw.glowBorder(g, bx, by, bx + bw, by + bh, color, 0.55f + 0.35f * (float) Math.sin(time * 0.5f), 3);
        String label = locked ? "In the making" : chosen ? "Chosen" : wait > 0 ? "Wait " + minutes(wait) : asking ? "Confirm?" : "Choose";
        int labelColor = asking ? Colors.lerpRgb(color, 0xFFFFFF, 0.4f) : overButton ? 0xFFFFFF : canTake ? GOLD : 0x9A927E;
        UiDraw.textCentered(g, label, bx + bw / 2f, by + 3.5f, Colors.alpha(1f, labelColor), 0.7f, true);
    }

    private void footer(GuiGraphics g, PlayerClass shown, PlayerClass current) {
        int y = top + CARDS_TOP + CARD_H + 5;
        UiDraw.rect(g, left + 10, y, left + W - 10, top + H - 8, Colors.alpha(0.35f, 0x000000));
        UiDraw.border(g, left + 9, y - 1, left + W - 9, top + H - 7, 1, Colors.alpha(0.5f, LINE));
        if (shown != null) {
            UiDraw.wrap(g, Component.literal(shown.lore()).withStyle(s -> s.withItalic(true)), left + 15, y + 4, W - 120, Colors.alpha(1f, 0xD8CCB0), 0.55f, 7);
        } else {
            UiDraw.wrap(g, Component.literal("Every wayfarer finds a calling. Choose the one that fits your blade.").withStyle(s -> s.withItalic(true)),
                    left + 15, y + 4, W - 120, Colors.alpha(1f, 0xD8CCB0), 0.55f, 7);
        }
        long wait = ClassService.waitLeft(minecraft.player);
        String status;
        int statusColor;
        if (current == PlayerClass.NONE) {
            status = "Your first choice is free";
            statusColor = GOOD;
        } else if (wait > 0) {
            status = "Change again in " + minutes(wait);
            statusColor = BAD;
        } else {
            status = "You may change your class";
            statusColor = GOOD;
        }
        UiDraw.textRight(g, status, left + W - 15, y + 4, Colors.alpha(1f, statusColor), 0.55f, true);
        String hint = current == PlayerClass.NONE
                ? "Or choose later: " + com.fantasyweapons.client.input.KeyBindings.CLASSES.getTranslatedKeyMessage().getString()
                : "Changing waits " + ClassService.CHANGE_WAIT / 20 / 60 + " min";
        UiDraw.textRight(g, hint, left + W - 15, y + 12, Colors.alpha(1f, MUTED), 0.5f, false);
    }

    private static String minutes(long ticks) {
        long secs = (ticks + 19) / 20;
        return secs >= 60 ? (secs + 59) / 60 + " min" : secs + "s";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerClass current = ClassService.of(minecraft.player);
        long wait = ClassService.waitLeft(minecraft.player);
        for (int i = 0; i < CARDS.size(); i++) {
            PlayerClass c = CARDS.get(i);
            int bx = cardX(i) + 12, by = buttonY(), bw = CARD_W - 24;
            if (!inside(mouseX, mouseY, bx, by, bw, 14)) continue;
            if (!c.available() || c == current || wait > 0) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.2f, 0.4f));
                return true;
            }
            if (confirming == c) {
                PacketDistributor.sendToServer(new QuestPayloads.ChooseClass(c.id()));
                confirming = null;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.1f, 0.8f));
            } else {
                confirming = c;
                confirmUntil = time + CONFIRM_TICKS;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f, 0.6f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
