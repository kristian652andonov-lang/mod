package com.fantasyweapons.client.hud;

import com.fantasyweapons.armor.ArmorSet;
import com.fantasyweapons.armor.FantasyArmorItem;
import com.fantasyweapons.armor.SetAbilities;
import com.fantasyweapons.armor.SetState;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.config.ClientConfig;
import com.fantasyweapons.registry.ModAttachments;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * The full-set ability plaque (bottom left): the set's ability and what it is doing right now - souls stored, hits
 * towards the next lightning bolt, a primed rift, totality, the grove... Only shown while a whole set is worn.
 */
public final class SetHud {
    private static final int W = 132, H = 30;
    private static float alpha;
    private static ArmorSet shown;
    private static long lastFrame;

    private SetHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long nowMs = Util.getMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (nowMs - lastFrame) / 1000f);
        lastFrame = nowMs;
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !ClientConfig.get(ClientConfig.HUD_ENABLED, true)) return;
        ArmorSet set = FantasyArmorItem.fullSet(player);
        if (set != null) shown = set;
        alpha += ((set == null ? 0f : 1f) - alpha) * Math.min(1, dt * 8);
        if (alpha < 0.02f || shown == null) return;

        float scale = (float) ClientConfig.get(ClientConfig.HUD_SCALE, 1.0) * UiScale.hud();
        ClientConfig.HudAnchor anchor = ClientConfig.SPEC.isLoaded() ? ClientConfig.HUD_ANCHOR.get() : ClientConfig.HudAnchor.BOTTOM_RIGHT;
        float x0 = 8, y0 = g.guiHeight() - H * scale - 8;
        // on a narrow screen it would run into the hotbar (and the off-hand slot beside it): lift it above the hearts
        if (x0 + W * scale > g.guiWidth() / 2f - 91 - 30) y0 = g.guiHeight() - 62 - H * scale;
        // the weapon panel sits bottom left: stack the plaque above it
        if (anchor == ClientConfig.HudAnchor.BOTTOM_LEFT) y0 -= (WeaponHud.shownHeight() + 10) * scale;
        float a = alpha;
        int color = shown.material().color();
        float t = nowMs / 1000f;
        g.pose().pushPose();
        g.pose().translate(x0 - (1 - a) * 14, y0, 0);
        g.pose().scale(scale, scale, 1);
        UiDraw.theme(color, Colors.lerpRgb(color, 0xFFFFFF, 0.5f));
        try {
            UiDraw.panel(g, 0, 0, W, H, color, a);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
            UiDraw.glow(g, 14, 15, 26 + pulse * 4, Colors.alpha(a * 0.4f, color));
            UiDraw.diamond(g, 14, 15, 4.5f, Colors.alpha(a, Colors.lerpRgb(color, 0xFFFFFF, 0.35f)));
            UiDraw.medallion(g, 14, 15, 10, Colors.alpha(a, 0xFFFFFF));
            UiDraw.text(g, shown.abilityName().toUpperCase(), 28, 6, Colors.alpha(a, UiDraw.GOLD_LIGHT), 0.62f, true);
            SetState s = player.getExistingDataOrNull(ModAttachments.SET_STATE);
            renderState(g, player, s == null ? new SetState() : s, a, t, color);
        } finally {
            UiDraw.resetTheme();
        }
        g.pose().popPose();
    }

    private static void renderState(GuiGraphics g, LocalPlayer player, SetState s, float a, float t, int color) {
        long now = player.level().getGameTime();
        float x = 28, y = 17;
        int muted = Colors.alpha(a, UiDraw.INK_MUTED);
        int bright = Colors.alpha(a, Colors.lerpRgb(color, 0xFFFFFF, 0.3f));
        float flash = 0.65f + 0.35f * (float) Math.sin(t * 8);
        switch (shown) {
            case VOIDFANG -> {
                if (s.riftUntil > now) {
                    UiDraw.text(g, "Rift primed", x, y, bright, 0.55f, false);
                    UiDraw.bar(g, x + 52, y + 1, 44, 3, (s.riftUntil - now) / 100f, color, 0xFFFFFF, a, t);
                } else {
                    UiDraw.text(g, "Teleport to prime a rift", x, y, muted, 0.55f, false);
                }
            }
            case SOLARIS -> {
                float strength = player.getAttackStrengthScale(0);
                if (strength >= 0.98f) UiDraw.text(g, "Dawn ready", x, y, Colors.alpha(a * flash, UiDraw.GOLD_LIGHT), 0.55f, false);
                else {
                    UiDraw.text(g, "Charging strike", x, y, muted, 0.55f, false);
                    UiDraw.bar(g, x + 52, y + 1, 44, 3, strength, color, 0xFFFFFF, a, t);
                }
            }
            case FROSTREND -> UiDraw.text(g, "Attackers are chilled", x, y, bright, 0.55f, false);
            case DOOMCLEAVER -> {
                if (s.frenzy) UiDraw.text(g, "BLOOD FRENZY", x, y, Colors.alpha(a * flash, 0xFF4050), 0.58f, true);
                else UiDraw.text(g, "Frenzy below half health", x, y, muted, 0.55f, false);
            }
            case STORMBREAKER -> {
                UiDraw.text(g, "Storm", x, y, bright, 0.55f, false);
                pips(g, x + 26, y + 2.5f, 4, s.stormHits, color, a, s.stormHits == 3 ? flash : 1f);
            }
            case GRAVEBITE -> {
                if (s.allies > 0) {
                    UiDraw.text(g, "Allies", x, y, bright, 0.55f, false);
                    pips(g, x + 26, y + 2.5f, 3, s.allies, color, a, 1f);
                } else {
                    UiDraw.text(g, "Slain foes may rise", x, y, muted, 0.55f, false);
                }
            }
            case SOULREAPER -> {
                UiDraw.text(g, "Souls", x, y, bright, 0.55f, false);
                pips(g, x + 26, y + 2.5f, SetAbilities.MAX_SOULS, s.souls, color, a, s.souls >= SetAbilities.MAX_SOULS ? flash : 1f);
            }
            case BLOOMFALL -> {
                if (s.grove) UiDraw.text(g, "The grove takes root", x, y, Colors.alpha(a * (0.8f + 0.2f * flash), 0x9CFF8A), 0.55f, false);
                else UiDraw.text(g, "Stand still to take root", x, y, muted, 0.55f, false);
            }
            case ECLIPSE_REAPER -> {
                if (s.totalityUntil > now) {
                    UiDraw.text(g, "TOTALITY", x, y, Colors.alpha(a * flash, UiDraw.GOLD_LIGHT), 0.58f, true);
                    UiDraw.bar(g, x + 52, y + 1, 44, 3, (s.totalityUntil - now) / 80f, 0x8B3DFF, 0xFFD978, a, t);
                } else if (s.totalityReadyAt > now) {
                    UiDraw.text(g, "Recharging", x, y, muted, 0.55f, false);
                    UiDraw.bar(g, x + 52, y + 1, 44, 3, 1f - (s.totalityReadyAt - now) / 200f, 0x5A5060, 0xB0A8C0, a, -1);
                } else {
                    UiDraw.text(g, "Switch mode to eclipse", x, y, bright, 0.55f, false);
                }
            }
            case STARFORGE -> UiDraw.text(g, "Heavy attacks pull", x, y, bright, 0.55f, false);
            case AETHERLANCE -> UiDraw.text(g, "Critical hits fire a bolt", x, y, bright, 0.55f, false);
            case MONOLITH -> UiDraw.text(g, "Unmovable · heavy shockwave", x, y, bright, 0.55f, false);
            case INFERNOCHAIN -> UiDraw.text(g, "Chain attacks burn", x, y, bright, 0.55f, false);
            case PYROMANCER -> {
                UiDraw.text(g, "Flame", x, y, bright, 0.55f, false);
                pips(g, x + 26, y + 2.5f, SetAbilities.PYRE_HITS, s.pyreHits, color, a, s.pyreHits == SetAbilities.PYRE_HITS - 1 ? flash : 1f);
            }
            case CRYOMANCER -> UiDraw.text(g, "Spells freeze deep", x, y, bright, 0.55f, false);
            case TIDEMANCER -> UiDraw.text(g, "Heals send out a wave", x, y, bright, 0.55f, false);
            case ARCHDRUID -> UiDraw.text(g, "Allies heal · spells root", x, y, bright, 0.55f, false);
            case LUMINARCH -> UiDraw.text(g, "Heals call down light", x, y, bright, 0.55f, false);
            case UMBRAMANCER -> UiDraw.text(g, "Kills feed you", x, y, bright, 0.55f, false);
        }
    }

    /** {@code filled} of {@code n} small gems in a row. */
    private static void pips(GuiGraphics g, float x, float cy, int n, int filled, int color, float a, float glow) {
        for (int i = 0; i < n; i++) {
            float cx = x + i * 9;
            boolean on = i < filled;
            if (on) UiDraw.glow(g, cx, cy, 12, Colors.alpha(a * 0.45f * glow, color));
            UiDraw.diamond(g, cx, cy, 3.4f, Colors.alpha(a, 0x2A1E14));
            UiDraw.diamond(g, cx, cy, 2.6f, Colors.alpha(a * (on ? glow : 1f), on ? Colors.lerpRgb(color, 0xFFFFFF, 0.25f) : 0x4A3E32));
        }
    }
}
