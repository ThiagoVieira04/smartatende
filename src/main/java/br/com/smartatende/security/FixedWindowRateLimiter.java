package br.com.smartatende.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Component
public class FixedWindowRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long startedAt, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Duration window;
    private final LongSupplier clock;

    @Autowired
    public FixedWindowRateLimiter(
            @Value("${app.rate-limit.window:60s}") Duration window) {
        this(window, System::currentTimeMillis);
    }

    FixedWindowRateLimiter(Duration window, LongSupplier clock) {
        this.window = window == null || window.isZero() || window.isNegative()
                ? Duration.ofSeconds(60)
                : window;
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int limit) {

        if (limit <= 0) {
            return true;
        }

        long now = clock.getAsLong();
        long windowMillis = window.toMillis();

        Window updated = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });

        if (windows.size() > CLEANUP_THRESHOLD) {
            removeExpired(now, windowMillis);
        }

        return updated.count() <= limit;
    }

    public void reset() {
        windows.clear();
    }

    private void removeExpired(long now, long windowMillis) {
        windows.entrySet().removeIf(
                entry -> now - entry.getValue().startedAt() >= windowMillis);
    }
}
