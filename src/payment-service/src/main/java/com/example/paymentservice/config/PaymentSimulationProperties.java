package com.example.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simulated payment gateway behaviour (SRS §5.6 "Simulate payment failure").
 *
 * @param failureMode NONE: every charge succeeds; ALWAYS: every charge is declined;
 *                    RATE: a charge is declined with probability {@code failureRate}
 * @param failureRate probability between 0.0 and 1.0, used only with RATE
 */
@ConfigurationProperties(prefix = "payment.simulation")
public record PaymentSimulationProperties(FailureMode failureMode, double failureRate) {

    public PaymentSimulationProperties {
        if (failureMode == null) {
            failureMode = FailureMode.NONE;
        }
        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("payment.simulation.failure-rate must be between 0.0 and 1.0");
        }
    }

    public enum FailureMode {
        NONE,
        ALWAYS,
        RATE
    }
}
