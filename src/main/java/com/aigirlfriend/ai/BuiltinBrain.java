package com.aigirlfriend.ai;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.ai.local.LocalChat;
import com.aigirlfriend.ai.local.Model;
import com.aigirlfriend.config.GFConfig;
import net.neoforged.fml.loading.FMLPaths;

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
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The built-in brain: a small open model (Qwen2.5 Instruct, Apache-2.0) run in-process by the pure-Java engine in
 * {@code ai.local}. Downloads the model once (resumable, checksum verified), loads it in the background, and
 * answers on a single low-priority thread so the game never waits. Until it is ready the caller falls back to
 * scripted lines.
 */
public final class BuiltinBrain {
    public enum State { IDLE, DOWNLOADING, LOADING, READY, FAILED }

    private record ModelFile(String file, String url, long size, String sha256, String label) {}

    // pinned to a repository revision, so the file always matches its checksum
    private static final ModelFile SMALL = new ModelFile("qwen2.5-0.5b-instruct-q8_0.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q8_0.gguf",
            675_710_816L, "ca59ca7f13d0e15a8cfa77bd17e65d24f6844b554a7b6c12e07a5f89ff76844e", "Qwen2.5 0.5B Instruct");
    private static final ModelFile SMART = new ModelFile("qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            1_117_320_736L, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e", "Qwen2.5 1.5B Instruct");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AIGirlfriend-Brain");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    private static volatile State state = State.IDLE;
    private static volatile String detail = "";
    private static volatile float progress;
    private static volatile String loadedKey = "";
    private static final java.util.concurrent.atomic.AtomicInteger QUEUED = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile long failedAt;
    private static LocalChat chat;
    private static Model model;

    private BuiltinBrain() {}

    public static State state() {
        return state;
    }

    /** 0..1 while downloading. */
    public static float progress() {
        return progress;
    }

    public static boolean ready() {
        return state == State.READY && configKey().equals(loadedKey);
    }

    public static boolean busy() {
        return QUEUED.get() > 0;
    }

    /** Jobs waiting for or running on the brain thread (replies and read-aheads). */
    public static int queued() {
        return QUEUED.get();
    }

    private static String configKey() {
        return GFConfig.AI_BUILTIN_MODEL.get() + "|" + GFConfig.AI_BUILTIN_PATH.get() + "|" + GFConfig.AI_BUILTIN_CONTEXT.get() + "|" + GFConfig.AI_BUILTIN_THREADS.get();
    }

    /** Starts downloading/loading in the background if needed; returns immediately. */
    public static synchronized void ensureStarted(boolean userRequested) {
        if (state == State.DOWNLOADING || state == State.LOADING) return;
        if (ready()) return;
        if (state == State.FAILED && !userRequested && System.currentTimeMillis() - failedAt < 600_000L) return; // retry after 10 min
        Path file = modelPath();
        if (file == null) {
            fail("builtinModel is CUSTOM but builtinModelPath is empty");
            return;
        }
        boolean present = Files.isRegularFile(file);
        if (!present && GFConfig.AI_BUILTIN_MODEL.get() == GFConfig.BuiltinModel.CUSTOM) {
            fail("builtinModelPath not found: " + file);
            return;
        }
        // a dedicated server never downloads on its own: an operator decides with /gf ai download
        boolean auto = GFConfig.AI_BUILTIN_DOWNLOAD.get() && !net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer();
        if (!present && !userRequested && !auto) {
            state = State.IDLE;
            detail = "model not downloaded (run /gf ai download)";
            return;
        }
        state = present ? State.LOADING : State.DOWNLOADING;
        progress = 0;
        detail = "";
        String key = configKey();
        WORKER.execute(() -> {
            try {
                if (!Files.isRegularFile(file)) download(modelFile(), file);
                load(file, key);
            } catch (Throwable e) {
                AIGirlfriend.LOGGER.error("Built-in AI brain could not start", e);
                fail(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    private static void fail(String why) {
        failedAt = System.currentTimeMillis();
        state = State.FAILED;
        detail = why;
    }

    private static ModelFile modelFile() {
        return switch (GFConfig.AI_BUILTIN_MODEL.get()) {
            case SMART -> SMART;
            case AUTO -> Runtime.getRuntime().availableProcessors() >= 12 ? SMART : SMALL; // SMART needs ~6 cores to answer in seconds
            default -> SMALL;
        };
    }

    /** Download size of the model that would be used, for messages. */
    public static String downloadSize() {
        return String.format(java.util.Locale.ROOT, "%.1f GB", modelFile().size() / 1e9);
    }

    private static Path modelPath() {
        if (GFConfig.AI_BUILTIN_MODEL.get() == GFConfig.BuiltinModel.CUSTOM) {
            String p = GFConfig.AI_BUILTIN_PATH.get().trim();
            if (p.isEmpty()) return null;
            Path path = Path.of(p);
            return path.isAbsolute() ? path : FMLPaths.GAMEDIR.get().resolve(path);
        }
        return FMLPaths.GAMEDIR.get().resolve("aigirlfriend").resolve("models").resolve(modelFile().file());
    }

    private static void download(ModelFile mf, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        long have = Files.isRegularFile(part) ? Files.size(part) : 0;
        if (have > mf.size()) {
            Files.delete(part);
            have = 0;
        }
        if (have < mf.size()) fetch(mf, part, have);
        verifyAndMove(mf, part, target);
    }

    private static void fetch(ModelFile mf, Path part, long have) throws IOException, InterruptedException {
        AIGirlfriend.LOGGER.info("Downloading the built-in AI model {} ({} MB) to {}", mf.label(), mf.size() / 1_000_000, part.getParent());
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(mf.url())).header("User-Agent", "AIGirlfriend-mod").GET();
        if (have > 0) req.header("Range", "bytes=" + have + "-");
        HttpResponse<InputStream> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        if (code == 416) { // nothing left to fetch: the part file is already complete
            resp.body().close();
            return;
        }
        if (code != 206 && code != 200) {
            resp.body().close();
            throw new IOException("Download failed: HTTP " + code);
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
                progress = (float) done / mf.size();
            }
        }
    }

    private static void verifyAndMove(ModelFile mf, Path part, Path target) throws IOException {
        detail = "verifying download";
        if (Files.size(part) != mf.size()) throw new IOException("Download incomplete (" + Files.size(part) + " of " + mf.size() + " bytes); it will resume next time");
        String sha = sha256(part);
        if (!sha.equalsIgnoreCase(mf.sha256())) {
            Files.deleteIfExists(part);
            throw new IOException("Downloaded model failed its checksum; deleted, it will download again");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        AIGirlfriend.LOGGER.info("Built-in AI model downloaded and verified");
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void load(Path file, String key) throws IOException {
        state = State.LOADING;
        detail = "";
        if (model != null) {
            model.close();
            model = null;
            chat = null;
        }
        int threads = GFConfig.AI_BUILTIN_THREADS.get();
        if (threads <= 0) threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
        long t0 = System.currentTimeMillis();
        Model m = new Model(file, threads);
        LocalChat c = new LocalChat(m, GFConfig.AI_BUILTIN_CONTEXT.get(), 3);
        // warm-up: compiles the hot loops and pages every weight in, so the first real reply is not slow
        c.reply("warmup", List.of(new LocalChat.Message(LocalChat.Role.USER, "Hi!")), 2, 0f, 1f, 1);
        c.forget("warmup");
        model = m;
        chat = c;
        loadedKey = key;
        state = State.READY;
        AIGirlfriend.LOGGER.info("Built-in AI brain ready ({}, {} threads, {} ms)", file.getFileName(), threads, System.currentTimeMillis() - t0);
    }

    /**
     * Generates her next message for this conversation; completes off-thread (exceptionally if not ready).
     * Once {@code cancelled} is set (the caller gave up waiting) the job is skipped or cut short.
     */
    public static CompletableFuture<String> reply(String conversation, List<LocalChat.Message> messages, int maxTokens, float temperature,
                                                  java.util.concurrent.atomic.AtomicBoolean cancelled) {
        if (!ready()) return CompletableFuture.failedFuture(new IllegalStateException("built-in brain not ready"));
        QUEUED.incrementAndGet();
        long seed = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (cancelled.get()) return null;
                LocalChat.Reply r = chat.reply(conversation, messages, maxTokens, temperature, 0.9f, seed, cancelled::get);
                AIGirlfriend.LOGGER.debug("Built-in brain: {} prompt tokens ({} reused), {} generated in {} ms",
                        r.promptTokens(), r.reusedTokens(), r.generatedTokens(), r.millis());
                return r.text();
            } finally {
                QUEUED.decrementAndGet();
            }
        }, WORKER);
    }

    /**
     * Reads the conversation so far in the background (while the player reads her line and types), so the next
     * reply only costs the new message. Skipped when she is busy answering.
     */
    public static void prefill(String conversation, List<LocalChat.Message> messages, @org.jetbrains.annotations.Nullable String openUser) {
        if (!ready() || QUEUED.get() > 0) return;
        QUEUED.incrementAndGet();
        WORKER.execute(() -> {
            try {
                long t0 = System.currentTimeMillis();
                int n = chat.prefill(conversation, messages, openUser, 200);
                if (n > 0) AIGirlfriend.LOGGER.debug("Built-in brain: read ahead {} tokens in {} ms", n, System.currentTimeMillis() - t0);
            } catch (RuntimeException e) {
                AIGirlfriend.LOGGER.debug("Built-in brain read-ahead failed", e);
            } finally {
                QUEUED.decrementAndGet();
            }
        });
    }

    public static String status() {
        return switch (state) {
            case IDLE -> "built-in model not loaded" + (detail.isEmpty() ? "" : " (" + detail + ")");
            case DOWNLOADING -> detail.isEmpty() ? String.format("downloading %s: %d%%", modelFile().label(), Math.round(progress * 100)) : detail;
            case LOADING -> "loading the built-in model";
            case READY -> "built-in " + (model == null ? "model" : model.architecture + " " + model.layers + " layers") + (ready() ? "" : " (config changed, reloading on next use)");
            case FAILED -> "built-in model failed: " + detail;
        };
    }
}
