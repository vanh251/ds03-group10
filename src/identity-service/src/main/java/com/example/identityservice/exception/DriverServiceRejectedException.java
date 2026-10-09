package com.example.identityservice.exception;

import lombok.Getter;

/**
 * driver-service answered with a 4xx: it refused the request and created nothing.
 * Carries the status and error code from its response body so they can be passed on.
 */
@Getter
public class DriverServiceRejectedException extends RuntimeException {

    private final int status;
    private final String code;

    public DriverServiceRejectedException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
