package com.aigirlfriend.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Downloads a file from a pinned, immutable URL: into {@code <name>.part} (resumed with a Range request after an
 * interruption), checked against its size and SHA-256, then atomically moved into place. A file that exists under
 * its final name has therefore always been verified. Blocking; call it from a background thread.
 */
public final class Download {
    /** A file at a URL that never changes (a commit-pinned revision, Maven Central), with its size and checksum. */
    public record Pinned(String url, long size, String sha256) {}

    /** Optional callbacks, invoked on the downloading thread. */
    public interface Listener {
        default void progress(long done, long total) {}

        default void verifying() {}
    }

    /** The server refused or failed the request; {@link #retryAfterSeconds} is set when it said when to come back. */
    public static final class HttpException extends IOException {
        public final int status;
        public final OptionalLong retryAfterSeconds;

        HttpException(int status, OptionalLong retryAfterSeconds) {
            super("Download failed: HTTP " + status);
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    private static final Listener SILENT = new Listener() {};
    private static final long STALL_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static volatile HttpClient http;
    private static volatile ScheduledExecutorService watchdog;

    private Download() {}

    /** Ensures {@code target} holds the pinned file, downloading or resuming it if needed. */
    public static void fetch(Pinned file, Path target, Listener listener) throws IOException, InterruptedException {
        if (listener == null) listener = SILENT;
        if (Files.isRegularFile(target)) return;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        long have = Files.isRegularFile(part) ? Files.size(part) : 0;
        if (have > file.size()) {
            Files.delete(part);
            have = 0;
        }
        if (have < file.size()) transfer(file, part, have, listener);
        verifyAndMove(file, part, target, listener);
    }

    private static void transfer(Pinned file, Path part, long have, Listener listener) throws IOException, InterruptedException {
        Thread reader = Thread.currentThread();
        AtomicLong lastData = new AtomicLong(System.nanoTime());
        AtomicBoolean stalled = new AtomicBoolean();
        // a connection that silently stops delivering would otherwise block this thread forever
        ScheduledFuture<?> watchdog = watchdog().scheduleWithFixedDelay(() -> {
            if (System.nanoTime() - lastData.get() > STALL_NANOS && stalled.compareAndSet(false, true)) reader.interrupt();
        }, 5, 5, TimeUnit.SECONDS);
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(file.url())).header("User-Agent", "AIGirlfriend-mod").GET();
            if (have > 0) req.header("Range", "bytes=" + have + "-");
            HttpResponse<InputStream> resp = client().send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();
            if (code == 416) { // nothing left to fetch: the part file is already complete
                resp.body().close();
                return;
            }
            if (code != 206 && code != 200) {
                resp.body().close();
                throw new HttpException(code, retryAfter(resp));
            }
            if (code == 200 && have > 0) have = 0; // server ignored the range: start over
            try (InputStream in = resp.body();
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                         have > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buf = new byte[1 << 16];
                long done = have;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    lastData.set(System.nanoTime());
                    listener.progress(done, file.size());
                }
            }
        } catch (IOException | InterruptedException e) {
            if (stalled.get()) throw new IOException("Download stalled; it will resume next time", e);
            throw e;
        } finally {
            watchdog.cancel(false);
            if (stalled.get()) Thread.interrupted(); // the interrupt was ours; do not leak it to the caller's thread
        }
    }

    private static void verifyAndMove(Pinned file, Path part, Path target, Listener listener) throws IOException {
        listener.verifying();
        long size = Files.size(part);
        if (size != file.size()) throw new IOException("Download incomplete (" + size + " of " + file.size() + " bytes); it will resume next time");
        if (!sha256(part).equalsIgnoreCase(file.sha256())) {
            Files.deleteIfExists(part);
            throw new IOException("Downloaded file failed its checksum; deleted, it will download again");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Seconds from a Retry-After header in its delta-seconds form (the HTTP-date form is treated as absent). */
    private static OptionalLong retryAfter(HttpResponse<?> resp) {
        try {
            return resp.headers().firstValue("Retry-After").map(v -> OptionalLong.of(Math.max(0, Long.parseLong(v.trim())))).orElse(OptionalLong.empty());
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static ScheduledExecutorService watchdog() {
        ScheduledExecutorService w = watchdog;
        if (w == null) {
            synchronized (Download.class) {
                w = watchdog;
                if (w == null) {
                    w = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "AIGirlfriend Download Watchdog");
                        t.setDaemon(true);
                        return t;
                    });
                    watchdog = w;
                }
            }
        }
        return w;
    }

    private static HttpClient client() {
        HttpClient c = http;
        if (c == null) {
            synchronized (Download.class) {
                c = http;
                if (c == null) {
                    c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
                    http = c;
                }
            }
        }
        return c;
    }
}
