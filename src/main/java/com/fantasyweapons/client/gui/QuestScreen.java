package com.fantasyweapons.client.gui;

import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.network.QuestPayloads;
import com.fantasyweapons.quest.Chapter;
import com.fantasyweapons.quest.Quest;
import com.fantasyweapons.quest.QuestObjective;
import com.fantasyweapons.quest.QuestService;
import com.fantasyweapons.quest.Quests;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Quest Journal: the chapters down the left (each with how many of its quests are claimed), the chapter's quests in
 * the middle (locked, under way, done and waiting to be claimed, or claimed), and the chosen quest on the right: what it
 * asks with how far along each part is, what it gives, and the button to claim it.
 */
public final class QuestScreen extends Screen {
    private static final int W = 416, H = 236;
    private static final int CHAPTERS_X = 10, CHAPTERS_W = 100, LIST_X = 116, LIST_W = 140, DETAIL_X = 262, DETAIL_W = 144, TOP = 30, ROW = 20;
    private static final int GOLD = 0xF2C46B, GOLD_LIGHT = 0xFFE7A8, MUTED = 0xB9AC92, GOOD = 0x7DE08A, LINE = 0xB08A3E;

    private Chapter chapter = Chapter.FIRST_STEPS;
    private Quest selected;
    private float scroll;
    private int left, top;

    public QuestScreen() {
        super(Component.literal("Quest Journal"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (selected == null) selectFirst();
    }

    private void selectFirst() {
        List<Quest> quests = Quests.of(chapter);
        selected = null;
        // the first quest waiting to be claimed, else the first still to do, else the first
        for (Quest q : quests) if (state(q) == State.DONE) {
            selected = q;
            return;
        }
        for (Quest q : quests) if (state(q) == State.OPEN) {
            selected = q;
            return;
        }
        selected = quests.isEmpty() ? null : quests.get(0);
    }

    private enum State { LOCKED, OPEN, DONE, CLAIMED }

    private State state(Quest q) {
        var player = minecraft.player;
        if (QuestService.data(player).claimed(q.id())) return State.CLAIMED;
        if (!QuestService.unlocked(player, q)) return State.LOCKED;
        return QuestService.complete(player, q) ? State.DONE : State.OPEN;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        UiDraw.style(UiDraw.NETHERITE, -1, GOLD, GOLD_LIGHT, MUTED);
        try {
            UiDraw.panel(g, left, top, W, H, chapter.color(), 1f);
            UiDraw.title(g, "Quest Journal", left + W / 2f, top + 9, Colors.alpha(1f, GOLD_LIGHT), 1f);
            int claimed = 0, all = Quests.all().size();
            for (Quest q : Quests.all()) if (QuestService.data(minecraft.player).claimed(q.id())) claimed++;
            String total = claimed + " / " + all + " claimed";
            UiDraw.textRight(g, total, left + W - 12, top + 11, Colors.alpha(1f, MUTED), 0.6f, false);
            chapters(g, mouseX, mouseY, time);
            questList(g, mouseX, mouseY, time);
            details(g, mouseX, mouseY, time);
        } finally {
            UiDraw.resetTheme();
        }
    }

    // ---- the chapters ----

    private void chapters(GuiGraphics g, int mouseX, int mouseY, float time) {
        int x = left + CHAPTERS_X, y = top + TOP;
        for (Chapter c : Chapter.values()) {
            List<Quest> quests = Quests.of(c);
            int done = 0, waiting = 0;
            for (Quest q : quests) {
                State s = state(q);
                if (s == State.CLAIMED) done++;
                else if (s == State.DONE) waiting++;
            }
            boolean on = c == chapter, hover = inside(mouseX, mouseY, x, y, CHAPTERS_W, 22);
            UiDraw.rect(g, x, y, x + CHAPTERS_W, y + 22, Colors.alpha(on ? 0.55f : hover ? 0.35f : 0.22f, on ? darker(c.color()) : 0x000000));
            if (on) UiDraw.glowBorder(g, x, y, x + CHAPTERS_W, y + 22, c.color(), 0.6f, 3);
            else UiDraw.border(g, x, y, x + CHAPTERS_W, y + 22, 1, Colors.alpha(0.5f, LINE));
            if (!quests.isEmpty()) g.renderFakeItem(quests.get(Math.min(quests.size() - 1, 0)).icon(), x + 3, y + 3);
            UiDraw.text(g, c.title(), x + 23, y + 4, Colors.alpha(1f, on ? 0xFFFFFF : c.color()), 0.72f, true);
            UiDraw.text(g, done + "/" + quests.size(), x + 23, y + 13, Colors.alpha(1f, MUTED), 0.55f, false);
            if (waiting > 0) {
                // quests done and waiting to be claimed: a pulsing mark
                float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.25f);
                UiDraw.glow(g, x + CHAPTERS_W - 9, y + 11, 14, Colors.alpha(0.6f * pulse, GOLD));
                UiDraw.textCentered(g, String.valueOf(waiting), x + CHAPTERS_W - 9, y + 8, Colors.alpha(1f, GOLD_LIGHT), 0.65f, true);
            }
            y += 25;
        }
    }

    // ---- the quests of the chapter ----

    private int listRows() {
        return (H - TOP - 12) / ROW;
    }

    private void questList(GuiGraphics g, int mouseX, int mouseY, float time) {
        List<Quest> quests = Quests.of(chapter);
        int x = left + LIST_X, y0 = top + TOP, rows = listRows();
        UiDraw.rect(g, x - 2, y0 - 2, x + LIST_W + 2, y0 + rows * ROW + 2, Colors.alpha(0.35f, 0x000000));
        UiDraw.border(g, x - 3, y0 - 3, x + LIST_W + 3, y0 + rows * ROW + 3, 1, Colors.alpha(0.6f, LINE));
        int first = (int) scroll;
        g.enableScissor(x - 2, y0 - 2, x + LIST_W + 2, y0 + rows * ROW + 2);
        for (int i = first; i < Math.min(quests.size(), first + rows + 1); i++) {
            Quest q = quests.get(i);
            int y = y0 + (i - first) * ROW;
            State s = state(q);
            boolean on = q == selected, hover = inside(mouseX, mouseY, x, y, LIST_W, ROW - 1);
            UiDraw.rect(g, x, y, x + LIST_W, y + ROW - 1, Colors.alpha(on ? 0.5f : hover ? 0.3f : 0.15f, on ? darker(chapter.color()) : 0x000000));
            if (s == State.DONE) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.25f + i);
                UiDraw.glowBorder(g, x, y, x + LIST_W, y + ROW - 1, GOLD, 0.35f + 0.35f * pulse, 2);
            }
            ItemStack icon = q.icon();
            g.renderFakeItem(icon, x + 2, y + 1);
            if (s == State.LOCKED) UiDraw.rect(g, x + 2, y + 1, x + 18, y + 17, Colors.alpha(0.6f, 0x101010));
            int color = switch (s) {
                case LOCKED -> 0x7A7368;
                case OPEN -> 0xE8DCC0;
                case DONE -> GOLD_LIGHT;
                case CLAIMED -> GOOD;
            };
            String title = q.title();
            float scale = 0.62f;
            while (UiDraw.font().width(title) * scale > LIST_W - 38 && title.length() > 4) title = title.substring(0, title.length() - 2);
            if (!title.equals(q.title())) title = title.trim() + "...";
            UiDraw.text(g, title, x + 21, y + 3, Colors.alpha(1f, color), scale, true);
            String status = switch (s) {
                case LOCKED -> "Locked";
                case OPEN -> progressText(q);
                case DONE -> "Done! Claim it";
                case CLAIMED -> "Claimed";
            };
            UiDraw.text(g, status, x + 21, y + 11, Colors.alpha(1f, s == State.DONE ? GOLD : MUTED), 0.52f, false);
            if (s == State.CLAIMED) UiDraw.text(g, "✔", x + LIST_W - 9, y + 5, Colors.alpha(1f, GOOD), 0.8f, true);
        }
        g.disableScissor();
        // the scrollbar
        if (quests.size() > rows) {
            float frac = rows / (float) quests.size(), pos = scroll / Math.max(1, quests.size() - rows);
            int bx = x + LIST_W + 5, bh = rows * ROW;
            UiDraw.rect(g, bx, y0, bx + 2, y0 + bh, Colors.alpha(0.4f, 0x000000));
            float th = Math.max(10, bh * frac), ty = y0 + (bh - th) * pos;
            UiDraw.rect(g, bx, ty, bx + 2, ty + th, Colors.alpha(0.9f, GOLD));
        }
    }

    private String progressText(Quest q) {
        int have = 0, need = 0;
        for (int i = 0; i < q.objectives().size(); i++) {
            have += QuestService.progress(minecraft.player, q, i);
            need += q.objectives().get(i).target();
        }
        return have + " / " + need;
    }

    // ---- the chosen quest ----

    private void details(GuiGraphics g, int mouseX, int mouseY, float time) {
        int x = left + DETAIL_X, y = top + TOP;
        UiDraw.rect(g, x - 2, y - 2, x + DETAIL_W + 2, top + H - 10, Colors.alpha(0.35f, 0x000000));
        UiDraw.border(g, x - 3, y - 3, x + DETAIL_W + 3, top + H - 9, 1, Colors.alpha(0.6f, LINE));
        if (selected == null) return;
        Quest q = selected;
        State s = state(q);
        UiDraw.sigil(g, x + 13, y + 13, 13, chapter.color(), time, s == State.DONE ? 1f : 0.45f);
        g.renderFakeItem(q.icon(), x + 5, y + 5);
        UiDraw.wrap(g, Component.literal(q.title()), x + 28, y + 3, DETAIL_W - 30, Colors.alpha(1f, chapter.color()), 0.8f, 8);
        int yy = y + 28;
        yy += UiDraw.wrap(g, q.description(), x + 2, yy, DETAIL_W - 4, Colors.alpha(1f, 0xD8CCB0), 0.55f, 6) * 6 + 4;
        // what it asks
        UiDraw.text(g, "OBJECTIVES", x + 2, yy, Colors.alpha(1f, GOLD), 0.55f, true);
        yy += 8;
        int shown = 0;
        for (int i = 0; i < q.objectives().size(); i++) {
            if (shown >= 6) {
                UiDraw.text(g, "... and " + (q.objectives().size() - shown) + " more", x + 4, yy, Colors.alpha(1f, MUTED), 0.5f, false);
                yy += 7;
                break;
            }
            QuestObjective o = q.objectives().get(i);
            int have = QuestService.progress(minecraft.player, q, i);
            boolean done = have >= o.target();
            g.pose().pushPose();
            g.pose().translate(x + 2, yy, 0);
            g.pose().scale(0.6f, 0.6f, 1);
            g.renderFakeItem(o.icon(), 0, 0);
            g.pose().popPose();
            String label = o.label();
            float scale = 0.5f;
            while (UiDraw.font().width(label) * scale > DETAIL_W - 50 && label.length() > 4) label = label.substring(0, label.length() - 2);
            if (!label.equals(o.label())) label = label.trim() + "...";
            UiDraw.text(g, label, x + 14, yy, Colors.alpha(1f, done ? GOOD : 0xE8DCC0), scale, false);
            String n = have + "/" + o.target();
            UiDraw.textRight(g, n, x + DETAIL_W - 2, yy, Colors.alpha(1f, done ? GOOD : MUTED), scale, false);
            float f = o.target() <= 0 ? 1 : have / (float) o.target();
            UiDraw.rect(g, x + 14, yy + 5.5f, x + DETAIL_W - 2, yy + 7, Colors.alpha(0.6f, 0x000000));
            UiDraw.rect(g, x + 14, yy + 5.5f, x + 14 + (DETAIL_W - 16) * f, yy + 7, Colors.alpha(1f, done ? GOOD : chapter.color()));
            yy += 11;
            shown++;
        }
        // what it gives
        yy += 2;
        UiDraw.text(g, "REWARDS", x + 2, yy, Colors.alpha(1f, GOLD), 0.55f, true);
        yy += 8;
        int rx = x + 2;
        hoveredReward = ItemStack.EMPTY;
        for (var reward : q.rewards()) {
            ItemStack stack = reward.get();
            UiDraw.rect(g, rx - 1, yy - 1, rx + 17, yy + 17, Colors.alpha(0.5f, 0x000000));
            g.renderFakeItem(stack, rx, yy);
            g.renderItemDecorations(font, stack, rx, yy);
            if (inside(mouseX, mouseY, rx, yy, 16, 16)) hoveredReward = stack;
            rx += 19;
        }
        if (q.xp() > 0) UiDraw.text(g, "+" + q.xp() + " XP", rx + 2, yy + 5, Colors.alpha(1f, GOOD), 0.6f, true);
        // claim
        int bx = x + DETAIL_W / 2 - 30, by = top + H - 30;
        boolean ready = s == State.DONE, hover = ready && inside(mouseX, mouseY, bx, by, 60, 15);
        if (ready) UiDraw.glow(g, bx + 30, by + 7, 70 + 6 * (float) Math.sin(time * 0.2f), Colors.alpha(0.35f, GOLD));
        UiDraw.button(g, bx, by, 60, 15, hover ? 1 : 0, ready ? 1f : 0.45f);
        String label = switch (s) {
            case LOCKED -> "Locked";
            case OPEN -> "Claim";
            case DONE -> "Claim";
            case CLAIMED -> "Claimed";
        };
        UiDraw.textCentered(g, label, bx + 30, by + 4, Colors.alpha(ready ? 1f : 0.55f, hover ? 0xFFFFFF : GOLD), 0.75f, true);
        if (s == State.LOCKED && q.requires() != null) {
            Quest need = Quests.get(q.requires());
            if (need != null) UiDraw.textCentered(g, "First: " + need.title(), x + DETAIL_W / 2f, by - 8, Colors.alpha(1f, MUTED), 0.5f, false);
        }
        if (!hoveredReward.isEmpty()) g.renderTooltip(font, hoveredReward, mouseX, mouseY);
    }

    private ItemStack hoveredReward = ItemStack.EMPTY;

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + CHAPTERS_X, y = top + TOP;
        for (Chapter c : Chapter.values()) {
            if (inside(mouseX, mouseY, x, y, CHAPTERS_W, 22)) {
                if (c != chapter) {
                    chapter = c;
                    scroll = 0;
                    selectFirst();
                    click(1.2f);
                }
                return true;
            }
            y += 25;
        }
        List<Quest> quests = Quests.of(chapter);
        int lx = left + LIST_X, ly = top + TOP;
        if (inside(mouseX, mouseY, lx, ly, LIST_W, listRows() * ROW)) {
            int i = (int) scroll + (int) ((mouseY - ly) / ROW);
            if (i >= 0 && i < quests.size()) {
                selected = quests.get(i);
                click(1.4f);
            }
            return true;
        }
        if (selected != null && state(selected) == State.DONE) {
            int bx = left + DETAIL_X + DETAIL_W / 2 - 30, by = top + H - 30;
            if (inside(mouseX, mouseY, bx, by, 60, 15)) {
                PacketDistributor.sendToServer(new QuestPayloads.Claim(selected.id()));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.5f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, Quests.of(chapter).size() - listRows());
        scroll = Math.max(0, Math.min(max, scroll - (float) scrollY));
        return true;
    }

    private void click(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, 0.6f));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int darker(int rgb) {
        return Colors.lerpRgb(rgb, 0x000000, 0.55f);
    }
}
