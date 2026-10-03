package com.example.matchingservice.exception;

import java.util.UUID;

public class MatchRequestNotFoundException extends RuntimeException {
    public MatchRequestNotFoundException(UUID requestId) {
        super("Match request not found with id: " + requestId);
    }

    public MatchRequestNotFoundException(String message) {
        super(message);
    }
}
