package com.terrahaul.client.render;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Counts the vertex buffers that hold OpenGL objects and the buffers held by render sections, for
 * the development memory log ({@code -PmemoryLog}). The counters are updated where the buffers are
 * created and closed ({@code VertexBufferMixin}, {@link SectionBufferMap}) and cost one atomic
 * increment each, far less than the OpenGL calls next to them.
 */
public final class RenderMemory {
    private static final AtomicInteger GL_BUFFERS = new AtomicInteger();
    private static final AtomicInteger SECTION_BUFFERS = new AtomicInteger();
    private static final AtomicInteger SECTIONS_WITH_BUFFERS = new AtomicInteger();

    private RenderMemory() {
    }

    /** A vertex buffer created its three OpenGL objects. */
    public static void glBufferCreated() {
        GL_BUFFERS.incrementAndGet();
    }

    /** A vertex buffer that had OpenGL objects was closed. */
    public static void glBufferDeleted() {
        GL_BUFFERS.decrementAndGet();
    }

    static void sectionBufferCreated(boolean firstOfSection) {
        SECTION_BUFFERS.incrementAndGet();
        if (firstOfSection) {
            SECTIONS_WITH_BUFFERS.incrementAndGet();
        }
    }

    static void sectionBuffersClosed(int count) {
        if (count > 0) {
            SECTION_BUFFERS.addAndGet(-count);
            SECTIONS_WITH_BUFFERS.decrementAndGet();
        }
    }

    /** One line for the memory log: render sections, the ones with buffers, and OpenGL buffers. */
    public static String describe(Minecraft mc) {
        long sections = mc.level == null ? 0 : Math.round(mc.levelRenderer.getTotalSections());
        return String.format(Locale.ROOT, "render sections %d, with buffers %d (%d section buffers), vertex buffers with OpenGL objects %d",
            sections, SECTIONS_WITH_BUFFERS.get(), SECTION_BUFFERS.get(), GL_BUFFERS.get());
    }
}
