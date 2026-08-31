package com.example.orderservice.lab;

import com.example.orderservice.domain.Order;
import com.example.orderservice.repository.OrderRepository;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INTENTIONALLY BUGGY — Labs #3 (missing index) and #4 (pool exhaustion).
 */
@Service
@RequiredArgsConstructor
public class LabOrderDataService {

    public static final String RARE_SKU = "SKU-RARE";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final OrderRepository orders;

    public int seed(int rows) {
        int n = Math.max(1, Math.min(rows, 200_000));
        jdbc.update("""
                INSERT INTO orders (customer_id, product_code, quantity, total_amount, status, created_at)
                SELECT
                    'cust-lab-' || g,
                    CASE WHEN g % 200 = 0 THEN 'SKU-RARE' ELSE 'SKU-COMMON' END,
                    1,
                    9.99,
                    'CREATED',
                    NOW()
                FROM generate_series(1, ?) AS g
                """, n);
        return n;
    }

    public Map<String, Object> findByProductTimed(String productCode) {
        long start = System.nanoTime();
        List<Order> found = orders.findByProductCode(productCode);
        long ms = (System.nanoTime() - start) / 1_000_000;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productCode", productCode);
        body.put("matches", found.size());
        body.put("elapsedMs", ms);
        body.put("hint", "No index on product_code — expect seq scan once the table is large");
        return body;
    }

    public List<Map<String, Object>> explainProductQuery(String productCode) {
        return jdbc.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orders WHERE product_code = ?",
                productCode);
    }

    public Map<String, Object> poolSnapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (dataSource instanceof HikariDataSource hikari) {
            var mx = hikari.getHikariPoolMXBean();
            body.put("pool", hikari.getPoolName());
            body.put("active", mx.getActiveConnections());
            body.put("idle", mx.getIdleConnections());
            body.put("total", mx.getTotalConnections());
            body.put("threadsAwaiting", mx.getThreadsAwaitingConnection());
            body.put("maxPoolSize", hikari.getMaximumPoolSize());
            body.put("connectionTimeoutMs", hikari.getConnectionTimeout());
        }
        body.put("orderRows", jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
        return body;
    }

    /**
     * Each caller holds one Hikari connection: seq scan + pg_sleep.
     * Fire more callers than maximum-pool-size to queue / time out.
     */
    public Map<String, Object> exhaustPool(int callers, int seconds) {
        int c = Math.max(1, Math.min(callers, 40));
        int s = Math.max(1, Math.min(seconds, 20));
        ExecutorService exec = Executors.newFixedThreadPool(c);
        CountDownLatch ready = new CountDownLatch(c);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long t0 = System.nanoTime();

        for (int i = 0; i < c; i++) {
            exec.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                    jdbc.execute((Connection con) -> {
                        try (PreparedStatement scan = con.prepareStatement(
                                "SELECT count(*) FROM orders WHERE product_code = ?")) {
                            scan.setString(1, RARE_SKU);
                            scan.executeQuery().close();
                        }
                        try (PreparedStatement sleep = con.prepareStatement("SELECT pg_sleep(?)")) {
                            sleep.setInt(1, s);
                            sleep.executeQuery().close();
                        }
                        return null;
                    });
                    ok.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            });
        }

        try {
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            exec.shutdown();
            exec.awaitTermination(s + 45L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> body = poolSnapshot();
        body.put("action", "slow-query burst");
        body.put("callers", c);
        body.put("sleepSeconds", s);
        body.put("succeeded", ok.get());
        body.put("failed", failed.get());
        body.put("elapsedMs", (System.nanoTime() - t0) / 1_000_000);
        return body;
    }
}
