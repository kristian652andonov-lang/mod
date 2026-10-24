package com.fantasyweapons.client.gui;

import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.input.KeyBindings;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.playerclass.ClassService;
import com.fantasyweapons.playerclass.PlayerClass;
import com.fantasyweapons.quest.Quest;
import com.fantasyweapons.quest.QuestService;
import com.fantasyweapons.quest.Quests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Two buttons in the player's inventory, beside the recipe book's and framed like it: the Quest Journal (a sealed scroll,
 * with a gold mark while a quest waits to be claimed) and the class menu (the player's class's sign). They keep beside
 * the recipe book's button when the book opens and moves the inventory aside.
 */
public final class InventoryButtons {
    private static ResourceLocation sprite(String name, boolean hover) {
        return com.fantasyweapons.FantasyWeapons.id("inventory/" + name + (hover ? "_highlighted" : ""));
    }

    private InventoryButtons() {
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        event.addListener(new Journal(inventory, 126));
        event.addListener(new Classes(inventory, 148));
    }

    private abstract static class Small extends AbstractButton {
        private final InventoryScreen screen;
        private final int offset;

        Small(InventoryScreen screen, int offset, Component name) {
            super(screen.getGuiLeft() + offset, screen.height / 2 - 22, 20, 18, name);
            this.screen = screen;
            this.offset = offset;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // beside the recipe book's button, wherever the open book has moved the inventory
            setX(screen.getGuiLeft() + offset);
            setY(screen.height / 2 - 22);
            g.blitSprite(sprite(spriteName(), isHoveredOrFocused()), getX(), getY(), width, height);
            mark(g, getX(), getY());
            setTooltip(Tooltip.create(tooltip()));
        }

        /** The button's sprite (framed icon) in {@code textures/gui/sprites/inventory}. */
        abstract String spriteName();

        /** Anything drawn over the sprite. */
        void mark(GuiGraphics g, int x, int y) {
        }

        abstract Component tooltip();

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class Journal extends Small {
        Journal(InventoryScreen screen, int offset) {
            super(screen, offset, Component.literal("Quest Journal"));
        }

        @Override
        public void onPress() {
            Minecraft.getInstance().setScreen(new QuestScreen());
        }

        private static int waiting() {
            var player = Minecraft.getInstance().player;
            int n = 0;
            for (Quest q : Quests.all()) {
                if (!QuestService.data(player).claimed(q.id()) && QuestService.unlocked(player, q) && QuestService.complete(player, q)) n++;
            }
            return n;
        }

        @Override
        String spriteName() {
            return "quests";
        }

        @Override
        void mark(GuiGraphics g, int x, int y) {
            if (waiting() == 0) return;
            // a quest waits to be claimed: a gold gem in the corner, glinting
            float pulse = 0.6f + 0.4f * (float) Math.sin(net.minecraft.Util.getMillis() / 180.0);
            UiDraw.glow(g, x + 17, y + 3, 8, Colors.alpha(0.8f * pulse, 0xF2C46B));
            g.fill(x + 15, y + 1, x + 19, y + 5, 0xFF3A2410);
            g.fill(x + 16, y + 2, x + 18, y + 4, Colors.alpha(1f, 0xFFD25A));
        }

        @Override
        Component tooltip() {
            int n = waiting();
            Component title = Component.literal("Quest Journal").withColor(0xF2C46B)
                    .append(Component.literal(" (" + KeyBindings.QUESTS.getTranslatedKeyMessage().getString() + ")").withColor(0xB9AC92));
            return n == 0 ? title : title.copy().append(Component.literal("\n" + n + (n == 1 ? " quest" : " quests") + " to claim").withColor(0x8FE08A));
        }
    }

    private static final class Classes extends Small {
        Classes(InventoryScreen screen, int offset) {
            super(screen, offset, Component.literal("Class"));
        }

        @Override
        public void onPress() {
            Minecraft.getInstance().setScreen(new ClassScreen());
        }

        @Override
        String spriteName() {
            PlayerClass c = ClassService.of(Minecraft.getInstance().player);
            return "class_" + (c == PlayerClass.NONE ? "none" : c.id());
        }

        @Override
        Component tooltip() {
            PlayerClass c = ClassService.of(Minecraft.getInstance().player);
            return Component.literal("Class: ").withColor(0xB9AC92).append(Component.literal(c.displayName()).withColor(c.color()))
                    .append(Component.literal(" (" + KeyBindings.CLASSES.getTranslatedKeyMessage().getString() + ")").withColor(0xB9AC92));
        }
    }
}
