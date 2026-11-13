package br.com.smartatende.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AiMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter retryCounter;
    private final Counter circuitBlockedCounter;

    public AiMetrics(MeterRegistry meterRegistry, AiCircuitBreaker circuitBreaker) {
        this.meterRegistry = meterRegistry;
        this.successCounter = Counter.builder("smartatende.ai.requests")
                .tag("result", "success")
                .description("Requisições bem-sucedidas à IA")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("smartatende.ai.retries")
                .description("Retentativas da IA por falhas transitórias")
                .register(meterRegistry);
        this.circuitBlockedCounter = Counter.builder("smartatende.ai.circuit.blocked")
                .description("Requisições bloqueadas pelo circuit breaker")
                .register(meterRegistry);

        Gauge.builder("smartatende.ai.circuit.state", circuitBreaker,
                        breaker -> stateValue(breaker.getState()))
                .description("Estado do circuit breaker (0=CLOSED, 1=HALF_OPEN, 2=OPEN)")
                .register(meterRegistry);

        Gauge.builder("smartatende.ai.circuit.failures", circuitBreaker,
                        breaker -> breaker.getConsecutiveFailures())
                .description("Falhas consecutivas do circuit breaker")
                .register(meterRegistry);
    }

    public void recordSuccess() {
        successCounter.increment();
    }

    public void recordFailure(String type) {
        meterRegistry.counter("smartatende.ai.requests",
                "result", "failure",
                "type", type).increment();
    }

    public void recordRetry() {
        retryCounter.increment();
    }

    public void recordCircuitBlocked() {
        circuitBlockedCounter.increment();
    }

    private static double stateValue(AiCircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0.0;
            case HALF_OPEN -> 1.0;
            case OPEN -> 2.0;
        };
    }
}
