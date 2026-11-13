package br.com.smartatende.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTests {

    private final AtomicLong agora = new AtomicLong(1_000);

    private final FixedWindowRateLimiter limiter =
            new FixedWindowRateLimiter(Duration.ofSeconds(60), agora::get);

    @Test
    void permiteAteOLimite() {
        assertTrue(limiter.tryAcquire("ip:auth", 2));
        assertTrue(limiter.tryAcquire("ip:auth", 2));
        assertFalse(limiter.tryAcquire("ip:auth", 2));
    }

    @Test
    void liberaQuandoNovaJanelaComeca() {
        assertTrue(limiter.tryAcquire("ip:auth", 1));
        assertFalse(limiter.tryAcquire("ip:auth", 1));

        agora.addAndGet(60_000);

        assertTrue(limiter.tryAcquire("ip:auth", 1));
    }

    @Test
    void chavesDiferentesSaoIndependentes() {
        assertTrue(limiter.tryAcquire("ip1:auth", 1));
        assertFalse(limiter.tryAcquire("ip1:auth", 1));
        assertTrue(limiter.tryAcquire("ip2:auth", 1));
    }

    @Test
    void resetLimpaContadores() {
        assertTrue(limiter.tryAcquire("ip:auth", 1));
        assertFalse(limiter.tryAcquire("ip:auth", 1));

        limiter.reset();

        assertTrue(limiter.tryAcquire("ip:auth", 1));
    }
}
