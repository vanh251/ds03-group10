package com.example.paymentservice.exception;

/**
 * A Kafka event is malformed or misses a required field. Retrying cannot help,
 * so the consumer error handler sends the record straight to the DLQ (docs/events.md §8).
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
