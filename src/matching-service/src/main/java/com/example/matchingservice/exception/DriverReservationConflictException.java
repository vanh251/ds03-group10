package com.example.matchingservice.exception;

import java.util.UUID;

public class DriverReservationConflictException extends RuntimeException {
    public DriverReservationConflictException(UUID driverId) {
        super("Driver reservation conflict (CAS failed) for driver: " + driverId);
    }

    public DriverReservationConflictException(String message) {
        super(message);
    }
}
