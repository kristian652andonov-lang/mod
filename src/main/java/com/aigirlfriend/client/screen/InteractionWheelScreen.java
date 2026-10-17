package com.aigirlfriend.client.screen;

import com.aigirlfriend.client.ClientState;
import com.aigirlfriend.client.Keybinds;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.interaction.ActionType;
import com.aigirlfriend.network.StatusSnapshot;
import com.aigirlfriend.network.payload.PerformActionPayload;
import com.aigirlfriend.network.payload.StatusRequestPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Radial interaction menu: everyday commands on the inner ring, affection on the outer ring.
 * Only options the server reported as currently valid are shown. Click an option, or hold the wheel key,
 * point, and release. Does not pause the game or cover the whole screen.
 */
public class InteractionWheelScreen extends Screen {
    private static final float INNER_R0 = 30, INNER_R1 = 70, OUTER_R0 = 74, OUTER_R1 = 118;

    private final int entityId;
    private final List<ActionType> inner = new ArrayList<>();
    private final List<ActionType> outer = new ArrayList<>();
    private final long openedAt = Util.getMillis();
    private ActionType hovered;
    private boolean keyStillHeld = true;

    public InteractionWheelScreen(int entityId, int[] actions) {
        super(Component.translatable("aigirlfriend.screen.wheel"));
        this.entityId = entityId;
        for (int id : actions) {
            ActionType a = ActionType.byId(id);
            if (a == null) continue;
            (a.command ? inner : outer).add(a);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float openProgress() {
        float t = Mth.clamp((Util.getMillis() - openedAt) / 140f, 0f, 1f);
        return 1f - (1f - t) * (1f - t);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float p = openProgress();
        float cx = width / 2f, cy = height / 2f;
        hovered = pick(mouseX - cx, mouseY - cy);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(0.75f + 0.25f * p, 0.75f + 0.25f * p, 1f);
        g.pose().translate(-cx, -cy, 0);
        drawRing(g, cx, cy, inner, INNER_R0, INNER_R1, 0xD0281A2C, 0xF08FD3FF, p);
        drawRing(g, cx, cy, outer, OUTER_R0, OUTER_R1, 0xD03A1631, 0xF0FF7FB8, p);
        drawCenter(g, cx, cy, p);
        g.pose().popPose();
        if (hovered != null && GFClientConfig.TOOLTIPS.get()) {
            g.renderTooltip(font, Component.translatable(hovered.key() + ".desc"), mouseX, mouseY + 12);
        }
        String hint = Component.translatable("aigirlfriend.screen.wheel.hint").getString();
        g.drawCenteredString(font, hint, (int) cx, (int) (cy + OUTER_R1 + 10), 0xAAFFFFFF);
    }

    private void drawRing(GuiGraphics g, float cx, float cy, List<ActionType> items, float r0, float r1, int base, int highlight, float p) {
        if (items.isEmpty()) return;
        float span = Mth.TWO_PI / items.size();
        float gap = Math.min(0.04f, span * 0.1f);
        for (int i = 0; i < items.size(); i++) {
            ActionType a = items.get(i);
            float a0 = -Mth.HALF_PI + i * span + gap, a1 = -Mth.HALF_PI + (i + 1) * span - gap;
            boolean hot = a == hovered;
            float grow = hot ? 4f : 0f;
            GuiDraw.arc(g, cx, cy, r0, r1 + grow, a0, a1, hot ? highlight : GuiDraw.withAlpha(base, ((base >>> 24) / 255f) * p));
            float mid = (a0 + a1) / 2f, rm = (r0 + r1) / 2f + grow / 2f;
            int ix = (int) (cx + Mth.cos(mid) * rm) - 8, iy = (int) (cy + Mth.sin(mid) * rm) - 8;
            GuiDraw.icon(g, a.ordinal(), ix, iy);
        }
    }

    private void drawCenter(GuiGraphics g, float cx, float cy, float p) {
        int x = (int) cx, y = (int) cy;
        StatusSnapshot s = ClientState.status(entityId);
        if (hovered != null) {
            g.drawCenteredString(font, Component.translatable(hovered.key()), x, y - 4, 0xFFFFFFFF);
            return;
        }
        if (s == null) return;
        Mood mood = Mood.byId(s.mood());
        g.drawCenteredString(font, s.name(), x, y - 14, 0xFFFFC2E2);
        g.drawCenteredString(font, Component.translatable(mood.key()), x, y - 3, 0xFF000000 | mood.color);
        g.drawCenteredString(font, Component.translatable(RelationshipStage.byId(s.stage()).key()), x, y + 8, 0xFFBBBBBB);
    }

    private ActionType pick(float dx, float dy) {
        float r = Mth.sqrt(dx * dx + dy * dy);
        List<ActionType> ring;
        if (r >= INNER_R0 && r <= INNER_R1) ring = inner;
        else if (r > INNER_R1 && r <= OUTER_R1 + 6) ring = outer;
        else return null;
        if (ring.isEmpty()) return null;
        float angle = (float) Math.atan2(dy, dx) + Mth.HALF_PI;
        if (angle < 0) angle += Mth.TWO_PI;
        int idx = (int) (angle / (Mth.TWO_PI / ring.size()));
        return ring.get(Mth.clamp(idx, 0, ring.size() - 1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered != null) {
            select(hovered);
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyStillHeld && Keybinds.WHEEL.matches(keyCode, scanCode)) {
            keyStillHeld = false;
            if (Util.getMillis() - openedAt > 250 && hovered != null) {
                select(hovered);
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keybinds.WHEEL.matches(keyCode, scanCode) && !keyStillHeld) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(ActionType action) {
        switch (action) {
            case TALK -> {
                PacketDistributor.sendToServer(new PerformActionPayload(entityId, action.ordinal()));
                minecraft.setScreen(new TalkScreen(entityId));
            }
            case STATUS -> {
                PacketDistributor.sendToServer(new StatusRequestPayload(entityId));
                minecraft.setScreen(new StatusScreen(entityId));
            }
            default -> {
                PacketDistributor.sendToServer(new PerformActionPayload(entityId, action.ordinal()));
                onClose();
            }
        }
    }
}
