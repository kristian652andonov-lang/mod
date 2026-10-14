package com.fantasyweapons.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of the Codex being drawn onto the book: a {@value #W} by {@value #H} sheet (y down, like a screen) placed on the
 * page's surface, with a writing cursor for laying out text, pictures and recipes from the top. What can be pointed at -
 * items (their tooltips) and links - is noted as {@link Region}s for the reader's mouse.
 */
public final class PageCanvas {
    public static final float W = 112, H = 152, MARGIN = 7;
    /**
     * Where writing starts and must end: the page's gilded frame has an ornament at the top and the bottom of its middle
     * (sheet units 7 to 15 and 139 to 143, between x 44 and 68) and a ruled line just inside each edge.
     */
    public static final float TOP = 17, BOTTOM = 135;
    /** The page's ink: dark brown, a faded brown for notes, and a deep red and gold for headings. */
    public static final int INK = 0xFF3A2614, FADED = 0xFF7A5E3E, RED = 0xFF7A1E14, GOLD = 0xFF9A6A12, RULE = 0x663A2614;

    /** Something on the page the mouse can point at: an item (its tooltip), some words, or a link. */
    public record Region(float x0, float y0, float x1, float y1, @Nullable ItemStack item, @Nullable List<Component> text, @Nullable Runnable click) {
        boolean contains(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    private final Matrix4f content;
    private final MultiBufferSource buffers;
    private final Font font = Minecraft.getInstance().font;
    private final float mouseX, mouseY;
    final List<Region> regions = new ArrayList<>();
    final boolean left;
    /** The writing cursor: where the next line goes. */
    public float y = TOP;

    PageCanvas(Matrix4f content, MultiBufferSource buffers, boolean left, float mouseX, float mouseY) {
        this.content = content;
        this.buffers = buffers;
        this.left = left;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** How far out from the page everything is drawn, besides its own offset (sheet units): raised for the thumb tabs. */
    public float lift;
    /** Whether items are drawn: not on the hidden face of a turning leaf. */
    public boolean items = true;
    /** Whether this is a turning leaf's face (the thumb tabs stay on the book's right page). */
    public boolean leaf;

    public float inner() {
        return W - 2 * MARGIN;
    }

    public boolean hovered(float x0, float y0, float x1, float y1) {
        return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
    }

    // ---- drawing ----

    public void text(FormattedCharSequence text, float x, float y, int color, float scale) {
        Matrix4f m = new Matrix4f(content).translate(x, y, 0.05f + lift).scale(scale, scale, 1);
        font.drawInBatch(text, 0, 0, color, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    public void text(String text, float x, float y, int color, float scale) {
        text(Component.literal(text).getVisualOrderText(), x, y, color, scale);
    }

    public float width(String text, float scale) {
        return font.width(text) * scale;
    }

    public void centered(String text, float y, int color, float scale) {
        text(text, (W - width(text, scale)) / 2, y, color, scale);
    }

    /** A filled rectangle (colour with alpha). */
    public void fill(float x0, float y0, float x1, float y1, int argb) {
        fill(x0, y0, x1, y1, argb, 0.02f);
    }

    /** A filled rectangle {@code z} out from the page (sheet units). */
    public void fill(float x0, float y0, float x1, float y1, int argb, float z) {
        z += lift;
        VertexConsumer v = buffers.getBuffer(RenderType.gui());
        v.addVertex(content, x0, y0, z).setColor(argb);
        v.addVertex(content, x0, y1, z).setColor(argb);
        v.addVertex(content, x1, y1, z).setColor(argb);
        v.addVertex(content, x1, y0, z).setColor(argb);
    }

    /** A rectangle's outline, {@code t} thick, a layer above the fills (so the two never flicker over each other). */
    public void frame(float x0, float y0, float x1, float y1, float t, int argb) {
        fill(x0, y0, x1, y0 + t, argb, LINES);
        fill(x0, y1 - t, x1, y1, argb, LINES);
        fill(x0, y0 + t, x0 + t, y1 - t, argb, LINES);
        fill(x1 - t, y0 + t, x1, y1 - t, argb, LINES);
    }

    /**
     * A grid of {@code cols} by {@code rows} cells: its shading laid once, then each of its lines once, a layer above (cells
     * drawn one by one would lay neighbouring edges twice, over each other, and they flicker).
     */
    public void grid(float x, float y, int cols, int rows, float cell, float t, int line, int shade) {
        float w = cols * cell, h = rows * cell;
        if ((shade >>> 24) != 0) fill(x, y, x + w, y + h, shade);
        for (int c = 0; c <= cols; c++) {
            float lx = x + c * cell - t / 2;
            fill(lx, y - t / 2, lx + t, y + h + t / 2, line, LINES);
        }
        for (int r = 0; r <= rows; r++) {
            float ly = y + r * cell - t / 2;
            // across, between the uprights, so no spot is drawn twice
            for (int c = 0; c < cols; c++) fill(x + c * cell + t / 2, ly, x + (c + 1) * cell - t / 2, ly + t, line, LINES);
        }
    }

    /** The layer outlines and grid lines are drawn at, over the fills and under the writing (sheet units). */
    private static final float LINES = 0.035f;

    /**
     * An item drawn as in a slot, {@code size} across, with its tooltip when pointed at. It is pressed flat onto the page
     * (a block's cube would otherwise stand out of the tilted paper and look shifted off its place) and lit exactly as in
     * the inventory, flat items from the front and blocks from above, whatever way the page faces.
     */
    public void item(ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty() || !items) return;
        var model = Minecraft.getInstance().getItemRenderer().getModel(stack, Minecraft.getInstance().level, null, 0);
        PoseStack pose = new PoseStack();
        Matrix4f m = new Matrix4f(content).translate(x + size / 2, y + size / 2, 0.16f + lift).scale(size, -size, DEPTH);
        pose.last().pose().set(m);
        // the normals as in a slot on the screen (y flipped like the pose), not turned with the page
        pose.last().normal().set(new Matrix3f().scaling(1, -1, 1));
        boolean flatLight = !model.usesBlockLight();
        if (flatLight) com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
        else com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, buffers, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, model);
        // drawn now, while its light is set
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        if (stack.getCount() > 1) {
            String n = String.valueOf(stack.getCount());
            float s = size / 16f * 0.7f;
            Matrix4f t = new Matrix4f(content).translate(0, 0, 0.9f + lift);
            font.drawInBatch(n, (x + size - font.width(n) * s) / s, (y + size - 6 * s) / s, 0xFFFFFFFF, true, t.scale(s, s, 1), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        }
        regions.add(new Region(x, y, x + size, y + size, stack, null, null));
    }

    /**
     * The depth an item keeps when pressed onto the page, whatever its size (sheet units): thin enough that it stays under
     * a leaf lying on the page, so the page beneath a turning leaf never shows through it.
     */
    private static final float DEPTH = 0.2f;

    /** Icon {@code index} of a sheet of {@code count} square icons in a row, {@code size} across, in its own colours. */
    public void icon(net.minecraft.resources.ResourceLocation sheet, int index, int count, float x, float y, float size) {
        VertexConsumer v = buffers.getBuffer(RenderType.text(sheet));
        float u0 = index / (float) count, u1 = (index + 1) / (float) count, z = 0.06f + lift;
        v.addVertex(content, x, y, z).setColor(0xFFFFFFFF).setUv(u0, 0).setLight(LightTexture.FULL_BRIGHT);
        v.addVertex(content, x, y + size, z).setColor(0xFFFFFFFF).setUv(u0, 1).setLight(LightTexture.FULL_BRIGHT);
        v.addVertex(content, x + size, y + size, z).setColor(0xFFFFFFFF).setUv(u1, 1).setLight(LightTexture.FULL_BRIGHT);
        v.addVertex(content, x + size, y, z).setColor(0xFFFFFFFF).setUv(u1, 0).setLight(LightTexture.FULL_BRIGHT);
    }

    /** Notes a region the mouse can point at. */
    public void region(float x0, float y0, float x1, float y1, @Nullable List<Component> text, @Nullable Runnable click) {
        regions.add(new Region(x0, y0, x1, y1, null, text, click));
    }

    // ---- writing at the cursor ----

    /** A heading in red, centred, with a gilded rule under it. */
    public void heading(String text) {
        float s = text.length() > 18 ? 0.85f : 1f;
        centered(text, y, RED, s);
        y += 9 * s + 1;
        rule();
    }

    /** A gilded rule across the page with a small diamond in the middle. */
    public void rule() {
        float mid = W / 2;
        fill(MARGIN + 6, y + 1, mid - 3, y + 1.6f, 0xAA9A6A12);
        fill(mid + 3, y + 1, W - MARGIN - 6, y + 1.6f, 0xAA9A6A12);
        fill(mid - 1.2f, y + 0.1f, mid + 1.2f, y + 2.5f, 0xFF9A6A12);
        y += 5;
    }

    /** Wrapped text at the cursor. */
    public void para(String text, int color, float scale) {
        para(Component.literal(text), color, scale);
    }

    public void para(FormattedText text, int color, float scale) {
        for (FormattedCharSequence line : font.split(text, (int) (inner() / scale))) {
            text(line, MARGIN, y, color, scale);
            y += 9 * scale;
        }
        y += 2;
    }

    /** A line of text the reader can click, underlined and reddened under the mouse. */
    public void link(String text, float x, float yy, float scale, Runnable click) {
        float w = width(text, scale), h = 8 * scale;
        boolean over = hovered(x - 1, yy - 1, x + w + 1, yy + h + 1);
        text(text, x, yy, over ? RED : INK, scale);
        if (over) fill(x, yy + h + 0.4f, x + w, yy + h + 0.9f, 0xFF7A1E14);
        region(x - 1, yy - 1, x + w + 1, yy + h + 1, null, click);
    }

    public void gap(float h) {
        y += h;
    }

    /** Lines that would still fit under the cursor at this scale. */
    public boolean fits(float height) {
        return y + height <= BOTTOM;
    }
}
