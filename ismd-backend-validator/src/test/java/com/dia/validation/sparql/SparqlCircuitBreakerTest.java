package com.dia.validation.sparql;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors {@code ismd-tool-backend}'s SparqlCircuitBreakerTest: open after threshold,
 * fast-fail while open, half-open trial after cooldown, and that only
 * {@link SparqlEndpointUnavailableException} trips the breaker.
 */
class SparqlCircuitBreakerTest {

    private static SparqlEndpointUnavailableException down() {
        return new SparqlEndpointUnavailableException("NKD", "down");
    }

    @Test
    void opensAfterThresholdAndFastFailsWhileOpen() {
        SparqlCircuitBreaker breaker = new SparqlCircuitBreaker("NKD", 3, 60_000);
        AtomicInteger calls = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> breaker.call(() -> {
                calls.incrementAndGet();
                throw down();
            })).isInstanceOf(SparqlEndpointUnavailableException.class);
        }
        assertThat(calls.get()).isEqualTo(3);

        // Breaker is now open: the action is not invoked, it fast-fails.
        assertThatThrownBy(() -> breaker.call(() -> {
            calls.incrementAndGet();
            return "unreached";
        })).isInstanceOf(SparqlEndpointUnavailableException.class)
                .hasMessageContaining("circuit breaker open");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void halfOpenTrialClosesOnSuccessAfterCooldown() throws InterruptedException {
        SparqlCircuitBreaker breaker = new SparqlCircuitBreaker("NKD", 1, 50);
        assertThatThrownBy(() -> breaker.call(() -> {
            throw down();
        })).isInstanceOf(SparqlEndpointUnavailableException.class);
        assertThat(breaker.isOpen()).isTrue();

        Thread.sleep(80); // let cooldown expire
        String result = breaker.call(() -> "ok");
        assertThat(result).isEqualTo("ok");
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void nonSparqlExceptionDoesNotTripBreaker() {
        SparqlCircuitBreaker breaker = new SparqlCircuitBreaker("NKD", 1, 60_000);
        assertThatThrownBy(() -> breaker.call(() -> {
            throw new IllegalStateException("domain error");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(breaker.isOpen()).isFalse();
    }
}
