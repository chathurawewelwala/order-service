package com.example.orderservice.lab;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INTENTIONALLY BUGGY — Lab scenario #2 (concurrency).
 *
 * One synchronized lock around a shared HashMap + counter.
 * Work is submitted to an unbounded cached thread pool so many threads
 * pile up BLOCKED on the same monitor.
 */
@Component
public class ContendedOrderProcessor {

    private final Object lock = new Object();
    private final Map<String, Long> tallies = new HashMap<>();
    private long processed;

    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger liveWorkers = new AtomicInteger();

    private final ThreadFactory factory = r -> {
        Thread t = new Thread(r);
        t.setName("lab-batch-" + liveWorkers.incrementAndGet());
        t.setDaemon(true);
        return t;
    };

    /** Unbounded: a new thread per burst of work when idle threads are busy. */
    private final ExecutorService pool = Executors.newCachedThreadPool(factory);

    public Map<String, Object> runBurst(int workers, int tasksPerWorker) {
        int w = Math.clamp(workers, 1, 200);
        int n = Math.clamp(tasksPerWorker, 1, 50_000);
        submitted.addAndGet(w * n);

        for (int i = 0; i < w; i++) {
            final int worker = i;
            pool.execute(() -> {
                for (int t = 0; t < n; t++) {
                    processOne("SKU-" + (t % 16), worker);
                }
            });
        }
        return snapshot("submitted " + w + " workers x " + n + " tasks");
    }

    /** The bottleneck: every task takes the same lock. */
    private void processOne(String sku, int worker) {
        synchronized (lock) {
            processed++;
            tallies.merge(sku, 1L, Long::sum);
            try {
                // Simulate a cache write / counter update under the lock
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completed.incrementAndGet();
        }
    }

    public Map<String, Object> snapshot(String action) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("action", action);
        synchronized (lock) {
            body.put("processed", processed);
            body.put("distinctSkus", tallies.size());
        }
        body.put("submitted", submitted.get());
        body.put("completed", completed.get());
        body.put("outstanding", submitted.get() - completed.get());
        body.put("pool", pool.toString());
        return body;
    }

    public void awaitQuiet(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (submitted.get() > completed.get() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }
}
