package com.asher.bugfixer.http;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded best-effort local deduplication keyed by Jira's delivery identifier. */
public final class DeliveryDeduplicator {
    private static final int MAX_ENTRIES = 10_000;
    private static final Duration RETENTION = Duration.ofHours(24);
    private final ConcurrentHashMap<String, Instant> deliveries = new ConcurrentHashMap<>();

    public boolean alreadySeen(String deliveryId) {
        pruneIfNeeded();
        return deliveries.putIfAbsent(deliveryId, Instant.now()) != null;
    }

    private void pruneIfNeeded() {
        if (deliveries.size() < MAX_ENTRIES) {
            return;
        }
        Instant threshold = Instant.now().minus(RETENTION);
        deliveries.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
    }
}
