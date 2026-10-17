package com.aigirlfriend.client.screen;

import com.aigirlfriend.client.ClientState;
import com.aigirlfriend.network.StatusSnapshot;
import com.aigirlfriend.network.payload.TalkPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Free-form conversation with her. Her answers appear here and as speech bubbles. */
public class TalkScreen extends Screen {
    private static final int W = 320, H = 200;
    private final int entityId;
    private EditBox input;

    public TalkScreen(int entityId) {
        super(Component.translatable("aigirlfriend.screen.talk"));
        this.entityId = entityId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        input = new EditBox(font, x + 8, y + H - 26, W - 80, 18, Component.translatable("aigirlfriend.screen.talk.input"));
        input.setMaxLength(256);
        input.setHint(Component.translatable("aigirlfriend.screen.talk.hint"));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.translatable("aigirlfriend.screen.talk.send"), b -> send()).bounds(x + W - 68, y + H - 26, 60, 18).build());
        setInitialFocus(input);
    }

    private void send() {
        String msg = input.getValue().trim();
        if (msg.isEmpty()) return;
        PacketDistributor.sendToServer(new TalkPayload(entityId, msg));
        ClientState.log(entityId, true, msg);
        input.setValue("");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        super.render(g, mouseX, mouseY, partialTick);
        GuiDraw.panel(g, x, y, x + W, y + H - 32, 0xD0201420, 0xFFFF8FC8);
        StatusSnapshot s = ClientState.status(entityId);
        String name = s != null ? s.name() : "...";
        g.drawString(font, Component.translatable("aigirlfriend.screen.talk.title", name), x + 8, y + 6, 0xFFFFC2E2, false);
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (ClientState.LogLine l : ClientState.log(entityId)) {
            String prefix = l.fromPlayer() ? "You: " : name + ": ";
            for (FormattedCharSequence seq : font.split(Component.literal(prefix + l.text()), W - 20)) {
                lines.add(seq);
                colors.add(l.fromPlayer() ? 0xFFB0D8FF : 0xFFFFD6EC);
            }
        }
        ClientState.Speech sp = ClientState.speech(entityId);
        if (sp != null && sp.typing()) {
            lines.add(Component.literal(name + " is typing" + ".".repeat((int) (ClientState.clientTicks / 6 % 3) + 1)).getVisualOrderText());
            colors.add(0xFF999999);
        }
        int maxLines = (H - 32 - 24) / 10;
        int start = Math.max(0, lines.size() - maxLines);
        for (int i = start; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + 10, y + 20 + (i - start) * 10, colors.get(i), false);
        }
    }
}
