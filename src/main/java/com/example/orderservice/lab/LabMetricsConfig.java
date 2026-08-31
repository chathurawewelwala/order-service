package com.example.orderservice.lab;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes cache size so Grafana / /actuator/prometheus can show linear growth
 * before the process OOMs.
 */
@Configuration
public class LabMetricsConfig {

    public LabMetricsConfig(MeterRegistry registry, LeakyOrderEventCache cache) {
        Gauge.builder("lab.leaky_cache.entries", cache, LeakyOrderEventCache::size)
                .description("Entries in the intentionally unbounded event cache")
                .register(registry);
        Gauge.builder("lab.leaky_cache.bytes", cache, c -> (double) c.estimatedBytes())
                .description("Estimated payload bytes in the unbounded event cache")
                .register(registry);
    }
}
