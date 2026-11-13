package br.com.smartatende.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiCircuitBreakerHealthIndicatorTests {

    private final AiCircuitBreaker circuitBreaker =
            new AiCircuitBreaker(2, Duration.ofSeconds(30), Instant::now);

    private final AiCircuitBreakerHealthIndicator indicator =
            new AiCircuitBreakerHealthIndicator(circuitBreaker);

    @Test
    void reportaCircuitoFechadoComoUp() {
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("CLOSED", health.getDetails().get("state"));
        assertEquals(0, health.getDetails().get("consecutiveFailures"));
    }

    @Test
    void reportaCircuitoAbertoComDetalhes() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("OPEN", health.getDetails().get("state"));
        assertEquals(2, health.getDetails().get("consecutiveFailures"));
    }
}
