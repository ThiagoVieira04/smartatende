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

    public record Decision(
            boolean allowed,
            int remaining,
            long retryAfterSeconds) {
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

    public Decision acquire(String key, int limit) {

        if (limit <= 0) {
            return new Decision(true, Integer.MAX_VALUE, 0);
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

        boolean allowed = updated.count() <= limit;
        int remaining = Math.max(0, limit - updated.count());
        long retryAfter = allowed
                ? 0
                : segundosAteProximaJanela(updated.startedAt(), now, windowMillis);

        return new Decision(allowed, remaining, retryAfter);
    }

    public boolean tryAcquire(String key, int limit) {
        return acquire(key, limit).allowed();
    }

    public void reset() {
        windows.clear();
    }

    private long segundosAteProximaJanela(
            long startedAt,
            long now,
            long windowMillis) {

        long restanteMillis = startedAt + windowMillis - now;

        if (restanteMillis <= 0) {
            return 1;
        }

        return Math.max(1, (restanteMillis + 999) / 1000);
    }

    private void removeExpired(long now, long windowMillis) {
        windows.entrySet().removeIf(
                entry -> now - entry.getValue().startedAt() >= windowMillis);
    }
}
