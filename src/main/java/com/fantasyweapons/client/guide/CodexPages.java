package com.fantasyweapons.client.guide;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Puts the open spread's two pages onto the Codex in the reader's hands, and finds what the mouse points at on them.
 * Each page is a {@link PageCanvas} laid on its page part's surface, inside the gilded frame the model draws there; its
 * orientation (which way is up and across, as the reader sees it) is read from where the part was drawn. The same
 * placement, through the hand's projection, turns the mouse into a spot on a page.
 */
public final class CodexPages {
    /** A page's writing area in its part's model space (blocks): inside the glowing frame, and its surface (each page faces out of the book). */
    private static final float X0 = -12.46f / 16, X1 = -1.75f / 16, Y0 = -7.26f / 16, Y1 = 7.24f / 16;
    // just over the stains the artist put on the paper (their tops 0.12 px out), under the gilded frame
    private static final float RIGHT_Z = -0.125f / 16, LEFT_Z = 0.125f / 16;
    /** A turning leaf's two faces: the page it lifts away (in front), and the page it lays down (behind). */
    private static final float LEAF_FRONT_Z = -0.21f / 16, LEAF_BACK_Z = -0.03f / 16;

    private static final Matrix4f[] clip = {new Matrix4f(), new Matrix4f()};
    private static final float[][] mouse = {{-1, -1}, {-1, -1}};
    private static List<PageCanvas.Region> leftRegions = List.of(), rightRegions = List.of();
    private static boolean placed;
    /** The mouse, as a fraction of the window (set by the reading screen each frame). */
    static double mouseFx = -1, mouseFy = -1;
    /** For scripted screenshots: a page spot (page, x, y) the mouse is held over instead of the real one. */
    @Nullable
    public static float[] pinned;

    private CodexPages() {
    }

    static void render(Matrix4f leftPart, Matrix4f rightPart, @Nullable Matrix4f leafPart, MultiBufferSource buffers) {
        CodexBook book = Codex.book();
        if (book == null) return;
        Matrix4f view = RenderSystem.getModelViewMatrix(), projection = RenderSystem.getProjectionMatrix();
        Matrix4f left = page(leftPart, LEFT_Z, view), right = page(rightPart, RIGHT_Z, view);
        clip[0].set(projection).mul(view).mul(left);
        clip[1].set(projection).mul(view).mul(right);
        placed = true;
        locate(0);
        locate(1);
        // items are lit as in the inventory while the pages are drawn
        Lighting.setupForFlatItems();
        PageCanvas l = new PageCanvas(left, buffers, true, mouse[0][0], mouse[0][1]);
        PageCanvas r = new PageCanvas(right, buffers, false, mouse[1][0], mouse[1][1]);
        book.draw(Codex.shownSpread(true), true, l);
        book.draw(Codex.shownSpread(false), false, r);
        if (leafPart != null && Codex.flipProgress() >= 0) {
            // the leaf carries the page it turns over: the old right page and the new left one going forward, the old
            // left page and the new right one going back (each face is drawn facing the reader; the other is hidden
            // behind the leaf)
            boolean forward = Codex.flipForward();
            int from = Codex.flippedFrom(), to = Codex.spread();
            book.draw(forward ? from : to, false, new PageCanvas(page(leafPart, LEAF_FRONT_Z, view), buffers, false, -1, -1));
            book.draw(forward ? to : from, true, new PageCanvas(page(leafPart, LEAF_BACK_Z, view), buffers, true, -1, -1));
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        var level = Minecraft.getInstance().level;
        if (level != null && level.effects().constantAmbientLight()) Lighting.setupNetherLevel();
        else Lighting.setupLevel();
        leftRegions = l.regions;
        rightRegions = r.regions;
    }

    /**
     * A page's sheet on its part: origin at the top left as the reader sees it, x across and y down in sheet units, z out
     * of the page towards the reader (the same handedness as the screen, so it is drawn like a screen).
     */
    private static Matrix4f page(Matrix4f part, float z, Matrix4f view) {
        Matrix4f seen = new Matrix4f(view).mul(part);
        float midX = (X0 + X1) / 2, midY = (Y0 + Y1) / 2;
        Vector3f a = seen.transformPosition(new Vector3f(X0, midY, z)), b = seen.transformPosition(new Vector3f(X1, midY, z));
        float left = a.x < b.x ? X0 : X1, right = left == X0 ? X1 : X0;
        Vector3f t = seen.transformPosition(new Vector3f(midX, Y1, z)), d = seen.transformPosition(new Vector3f(midX, Y0, z));
        float top = t.y > d.y ? Y1 : Y0, bottom = top == Y1 ? Y0 : Y1;
        float out = seen.transformDirection(new Vector3f(0, 0, 1)).z > 0 ? 1 : -1;
        float su = (right - left) / PageCanvas.W, sv = (bottom - top) / PageCanvas.H;
        Matrix4f sheet = new Matrix4f(su, 0, 0, 0, 0, sv, 0, 0, 0, 0, out * Math.abs(su), 0, left, top, z, 1);
        return new Matrix4f(part).mul(sheet);
    }

    /** Where a spot of page {@code i} is in the window, as fractions of its width and height. */
    static double[] toWindow(int i, float x, float y) {
        Vector4f p = clip[i].transform(new Vector4f(x, y, 0, 1));
        return new double[]{(p.x / p.w + 1) / 2, (1 - p.y / p.w) / 2};
    }

    /** Where the mouse is on page {@code i} (sheet units), or off it. */
    private static void locate(int i) {
        mouse[i][0] = mouse[i][1] = -1;
        if (mouseFx < 0) return;
        float[] p = pick(clip[i], mouseFx, mouseFy);
        if (p != null) {
            mouse[i][0] = p[0];
            mouse[i][1] = p[1];
        }
    }

    /** The point of a page's plane under the window point (fx, fy), in its sheet units. */
    @Nullable
    private static float[] pick(Matrix4f clip, double fx, double fy) {
        Matrix4f inverse = new Matrix4f(clip).invert();
        float nx = (float) (fx * 2 - 1), ny = (float) (1 - fy * 2);
        Vector4f near = inverse.transform(new Vector4f(nx, ny, -1, 1)), far = inverse.transform(new Vector4f(nx, ny, 1, 1));
        near.div(near.w);
        far.div(far.w);
        float dz = far.z - near.z;
        if (Math.abs(dz) < 1e-9f) return null;
        float t = -near.z / dz;
        return new float[]{near.x + (far.x - near.x) * t, near.y + (far.y - near.y) * t};
    }

    /** What the mouse points at on either page, if anything. */
    @Nullable
    static PageCanvas.Region hovered() {
        if (!placed) return null;
        for (int i = 0; i < 2; i++) {
            float x = mouse[i][0], y = mouse[i][1];
            if (x < 0 || y < 0 || x > PageCanvas.W || y > PageCanvas.H) continue;
            for (PageCanvas.Region r : i == 0 ? leftRegions : rightRegions) if (r.contains(x, y)) return r;
        }
        return null;
    }

    /** Which page the mouse is over: 0 left, 1 right, -1 neither. */
    static int pageUnderMouse() {
        for (int i = 0; i < 2; i++) {
            float x = mouse[i][0], y = mouse[i][1];
            if (x >= 0 && y >= 0 && x <= PageCanvas.W && y <= PageCanvas.H) return i;
        }
        return -1;
    }

    static void reset() {
        placed = false;
        mouseFx = mouseFy = -1;
    }
}
