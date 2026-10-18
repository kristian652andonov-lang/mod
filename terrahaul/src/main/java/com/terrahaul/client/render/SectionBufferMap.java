package com.terrahaul.client.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;

/**
 * The vertex buffers of one render section, created when the section first receives geometry in
 * a layer instead of one per chunk layer when the section is created.
 * <p>
 * Vanilla gives every render section a hash map with five vertex buffers: 528 bytes before the
 * section holds anything. The renderer keeps a section for every 16-block cube within render
 * distance horizontally but over the whole world height, while it only ever draws sections within
 * one render distance above and below the camera. A Terrahaul world is 159 sections tall instead
 * of 24, so at render distance 16 there are 173,000 sections and most never hold a block. Empty,
 * this map is 24 bytes: 87 MB less heap at render distance 16 and 338 MB less at 32 (measured
 * object layouts). Usually only one or two of the five layers ever get a buffer.
 * <p>
 * {@link #get} is the only accessor vanilla uses ({@code RenderSection.getBuffer}). It creates the
 * buffer on first request, on the render thread when drawing and on the compile workers when they
 * queue finished geometry for upload. Creation is synchronised, and the buffers live in an array
 * that is replaced, never modified, once published, so lookups need no lock. Creating a buffer off
 * the render thread is safe because {@code VertexBufferMixin} defers its OpenGL objects to the
 * first bind, which the upload queue performs on the render thread.
 * <p>
 * {@link #release} (vanilla's {@code releaseBuffers}, when the view area is thrown away) closes
 * the buffers for good: as with vanilla's closed buffers, later requests get a closed buffer, so an
 * upload still in flight is dropped instead of creating OpenGL objects that nobody would delete.
 * {@link #discard} frees the buffers of a section that is being moved out of drawing range; new
 * ones are created if it is compiled again. The map lists only the buffers created so far and
 * cannot be modified through the {@code Map} interface.
 */
public final class SectionBufferMap extends AbstractMap<RenderType, VertexBuffer> {
    private static final RenderType[] LAYERS = RenderType.chunkBufferLayers().toArray(new RenderType[0]);
    /** The buffers of a released map. */
    private static final VertexBuffer[] RELEASED = new VertexBuffer[0];

    /** Buffers by layer index, or null before the first one; never modified once published. */
    @Nullable
    private volatile VertexBuffer[] buffers;

    @Override
    @Nullable
    public VertexBuffer get(Object key) {
        int layer = layerIndex(key);
        if (layer < 0) {
            return null;
        }
        VertexBuffer[] current = this.buffers;
        if (current != null && current != RELEASED && current[layer] != null) {
            return current[layer];
        }
        return this.create(layer);
    }

    private synchronized VertexBuffer create(int layer) {
        VertexBuffer[] current = this.buffers;
        if (current == RELEASED) {
            VertexBuffer closed = new VertexBuffer(VertexBuffer.Usage.STATIC);
            closed.close();
            return closed;
        }
        if (current != null && current[layer] != null) {
            return current[layer];
        }
        VertexBuffer[] next = current == null ? new VertexBuffer[LAYERS.length] : current.clone();
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        next[layer] = buffer;
        this.buffers = next;
        RenderMemory.sectionBufferCreated(current == null);
        return buffer;
    }

    /** True when at least one buffer exists. */
    public boolean hasBuffers() {
        VertexBuffer[] current = this.buffers;
        return current != null && current != RELEASED;
    }

    /** Closes the buffers for good; later requests return closed buffers. Render thread only. */
    public void release() {
        VertexBuffer[] old;
        synchronized (this) {
            old = this.buffers;
            this.buffers = RELEASED;
        }
        close(old);
    }

    /** Closes the buffers; later requests create new ones. Render thread only. */
    public void discard() {
        VertexBuffer[] old;
        synchronized (this) {
            old = this.buffers;
            if (old == RELEASED) {
                return;
            }
            this.buffers = null;
        }
        close(old);
    }

    private static void close(@Nullable VertexBuffer[] buffers) {
        if (buffers == null || buffers == RELEASED) {
            return;
        }
        int closed = 0;
        for (VertexBuffer buffer : buffers) {
            if (buffer != null) {
                buffer.close();
                closed++;
            }
        }
        RenderMemory.sectionBuffersClosed(closed);
    }

    private static int layerIndex(Object key) {
        for (int i = 0; i < LAYERS.length; i++) {
            if (LAYERS[i] == key) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int size() {
        return this.values().size();
    }

    @Override
    public Collection<VertexBuffer> values() {
        VertexBuffer[] current = this.buffers;
        List<VertexBuffer> values = new ArrayList<>(LAYERS.length);
        if (current != null) {
            for (VertexBuffer buffer : current) {
                if (buffer != null) {
                    values.add(buffer);
                }
            }
        }
        return Collections.unmodifiableList(values);
    }

    @Override
    public Set<Entry<RenderType, VertexBuffer>> entrySet() {
        VertexBuffer[] current = this.buffers;
        Set<Entry<RenderType, VertexBuffer>> entries = new LinkedHashSet<>();
        if (current != null) {
            for (int i = 0; i < current.length; i++) {
                if (current[i] != null) {
                    entries.add(new SimpleImmutableEntry<>(LAYERS[i], current[i]));
                }
            }
        }
        return Collections.unmodifiableSet(entries);
    }
}
