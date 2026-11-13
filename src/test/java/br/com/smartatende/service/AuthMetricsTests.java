package br.com.smartatende.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthMetricsTests {

    private SimpleMeterRegistry registry;
    private AuthMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AuthMetrics(registry);
    }

    @Test
    void registraLoginERegistro() {
        metrics.recordLoginSuccess();
        metrics.recordLoginFailure();
        metrics.recordLoginFailure();
        metrics.recordRegisterSuccess();

        assertEquals(1.0, registry.get("smartatende.auth.login")
                .tag("result", "success").counter().count());
        assertEquals(2.0, registry.get("smartatende.auth.login")
                .tag("result", "failure").counter().count());
        assertEquals(1.0, registry.get("smartatende.auth.register")
                .tag("result", "success").counter().count());
    }
}
