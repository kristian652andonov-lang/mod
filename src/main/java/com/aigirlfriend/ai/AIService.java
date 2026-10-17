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
        return GFConfig.AI_ENABLED.get() && GFConfig.AI_PROVIDER.get() != GFConfig.AIProviderType.NONE;
    }

    /** False while disabled, saturated, backing off after repeated failures, or rate limited for this companion. */
    public static boolean available(UUID companion) {
        if (!enabled()) return false;
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
        return switch (GFConfig.AI_PROVIDER.get()) {
            case NONE -> null;
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

    public static String status() {
        if (!GFConfig.AI_ENABLED.get()) return "disabled (local dialogue)";
        AIProvider p = provider();
        if (p == null) return "no provider / missing API key (local dialogue)";
        String s = p.id() + ", in flight " + IN_FLIGHT.get();
        if (System.currentTimeMillis() < failuresUntil) s += ", backing off after failures";
        return s;
    }
}
