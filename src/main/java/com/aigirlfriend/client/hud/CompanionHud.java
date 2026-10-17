package com.aigirlfriend.client.hud;

import com.aigirlfriend.client.ClientState;
import com.aigirlfriend.client.screen.GuiDraw;
import com.aigirlfriend.companion.Activity;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.network.StatusSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Small optional status widget while your companion is nearby. Never covers the screen. */
public final class CompanionHud implements LayeredDraw.Layer {
    @Nullable
    public static GirlfriendEntity nearestOwned(double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        GirlfriendEntity best = null;
        double bestD = radius * radius;
        for (GirlfriendEntity gf : mc.level.getEntitiesOfClass(GirlfriendEntity.class, mc.player.getBoundingBox().inflate(radius))) {
            if (!gf.isOwnedBy(mc.player)) continue;
            double d = gf.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = gf;
            }
        }
        return best;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!GFClientConfig.SHOW_HUD.get() || mc.options.hideGui || mc.screen != null || mc.getDebugOverlay().showDebugScreen()) return;
        GirlfriendEntity gf = nearestOwned(32);
        if (gf == null) return;
        StatusSnapshot s = ClientState.status(gf.getId());
        Font font = mc.font;
        boolean minimal = GFClientConfig.MINIMAL_UI.get();
        Mood mood = gf.getSyncedMood();
        int w = minimal ? 90 : 112, h = minimal ? 22 : 50;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int x, y;
        switch (GFClientConfig.HUD_CORNER.get()) {
            case TOP_RIGHT -> { x = sw - w - 4; y = 4; }
            case BOTTOM_LEFT -> { x = 4; y = sh - h - 52; }
            case BOTTOM_RIGHT -> { x = sw - w - 4; y = sh - h - 52; }
            default -> { x = 4; y = 4; }
        }
        GuiDraw.panel(g, x, y, x + w, y + h, 0x90201420, 0xC0FF8FC8);
        GuiDraw.icon(g, GuiDraw.ICON_HEART, x + 3, y + 3);
        g.drawString(font, gf.getCompanionName(), x + 21, y + 4, 0xFFFFC2E2, false);
        g.drawString(font, Component.translatable(mood.key()), x + 21, y + 13, 0xFF000000 | mood.color, false);
        if (s != null && s.requestType() >= 0) GuiDraw.icon(g, GuiDraw.ICON_REQUEST, x + w - 18, y + 3);
        if (minimal || s == null) return;
        GuiDraw.bar(g, x + 6, y + 25, w - 12, 5, 1f - s.hunger() / 100f, 0xFFE8A040);
        GuiDraw.bar(g, x + 6, y + 32, w - 12, 5, s.energy() / 100f, 0xFF60C0F0);
        g.drawString(font, Component.translatable(Activity.byId(s.activity()).key()), x + 6, y + 39, 0xFFBBBBBB, false);
    }
}
