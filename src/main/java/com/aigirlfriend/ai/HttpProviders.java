package com.aigirlfriend.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** HTTP implementations: Ollama (native), any OpenAI-compatible server, and Anthropic's Messages API. */
public final class HttpProviders {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpProviders() {}

    private static String trimSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static CompletableFuture<JsonObject> post(String url, JsonObject body, int timeoutSeconds, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i + 1] != null && !headers[i + 1].isEmpty()) b.header(headers[i], headers[i + 1]);
        }
        return CLIENT.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                .orTimeout(timeoutSeconds + 1L, java.util.concurrent.TimeUnit.SECONDS)
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        String snippet = resp.body() == null ? "" : resp.body().substring(0, Math.min(200, resp.body().length()));
                        throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + snippet);
                    }
                    return JsonParser.parseString(resp.body()).getAsJsonObject();
                });
    }

    private static JsonArray messages(String system, String user, boolean includeSystem) {
        JsonArray arr = new JsonArray();
        if (includeSystem) {
            JsonObject s = new JsonObject();
            s.addProperty("role", "system");
            s.addProperty("content", system);
            arr.add(s);
        }
        JsonObject u = new JsonObject();
        u.addProperty("role", "user");
        u.addProperty("content", user);
        arr.add(u);
        return arr;
    }

    /** Ollama's native chat endpoint: POST {endpoint}/api/chat. */
    public record Ollama(String endpoint, String model, int timeout, int maxTokens, double temperature) implements AIProvider {
        @Override
        public CompletableFuture<String> complete(String system, String user) {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages(system, user, true));
            body.addProperty("stream", false);
            JsonObject options = new JsonObject();
            options.addProperty("temperature", temperature);
            options.addProperty("num_predict", maxTokens);
            body.add("options", options);
            return post(trimSlash(endpoint) + "/api/chat", body, timeout).thenApply(json -> {
                JsonObject msg = json.getAsJsonObject("message");
                return msg == null ? "" : msg.get("content").getAsString();
            });
        }

        @Override
        public String id() {
            return "ollama:" + model;
        }
    }

    /** Any server speaking the OpenAI chat-completions format: POST {endpoint}/chat/completions. */
    public record OpenAICompatible(String endpoint, String model, String apiKey, int timeout, int maxTokens, double temperature) implements AIProvider {
        @Override
        public CompletableFuture<String> complete(String system, String user) {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages(system, user, true));
            body.addProperty("temperature", temperature);
            body.addProperty("max_tokens", maxTokens);
            String url = trimSlash(endpoint);
            if (!url.endsWith("/chat/completions")) url = url + "/chat/completions";
            return post(url, body, timeout, "Authorization", apiKey.isEmpty() ? null : "Bearer " + apiKey).thenApply(json -> {
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) return "";
                JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                JsonElement content = message == null ? null : message.get("content");
                return content == null || content.isJsonNull() ? "" : content.getAsString();
            });
        }

        @Override
        public String id() {
            return "openai:" + model;
        }
    }

    /**
     * Anthropic Messages API: POST {endpoint}/v1/messages with x-api-key and anthropic-version headers.
     * Current models reject sampling parameters, so no temperature is sent; depth is set with output_config.effort.
     * Refusals (stop_reason "refusal") are treated as failures so the local dialogue takes over.
     */
    public record Anthropic(String endpoint, String model, String apiKey, int timeout, int maxTokens, String effort) implements AIProvider {
        private boolean supportsServerFallback() {
            return model.equals("claude-opus-5-5") || model.equals("claude-opus-5") || model.equals("claude-fable-5-1") || model.equals("claude-sonnet-5-5");
        }

        private boolean supportsEffort() {
            return model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5") || model.startsWith("claude-sonnet-5")
                    || model.startsWith("claude-haiku-5") || model.startsWith("claude-opus-4-6") || model.startsWith("claude-opus-4-7")
                    || model.startsWith("claude-opus-4-8") || model.startsWith("claude-sonnet-4-6");
        }

        @Override
        public CompletableFuture<String> complete(String system, String user) {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("system", system);
            body.add("messages", messages(system, user, false));
            if (supportsEffort() && effort != null && !effort.isBlank()) {
                JsonObject oc = new JsonObject();
                oc.addProperty("effort", effort.trim().toLowerCase());
                body.add("output_config", oc);
            }
            String beta = null;
            if (supportsServerFallback()) {
                body.addProperty("fallbacks", "default");
                beta = "server-side-fallback-2026-07-01";
            }
            String base = trimSlash(endpoint.isBlank() ? "https://api.anthropic.com" : endpoint);
            String url = base.endsWith("/v1/messages") ? base : base + "/v1/messages";
            return post(url, body, timeout, "x-api-key", apiKey, "anthropic-version", "2023-06-01", "anthropic-beta", beta).thenApply(json -> {
                JsonElement stop = json.get("stop_reason");
                if (stop != null && !stop.isJsonNull() && "refusal".equals(stop.getAsString())) throw new IllegalStateException("refusal");
                JsonArray content = json.getAsJsonArray("content");
                StringBuilder sb = new StringBuilder();
                if (content != null) {
                    for (JsonElement e : content) {
                        JsonObject block = e.getAsJsonObject();
                        if ("text".equals(block.get("type").getAsString())) sb.append(block.get("text").getAsString());
                    }
                }
                return sb.toString();
            });
        }

        @Override
        public String id() {
            return "anthropic:" + model;
        }
    }
}
