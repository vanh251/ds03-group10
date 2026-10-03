package com.example.tripservice.exception;

import java.util.UUID;

public class RideRequestNotFoundException extends RuntimeException {
    public RideRequestNotFoundException(UUID requestId) {
        super("Ride request not found with id: " + requestId);
    }

    public RideRequestNotFoundException(String message) {
        super(message);
    }
}
