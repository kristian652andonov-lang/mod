package com.aigirlfriend.ai;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.config.GFConfig;
import com.aigirlfriend.dialogue.DialogueContext;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Front door for AI dialogue. Never blocks the game thread: requests run asynchronously, are limited by a global
 * concurrency cap and a per-companion rate limit, are validated (and retried) before use, may be served from a
 * small cache, and on any failure the caller's fallback (local dialogue) is used instead.
 * Results are handed back to the server thread by the caller-provided executor.
 */
public final class AIService {
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();
    private static final Map<String, ResponseValidator.Result> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResponseValidator.Result> eldest) {
            return size() > Math.max(0, GFConfig.AI_CACHE_SIZE.get());
        }
    };
    private static volatile long failuresUntil;
    private static volatile int consecutiveFailures;

    private AIService() {}

    public static boolean enabled() {
        return GFConfig.AI_BRAIN.get() != GFConfig.Brain.OFF;
    }

    public static boolean builtin() {
        return GFConfig.AI_BRAIN.get() == GFConfig.Brain.BUILTIN;
    }

    /** False while disabled, saturated, backing off after repeated failures, or rate limited for this companion. */
    public static boolean available(UUID companion) {
        if (!enabled()) return false;
        if (builtin()) {
            // one local worker answers in order, so no rate limit, just a short queue
            BuiltinBrain.ensureStarted(false);
            return BuiltinBrain.ready() && IN_FLIGHT.get() < 3 && BuiltinBrain.queued() < 4;
        }
        if (System.currentTimeMillis() < failuresUntil) return false;
        if (IN_FLIGHT.get() >= GFConfig.AI_MAX_CONCURRENT.get()) return false;
        Long last = LAST_REQUEST.get(companion);
        return last == null || System.currentTimeMillis() - last >= GFConfig.AI_MIN_SECONDS_BETWEEN.get() * 1000L;
    }

    @Nullable
    private static AIProvider provider() {
        int timeout = GFConfig.AI_TIMEOUT_SECONDS.get();
        int maxTokens = GFConfig.AI_MAX_TOKENS.get();
        double temperature = GFConfig.AI_TEMPERATURE.get();
        String endpoint = GFConfig.AI_ENDPOINT.get();
        String model = GFConfig.AI_MODEL.get();
        return switch (GFConfig.AI_BRAIN.get()) {
            case OFF, BUILTIN -> null;
            case OLLAMA -> new HttpProviders.Ollama(endpoint, model, timeout, maxTokens, temperature);
            case OPENAI_COMPATIBLE -> new HttpProviders.OpenAICompatible(endpoint, model, SecretStore.apiKey(), timeout, maxTokens, temperature);
            case ANTHROPIC -> {
                String key = SecretStore.apiKey();
                yield key.isEmpty() ? null : new HttpProviders.Anthropic(endpoint, model, key, timeout, maxTokens, GFConfig.AI_ANTHROPIC_EFFORT.get());
            }
        };
    }

    /**
     * Generates a reply. {@code onResult} receives a validated result or null (use the local fallback).
     * The callback is invoked through {@code mainThread}, never on the network thread.
     */
    public static void generate(UUID companion, DialogueContext ctx, @Nullable String playerMessage, @Nullable String cacheKey,
                                Consumer<Runnable> mainThread, Consumer<ResponseValidator.Result> onResult) {
        if (cacheKey != null) {
            ResponseValidator.Result cached;
            synchronized (CACHE) {
                cached = CACHE.get(cacheKey);
            }
            if (cached != null) {
                onResult.accept(cached);
                return;
            }
        }
        if (builtin()) {
            generateBuiltin(companion, ctx, playerMessage, cacheKey, mainThread, onResult);
            return;
        }
        AIProvider p = provider();
        if (p == null || !available(companion)) {
            onResult.accept(null);
            return;
        }
        LAST_REQUEST.put(companion, System.currentTimeMillis());
        IN_FLIGHT.incrementAndGet();
        String system = PromptBuilder.system(ctx);
        String user = PromptBuilder.user(ctx, playerMessage);
        attempt(p, system, user, ctx.name, GFConfig.AI_RETRIES.get()).whenComplete((result, error) -> {
            IN_FLIGHT.decrementAndGet();
            if (error != null || result == null) {
                registerFailure(error);
            } else {
                consecutiveFailures = 0;
                if (cacheKey != null) {
                    synchronized (CACHE) {
                        CACHE.put(cacheKey, result);
                    }
                }
            }
            ResponseValidator.Result r = error == null ? result : null;
            mainThread.accept(() -> onResult.accept(r));
        });
    }

    /** Built-in model: multi-turn chat whose earlier turns stay cached, so a reply only costs the new message. */
    private static void generateBuiltin(UUID companion, DialogueContext ctx, @Nullable String playerMessage, @Nullable String cacheKey,
                                        Consumer<Runnable> mainThread, Consumer<ResponseValidator.Result> onResult) {
        if (!available(companion)) {
            onResult.accept(null);
            return;
        }
        LAST_REQUEST.put(companion, System.currentTimeMillis());
        var messages = PromptBuilder.chat(ctx, playerMessage);
        IN_FLIGHT.incrementAndGet();
        // small models answer more sensibly (and get facts right more often) when sampled conservatively
        float temperature = (float) Math.min(GFConfig.AI_TEMPERATURE.get(), 0.6);
        String conversation = companion.toString();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        BuiltinBrain.reply(conversation, messages, 72, temperature, cancelled)
                .thenCompose(text -> {
                    ResponseValidator.Result first = usable(ResponseValidator.validate(text, ctx.name, ctx.playerName), ctx);
                    if (first != null || cancelled.get()) return CompletableFuture.completedFuture(first);
                    // invalid or a repeat of something she just said: one more try, a little more adventurous
                    // (the prompt is still cached, so this only costs the new words)
                    return BuiltinBrain.reply(conversation, messages, 72, Math.max(temperature, 0.85f), cancelled)
                            .thenApply(t -> usable(ResponseValidator.validate(t, ctx.name, ctx.playerName), ctx));
                })
                // generous: a slow computer still gets her real answer (the first one after loading reads the whole persona)
                .orTimeout(Math.max(120, GFConfig.AI_TIMEOUT_SECONDS.get()), java.util.concurrent.TimeUnit.SECONDS)
                .handle((result, error) -> {
                    cancelled.set(true); // a timed-out job must not keep the brain busy
                    IN_FLIGHT.decrementAndGet();
                    ResponseValidator.Result r = error == null ? result : null;
                    if (error != null) AIGirlfriend.LOGGER.warn("Built-in brain failed: {}", error.toString());
                    if (r != null && cacheKey != null) {
                        synchronized (CACHE) {
                            CACHE.put(cacheKey, r);
                        }
                    }
                    mainThread.accept(() -> onResult.accept(r));
                    return null;
                });
    }

    /** After she said something: let the built-in brain read the conversation so far while the player answers. */
    public static void prewarm(UUID companion, DialogueContext ctx) {
        if (builtin() && BuiltinBrain.ready()) {
            PromptBuilder.ReadAhead ahead = PromptBuilder.chatSoFar(ctx);
            BuiltinBrain.prefill(companion.toString(), ahead.turns(), ahead.openUser());
        }
    }

    @Nullable
    private static ResponseValidator.Result usable(@Nullable ResponseValidator.Result r, DialogueContext ctx) {
        if (r == null) return null;
        String norm = normalize(r.text());
        for (var line : ctx.history) {
            if (!line.fromPlayer() && normalize(line.text()).equals(norm)) return null;
        }
        return r;
    }

    private static String normalize(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").trim();
    }

    private static CompletableFuture<ResponseValidator.Result> attempt(AIProvider p, String system, String user, String name, int retriesLeft) {
        return p.complete(system, user).handle((text, err) -> {
            ResponseValidator.Result r = err == null ? ResponseValidator.validate(text, name) : null;
            return r;
        }).thenCompose(r -> {
            if (r != null || retriesLeft <= 0) return CompletableFuture.completedFuture(r);
            return attempt(p, system, user + "\n(Keep it short, in character, and natural.)", name, retriesLeft - 1);
        });
    }

    private static void registerFailure(@Nullable Throwable error) {
        int n = ++consecutiveFailures;
        if (n >= 3) {
            failuresUntil = System.currentTimeMillis() + Math.min(300_000L, 15_000L * n);
            AIGirlfriend.LOGGER.warn("AI provider failing ({}x), using local dialogue for a while: {}", n, error == null ? "invalid responses" : error.toString());
        } else if (error != null) {
            AIGirlfriend.LOGGER.debug("AI request failed: {}", error.toString());
        }
    }

    /** Forget per-world state (server stopping). */
    public static void clear() {
        LAST_REQUEST.clear();
        failuresUntil = 0;
        consecutiveFailures = 0;
    }

    /** A short explanation for players while the built-in brain is not ready yet, or null. */
    @Nullable
    public static String brainNotice() {
        if (!enabled() || !builtin() || BuiltinBrain.ready()) return null;
        return switch (BuiltinBrain.state()) {
            case DOWNLOADING -> "Her AI brain is downloading (" + Math.round(BuiltinBrain.progress() * 100) + "%). Until it is ready she uses simple scripted replies.";
            case LOADING -> "Her AI brain is loading, give it a few seconds.";
            case FAILED -> "Her AI brain could not start (" + BuiltinBrain.status() + "). She uses simple scripted replies; see /gf ai.";
            default -> GFConfig.AI_BUILTIN_DOWNLOAD.get() ? null : "Her AI brain is not downloaded yet: run /gf ai download (about "
                    + BuiltinBrain.downloadSize() + ", one time).";
        };
    }

    public static String status() {
        if (!enabled()) return "off (scripted dialogue only)";
        if (builtin()) return BuiltinBrain.status() + (BuiltinBrain.busy() ? ", thinking" : "");
        AIProvider p = provider();
        if (p == null) return "no provider / missing API key (local dialogue)";
        String s = p.id() + ", in flight " + IN_FLIGHT.get();
        if (System.currentTimeMillis() < failuresUntil) s += ", backing off after failures";
        return s;
    }
}
