package br.com.smartatende.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AiCircuitBreakerHealthIndicator implements HealthIndicator {

    private final AiCircuitBreaker circuitBreaker;

    public AiCircuitBreakerHealthIndicator(AiCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("state", circuitBreaker.getState().name())
                .withDetail("consecutiveFailures", circuitBreaker.getConsecutiveFailures())
                .withDetail("openUntil", circuitBreaker.getOpenUntil().toString())
                .build();
    }
}
