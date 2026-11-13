package br.com.smartatende.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

@Component
public class AiCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreaker.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Supplier<Instant> now;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openUntil = Instant.EPOCH;
    private boolean halfOpenTrialInProgress;

    @Autowired
    public AiCircuitBreaker(
            @Value("${app.ai.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${app.ai.circuit-breaker.open-duration:30s}") Duration openDuration) {
        this(failureThreshold, openDuration, Instant::now);
    }

    AiCircuitBreaker(int failureThreshold, Duration openDuration, Supplier<Instant> now) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null || openDuration.isZero() || openDuration.isNegative()
                ? Duration.ofSeconds(30)
                : openDuration;
        this.now = now;
    }

    public synchronized boolean allowRequest() {
        Instant instant = now.get();

        if (state == State.OPEN) {
            if (instant.isBefore(openUntil)) {
                return false;
            }
            transitionTo(State.HALF_OPEN);
            halfOpenTrialInProgress = false;
        }

        if (state == State.HALF_OPEN) {
            if (halfOpenTrialInProgress) {
                return false;
            }
            halfOpenTrialInProgress = true;
        }

        return true;
    }

    public synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            transitionTo(State.CLOSED);
        }
        consecutiveFailures = 0;
        halfOpenTrialInProgress = false;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }

        consecutiveFailures++;

        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public synchronized Instant getOpenUntil() {
        return openUntil;
    }

    public synchronized void reset() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        openUntil = Instant.EPOCH;
        halfOpenTrialInProgress = false;
    }

    private void open() {
        state = State.OPEN;
        openUntil = now.get().plus(openDuration);
        halfOpenTrialInProgress = false;
        log.warn("Circuit breaker da IA aberto por {} (falhas consecutivas={}).",
                openDuration, consecutiveFailures);
    }

    private void transitionTo(State newState) {
        if (state != newState) {
            log.info("Circuit breaker da IA: {} -> {}", state, newState);
            state = newState;
        }
    }
}
