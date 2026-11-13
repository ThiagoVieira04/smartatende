package br.com.smartatende.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCircuitBreakerTests {

    private final AtomicReference<Instant> agora =
            new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    private final AiCircuitBreaker circuitBreaker =
            new AiCircuitBreaker(2, Duration.ofSeconds(30), agora::get);

    @Test
    void permiteRequisicoesEnquantoFechado() {
        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordSuccess();
        assertTrue(circuitBreaker.allowRequest());
        assertEquals(AiCircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void abreAposAtingirLimiteDeFalhas() {
        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();

        assertEquals(AiCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void permaneceAbertoAteDuracaoConfigurada() {
        abrirCircuito();

        avancar(Duration.ofSeconds(29));
        assertFalse(circuitBreaker.allowRequest());

        avancar(Duration.ofSeconds(1));
        assertTrue(circuitBreaker.allowRequest());
        assertEquals(AiCircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void permiteApenasUmaTentativaNoHalfOpen() {
        abrirCircuito();
        avancar(Duration.ofSeconds(30));

        assertTrue(circuitBreaker.allowRequest());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void sucessoNoHalfOpenFechaCircuito() {
        abrirCircuito();
        avancar(Duration.ofSeconds(30));

        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordSuccess();

        assertEquals(AiCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void falhaNoHalfOpenReabreCircuito() {
        abrirCircuito();
        avancar(Duration.ofSeconds(30));

        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();

        assertEquals(AiCircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    void resetFechaCircuito() {
        abrirCircuito();

        circuitBreaker.reset();

        assertEquals(AiCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    private void abrirCircuito() {
        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();
        assertEquals(AiCircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    private void avancar(Duration duration) {
        agora.updateAndGet(instant -> instant.plus(duration));
    }
}
