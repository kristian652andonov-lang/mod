package com.aigirlfriend.ai.local;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chat on top of a {@link Model}: renders ChatML prompts, keeps one KV-cache session per conversation and reuses
 * whatever prefix of the new prompt was already processed (the system prompt and earlier turns), so each reply
 * only costs the new message plus the generated tokens. Not thread-safe: call from one generation thread.
 */
public final class LocalChat {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public record Message(Role role, String text) {}

    public record Reply(String text, int promptTokens, int reusedTokens, int generatedTokens, long millis) {}

    private final Model model;
    private final int contextSize;
    private final int maxSessions;
    private final int imStart, imEnd, endOfText, newline;
    private final Map<String, Model.Session> sessions = new LinkedHashMap<>(8, 0.75f, true);

    public LocalChat(Model model, int contextSize, int maxSessions) {
        this.model = model;
        this.contextSize = Math.min(contextSize, model.trainContext);
        this.maxSessions = Math.max(1, maxSessions);
        Tokenizer t = model.tokenizer;
        this.imStart = t.id("<|im_start|>");
        this.imEnd = t.id("<|im_end|>");
        this.endOfText = t.id("<|endoftext|>");
        List<Integer> nl = t.encode("\n", false);
        this.newline = nl.size() == 1 ? nl.get(0) : -1;
        if (imStart < 0 || imEnd < 0) throw new IllegalArgumentException("Model has no ChatML tokens; use an instruct/chat model");
    }

    public Model model() {
        return model;
    }

    private List<Integer> render(List<Message> messages) {
        List<Integer> out = renderTurns(messages);
        out.add(imStart);
        out.addAll(model.tokenizer.encode("assistant\n", false));
        return out;
    }

    private List<Integer> renderTurns(List<Message> messages) {
        Tokenizer t = model.tokenizer;
        List<Integer> out = new ArrayList<>();
        for (Message m : messages) {
            out.add(imStart);
            out.addAll(t.encode(m.role().name().toLowerCase() + "\n", false));
            out.addAll(t.encode(m.text(), false));
            out.add(imEnd);
            out.addAll(t.encode("\n", false));
        }
        return out;
    }

    /**
     * Processes these messages ahead of time (no reply), so a later {@link #reply} whose prompt starts with them
     * only has to read what is new. Returns the number of tokens processed (0 if already cached or too long).
     */
    public int prefill(String sessionKey, List<Message> messages, int reserve) {
        List<Integer> prompt = renderTurns(messages);
        if (prompt.size() + reserve > contextSize) return 0;
        Model.Session s = session(sessionKey);
        int reuse = 0;
        int limit = Math.min(s.length, prompt.size());
        while (reuse < limit && s.tokens[reuse] == prompt.get(reuse)) reuse++;
        if (reuse == prompt.size()) return 0;
        s.length = reuse;
        int[] tokens = prompt.stream().mapToInt(Integer::intValue).toArray();
        model.forward(s, tokens, reuse, tokens.length);
        return tokens.length - reuse;
    }

    /**
     * Generates the assistant's next message. Older non-system messages are dropped if the conversation does
     * not fit the context. Generation stops at the end-of-turn token, after {@code maxTokens}, or at the end of the paragraph.
     */
    public Reply reply(String sessionKey, List<Message> messages, int maxTokens, float temperature, float topP, long seed) {
        long start = System.currentTimeMillis();
        List<Message> msgs = new ArrayList<>(messages);
        List<Integer> prompt = render(msgs);
        while (prompt.size() + maxTokens > contextSize && msgs.size() > 2) {
            msgs.remove(msgs.get(0).role() == Role.SYSTEM ? 1 : 0);
            prompt = render(msgs);
        }
        if (prompt.size() + maxTokens > contextSize) throw new IllegalArgumentException("prompt too long");

        Model.Session s = session(sessionKey);
        int reuse = 0;
        int limit = Math.min(s.length, prompt.size() - 1);
        while (reuse < limit && s.tokens[reuse] == prompt.get(reuse)) reuse++;
        s.length = reuse;
        int[] tokens = prompt.stream().mapToInt(Integer::intValue).toArray();
        float[] logits = model.forward(s, tokens, reuse, tokens.length);

        Sampler sampler = new Sampler(temperature, 40, topP, 1.12f);
        Random random = new Random(seed);
        int[] recent = new int[64];
        int recentCount = 0, recentPos = 0;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < maxTokens; i++) {
            int tok = sampler.sample(logits, recent, recentCount, null, random);
            if (tok == imEnd || tok == endOfText || model.tokenizer.isControl(tok)) break;
            if (tok == newline && !out.isEmpty()) break; // replies are a single paragraph
            if (tok != newline) {
                out.add(tok);
                recent[recentPos] = tok;
                recentPos = (recentPos + 1) % recent.length;
                recentCount = Math.min(recentCount + 1, recent.length);
            }
            if (s.length + 1 >= s.capacity) break;
            logits = model.forward(s, new int[]{tok}, 0, 1);
        }
        String text = model.tokenizer.decode(out).strip();
        return new Reply(text, tokens.length, reuse, out.size(), System.currentTimeMillis() - start);
    }

    private Model.Session session(String key) {
        Model.Session s = sessions.get(key);
        if (s == null) {
            if (sessions.size() >= maxSessions) {
                Iterator<String> it = sessions.keySet().iterator();
                it.next();
                it.remove();
            }
            s = model.newSession(contextSize);
            sessions.put(key, s);
        }
        return s;
    }

    public void forget(String key) {
        sessions.remove(key);
    }
}
