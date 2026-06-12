package com.hify.common.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Provider 熔断器状态指标绑定器。 */
@Component
@RequiredArgsConstructor
public class CircuitBreakerMetricsBinder {

    private static final String CIRCUIT_BREAKER_STATE = "hify.provider.circuit.breaker.state";
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;
    private final Set<String> registeredMeters = ConcurrentHashMap.newKeySet();

    public void bind(String providerName, CircuitBreaker circuitBreaker) {
        if (circuitBreaker == null) {
            return;
        }
        String safeProviderName = safeTag(providerName);
        registerStateGauge(safeProviderName, circuitBreaker, CircuitBreaker.State.CLOSED);
        registerStateGauge(safeProviderName, circuitBreaker, CircuitBreaker.State.OPEN);
        registerStateGauge(safeProviderName, circuitBreaker, CircuitBreaker.State.HALF_OPEN);
    }

    private void registerStateGauge(String providerName, CircuitBreaker circuitBreaker, CircuitBreaker.State state) {
        String meterKey = providerName + ":" + state.name();
        if (!registeredMeters.add(meterKey)) {
            return;
        }
        Gauge.builder(CIRCUIT_BREAKER_STATE, circuitBreaker,
                        breaker -> breaker.getState() == state ? 1.0 : 0.0)
                .tag("provider", providerName)
                .tag("state", state.name())
                .register(meterRegistry);
    }

    private String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value;
    }
}
