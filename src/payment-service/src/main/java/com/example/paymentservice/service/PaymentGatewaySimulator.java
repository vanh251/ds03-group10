package com.example.paymentservice.service;

import com.example.paymentservice.config.PaymentSimulationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for a real payment gateway (SRS §29.4 Simulated Payment).
 * Configured with PAYMENT_FAILURE_MODE / PAYMENT_FAILURE_RATE to demo the PaymentFailed saga path.
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewaySimulator {

    public static final String SIMULATED_FAILURE = "SIMULATED_FAILURE";

    private final PaymentSimulationProperties properties;

    public boolean isDeclined() {
        return switch (properties.failureMode()) {
            case NONE -> false;
            case ALWAYS -> true;
            case RATE -> ThreadLocalRandom.current().nextDouble() < properties.failureRate();
        };
    }
}
