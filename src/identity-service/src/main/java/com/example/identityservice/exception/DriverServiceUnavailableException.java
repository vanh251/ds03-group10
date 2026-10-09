package com.example.identityservice.exception;

/**
 * driver-service could not give a definite answer (timeout, connection refused, 5xx, unreadable response),
 * so the caller cannot know whether the profile was created.
 */
public class DriverServiceUnavailableException extends RuntimeException {

    public DriverServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
