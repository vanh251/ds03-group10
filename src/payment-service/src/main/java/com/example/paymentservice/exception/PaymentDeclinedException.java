package com.example.paymentservice.exception;

import org.springframework.http.HttpStatus;

/**
 * The (simulated) gateway declined the charge. Mapped to 402 Payment Required (docs/api.md).
 * The FAILED payment and its PaymentFailed outbox event are committed before this is thrown.
 */
public class PaymentDeclinedException extends BusinessException {

    public PaymentDeclinedException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED");
    }
}
