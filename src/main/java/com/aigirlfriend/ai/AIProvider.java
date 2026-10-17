package com.aigirlfriend.ai;

import java.util.concurrent.CompletableFuture;

/** One chat backend. Implementations must be non-blocking: they return a future completed off-thread. */
public interface AIProvider {
    CompletableFuture<String> complete(String system, String user);

    String id();
}
