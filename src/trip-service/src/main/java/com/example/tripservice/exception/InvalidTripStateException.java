package com.example.tripservice.exception;

import com.example.tripservice.entity.TripStatus;

public class InvalidTripStateException extends RuntimeException {
    public InvalidTripStateException(String message) {
        super(message);
    }

    public InvalidTripStateException(TripStatus current, TripStatus target) {
        super(String.format("Invalid trip state transition from %s to %s", current, target));
    }
}
