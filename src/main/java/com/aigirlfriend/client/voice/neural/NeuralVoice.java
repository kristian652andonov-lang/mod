package com.aigirlfriend.client.voice.neural;

import com.aigirlfriend.util.Download;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Lifecycle of her natural (neural) voice: a one-time background download of the runtime, model, voices and
 * lexicons, loading on the voice worker, then speaking lines until it is no longer wanted. Until it is ready, or
 * if this computer cannot run it, callers keep using her other voice. Never blocks the game thread.
 */
public final class NeuralVoice {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SAMPLE_RATE = KokoroModel.SAMPLE_RATE;
    private static final long[] RETRY_SECONDS = {30, 120, 600, 1800, 3600};
    private static final long PRESENCE_CHECK_MS = 10_000;
    private static final int MAX_RENDER_FAILURES = 3;
    /**
     * Written (with the process id) while the runtime and model load, deleted once loaded, on a Java-level failure
     * and on any orderly exit. Found from a process that is gone, it means a native crash during loading took the
     * game down, so the voice is not loaded again (and crashes again) every session until the player deletes it.
     */
    private static final String CRASH_MARKER = "loading.marker";

    public enum State { IDLE, UNSUPPORTED, DOWNLOADING, WAITING_TO_RETRY, LOADING, READY, FAILED }

    /** What the config asks for, read on the game thread: voice folder, voice id or blend, ONNX threads (0 = auto). */
    public record Settings(Path dir, String voice, int threads) {}

    /** A chat notice for the player: a translation key and its single argument. */
    public record Notice(String key, String arg) {}

    /**
     * Receives a line's audio chunk by chunk; returns false to stop speaking (the line was cancelled). {@code play}
     * turns true once the line may start playing: from then on the rest renders before what is ready runs out.
     */
    public interface Sink {
        boolean accept(float[] samples, char boundary, boolean last, boolean play);
    }

    private static volatile State state = State.IDLE;
    private static volatile float progress;
    private static volatile String detail = "";
    @Nullable
    private static volatile KokoroVoice engine;
    @Nullable
    private static Settings loaded;
    private static long retryAt, lastPresenceCheck;
    private static int downloadFailures, renderFailures;
    private static boolean announcedProblem, announcedReady, markerHook;
    /** Whether a voice mode still wants her natural voice, and whether it is the only voice it allows (NEURAL). */
    private static volatile boolean wanted, explicit;
    @Nullable
    private static volatile Path marker;
    /** Size of this session's download, for the "ready" notice; null when nothing was downloaded. */
    @Nullable
    private static String downloaded;
    private static final Queue<Notice> NOTICES = new ConcurrentLinkedQueue<>();

    private NeuralVoice() {}

    public static State state() {
        return state;
    }

    public static boolean ready() {
        return state == State.READY;
    }

    /** One line for logs and debug screens. */
    public static String status() {
        return switch (state) {
            case DOWNLOADING -> String.format(Locale.ROOT, "downloading %d%%", Math.round(progress * 100));
            case WAITING_TO_RETRY -> "download failed, retrying (" + detail + ")";
            case READY -> {
                KokoroVoice v = engine;
                yield "ready (" + detail + (v == null ? "" : String.format(Locale.ROOT, ", renders at %.2fx real time", 1 / Math.max(0.01f, v.rtf()))) + ")";
            }
            case FAILED, UNSUPPORTED -> state.name().toLowerCase(Locale.ROOT) + ": " + detail;
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }

    @Nullable
    public static Notice takeNotice() {
        return NOTICES.poll();
    }

    /**
     * Called every tick while the voice mode wants her natural voice: starts downloading (only if
     * {@code mayDownload}) or loading when needed, and reloads after a thread count change. Returns at once.
     */
    public static synchronized void ensureStarted(Settings settings, boolean mayDownload, boolean explicit, Executor worker) {
        wanted = true;
        NeuralVoice.explicit = explicit;
        switch (state) {
            case DOWNLOADING, LOADING, UNSUPPORTED, FAILED -> {
                return;
            }
            case READY -> {
                if (loaded != null && loaded.threads() != settings.threads()) startLoad(settings, worker);
                return;
            }
            case WAITING_TO_RETRY -> {
                if (System.currentTimeMillis() < retryAt) return;
            }
            case IDLE -> {
                if (!mayDownload && System.currentTimeMillis() - lastPresenceCheck < PRESENCE_CHECK_MS) return;
            }
        }
        if (OrtRuntime.platform() == null) {
            state = State.UNSUPPORTED;
            detail = System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch");
            LOGGER.info("Companion voice: her natural voice is not available on {}; using her other voice", detail);
            if (explicit) problem("aigirlfriend.voice.unsupported", detail);
            return;
        }
        lastPresenceCheck = System.currentTimeMillis();
        List<NeuralFiles.Entry> missing = missing(settings.dir());
        if (!missing.isEmpty() && !mayDownload) {
            state = State.IDLE;
            return;
        }
        String crt = VcRuntime.check().problem(); // before downloading anything this computer could not run
        if (crt != null) {
            state = State.UNSUPPORTED;
            detail = crt;
            LOGGER.warn("Companion voice: her natural voice needs msvcp140.dll 14.40 or newer from the Microsoft Visual C++ "
                    + "Redistributable ({}); using her other voice", crt);
            problem("aigirlfriend.voice.vcredist", crt);
            return;
        }
        if (missing.isEmpty()) startLoad(settings, worker);
        else startDownload(settings, missing, worker);
    }

    /**
     * Frees the model (hundreds of MB of native memory) when her natural voice is no longer wanted. A load in progress
     * closes what it loaded as soon as it finishes, and a download in progress stops before the next file.
     */
    public static synchronized void unload(Executor worker) {
        wanted = false;
        if (state == State.LOADING) state = State.IDLE;
        if (state != State.READY) return;
        state = State.IDLE;
        submit(worker, NeuralVoice::closeEngine);
    }

    /**
     * Speaks a cleaned line (voice worker only). {@code speedScale} multiplies the mood's pace; {@code playbackScale}
     * is how much longer the audio will play than rendered (below 1 when a pitch change resamples it). Throws if the
     * voice is not ready or fails; after repeated failures it gives up for this session.
     */
    public static void speak(String text, int emotion, float speedScale, float playbackScale, String voice, Sink sink) throws Exception {
        KokoroVoice v = engine;
        if (v == null || state != State.READY) throw new IllegalStateException("her natural voice is not ready");
        try {
            v.setVoice(VoiceSpec.parse(voice));
            v.speak(text, emotion, speedScale, playbackScale, sink);
            renderFailures = 0;
        } catch (Exception e) {
            if (++renderFailures >= MAX_RENDER_FAILURES) {
                fail("it kept failing (" + e + ")", e);
                closeEngine();
            }
            throw e;
        }
    }

    /** Auto thread count: a quarter of the logical processors, 1 to 4, so the game keeps the rest. */
    static int threads(int configured) {
        if (configured > 0) return configured;
        return Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
    }

    private static List<NeuralFiles.Entry> missing(Path dir) {
        List<NeuralFiles.Entry> out = new ArrayList<>();
        for (NeuralFiles.Entry e : NeuralFiles.all()) {
            if (!Files.isRegularFile(e.in(dir))) out.add(e);
        }
        return out;
    }

    private static void startDownload(Settings settings, List<NeuralFiles.Entry> missing, Executor worker) {
        state = State.DOWNLOADING;
        progress = 0;
        long total = missing.stream().mapToLong(e -> e.pin().size()).sum();
        if (downloaded == null) {
            downloaded = megabytes(total);
            NOTICES.add(new Notice("aigirlfriend.voice.downloading", downloaded));
        }
        Thread t = new Thread(() -> download(settings, missing, total, worker), "AIGirlfriend Voice Download");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    private static void download(Settings settings, List<NeuralFiles.Entry> missing, long total, Executor worker) {
        LOGGER.info("Companion voice: downloading her natural voice ({}) to {}", megabytes(total), settings.dir());
        long done = 0;
        try {
            for (NeuralFiles.Entry e : missing) {
                if (!wanted) break;
                long before = done;
                Download.fetch(e.pin(), e.in(settings.dir()), new Download.Listener() {
                    @Override
                    public void progress(long bytes, long size) {
                        progress = (float) (before + bytes) / total;
                    }
                });
                done += e.pin().size();
            }
            synchronized (NeuralVoice.class) {
                downloadFailures = 0;
                if (!wanted || !missing(settings.dir()).isEmpty()) {
                    LOGGER.info("Companion voice: her natural voice is no longer wanted; the rest of the download waits until it is");
                    state = State.IDLE;
                    return;
                }
                LOGGER.info("Companion voice: download complete and verified");
                startLoad(settings, worker);
            }
        } catch (Exception e) {
            synchronized (NeuralVoice.class) {
                long wait = RETRY_SECONDS[Math.min(downloadFailures, RETRY_SECONDS.length - 1)];
                if (e instanceof Download.HttpException http && http.retryAfterSeconds.isPresent()) {
                    wait = Math.max(wait, Math.min(3600, http.retryAfterSeconds.getAsLong()));
                }
                downloadFailures++;
                retryAt = System.currentTimeMillis() + wait * 1000;
                detail = e.getMessage() == null ? e.toString() : e.getMessage();
                state = State.WAITING_TO_RETRY;
                if (downloadFailures == 1) LOGGER.warn("Companion voice: downloading her natural voice failed; retrying in {} s", wait, e);
                else LOGGER.warn("Companion voice: download failed again ({}); retrying in {} s", detail, wait);
            }
        }
    }

    private static void startLoad(Settings settings, Executor worker) {
        Path crashed = settings.dir().resolve(CRASH_MARKER);
        if (crashedWhileLoading(crashed)) {
            state = State.FAILED;
            detail = "it crashed the game the last time it loaded";
            LOGGER.warn("Companion voice: her natural voice crashed the game the last time it loaded, so it stays off; "
                    + "delete {} to try again", crashed.toAbsolutePath());
            problem("aigirlfriend.voice.crashed", crashed.toAbsolutePath().toString());
            return;
        }
        state = State.LOADING;
        if (!submit(worker, () -> load(settings))) state = State.IDLE;
    }

    /** Voice worker: (re)loads the runtime and model. */
    private static void load(Settings settings) {
        closeEngine();
        try {
            long t0 = System.currentTimeMillis();
            Path dir = settings.dir();
            markLoading(dir.resolve(CRASH_MARKER));
            OrtRuntime runtime = OrtRuntime.load(NeuralFiles.RUNTIME.in(dir), dir.resolve("native"));
            int threads = threads(settings.threads());
            KokoroVoice voice = KokoroVoice.load(runtime, dir, VoiceSpec.parse(settings.voice()), threads);
            clearMarker(); // loaded and spoken once: whatever could crash natively has run
            synchronized (NeuralVoice.class) {
                engine = voice;
                if (state != State.LOADING) { // let go while it was loading
                    closeEngine();
                    return;
                }
                state = State.READY;
                loaded = settings;
                renderFailures = 0;
                detail = String.format(Locale.ROOT, "%s, %d threads, loaded in %d ms", voice.spec(), threads, System.currentTimeMillis() - t0);
            }
            LOGGER.info("Companion voice: her natural voice is ready ({})", detail);
            if (downloaded != null && !announcedReady) {
                announcedReady = true;
                NOTICES.add(new Notice("aigirlfriend.voice.ready", downloaded));
            }
        } catch (Throwable t) {
            clearMarker(); // an exception is not a crash: the next session may try again
            fail(t.toString(), t);
        }
    }

    /** A marker from a process that is gone (a live one is another game instance loading at the same moment). */
    private static boolean crashedWhileLoading(Path marker) {
        if (!Files.exists(marker)) return false;
        try {
            long pid = Long.parseLong(Files.readString(marker).strip());
            return pid != ProcessHandle.current().pid() && ProcessHandle.of(pid).map(p -> !p.isAlive()).orElse(true);
        } catch (IOException | NumberFormatException e) {
            return true; // cut short while being written
        }
    }

    private static synchronized void markLoading(Path path) throws IOException {
        if (!markerHook) {
            markerHook = true;
            Runtime.getRuntime().addShutdownHook(new Thread(NeuralVoice::clearMarker, "AIGirlfriend Voice Exit")); // not run on a native crash
        }
        Files.writeString(path, Long.toString(ProcessHandle.current().pid()));
        marker = path;
    }

    private static void clearMarker() {
        Path path = marker;
        marker = null;
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("Companion voice: could not delete {}", path, e);
        }
    }

    private static synchronized void fail(String why, Throwable t) {
        state = State.FAILED;
        detail = why;
        LOGGER.warn("Companion voice: her natural voice could not start ({}); {}. Deleting the voice folder makes it download again.",
                why, explicit ? "voiceMode NEURAL has no other voice, so she stays silent" : "she keeps her other voice", t);
        String shortWhy = why.length() > 120 ? why.substring(0, 120) + "..." : why;
        problem(explicit ? "aigirlfriend.voice.failed_only" : "aigirlfriend.voice.failed", shortWhy);
    }

    private static void problem(String key, String arg) {
        if (announcedProblem) return;
        announcedProblem = true;
        NOTICES.add(new Notice(key, arg));
    }

    private static void closeEngine() {
        KokoroVoice v = engine;
        engine = null;
        if (v == null) return;
        try {
            v.close();
        } catch (Exception e) {
            LOGGER.debug("Companion voice: closing the model failed", e);
        }
    }

    private static boolean submit(Executor worker, Runnable job) {
        try {
            worker.execute(job);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private static String megabytes(long bytes) {
        return Math.round(bytes / 1e6) + " MB";
    }
}
