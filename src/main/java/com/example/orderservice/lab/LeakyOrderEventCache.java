package com.example.orderservice.lab;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * INTENTIONALLY BUGGY — Lab scenario #1 (OOM RCA).
 *
 * Simulates a listener-style cache: every incoming event is stored forever.
 * No TTL, no max size, no eviction. This is the HashMap that "Leak Suspects"
 * should point at in a heap dump.
 */
@Component
public class LeakyOrderEventCache {

    /**
     * Static so it is a GC root (survives even if the Spring bean is recreated).
     * That matches the interview story: a listener field that is never cleared.
     */
    private static final Map<String, CachedEvent> EVENTS = new HashMap<>();

    public record CachedEvent(
            String eventId,
            String customerId,
            Instant receivedAt,
            byte[] payload
    ) {
    }

    /**
     * Store {@code count} unique events. Each payload is {@code payloadKb} KB
     * so heap growth is visible without needing millions of tiny entries.
     */
    public int ingest(int count, int payloadKb, String customerId) {
        int kb = Math.max(1, payloadKb);
        int n = Math.max(1, count);
        for (int i = 0; i < n; i++) {
            String eventId = UUID.randomUUID().toString();
            EVENTS.put(eventId, new CachedEvent(
                    eventId,
                    customerId,
                    Instant.now(),
                    new byte[kb * 1024]
            ));
        }
        return EVENTS.size();
    }

    public int size() {
        return EVENTS.size();
    }

    public long estimatedBytes() {
        long bytes = 0;
        for (CachedEvent event : EVENTS.values()) {
            if (event.payload() != null) {
                bytes += event.payload().length;
            }
        }
        return bytes;
    }

    /** Lab-only: wipe so you can re-run after a dump without restarting. */
    public void clear() {
        EVENTS.clear();
    }
}
