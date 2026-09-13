package com.finotech.jewellery.modules.dashboard.application.service;

import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * A short in-process cache keyed by caller and branch.
 *
 * <p>A pull-to-refresh storm from one phone should cost one set of queries,
 * not one per gesture. Thirty seconds is short enough that a permission change
 * or a sale is visible on the next natural refresh, and the client can bypass
 * it with {@code refresh=true}. Deliberately a map rather than a cache library:
 * there is one entry per active user and nothing to tune.
 */
@Component
public class DashboardCache {

    static final Duration TTL = Duration.ofSeconds(30);
    private static final int PRUNE_THRESHOLD = 1_000;

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    public DashboardSummaryResponse get(UUID userId, UUID branchId, boolean bypass,
                                        Supplier<DashboardSummaryResponse> loader) {
        Key key = new Key(userId, branchId);
        Instant now = Instant.now();
        if (!bypass) {
            Entry cached = entries.get(key);
            if (cached != null && cached.expiresAt().isAfter(now)) {
                return cached.value();
            }
        }
        DashboardSummaryResponse fresh = loader.get();
        entries.put(key, new Entry(fresh, now.plus(TTL)));
        if (entries.size() > PRUNE_THRESHOLD) {
            entries.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
        }
        return fresh;
    }

    public void clear() {
        entries.clear();
    }

    private record Key(UUID userId, UUID branchId) {
    }

    private record Entry(DashboardSummaryResponse value, Instant expiresAt) {
    }
}
