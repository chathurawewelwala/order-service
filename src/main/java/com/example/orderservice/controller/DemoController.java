package com.example.orderservice.controller;

import com.example.orderservice.lab.LeakyOrderEventCache;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lab endpoints for JVM / RCA exercises. Not production APIs.
 */
@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoController {

    private final LeakyOrderEventCache leakyCache;

    /**
     * Scenario #1 — unbounded listener cache.
     *
     * Example: POST /api/v1/demo/events?count=2000&payloadKb=256
     * That stores ~500 MB and never evicts it.
     */
    @PostMapping("/events")
    public Map<String, Object> ingestEvents(
            @RequestParam(defaultValue = "500") int count,
            @RequestParam(defaultValue = "256") int payloadKb,
            @RequestParam(defaultValue = "cust-lab") String customerId) {
        int size = leakyCache.ingest(count, payloadKb, customerId);
        return stats("ingested " + count + " events (" + payloadKb + " KB each)", size);
    }

    @GetMapping("/events/stats")
    public Map<String, Object> eventStats() {
        return stats("snapshot", leakyCache.size());
    }

    @PostMapping("/events/clear")
    public Map<String, Object> clearEvents() {
        leakyCache.clear();
        return stats("cleared", 0);
    }

    /** Original crude leak — keep for a simpler "allocate N MB" demo. */
    @GetMapping("/leak")
    public Map<String, Object> leak(@RequestParam(defaultValue = "10") int mb) {
        int size = leakyCache.ingest(1, mb * 1024, "legacy-leak");
        return stats("legacy leak " + mb + " MB", size);
    }

    private Map<String, Object> stats(String action, int cacheEntries) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("cacheEntries", cacheEntries);
        body.put("cacheEstimatedBytes", leakyCache.estimatedBytes());
        body.put("heapUsedBytes", heap.getUsed());
        body.put("heapMaxBytes", heap.getMax());
        body.put("heapUsedPct", heap.getMax() > 0
                ? Math.round(heap.getUsed() * 1000.0 / heap.getMax()) / 10.0
                : null);
        return body;
    }
}
