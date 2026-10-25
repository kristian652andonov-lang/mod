package com.fantasyweapons.client.gui;

import com.fantasyweapons.text.Tr;
import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.network.QuestPayloads;
import com.fantasyweapons.quest.Chapter;
import com.fantasyweapons.quest.Quest;
import com.fantasyweapons.quest.QuestGroup;
import com.fantasyweapons.quest.QuestObjective;
import com.fantasyweapons.quest.QuestService;
import com.fantasyweapons.quest.Quests;
import com.fantasyweapons.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Quest Journal: the chapters as tabs down the left, and the chapter's quests as a map of turning sigils joined by
 * the paths between them. A quest that opens another leads to it; a weapon's or an ore's quests sit under one node that
 * opens with a click to show them. Choosing a quest slides its page in from the right: what it asks and how far along
 * each part is, what it gives, and the seal to claim it. The map can be dragged or scrolled when it is bigger than its
 * window.
 */
public final class QuestScreen extends Screen {
    private static final ResourceLocation SPARKLE = FantasyWeapons.id("textures/gui/fantasy/sparkle.png");
    private static final ResourceLocation LOCK = FantasyWeapons.id("textures/gui/lock.png");

    private static final int W = 416, H = 236, TOP = 26, PAD = 8, TABS_W = 96, GAP = 6;
    private static final int MAP_X = PAD + TABS_W + GAP, MAP_W = W - MAP_X - PAD, MAP_H = H - TOP - PAD, PANEL_W = 150;
    private static final float NODE_R = 11, BIG_R = 14, SX = 50, SX_BRANCH = 46, SY = 44, BRANCH = 42, MARGIN_X = 30, MARGIN_Y = 24;
    private static final int COLUMNS = 5;
    private static final int GOLD = 0xF2C46B, GOLD_LIGHT = 0xFFE7A8, MUTED = 0xB9AC92, GOOD = 0x8FE08A, LINE = 0xB08A3E, LOCKED = 0x6A6670;

    private enum State { LOCKED, OPEN, DONE, CLAIMED }

    /** A node of the map: one quest, or a group of them. Positions are in map space (before panning). */
    private static final class Node {
        final Quest quest;
        final QuestGroup group;
        final List<Quest> members;
        float x, y, r, alpha = 1;

        Node(Quest quest) {
            this.quest = quest;
            this.group = null;
            this.members = List.of();
        }

        Node(QuestGroup group, List<Quest> members) {
            this.quest = null;
            this.group = group;
            this.members = members;
        }
    }

    private record Link(Node from, Node to, boolean open) {
    }

    private Chapter chapter = Chapter.FIRST_STEPS;
    private Quest selected, shown;
    private String expanded;
    private final Map<String, Float> opening = new HashMap<>();
    private float panelAnim, panX, panY, panTargetX, panTargetY, contentW, contentH;
    private float time, lastTime = -1;
    private boolean dragging, dragMoved;
    private double dragX, dragY;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private ItemStack hoveredReward = ItemStack.EMPTY;
    private int left, top;
    private final GuiZoom zoom = new GuiZoom(W, H);

    public QuestScreen() {
        super(Component.literal(Tr.s("Quest Journal")));
    }

    @Override
    protected void init() {
        zoom.fit(width, height);
        left = zoom.left(width);
        top = zoom.top(height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- states ----

    private State state(Quest q) {
        var player = minecraft.player;
        if (QuestService.data(player).claimed(q.id())) return State.CLAIMED;
        if (!QuestService.unlocked(player, q)) return State.LOCKED;
        return QuestService.complete(player, q) ? State.DONE : State.OPEN;
    }

    /** A group is as far along as its furthest quest: waiting to be claimed first, then open, then all claimed. */
    private State state(Node n) {
        if (n.quest != null) return state(n.quest);
        boolean open = false, allClaimed = true;
        for (Quest q : n.members) {
            State s = state(q);
            if (s == State.DONE) return State.DONE;
            if (s == State.OPEN) open = true;
            if (s != State.CLAIMED) allClaimed = false;
        }
        return allClaimed ? State.CLAIMED : open ? State.OPEN : State.LOCKED;
    }

    private int stateColor(State s) {
        return switch (s) {
            case LOCKED -> LOCKED;
            case OPEN -> chapter.color();
            case DONE -> GOLD;
            case CLAIMED -> 0x9CE07A;
        };
    }

    private static ItemStack chapterIcon(Chapter c) {
        return switch (c) {
            case FIRST_STEPS -> new ItemStack(ModItems.CODEX.get());
            case ORES -> new ItemStack(ModItems.ORES.get(Material.VOID_CRYSTAL).get());
            case REFINING -> new ItemStack(ModItems.REFINED.get(Material.SOLARITE).get());
            case WEAPONS -> new ItemStack(ModItems.WEAPONS.get("voidfang").get());
            case ARMOR -> new ItemStack(ModItems.ARMOR.get(com.fantasyweapons.armor.ArmorSet.SOLARIS)
                    .get(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE).get());
            case HUNTS -> new ItemStack(Items.DIAMOND_SWORD);
            case JOURNEYS -> new ItemStack(Items.ENDER_EYE);
            case MASTERY -> new ItemStack(Items.EXPERIENCE_BOTTLE);
        };
    }

    // ---- the map's layout ----

    /** Width of the map's window not covered by the quest's page. */
    private float visibleW() {
        return MAP_W - PANEL_W * ease(panelAnim);
    }

    /**
     * Lays the chapter out in rows: first the paths of quests that lead one to the next, then the groups five to a row
     * (an open group's quests in a row of their own beneath it), then the quests that stand alone.
     */
    private void layout() {
        nodes.clear();
        links.clear();
        List<Quest> quests = Quests.of(chapter);
        Map<String, List<Quest>> groups = new LinkedHashMap<>();
        Map<String, QuestGroup> groupInfo = new HashMap<>();
        List<Quest> loose = new ArrayList<>();
        for (Quest q : quests) {
            if (q.group() != null) {
                groups.computeIfAbsent(q.group().id(), k -> new ArrayList<>()).add(q);
                groupInfo.putIfAbsent(q.group().id(), q.group());
            } else {
                loose.add(q);
            }
        }
        Map<String, Quest> looseById = new HashMap<>();
        for (Quest q : loose) looseById.put(q.id(), q);
        Map<String, List<Quest>> children = new HashMap<>();
        for (Quest q : loose) if (q.requires() != null && looseById.containsKey(q.requires())) children.computeIfAbsent(q.requires(), k -> new ArrayList<>()).add(q);
        List<List<Quest>> chains = new ArrayList<>();
        List<Quest> singles = new ArrayList<>();
        for (Quest q : loose) {
            if (q.requires() != null && looseById.containsKey(q.requires())) continue;
            if (!children.containsKey(q.id())) {
                singles.add(q);
                continue;
            }
            // follow the path; a quest opening two others starts a second path from the first branch
            List<Quest> todo = new ArrayList<>(List.of(q));
            while (!todo.isEmpty()) {
                Quest at = todo.remove(0);
                List<Quest> line = new ArrayList<>();
                while (at != null) {
                    line.add(at);
                    List<Quest> next = children.getOrDefault(at.id(), List.of());
                    for (int i = 1; i < next.size(); i++) todo.add(next.get(i));
                    at = next.isEmpty() ? null : next.get(0);
                }
                chains.add(line);
            }
        }

        // rows of nodes, centred later
        List<List<Node>> rows = new ArrayList<>();
        List<Float> rowHeights = new ArrayList<>();
        Map<Node, Node> branchAnchor = new HashMap<>();
        Map<String, Node> byQuest = new HashMap<>();
        for (List<Quest> chain : chains) {
            List<Node> row = new ArrayList<>();
            for (Quest q : chain) {
                Node n = new Node(q);
                n.r = q.objectives().size() >= 10 ? BIG_R : NODE_R;
                row.add(n);
                byQuest.put(q.id(), n);
            }
            rows.add(row);
            rowHeights.add(SY);
        }
        List<Node> groupNodes = new ArrayList<>();
        for (var e : groups.entrySet()) {
            Node n = new Node(groupInfo.get(e.getKey()), e.getValue());
            n.r = NODE_R + 1;
            groupNodes.add(n);
        }
        for (int i = 0; i < groupNodes.size(); i += COLUMNS) {
            List<Node> row = new ArrayList<>(groupNodes.subList(i, Math.min(groupNodes.size(), i + COLUMNS)));
            rows.add(row);
            rowHeights.add(SY);
            for (Node g : row) {
                float e = opening.getOrDefault(g.group.id(), 0f);
                if (e < 0.01f) continue;
                List<Node> branch = new ArrayList<>();
                for (Quest q : g.members) {
                    Node n = new Node(q);
                    n.r = NODE_R;
                    n.alpha = e;
                    branch.add(n);
                    byQuest.put(q.id(), n);
                    branchAnchor.put(n, g);
                }
                rows.add(branch);
                rowHeights.add(BRANCH * e);
            }
        }
        for (int i = 0; i < singles.size(); i += COLUMNS) {
            List<Node> row = new ArrayList<>();
            for (Quest q : singles.subList(i, Math.min(singles.size(), i + COLUMNS))) {
                Node n = new Node(q);
                n.r = q.objectives().size() >= 10 ? BIG_R : NODE_R;
                row.add(n);
                byQuest.put(q.id(), n);
            }
            rows.add(row);
            rowHeights.add(SY);
        }

        // sizes and positions
        float widest = 0;
        for (List<Node> row : rows) {
            boolean branch = !row.isEmpty() && branchAnchor.containsKey(row.get(0));
            widest = Math.max(widest, (row.size() - 1) * (branch ? SX_BRANCH : SX));
        }
        float visW = visibleW();
        contentW = Math.max(visW, widest + MARGIN_X * 2);
        float y = MARGIN_Y;
        for (int i = 0; i < rows.size(); i++) {
            List<Node> row = rows.get(i);
            float h = rowHeights.get(i);
            boolean branch = !row.isEmpty() && branchAnchor.containsKey(row.get(0));
            if (branch) {
                Node anchor = branchAnchor.get(row.get(0));
                float e = row.get(0).alpha, span = (row.size() - 1) * SX_BRANCH;
                float x0 = Math.max(MARGIN_X, Math.min(contentW - MARGIN_X - span, anchor.x - span / 2));
                // the row unfolds out of its group's node
                float rowY = y - SY + BRANCH;
                for (int k = 0; k < row.size(); k++) {
                    Node n = row.get(k);
                    float e2 = ease(e);
                    n.x = anchor.x + (x0 + k * SX_BRANCH - anchor.x) * e2;
                    n.y = anchor.y + (rowY - anchor.y) * e2;
                }
                y += h;
            } else {
                float span = (row.size() - 1) * SX, x0 = (contentW - span) / 2;
                for (int k = 0; k < row.size(); k++) {
                    row.get(k).x = x0 + k * SX;
                    row.get(k).y = y;
                }
                y += i + 1 < rows.size() ? h : 0;
            }
            nodes.addAll(row);
        }
        contentH = y + MARGIN_Y;

        // the paths between them
        for (Node n : nodes) {
            if (n.quest == null) continue;
            Node anchor = branchAnchor.get(n);
            if (anchor != null && n.quest == anchor.members.get(0)) links.add(new Link(anchor, n, true));
            if (n.quest.requires() != null) {
                Node from = byQuest.get(n.quest.requires());
                if (from != null) links.add(new Link(from, n, state(n.quest) != State.LOCKED));
            }
        }
    }

    /** Where map space lands on screen: centred when it fits the window, panned when it does not. */
    private float offsetX() {
        float visW = visibleW();
        return contentW <= visW + 0.5f ? (visW - contentW) / 2 : -panX;
    }

    private float offsetY() {
        return contentH <= MAP_H + 0.5f ? (MAP_H - contentH) / 2 : -panY;
    }

    private float sx(Node n) {
        return left + MAP_X + offsetX() + n.x;
    }

    private float sy(Node n) {
        return top + TOP + offsetY() + n.y;
    }

    private void clampPan() {
        float maxX = Math.max(0, contentW - visibleW()), maxY = Math.max(0, contentH - MAP_H);
        panTargetX = Math.max(0, Math.min(maxX, panTargetX));
        panTargetY = Math.max(0, Math.min(maxY, panTargetY));
        panX = Math.max(0, Math.min(maxX, panX));
        panY = Math.max(0, Math.min(maxY, panY));
    }

    /** Pans so a node sits in view (beside the quest's page when it is open). */
    private void focus(Node n, float panelTarget) {
        float visW = MAP_W - PANEL_W * panelTarget;
        panTargetX = n.x - visW / 2;
        if (n.y + offsetY() < 20 || n.y + offsetY() > MAP_H - 30) panTargetY = n.y - MAP_H / 2f;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int realX = mouseX, realY = mouseY;
        // the menu is drawn zoomed to the window (see GuiZoom): the mouse, in its units
        if (devMouse != null) {
            mouseX = devMouse[0];
            mouseY = devMouse[1];
            realX = (int) (mouseX * zoom.zoom);
            realY = (int) (mouseY * zoom.zoom);
        } else {
            mouseX = (int) zoom.in(mouseX);
            mouseY = (int) zoom.in(mouseY);
        }
        time = Util.getMillis() / 50f;
        float dt = lastTime < 0 ? 1 : Math.min(20, time - lastTime);
        lastTime = time;
        animate(dt);
        layout();
        clampPan();
        UiDraw.style(UiDraw.NETHERITE, -1, GOLD, GOLD_LIGHT, MUTED);
        zoom.push(g);
        try {
            UiDraw.panel(g, left, top, W, H, chapter.color(), 1f);
            header(g, mouseX, mouseY);
            tabs(g, mouseX, mouseY);
            map(g, mouseX, mouseY);
        } finally {
            zoom.pop(g);
            UiDraw.resetTheme();
        }
        if (!hoveredReward.isEmpty()) g.renderTooltip(font, hoveredReward, realX, realY);
    }

    private void animate(float dt) {
        float k = 1 - (float) Math.exp(-dt * 0.35f);
        panelAnim += ((selected != null ? 1 : 0) - panelAnim) * k;
        if (selected != null) shown = selected;
        else if (panelAnim < 0.01f) shown = null;
        for (Quest q : Quests.of(chapter)) {
            if (q.group() == null) continue;
            String id = q.group().id();
            float now = opening.getOrDefault(id, 0f), target = id.equals(expanded) ? 1 : 0;
            opening.put(id, now + (target - now) * k);
        }
        panX += (panTargetX - panX) * k;
        panY += (panTargetY - panY) * k;
    }

    private int claimable() {
        var player = minecraft.player;
        int n = 0;
        for (Quest q : Quests.all()) if (!QuestService.data(player).claimed(q.id()) && QuestService.unlocked(player, q) && QuestService.complete(player, q)) n++;
        return n;
    }

    private static final int CLAIM_ALL_X = PAD + 1, CLAIM_ALL_Y = 6, CLAIM_ALL_W = 82, CLAIM_ALL_H = 14;

    private void header(GuiGraphics g, int mouseX, int mouseY) {
        // claim every quest that is done, at once
        int n = claimable(), bx = left + CLAIM_ALL_X, by = top + CLAIM_ALL_Y;
        boolean ready = n > 0, over = ready && inside(mouseX, mouseY, bx, by, CLAIM_ALL_W, CLAIM_ALL_H);
        UiDraw.button(g, bx, by, CLAIM_ALL_W, CLAIM_ALL_H, ready ? over ? 1 : 0 : 2, ready ? 1f : 0.5f);
        if (ready) UiDraw.glowBorder(g, bx, by, bx + CLAIM_ALL_W, by + CLAIM_ALL_H, GOLD, 0.5f + 0.3f * (float) Math.sin(time * 0.3f), 3);
        UiDraw.textCentered(g, ready ? Tr.f("Claim All (%s)", n) : "Claim All", bx + CLAIM_ALL_W / 2f, by + 3.5f,
                Colors.alpha(ready ? 1f : 0.55f, over ? 0xFFFFFF : GOLD), 0.68f, true);
        UiDraw.title(g, "Quest Journal", left + W / 2f, top + 9, Colors.alpha(1f, GOLD_LIGHT), 1f);
        UiDraw.miniSigil(g, left + W / 2f - UiDraw.width("Quest Journal") / 2f - 10, top + 13, 6, chapter.color(), time, 0.8f);
        UiDraw.miniSigil(g, left + W / 2f + UiDraw.width("Quest Journal") / 2f + 10, top + 13, 6, chapter.color(), -time, 0.8f);
        int claimed = 0, all = Quests.all().size();
        for (Quest q : Quests.all()) if (QuestService.data(minecraft.player).claimed(q.id())) claimed++;
        float px = left + W - PAD - 80, py = top + 15;
        UiDraw.textRight(g, Tr.f("%s / %s claimed", claimed, all), left + W - PAD, top + 7, Colors.alpha(1f, MUTED), 0.55f, false);
        UiDraw.rect(g, px, py, px + 80, py + 2.5f, Colors.alpha(0.6f, 0x000000));
        UiDraw.rect(g, px, py, px + 80 * claimed / (float) all, py + 2.5f, Colors.alpha(1f, GOLD));
    }

    // ---- the chapter tabs ----

    private float tabStep() {
        return (MAP_H + 3) / (float) Chapter.values().length;
    }

    private void tabs(GuiGraphics g, int mouseX, int mouseY) {
        float step = tabStep(), h = step - 3;
        int x = left + PAD;
        for (Chapter c : Chapter.values()) {
            float y = top + TOP + c.ordinal() * step;
            List<Quest> quests = Quests.of(c);
            int done = 0, waiting = 0;
            for (Quest q : quests) {
                State s = state(q);
                if (s == State.CLAIMED) done++;
                else if (s == State.DONE) waiting++;
            }
            boolean on = c == chapter, hover = inside(mouseX, mouseY, x, y, TABS_W, h);
            UiDraw.rect(g, x, y, x + TABS_W, y + h, Colors.alpha(on ? 0.6f : hover ? 0.4f : 0.28f, on ? darker(c.color()) : 0x000000));
            UiDraw.hGradient(g, x, y, x + TABS_W, y + h, Colors.alpha(on ? 0.28f : 0.1f, c.color()), 0);
            if (on) {
                UiDraw.glowBorder(g, x, y, x + TABS_W, y + h, c.color(), 0.7f, 3);
                // joined to the map, like a book's tab
                UiDraw.rect(g, x + TABS_W, y + 3, left + MAP_X, y + h - 3, Colors.alpha(0.45f, c.color()));
            } else {
                UiDraw.border(g, x, y, x + TABS_W, y + h, 1, Colors.alpha(hover ? 0.8f : 0.5f, LINE));
            }
            g.renderFakeItem(chapterIcon(c), x + 4, (int) (y + (h - 16) / 2));
            UiDraw.text(g, c.title(), x + 23, y + h / 2 - 6, Colors.alpha(1f, on ? 0xFFFFFF : c.color()), 0.68f, true);
            float bw = TABS_W - 50;
            UiDraw.rect(g, x + 23, y + h / 2 + 2.5f, x + 23 + bw, y + h / 2 + 4.5f, Colors.alpha(0.6f, 0x000000));
            if (!quests.isEmpty()) UiDraw.rect(g, x + 23, y + h / 2 + 2.5f, x + 23 + bw * done / quests.size(), y + h / 2 + 4.5f, Colors.alpha(1f, c.color()));
            UiDraw.text(g, done + "/" + quests.size(), x + 25 + bw, y + h / 2 + 1f, Colors.alpha(1f, MUTED), 0.52f, false);
            if (waiting > 0) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.25f);
                UiDraw.glow(g, x + TABS_W - 7, y + 6, 14, Colors.alpha(0.7f * pulse, GOLD));
                UiDraw.textCentered(g, String.valueOf(waiting), x + TABS_W - 7, y + 3.5f, Colors.alpha(1f, GOLD_LIGHT), 0.55f, true);
            }
        }
    }

    // ---- the map ----

    private void map(GuiGraphics g, int mouseX, int mouseY) {
        int mx = left + MAP_X, my = top + TOP;
        hoveredReward = ItemStack.EMPTY;
        zoom.scissor(g, mx, my, mx + MAP_W, my + MAP_H);
        background(g, mx, my);
        float visW = visibleW();
        boolean overMap = inside(mouseX, mouseY, mx, my, (int) visW, MAP_H);
        Node hover = null;
        if (overMap && !dragMoved) {
            for (Node n : nodes) {
                float dx = mouseX - sx(n), dy = mouseY - sy(n);
                if (n.alpha > 0.5f && dx * dx + dy * dy <= (n.r + 2) * (n.r + 2)) hover = n;
            }
        }
        for (Link l : links) link(g, l);
        for (Node n : nodes) nodeBack(g, n);
        for (Node n : nodes) nodeFront(g, n, n == hover);
        // above the items of the map (drawn deeper into the screen than flat shapes)
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (hover != null) label(g, hover);
        scrollbars(g, mx, my, visW);
        if (panelAnim > 0.01f && shown != null) page(g, mouseX, mouseY);
        g.pose().popPose();
        g.disableScissor();
        UiDraw.border(g, mx - 1, my - 1, mx + MAP_W + 1, my + MAP_H + 1, 1, Colors.alpha(0.8f, LINE));
        UiDraw.glowBorder(g, mx, my, mx + MAP_W, my + MAP_H, chapter.color(), 0.25f, 3);
    }

    /** Night sky over the map: the chapter's great sigil turning slowly, motes drifting up, darker towards the edges. */
    private void background(GuiGraphics g, int mx, int my) {
        UiDraw.rect(g, mx, my, mx + MAP_W, my + MAP_H, 0xFF0C0912);
        UiDraw.tile(g, UiDraw.SLATE, mx, my, MAP_W, MAP_H, 64, Colors.alpha(0.12f, 0xFFFFFF));
        UiDraw.vGradient(g, mx, my, mx + MAP_W, my + MAP_H, Colors.alpha(0.16f, chapter.color()), Colors.alpha(0.03f, chapter.color()));
        float cx = mx + visibleW() / 2, cy = my + MAP_H / 2f;
        greatSigil(g, cx, cy, 84, chapter.color());
        // faint grid of the map
        int grid = Colors.alpha(0.05f, chapter.color());
        float ox = offsetX() % 24, oy = offsetY() % 24;
        for (float gx = mx + ox; gx < mx + MAP_W; gx += 24) UiDraw.rect(g, gx, my, gx + 0.5f, my + MAP_H, grid);
        for (float gy = my + oy; gy < my + MAP_H; gy += 24) UiDraw.rect(g, mx, gy, mx + MAP_W, gy + 0.5f, grid);
        // motes
        for (int i = 0; i < 26; i++) {
            float seed = i * 91.73f;
            float px = mx + (float) ((Math.sin(seed) * 0.5 + 0.5) * MAP_W);
            float speed = 0.12f + 0.1f * (float) (Math.sin(seed * 1.7) * 0.5 + 0.5);
            float py = my + MAP_H - ((time * speed + seed * 3) % (MAP_H + 10)) + 5;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(time * 0.15f + seed);
            px += (float) Math.sin(time * 0.03f + seed) * 4;
            UiDraw.glow(g, px, py, 3 + 3 * twinkle, Colors.alpha(0.35f * twinkle, Colors.lerpRgb(chapter.color(), 0xFFFFFF, 0.4f)));
        }
        float e = 18;
        UiDraw.vGradient(g, mx, my, mx + MAP_W, my + e, Colors.alpha(0.55f, 0x000000), 0);
        UiDraw.vGradient(g, mx, my + MAP_H - e, mx + MAP_W, my + MAP_H, 0, Colors.alpha(0.55f, 0x000000));
        UiDraw.hGradient(g, mx, my, mx + e, my + MAP_H, Colors.alpha(0.5f, 0x000000), 0);
        UiDraw.hGradient(g, mx + MAP_W - e, my, mx + MAP_W, my + MAP_H, 0, Colors.alpha(0.5f, 0x000000));
    }

    private static final String RUNES = "the road of legends is walked one step at a time by those who dare ";

    /**
     * The chapter's great sigil behind the map: fine double rings with a band of runes turning between them, a pentagram
     * turning the other way, and a small star at its heart.
     */
    private void greatSigil(GuiGraphics g, float cx, float cy, float r, int rgb) {
        float full = (float) (Math.PI * 2);
        UiDraw.glow(g, cx, cy, r * 2.6f, Colors.alpha(0.07f, rgb));
        UiDraw.arc(g, cx, cy, r - 0.6f, r, 0, full, Colors.alpha(0.32f, rgb), 96);
        UiDraw.arc(g, cx, cy, r - 2.6f, r - 2.1f, 0, full, Colors.alpha(0.22f, rgb), 96);
        float rr = r - 12, ri = r - 17;
        UiDraw.arc(g, cx, cy, ri - 0.5f, ri, 0, full, Colors.alpha(0.25f, rgb), 80);
        // the band of runes, in the enchanters' script
        var alt = net.minecraft.network.chat.Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"));
        int count = 44;
        float spin = time * 0.004f;
        for (int i = 0; i < count; i++) {
            float a = spin + i * full / count;
            g.pose().pushPose();
            g.pose().translate(cx + (float) Math.sin(a) * (rr - 1.5f), cy - (float) Math.cos(a) * (rr - 1.5f), 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(a));
            g.pose().scale(0.55f, 0.55f, 1);
            String ch = String.valueOf(RUNES.charAt(i % RUNES.length()));
            g.drawString(font, Component.literal(ch).withStyle(alt), -font.width(Component.literal(ch).withStyle(alt)) / 2, -4,
                    Colors.alpha(0.3f, rgb), false);
            g.pose().popPose();
        }
        // the pentagram, and its points
        float rot = -time * 0.006f, rp = ri - 3;
        float[] px = new float[5], py = new float[5];
        for (int i = 0; i < 5; i++) {
            double a = rot + i * full / 5;
            px[i] = cx + (float) Math.sin(a) * rp;
            py[i] = cy - (float) Math.cos(a) * rp;
        }
        for (int i = 0; i < 5; i++) UiDraw.line(g, px[i], py[i], px[(i + 2) % 5], py[(i + 2) % 5], 0.7f, Colors.alpha(0.22f, rgb), Colors.alpha(0.22f, rgb));
        for (int i = 0; i < 5; i++) UiDraw.glow(g, px[i], py[i], 7, Colors.alpha(0.35f, Colors.lerpRgb(rgb, 0xFFFFFF, 0.4f)));
        UiDraw.arc(g, cx, cy, r * 0.3f - 0.5f, r * 0.3f, 0, full, Colors.alpha(0.2f, rgb), 48);
        UiDraw.miniSigil(g, cx, cy, r * 0.22f, rgb, time * 1.5f, 0.35f);
    }

    /** A path: lit, with sparks running along it, once the quest it leads to is open; dark until then. */
    private void link(GuiGraphics g, Link l) {
        float x1 = sx(l.from()), y1 = sy(l.from()), x2 = sx(l.to()), y2 = sy(l.to());
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < l.from().r + l.to().r + 1) return;
        float ux = dx / len, uy = dy / len;
        float ax = x1 + ux * (l.from().r + 2), ay = y1 + uy * (l.from().r + 2), bx = x2 - ux * (l.to().r + 2), by = y2 - uy * (l.to().r + 2);
        float a = Math.min(l.from().alpha, l.to().alpha);
        if (l.open()) {
            int c = Colors.lerpRgb(chapter.color(), GOLD, 0.4f);
            UiDraw.line(g, ax, ay, bx, by, 3f, Colors.alpha(0.18f * a, c), Colors.alpha(0.18f * a, c));
            UiDraw.line(g, ax, ay, bx, by, 1.2f, Colors.alpha(0.85f * a, c), Colors.alpha(0.85f * a, c));
            float seg = len - l.from().r - l.to().r - 4;
            for (int k = 0; k < 3; k++) {
                float t = (time * 0.025f + k / 3f) % 1f;
                UiDraw.glow(g, ax + ux * seg * t, ay + uy * seg * t, 5, Colors.alpha(0.8f * a * (float) Math.sin(Math.PI * t), GOLD_LIGHT));
            }
        } else {
            UiDraw.line(g, ax, ay, bx, by, 1f, Colors.alpha(0.45f * a, LOCKED), Colors.alpha(0.45f * a, LOCKED));
        }
    }

    /** Behind a node: its sigil, turning (faster, and brighter, when the quest waits to be claimed). */
    private void nodeBack(GuiGraphics g, Node n) {
        State s = state(n);
        float cx = sx(n), cy = sy(n), phase = n.x * 0.37f + n.y * 0.21f;
        float strength = switch (s) {
            case LOCKED -> 0.2f;
            case OPEN -> 0.55f;
            case DONE -> 0.85f + 0.15f * (float) Math.sin(time * 0.2f + phase);
            case CLAIMED -> 0.4f;
        };
        UiDraw.miniSigil(g, cx, cy, n.r * 1.62f, stateColor(s), (time + phase * 20) * (s == State.DONE ? 2.2f : 1f), strength * n.alpha);
        if (s == State.DONE) UiDraw.glow(g, cx, cy, n.r * 4.2f, Colors.alpha(0.3f * n.alpha * (0.7f + 0.3f * (float) Math.sin(time * 0.2f + phase)), GOLD));
    }

    private void nodeFront(GuiGraphics g, Node n, boolean hover) {
        State s = state(n);
        float cx = sx(n), cy = sy(n), r = n.r * (hover ? 1.1f : 1f);
        int tint = s == State.LOCKED ? 0xFF8A8A8A : 0xFFFFFFFF;
        UiDraw.medallion(g, cx, cy, r + 1.5f, Colors.alpha(n.alpha, tint & 0xFFFFFF));
        if (n.group != null) {
            // a group's ring fills as its quests are claimed
            int claimed = 0;
            for (Quest q : n.members) if (state(q) == State.CLAIMED) claimed++;
            float full = (float) (Math.PI * 2);
            UiDraw.arc(g, cx, cy, r + 2.5f, r + 4f, 0, full, Colors.alpha(0.5f * n.alpha, 0x000000), 32);
            if (claimed > 0) UiDraw.arc(g, cx, cy, r + 2.5f, r + 4f, 0, full * claimed / n.members.size(), Colors.alpha(n.alpha, chapter.color()), 32);
        }
        ItemStack icon = n.quest != null ? n.quest.icon() : n.group.icon();
        float scale = r / NODE_R * 0.95f;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.renderFakeItem(icon, -8, -8);
        g.pose().popPose();
        if (s == State.LOCKED) {
            UiDraw.disc(g, cx, cy, r - 1.5f, Colors.alpha(0.55f * n.alpha, 0x0A0810));
            UiDraw.texture(g, LOCK, cx + r * 0.35f, cy + r * 0.3f, 8, 8, Colors.alpha(n.alpha, 0xFFFFFF));
        } else if (s == State.CLAIMED) {
            UiDraw.disc(g, cx + r * 0.72f, cy + r * 0.72f, 3.6f, Colors.alpha(n.alpha, 0x2E6B2A));
            UiDraw.textCentered(g, "✔", cx + r * 0.72f + 0.2f, cy + r * 0.72f - 2.2f, Colors.alpha(n.alpha, 0xC8FFB8), 0.5f, false);
        } else if (s == State.DONE) {
            // sparkles circling a quest waiting to be claimed
            for (int k = 0; k < 3; k++) {
                double a = time * 0.09 + k * Math.PI * 2 / 3;
                float sx = cx + (float) Math.sin(a) * (r + 6), sy = cy - (float) Math.cos(a) * (r + 6);
                float size = 4 + 1.5f * (float) Math.sin(time * 0.3f + k);
                UiDraw.additive(g, SPARKLE, sx - size / 2, sy - size / 2, size, size, Colors.alpha(0.9f * n.alpha, GOLD_LIGHT));
            }
        }
        boolean chosen = (n.quest != null && n.quest == selected) || (n.group != null && n.group.id().equals(expanded));
        if (chosen) {
            float full = (float) (Math.PI * 2), spin = time * 0.06f;
            for (int k = 0; k < 8; k++) {
                float a0 = spin + k * full / 8;
                UiDraw.arc(g, cx, cy, r + 5.5f, r + 7f, a0, a0 + full / 16, Colors.alpha(0.9f * n.alpha, GOLD_LIGHT), 3);
            }
        }
        if (n.group != null) {
            // its name beneath
            String name = n.group.title();
            UiDraw.textCentered(g, name, cx, cy + r + 6, Colors.alpha(n.alpha, s == State.LOCKED ? 0x8A8494 : 0xE8DCC0), 0.5f, true);
        }
    }

    /** What a node is, by the mouse: its name and how far along it is. */
    private void label(GuiGraphics g, Node n) {
        String title, status;
        State s = state(n);
        if (n.quest != null) {
            title = n.quest.title();
            status = switch (s) {
                case LOCKED -> {
                    Quest need = n.quest.requires() == null ? null : Quests.get(n.quest.requires());
                    yield need == null ? "Locked" : Tr.f("First: %s", need.title());
                }
                case OPEN -> progressText(n.quest);
                case DONE -> "Done! Claim your reward";
                case CLAIMED -> "Claimed";
            };
        } else {
            int claimed = 0;
            for (Quest q : n.members) if (state(q) == State.CLAIMED) claimed++;
            title = n.group.title();
            status = Tr.f(n.group.id().equals(expanded) ? "%s / %s claimed · click to close" : "%s / %s claimed · click to open", claimed, n.members.size());
        }
        float w = Math.max(UiDraw.width(title) * 0.62f, UiDraw.width(status) * 0.5f) + 8;
        float x = Math.max(left + MAP_X + 2, Math.min(left + MAP_X + visibleW() - w - 2, sx(n) - w / 2)), y = sy(n) - n.r - 22;
        if (y < top + TOP + 2) y = sy(n) + n.r + 6;
        UiDraw.rect(g, x, y, x + w, y + 17, Colors.alpha(0.88f, 0x0E0A14));
        UiDraw.border(g, x, y, x + w, y + 17, 1, Colors.alpha(0.9f, stateColor(s)));
        UiDraw.text(g, title, x + 4, y + 2.5f, Colors.alpha(1f, s == State.LOCKED ? 0xB0A8B8 : 0xFFFFFF), 0.62f, true);
        UiDraw.text(g, status, x + 4, y + 10, Colors.alpha(1f, s == State.DONE ? GOLD : MUTED), 0.5f, false);
    }

    private void scrollbars(GuiGraphics g, int mx, int my, float visW) {
        if (contentH > MAP_H + 0.5f) {
            float frac = MAP_H / contentH, pos = panY / (contentH - MAP_H), bx = mx + visW - 4, th = Math.max(12, MAP_H * frac);
            UiDraw.rect(g, bx, my + 2, bx + 2, my + MAP_H - 2, Colors.alpha(0.35f, 0x000000));
            UiDraw.rect(g, bx, my + 2 + (MAP_H - 4 - th) * pos, bx + 2, my + 2 + (MAP_H - 4 - th) * pos + th, Colors.alpha(0.85f, GOLD));
        }
        if (contentW > visW + 0.5f) {
            float frac = visW / contentW, pos = panX / (contentW - visW), by = my + MAP_H - 4, tw = Math.max(12, visW * frac);
            UiDraw.rect(g, mx + 2, by, mx + visW - 6, by + 2, Colors.alpha(0.35f, 0x000000));
            UiDraw.rect(g, mx + 2 + (visW - 8 - tw) * pos, by, mx + 2 + (visW - 8 - tw) * pos + tw, by + 2, Colors.alpha(0.85f, GOLD));
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

    // ---- the quest's page ----

    private float pageX() {
        return left + MAP_X + MAP_W - PANEL_W * ease(panelAnim);
    }

    private int claimX() {
        return (int) (pageX() + PANEL_W / 2f - 32);
    }

    private int claimY() {
        return top + TOP + MAP_H - 22;
    }

    private void page(GuiGraphics g, int mouseX, int mouseY) {
        Quest q = shown;
        State s = state(q);
        float x = pageX(), y = top + TOP, w = PANEL_W;
        UiDraw.rect(g, x, y, x + w, y + MAP_H, 0xFF100C17);
        UiDraw.vGradient(g, x, y, x + w, y + 60, Colors.alpha(0.22f, chapter.color()), 0);
        UiDraw.rect(g, x, y, x + 1, y + MAP_H, Colors.alpha(0.9f, chapter.color()));
        UiDraw.hGradient(g, x - 8, y, x, y + MAP_H, 0, Colors.alpha(0.45f, 0x000000));
        // close
        boolean overClose = inside(mouseX, mouseY, (int) (x + w - 12), (int) y + 2, 10, 10);
        UiDraw.textCentered(g, "✕", x + w - 7, y + 3.5f, Colors.alpha(1f, overClose ? 0xFFFFFF : MUTED), 0.7f, false);
        // the quest's sigil and name
        float ix = x + 18, iy = y + 18;
        UiDraw.miniSigil(g, ix, iy, 15, stateColor(s), time * (s == State.DONE ? 2 : 1), s == State.LOCKED ? 0.3f : 0.9f);
        UiDraw.medallion(g, ix, iy, 11, s == State.LOCKED ? 0xFF8A8A8A : 0xFFFFFFFF);
        g.renderFakeItem(q.icon(), (int) ix - 8, (int) iy - 8);
        int lines = UiDraw.wrap(g, Component.literal(q.title()), x + 36, y + 8, (int) w - 42, Colors.alpha(1f, chapter.color()), 0.75f, 8);
        String status = switch (s) {
            case LOCKED -> "Locked";
            case OPEN -> "Under way";
            case DONE -> "Done!";
            case CLAIMED -> "Claimed";
        };
        UiDraw.text(g, status, x + 36, y + 9 + lines * 8, Colors.alpha(1f, s == State.DONE ? GOLD : s == State.CLAIMED ? GOOD : MUTED), 0.5f, false);
        float yy = y + 38;
        yy += UiDraw.wrap(g, q.description(), x + 6, yy, (int) w - 12, Colors.alpha(1f, 0xD8CCB0), 0.52f, 6) * 6 + 4;
        // what it asks
        UiDraw.text(g, "OBJECTIVES", x + 6, yy, Colors.alpha(1f, GOLD), 0.52f, true);
        yy += 8;
        int shownCount = 0;
        for (int i = 0; i < q.objectives().size(); i++) {
            if (shownCount >= 5) {
                UiDraw.text(g, Tr.f("... and %s more", q.objectives().size() - shownCount), x + 8, yy, Colors.alpha(1f, MUTED), 0.48f, false);
                yy += 7;
                break;
            }
            QuestObjective o = q.objectives().get(i);
            int have = QuestService.progress(minecraft.player, q, i);
            boolean done = have >= o.target();
            g.pose().pushPose();
            g.pose().translate(x + 6, yy, 0);
            g.pose().scale(0.6f, 0.6f, 1);
            g.renderFakeItem(o.icon(), 0, 0);
            g.pose().popPose();
            String label = o.label();
            float scale = 0.48f;
            while (UiDraw.width(label) * scale > w - 54 && label.length() > 4) label = label.substring(0, label.length() - 2);
            if (!label.equals(o.label())) label = label.trim() + "...";
            UiDraw.text(g, label, x + 18, yy, Colors.alpha(1f, done ? GOOD : 0xE8DCC0), scale, false);
            UiDraw.textRight(g, have + "/" + o.target(), x + w - 6, yy, Colors.alpha(1f, done ? GOOD : MUTED), scale, false);
            float f = o.target() <= 0 ? 1 : have / (float) o.target();
            UiDraw.rect(g, x + 18, yy + 5.5f, x + w - 6, yy + 7, Colors.alpha(0.6f, 0x000000));
            UiDraw.rect(g, x + 18, yy + 5.5f, x + 18 + (w - 24) * f, yy + 7, Colors.alpha(1f, done ? GOOD : chapter.color()));
            yy += 11;
            shownCount++;
        }
        // what it gives
        yy += 2;
        UiDraw.text(g, "REWARDS", x + 6, yy, Colors.alpha(1f, GOLD), 0.52f, true);
        yy += 8;
        float rx = x + 6;
        for (var reward : q.rewards()) {
            ItemStack stack = reward.get();
            UiDraw.rect(g, rx - 1, yy - 1, rx + 17, yy + 17, Colors.alpha(0.5f, 0x000000));
            UiDraw.border(g, rx - 1, yy - 1, rx + 17, yy + 17, 1, Colors.alpha(0.5f, LINE));
            g.renderFakeItem(stack, (int) rx, (int) yy);
            g.renderItemDecorations(font, stack, (int) rx, (int) yy);
            if (inside(mouseX, mouseY, (int) rx, (int) yy, 16, 16)) hoveredReward = stack;
            rx += 20;
        }
        if (q.xp() > 0) UiDraw.text(g, Tr.f("+%s XP", q.xp()), rx + 2, yy + 5, Colors.alpha(1f, GOOD), 0.58f, true);
        // the seal to claim it: a glow round the button while it can be claimed
        int bx = claimX(), by = claimY(), bw = 64, bh = 15;
        boolean ready = s == State.DONE, over = ready && inside(mouseX, mouseY, bx, by, bw, bh);
        UiDraw.button(g, bx, by, bw, bh, ready ? over ? 1 : 0 : 2, ready ? 1f : 0.5f);
        if (ready) {
            float pulse = 0.55f + 0.35f * (float) Math.sin(time * 0.3f);
            UiDraw.glowBorder(g, bx, by, bx + bw, by + bh, GOLD, pulse, 4);
            // a spark running round the edge
            float per = 2 * (bw + bh), t = (time * 1.6f) % per;
            float px = t < bw ? bx + t : t < bw + bh ? bx + bw : t < 2 * bw + bh ? bx + bw - (t - bw - bh) : bx;
            float py = t < bw ? by : t < bw + bh ? by + (t - bw) : t < 2 * bw + bh ? by + bh : by + bh - (t - 2 * bw - bh);
            UiDraw.glow(g, px, py, 7, Colors.alpha(0.9f, GOLD_LIGHT));
        }
        String label = switch (s) {
            case LOCKED -> "Locked";
            case OPEN, DONE -> "Claim";
            case CLAIMED -> "Claimed";
        };
        UiDraw.textCentered(g, label, bx + bw / 2f, by + 4, Colors.alpha(ready ? 1f : 0.55f, over ? 0xFFFFFF : GOLD), 0.75f, true);
        if (s == State.LOCKED && q.requires() != null) {
            Quest need = Quests.get(q.requires());
            if (need != null) UiDraw.textCentered(g, Tr.f("First: %s", need.title()), x + w / 2, by - 8, Colors.alpha(1f, MUTED), 0.48f, false);
        }
    }

    // ---- DEVELOPMENT ONLY: for the screenshot scripts ----

    /** A mouse position drawn with instead of the real one, or null. */
    public static int[] devMouse;

    public void devChapter(Chapter c) {
        chapter = c;
        selected = null;
        expanded = null;
        opening.clear();
        panX = panY = panTargetX = panTargetY = 0;
    }

    public void devExpand(String group) {
        expanded = group;
    }

    public void devSelect(String quest) {
        selected = Quests.get(quest);
        for (Node n : nodes) if (n.quest == selected) focus(n, 1);
    }

    /** Puts the drawn mouse over a quest's (or group's) node. */
    public void devHover(String id) {
        for (Node n : nodes) {
            if ((n.quest != null && n.quest.id().equals(id)) || (n.group != null && n.group.id().equals(id))) {
                devMouse = new int[]{(int) sx(n), (int) sy(n)};
            }
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = zoom.in(mouseX);
        mouseY = zoom.in(mouseY);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, left + CLAIM_ALL_X, top + CLAIM_ALL_Y, CLAIM_ALL_W, CLAIM_ALL_H)) {
            if (claimable() > 0) {
                PacketDistributor.sendToServer(new QuestPayloads.ClaimAll());
                click(SoundEvents.PLAYER_LEVELUP, 1.4f);
            }
            return true;
        }
        // tabs
        float step = tabStep();
        for (Chapter c : Chapter.values()) {
            float y = top + TOP + c.ordinal() * step;
            if (inside(mouseX, mouseY, left + PAD, y, TABS_W, step - 3)) {
                if (c != chapter) {
                    chapter = c;
                    selected = null;
                    shown = null;
                    panelAnim = 0;
                    expanded = null;
                    opening.clear();
                    panX = panY = panTargetX = panTargetY = 0;
                    click(SoundEvents.BOOK_PAGE_TURN, 1.2f);
                }
                return true;
            }
        }
        int mx = left + MAP_X, my = top + TOP;
        if (!inside(mouseX, mouseY, mx, my, MAP_W, MAP_H)) return super.mouseClicked(mouseX, mouseY, button);
        // the quest's page
        if (shown != null && panelAnim > 0.5f && mouseX >= pageX()) {
            float x = pageX();
            if (inside(mouseX, mouseY, x + PANEL_W - 12, my + 2, 10, 10)) {
                selected = null;
                click(SoundEvents.BOOK_PAGE_TURN, 0.9f);
            } else if (selected != null && state(selected) == State.DONE && inside(mouseX, mouseY, claimX(), claimY(), 64, 15)) {
                PacketDistributor.sendToServer(new QuestPayloads.Claim(selected.id()));
                click(SoundEvents.PLAYER_LEVELUP, 1.4f);
            }
            return true;
        }
        // a node
        for (Node n : nodes) {
            float dx = (float) mouseX - sx(n), dy = (float) mouseY - sy(n);
            if (n.alpha < 0.5f || dx * dx + dy * dy > (n.r + 2) * (n.r + 2)) continue;
            if (n.group != null) {
                boolean open = !n.group.id().equals(expanded);
                expanded = open ? n.group.id() : null;
                if (selected != null && selected.group() != null && !open) selected = null;
                click(SoundEvents.AMETHYST_BLOCK_CHIME, open ? 1.4f : 0.9f);
                if (open) panTargetY = n.y - MAP_H / 2f + BRANCH / 2;
            } else {
                selected = n.quest == selected ? null : n.quest;
                click(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8f);
                if (selected != null) focus(n, 1);
            }
            return true;
        }
        // the map itself: dragged, or clicked to put the page away
        dragging = true;
        dragMoved = false;
        dragX = mouseX;
        dragY = mouseY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragDX, double dragDY) {
        mouseX = zoom.in(mouseX);
        mouseY = zoom.in(mouseY);
        dragDX = zoom.in(dragDX);
        dragDY = zoom.in(dragDY);
        if (dragging) {
            if (Math.abs(mouseX - dragX) + Math.abs(mouseY - dragY) > 3) dragMoved = true;
            panX -= (float) dragDX;
            panY -= (float) dragDY;
            panTargetX = panX;
            panTargetY = panY;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragDX, dragDY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = zoom.in(mouseX);
        mouseY = zoom.in(mouseY);
        if (dragging) {
            dragging = false;
            if (!dragMoved && selected != null) {
                selected = null;
                click(SoundEvents.BOOK_PAGE_TURN, 0.9f);
            }
            dragMoved = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = zoom.in(mouseX);
        mouseY = zoom.in(mouseY);
        if (inside(mouseX, mouseY, left + MAP_X, top + TOP, MAP_W, MAP_H)) {
            panTargetY -= (float) scrollY * 24;
            panTargetX -= (float) scrollX * 24;
            clampPan();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void click(net.minecraft.sounds.SoundEvent sound, float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6f));
    }

    private void click(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float pitch) {
        click(sound.value(), pitch);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static int darker(int rgb) {
        return Colors.lerpRgb(rgb, 0x000000, 0.55f);
    }
}
