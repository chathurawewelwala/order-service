package com.example.orderservice.controller;

import com.example.orderservice.lab.ContendedOrderProcessor;
import com.example.orderservice.lab.LabOrderDataService;
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
    private final ContendedOrderProcessor batch;
    private final LabOrderDataService labData;

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

    /**
     * Lab #2 — one synchronized lock + unbounded cached pool.
     * POST /api/v1/demo/batch?workers=40&tasks=200
     * Then in the pod: jcmd 1 Thread.print | grep -A2 BLOCKED
     */
    @PostMapping("/batch")
    public Map<String, Object> batch(
            @RequestParam(defaultValue = "40") int workers,
            @RequestParam(defaultValue = "200") int tasks) {
        return batch.runBurst(workers, tasks);
    }

    @GetMapping("/batch/stats")
    public Map<String, Object> batchStats() {
        return batch.snapshot("snapshot");
    }

    /**
     * Lab #3 — seed rows so product_code queries go sequential.
     * POST /api/v1/demo/orders/seed?rows=80000
     */
    @PostMapping("/orders/seed")
    public Map<String, Object> seed(@RequestParam(defaultValue = "80000") int rows) {
        int inserted = labData.seed(rows);
        Map<String, Object> body = labData.poolSnapshot();
        body.put("action", "seeded");
        body.put("inserted", inserted);
        return body;
    }

    @GetMapping("/orders/by-product")
    public Map<String, Object> byProduct(
            @RequestParam(defaultValue = LabOrderDataService.RARE_SKU) String productCode) {
        return labData.findByProductTimed(productCode);
    }

    @GetMapping("/orders/explain")
    public Map<String, Object> explain(
            @RequestParam(defaultValue = LabOrderDataService.RARE_SKU) String productCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", productCode);
        body.put("plan", labData.explainProductQuery(productCode));
        return body;
    }

    /**
     * Lab #4 — more callers than Hikari max pool, each holding pg_sleep.
     * POST /api/v1/demo/db/slow?callers=20&seconds=8
     */
    @PostMapping("/db/slow")
    public Map<String, Object> slowDb(
            @RequestParam(defaultValue = "20") int callers,
            @RequestParam(defaultValue = "8") int seconds) {
        return labData.exhaustPool(callers, seconds);
    }

    @GetMapping("/db/pool")
    public Map<String, Object> pool() {
        return labData.poolSnapshot();
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
