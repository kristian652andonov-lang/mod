package com.aigirlfriend.client.screen;

import com.aigirlfriend.client.ClientState;
import com.aigirlfriend.companion.Activity;
import com.aigirlfriend.companion.CommandMode;
import com.aigirlfriend.companion.CyclePhase;
import com.aigirlfriend.companion.Mood;
import com.aigirlfriend.companion.RelationshipStage;
import com.aigirlfriend.companion.RequestType;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.network.StatusSnapshot;
import com.aigirlfriend.network.payload.OutfitPayload;
import com.aigirlfriend.network.payload.RequestResponsePayload;
import com.aigirlfriend.network.payload.StatusRequestPayload;
import com.aigirlfriend.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Her status: mood and its causes, needs, relationship, activity, optional cycle info, request and memories. */
public class StatusScreen extends Screen {
    private static final int W = 300, H = 236;
    private final int entityId;
    private int ticks;
    private Button accept, decline;

    public StatusScreen(int entityId) {
        super(Component.translatable("aigirlfriend.screen.status"));
        this.entityId = entityId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        addRenderableWidget(Button.builder(Component.translatable("aigirlfriend.action.talk"), b -> minecraft.setScreen(new TalkScreen(entityId)))
                .bounds(x + 8, y + H - 24, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("aigirlfriend.screen.status.outfit"), b -> {
            StatusSnapshot s = ClientState.status(entityId);
            if (s != null && s.outfitCount() > 1) PacketDistributor.sendToServer(new OutfitPayload(entityId, (s.outfit() + 1) % s.outfitCount()));
            PacketDistributor.sendToServer(new StatusRequestPayload(entityId));
        }).bounds(x + 82, y + H - 24, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + W - 78, y + H - 24, 70, 18).build());
        accept = addRenderableWidget(Button.builder(Component.translatable("aigirlfriend.screen.status.accept"), b -> {
            PacketDistributor.sendToServer(new RequestResponsePayload(entityId, true));
        }).bounds(x + W - 112, y + 150, 50, 16).build());
        decline = addRenderableWidget(Button.builder(Component.translatable("aigirlfriend.screen.status.decline"), b -> {
            PacketDistributor.sendToServer(new RequestResponsePayload(entityId, false));
        }).bounds(x + W - 58, y + 150, 50, 16).build());
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) PacketDistributor.sendToServer(new StatusRequestPayload(entityId));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        mx = mouseX;
        my = mouseY;
        StatusSnapshot s = ClientState.status(entityId);
        boolean hasRequest = s != null && s.requestType() >= 0;
        accept.visible = hasRequest && !s.requestAccepted();
        decline.visible = hasRequest && !s.requestAccepted();
        super.render(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        GuiDraw.panel(g, x, y, x + W, y + H, 0xE0201420, 0xFFFF8FC8);
        if (s == null) {
            g.drawCenteredString(font, Component.translatable("aigirlfriend.screen.loading"), x + W / 2, y + H / 2, 0xFFFFFFFF);
            return;
        }
        Mood mood = Mood.byId(s.mood());
        RelationshipStage stage = RelationshipStage.byId(s.stage());
        g.drawString(font, Component.literal(s.name()).withStyle(st -> st.withBold(true)), x + 10, y + 8, 0xFFFFC2E2);
        g.drawString(font, Component.translatable("aigirlfriend.personality." + s.archetype()), x + 12 + font.width(s.name()) + 8, y + 8, 0xFFAAAAAA, false);
        MutableComponent relation = Component.translatable(stage.key()).append(" · ").append(Component.translatable("aigirlfriend.screen.status.days", s.daysTogether()));
        if (s.married()) relation.append(" · ").append(Component.translatable("aigirlfriend.screen.status.married").withStyle(st -> st.withColor(0xFFD75E)));
        g.drawString(font, relation, x + 10, y + 20, 0xFFE0A0FF, false);
        if (s.married()) {
            // her wedding ring, drawn small after the text
            g.pose().pushPose();
            g.pose().translate(x + 12 + font.width(relation), y + 17, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(new ItemStack(ModItems.WEDDING_RING.get()), 0, 0);
            g.pose().popPose();
        }

        // mood
        GuiDraw.icon(g, GuiDraw.ICON_HEART, x + 8, y + 32);
        g.drawString(font, Component.translatable("aigirlfriend.screen.status.mood").append(": ").append(Component.translatable(mood.key()).withStyle(st -> st.withColor(mood.color))), x + 28, y + 36, 0xFFFFFFFF, false);
        int cy = y + 48;
        if (!s.moodCauses().isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal("\"" + String.join(". ", s.moodCauses()) + ".\""), W - 40);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), x + 28, cy, 0xFFCCCCCC, false);
                cy += 10;
            }
        }

        // bars
        int bx = x + 10, by = y + 74, bw = 130;
        bar(g, GuiDraw.ICON_HEALTH, "aigirlfriend.screen.status.health", s.health() / Math.max(1f, s.maxHealth()), 0xFFE04848, bx, by, bw);
        bar(g, GuiDraw.ICON_FOOD, "aigirlfriend.screen.status.fullness", 1f - s.hunger() / 100f, 0xFFE8A040, bx, by + 14, bw);
        bar(g, GuiDraw.ICON_ENERGY, "aigirlfriend.screen.status.energy", s.energy() / 100f, 0xFF60C0F0, bx, by + 28, bw);
        bar(g, GuiDraw.ICON_ATTENTION, "aigirlfriend.screen.status.attention", s.attention() / 100f, 0xFFB080F0, bx, by + 42, bw);
        int rx = x + W / 2 + 6;
        bar(g, GuiDraw.ICON_HEART, "aigirlfriend.screen.status.affection", s.stageProgress(), 0xFFFF70B0, rx, by, bw);
        bar(g, GuiDraw.ICON_TRUST, "aigirlfriend.screen.status.trust", s.trust() / 100f, 0xFF70E0A0, rx, by + 14, bw);
        Activity act = Activity.byId(s.activity());
        CommandMode cmd = CommandMode.byId(s.command());
        g.drawString(font, Component.translatable(act.key()), rx + 18, by + 31, 0xFFDDDDDD, false);
        g.drawString(font, Component.translatable(cmd.key()), rx + 18, by + 45, 0xFF999999, false);

        int ly = y + 134;
        if (s.cyclePhase() >= 0 && GFClientConfig.SHOW_CYCLE.get()) {
            GuiDraw.icon(g, GuiDraw.ICON_MOON, x + 8, ly - 4);
            g.drawString(font, Component.translatable("aigirlfriend.screen.status.cycle", Component.translatable(CyclePhase.byId(s.cyclePhase()).key()), s.cycleDay()), x + 28, ly, 0xFFF0B0D0, false);
        } else if (s.favoriteCategory() != null) {
            g.drawString(font, Component.translatable("aigirlfriend.screen.status.favorite", Component.translatable(s.favoriteCategory().key()), s.favoriteActivity()), x + 28, ly, 0xFFF0D0A0, false);
        }

        // request
        if (s.requestType() >= 0) {
            RequestType rt = RequestType.values()[Math.min(s.requestType(), RequestType.values().length - 1)];
            GuiDraw.icon(g, GuiDraw.ICON_REQUEST, x + 8, y + 150);
            g.drawString(font, Component.translatable(rt.key(), s.requestDetail().toLowerCase().replace('_', ' ')), x + 28, y + 150, 0xFFFFE080, false);
            GuiDraw.bar(g, x + 28, y + 161, 120, 5, s.requestProgress(), 0xFFFFD040);
            if (s.requestAccepted()) g.drawString(font, Component.translatable("aigirlfriend.screen.status.accepted"), x + W - 110, y + 154, 0xFF80FF80, false);
        }

        // memories & mood history
        int my = y + 172;
        GuiDraw.icon(g, GuiDraw.ICON_MEMORY, x + 8, my - 4);
        int shown = 0;
        for (String mem : s.memories()) {
            if (shown >= 3) break;
            String line = font.plainSubstrByWidth("• " + mem, W - 40);
            g.drawString(font, line, x + 28, my + shown * 10, 0xFFCFC0E0, false);
            shown++;
        }
        if (shown == 0) g.drawString(font, Component.translatable("aigirlfriend.screen.status.no_memories"), x + 28, my, 0xFF888888, false);
        int hx = x + W - 10 - s.moodHistory().length * 8;
        for (int i = 0; i < s.moodHistory().length; i++) {
            Mood m = Mood.byId(s.moodHistory()[i]);
            int sx = hx + (s.moodHistory().length - 1 - i) * 8;
            g.fill(sx, y + 8, sx + 6, y + 14, 0xFF000000 | m.color);
            if (mouseX >= sx && mouseX < sx + 6 && mouseY >= y + 8 && mouseY < y + 14) {
                g.renderTooltip(font, Component.translatable("aigirlfriend.screen.status.history", i, Component.translatable(m.key())), mouseX, mouseY);
            }
        }
    }

    private int mx, my;

    private void bar(GuiGraphics g, int icon, String key, float frac, int color, int x, int y, int w) {
        GuiDraw.icon(g, icon, x - 2, y - 4);
        int bw = w - 46;
        GuiDraw.bar(g, x + 18, y, bw, 8, frac, color);
        g.drawString(font, Math.round(Math.max(0f, Math.min(1f, frac)) * 100) + "%", x + 22 + bw, y, 0xFFDDDDDD, false);
        if (mx >= x - 2 && mx < x + w && my >= y - 4 && my < y + 10) {
            g.renderTooltip(font, Component.translatable(key), mx, my);
        }
    }
}
