package com.aigirlfriend.ai.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Low-priority daemon threads that split matrix rows between them. The calling thread works too, so one call
 * uses {@code threads + 1} cores at most. Each worker keeps its own decode scratch buffers.
 */
final class WorkerPool implements AutoCloseable {
    interface RangeTask {
        void run(int from, int to, QMatrix.Scratch scratch);
    }

    private final ExecutorService executor;
    private final int helpers;
    private final ThreadLocal<QMatrix.Scratch> scratch = ThreadLocal.withInitial(QMatrix.Scratch::new);

    WorkerPool(int threads) {
        this.helpers = Math.max(0, threads - 1);
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "AIGirlfriend-LLM-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        };
        this.executor = helpers > 0 ? Executors.newFixedThreadPool(helpers, factory) : null;
    }

    int threads() {
        return helpers + 1;
    }

    /** Runs task over [0, count) in chunks of {@code grain}, spread over all threads; returns when done. */
    void parallelFor(int count, int grain, RangeTask task) {
        if (executor == null || count <= grain) {
            task.run(0, count, scratch.get());
            return;
        }
        AtomicInteger next = new AtomicInteger();
        Runnable worker = () -> {
            QMatrix.Scratch s = scratch.get();
            int from;
            while ((from = next.getAndAdd(grain)) < count) task.run(from, Math.min(count, from + grain), s);
        };
        int jobs = Math.min(helpers, (count + grain - 1) / grain - 1);
        CountDownLatch done = new CountDownLatch(jobs);
        RuntimeException[] error = new RuntimeException[1];
        for (int i = 0; i < jobs; i++) {
            executor.execute(() -> {
                try {
                    worker.run();
                } catch (RuntimeException e) {
                    error[0] = e;
                } finally {
                    done.countDown();
                }
            });
        }
        worker.run();
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
        if (error[0] != null) throw error[0];
    }

    QMatrix.Scratch localScratch() {
        return scratch.get();
    }

    @Override
    public void close() {
        if (executor != null) executor.shutdownNow();
    }
}
