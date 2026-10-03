package com.example.matchingservice.exception;

import java.util.UUID;

public class DriverUnavailableException extends RuntimeException {
    public DriverUnavailableException(UUID driverId) {
        super("Driver is not available: " + driverId);
    }

    public DriverUnavailableException(String message) {
        super(message);
    }
}
