package com.fantasyweapons.client.hud;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.client.vfx.VfxTextures;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.network.ProgressionEventPayload;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Animated notifications: WEAPON LEVEL UP banners, NEW ABILITY UNLOCKED cards, +EXP popups, upgrade toasts and short
 * "denied" messages. Banners queue and play one after another, sliding in and out at the top of the screen.
 */
public final class Notifications {
    private interface Banner {
        int durationMs();

        void render(GuiGraphics g, float t, float cx, float y, float alpha);
    }

    private static final Deque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner current;
    private static long currentStart;

    private record Popup(String text, int color, long start) {
    }

    private static final List<Popup> POPUPS = new ArrayList<>();
    private static long pendingExp;
    private static long pendingExpAt;
    private static String deniedText = "";
    private static long deniedAt;
    private static long lastFrame;

    private Notifications() {
    }

    public static void handle(ProgressionEventPayload p) {
        WeaponDefinition def = Weapons.get(p.weapon());
        switch (p.kind()) {
            case EXP -> {
                if (ClientConfig.get(ClientConfig.EXP_POPUPS, true)) {
                    pendingExp += p.amount();
                    if (pendingExpAt == 0) pendingExpAt = Util.getMillis();
                }
            }
            case LEVEL_UP -> {
                if (def == null) return;
                if (ClientConfig.get(ClientConfig.LEVEL_UP_NOTIFICATIONS, true)) {
                    QUEUE.add(new LevelUpBanner(def, p.oldLevel(), p.newLevel(), p.oldDamage(), p.newDamage(), p.amount()));
                }
                if (ClientConfig.get(ClientConfig.UNLOCK_NOTIFICATIONS, true)) {
                    for (String id : p.abilities()) {
                        AbilityDefinition a = def.ability(id);
                        if (a != null) QUEUE.add(new UnlockBanner(def, a));
                    }
                }
            }
            case UPGRADE -> {
                if (def == null || p.abilities().isEmpty()) return;
                AbilityDefinition a = def.ability(p.abilities().get(0));
                if (a != null) POPUPS.add(new Popup(a.name().toUpperCase() + "  LV " + p.oldLevel() + " → " + p.newLevel(),
                        def.element().light(), Util.getMillis()));
            }
            case FORM -> {
                if (def != null) POPUPS.add(new Popup(def.form(p.newLevel()).label() + ": " + p.message().toUpperCase(),
                        def.form(p.newLevel()).themePrimary(), Util.getMillis()));
            }
            case DENIED -> {
                deniedText = p.message();
                deniedAt = Util.getMillis();
                ClientState.clearPrediction(); // the server refused: drop any predicted charge at once
            }
            case POINTS -> {
            }
        }
    }

    /** The most recent refusal from the server (for diagnostics), or null. */
    public static String lastDenied() {
        return deniedText;
    }

    public static void clear() {
        QUEUE.clear();
        current = null;
        POPUPS.clear();
        pendingExp = 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long now = Util.getMillis();
        long frame = lastFrame == 0 ? 0 : now - lastFrame;
        lastFrame = now;
        if (mc.screen != null) {
            // menus cover the screen: hold the current banner (and the queue) until it closes
            if (current != null) currentStart += frame;
            return;
        }
        int sw = g.guiWidth(), sh = g.guiHeight();

        // ---- banners ----
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.poll();
            currentStart = now;
        }
        if (current != null) {
            float elapsed = now - currentStart;
            float t = elapsed / current.durationMs();
            if (t >= 1f) {
                current = null;
            } else {
                float in = Math.min(1, elapsed / 350f);
                float out = Math.min(1, (current.durationMs() - elapsed) / 450f);
                float alpha = Math.min(in, out);
                float slide = (1 - easeOut(in)) * -26 + (1 - easeOut(out)) * -12;
                current.render(g, t, sw / 2f, 26 + slide, alpha);
            }
        }

        // ---- +EXP popups (aggregate bursts of kills) ----
        if (pendingExp > 0 && now - pendingExpAt > 120) {
            POPUPS.add(new Popup("+" + String.format("%,d", pendingExp) + " EXP", 0xB9F27C, now));
            pendingExp = 0;
            pendingExpAt = 0;
        }
        float px = sw - WeaponHud.W * (float) ClientConfig.get(ClientConfig.HUD_SCALE, 1.0) / 2f - 8;
        float py = sh - WeaponHud.H * (float) ClientConfig.get(ClientConfig.HUD_SCALE, 1.0) - 22;
        POPUPS.removeIf(p -> now - p.start() > 1600);
        int i = 0;
        for (Popup p : POPUPS) {
            float t = (now - p.start()) / 1600f;
            float a = t < 0.1f ? t / 0.1f : (t > 0.7f ? 1 - (t - 0.7f) / 0.3f : 1);
            float y = py - t * 18 - i * 9;
            UiDraw.glow(g, px, y + 3, 50, Colors.alpha(a * 0.25f, p.color()));
            UiDraw.title(g, p.text(), px, y, Colors.alpha(a, p.color()), 0.75f);
            i++;
        }

        // ---- denied ----
        if (!deniedText.isEmpty()) {
            float t = (now - deniedAt) / 1500f;
            if (t < 1) {
                float a = t > 0.7f ? 1 - (t - 0.7f) / 0.3f : 1;
                float shake = t < 0.15f ? (float) Math.sin(t * 120) * 2 * (1 - t / 0.15f) : 0;
                UiDraw.title(g, deniedText.toUpperCase(), sw / 2f + shake, sh - 72, Colors.alpha(a, 0xFF5C6E), 0.8f);
            }
        }
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // ------------------------------------------------------------------------------------------------------------

    private record LevelUpBanner(WeaponDefinition def, int from, int to, float dmgFrom, float dmgTo, long points) implements Banner {
        @Override
        public int durationMs() {
            return 3600;
        }

        @Override
        public void render(GuiGraphics g, float t, float cx, float y, float alpha) {
            int theme = def.element().primary();
            int light = def.element().light();
            float w = 220, h = 66;
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 30);
            UiDraw.glow(g, cx, y + h / 2, 240, Colors.alpha(alpha * 0.15f, theme));
            UiDraw.rect(g, cx - w / 2, y, cx + w / 2, y + h, Colors.alpha(alpha * 0.6f, 0x05030A));
            UiDraw.panel(g, cx - w / 2, y, w, h, theme, alpha);
            // light sweep across the banner
            float sx = cx - w / 2 + (t * 1.6f % 1.2f - 0.1f) * w;
            UiDraw.hGradient(g, Math.max(cx - w / 2, sx - 30), y + 1, sx, y + h - 1, Colors.alpha(0, 0xFFFFFF), Colors.alpha(alpha * 0.12f, 0xFFFFFF));
            UiDraw.hGradient(g, sx, y + 1, Math.min(cx + w / 2, sx + 30), y + h - 1, Colors.alpha(alpha * 0.12f, 0xFFFFFF), Colors.alpha(0, 0xFFFFFF));
            UiDraw.title(g, "WEAPON LEVEL UP", cx, y + 6, Colors.alpha(alpha, Colors.lerpRgb(light, 0xFFFFFF, pulse * 0.5f)), 1.25f);
            UiDraw.textCentered(g, def.displayName().toUpperCase(), cx, y + 21, Colors.alpha(alpha, def.rarity().color()), 0.8f, true);
            String lv = "LEVEL " + from + "  →  LEVEL " + to;
            UiDraw.title(g, lv, cx, y + 32, Colors.alpha(alpha, 0xFFFFFF), 0.9f);
            String dmg = String.format("DAMAGE %,.0f  →  %,.0f", dmgFrom, dmgTo);
            UiDraw.textCentered(g, dmg, cx, y + 45, Colors.alpha(alpha, 0xFF8A7A), 0.7f, true);
            if (points > 0) {
                UiDraw.textCentered(g, "+" + points + " MASTERY POINT" + (points > 1 ? "S" : ""), cx, y + 55, Colors.alpha(alpha, 0xC9A2FF), 0.6f, true);
            }
        }
    }

    private record UnlockBanner(WeaponDefinition def, AbilityDefinition ability) implements Banner {
        @Override
        public int durationMs() {
            return 4800;
        }

        @Override
        public void render(GuiGraphics g, float t, float cx, float y, float alpha) {
            int theme = def.element().primary();
            int light = def.element().light();
            float w = 250, h = 78;
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 24);
            UiDraw.glow(g, cx, y + h / 2, 260, Colors.alpha(alpha * 0.15f, theme));
            UiDraw.rect(g, cx - w / 2, y, cx + w / 2, y + h, Colors.alpha(alpha * 0.6f, 0x05030A));
            UiDraw.panel(g, cx - w / 2, y, w, h, theme, alpha);
            UiDraw.glowBorder(g, cx - w / 2, y, cx + w / 2, y + h, light, alpha * (0.4f + 0.4f * pulse), 4);
            UiDraw.title(g, "NEW ABILITY UNLOCKED", cx, y + 6, Colors.alpha(alpha, Colors.lerpRgb(light, 0xFFFFFF, pulse * 0.4f)), 1.1f);
            float ix = cx - w / 2 + 12, iy = y + 22;
            UiDraw.glow(g, ix + 16, iy + 16, 60, Colors.alpha(alpha * (0.5f + 0.4f * pulse), theme));
            UiDraw.texture(g, ability.icon(), ix, iy, 32, 32, Colors.alpha(alpha, 0xFFFFFF));
            UiDraw.border(g, ix - 1, iy - 1, ix + 33, iy + 33, 1, Colors.alpha(alpha, light));
            UiDraw.additive(g, VfxTextures.STAR, ix - 6 + (float) Math.sin(t * 9) * 3, iy - 6, 14, 14, Colors.alpha(alpha * pulse, 0xFFFFFF));
            float tx = ix + 42;
            UiDraw.text(g, ability.name().toUpperCase(), tx, iy, Colors.alpha(alpha, 0xFFFFFF), 1.0f, true);
            UiDraw.wrap(g, ability.description(), tx, iy + 12, (int) (cx + w / 2 - tx - 10), Colors.alpha(alpha * 0.9f, 0xCFC8DD), 0.6f, 7);
            UiDraw.text(g, "UNLOCKED AT LEVEL " + ability.unlockLevel() + "  ·  " + ability.kind().displayName().toUpperCase(), tx, y + h - 11,
                    Colors.alpha(alpha, light), 0.58f, false);
        }
    }
}
