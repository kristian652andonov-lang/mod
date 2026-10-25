package com.fantasyweapons.client.guide;

import com.fantasyweapons.text.Tr;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Reading the Codex. The screen draws nothing of its own - the book is held open in the world, its pages drawn on it -
 * but takes the mouse and keys: pointing at a page shows what is there, clicking follows links (or turns the page,
 * clicked anywhere else on it), and the wheel, the arrow keys and Backspace turn the pages and go back.
 */
public final class CodexScreen extends Screen {
    CodexScreen() {
        super(Component.literal(Tr.s("Wayfarer's Codex")));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // the world and the book in the reader's hands show through
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        CodexPages.mouseFx = mouseX / (double) width;
        CodexPages.mouseFy = mouseY / (double) height;
        if (CodexPages.pinned != null) {
            double[] at = CodexPages.toWindow((int) CodexPages.pinned[0], CodexPages.pinned[1], CodexPages.pinned[2]);
            CodexPages.mouseFx = at[0];
            CodexPages.mouseFy = at[1];
            mouseX = (int) (at[0] * width);
            mouseY = (int) (at[1] * height);
        }
        PageCanvas.Region over = CodexPages.hovered();
        if (over != null && over.item() != null) graphics.renderTooltip(font, over.item(), mouseX, mouseY);
        else if (over != null && over.text() != null) graphics.renderComponentTooltip(font, over.text(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            PageCanvas.Region over = CodexPages.hovered();
            if (over != null && over.click() != null) {
                over.click().run();
                return true;
            }
            // anywhere else on a page turns it, like a real book
            int page = CodexPages.pageUnderMouse();
            if (page == 0) Codex.previous();
            else if (page == 1) Codex.next();
            if (page >= 0) return true;
        }
        if (button == 1) {
            Codex.goBack();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0) Codex.next();
        else if (scrollY > 0) Codex.previous();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_PAGE_DOWN -> Codex.next();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_PAGE_UP -> Codex.previous();
            case GLFW.GLFW_KEY_BACKSPACE -> Codex.goBack();
            case GLFW.GLFW_KEY_HOME -> Codex.turnTo(0, true);
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        CodexPages.reset();
        Codex.closed();
    }
}
