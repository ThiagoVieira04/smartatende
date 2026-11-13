package br.com.smartatende.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiMetricsTests {

    private SimpleMeterRegistry registry;
    private AiCircuitBreaker circuitBreaker;
    private AiMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        circuitBreaker = new AiCircuitBreaker(2, Duration.ofSeconds(30), Instant::now);
        metrics = new AiMetrics(registry, circuitBreaker);
    }

    @Test
    void registraSucessoEFalhasPorTipo() {
        metrics.recordSuccess();
        metrics.recordFailure("timeout");
        metrics.recordFailure("rate_limit");
        metrics.recordFailure("provider");

        assertEquals(1.0, registry.get("smartatende.ai.requests")
                .tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("smartatende.ai.requests")
                .tag("result", "failure").tag("type", "timeout").counter().count());
        assertEquals(1.0, registry.get("smartatende.ai.requests")
                .tag("result", "failure").tag("type", "rate_limit").counter().count());
        assertEquals(1.0, registry.get("smartatende.ai.requests")
                .tag("result", "failure").tag("type", "provider").counter().count());
    }

    @Test
    void registraRetriesECircuitoBloqueado() {
        metrics.recordRetry();
        metrics.recordRetry();
        metrics.recordCircuitBlocked();

        assertEquals(2.0, registry.get("smartatende.ai.retries").counter().count());
        assertEquals(1.0, registry.get("smartatende.ai.circuit.blocked").counter().count());
    }

    @Test
    void expoeGaugeDoEstadoEDasFalhasDoCircuito() {
        assertEquals(0.0, registry.get("smartatende.ai.circuit.state").gauge().value());
        assertEquals(0.0, registry.get("smartatende.ai.circuit.failures").gauge().value());

        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        assertEquals(2.0, registry.get("smartatende.ai.circuit.state").gauge().value());
        assertEquals(2.0, registry.get("smartatende.ai.circuit.failures").gauge().value());
    }
}
