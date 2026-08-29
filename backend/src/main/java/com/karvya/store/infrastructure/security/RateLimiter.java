package com.karvya.store.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A fixed-window rate limiter, keyed by whatever the caller chooses - an IP, an
 * email address, or both.
 *
 * <p>State is in memory, which is the right trade for a single-instance
 * deployment and honest about its limit: running several instances behind a
 * load balancer would need this moved to a shared store, since each process
 * would otherwise permit the full quota on its own. That is noted in the
 * README rather than hidden here.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * Records an attempt and reports whether it is within the allowance.
     *
     * @return true when the caller may proceed
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                return false;
            }
            timestamps.addLast(Instant.now());
            return true;
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    /**
     * Drops keys whose window has fully elapsed. Without this the map grows
     * once per distinct IP seen, which is unbounded on a public endpoint.
     */
    public void evictExpired(Duration window) {
        Instant cutoff = Instant.now().minus(window);
        hits.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }

    public int size() {
        return hits.size();
    }
}
