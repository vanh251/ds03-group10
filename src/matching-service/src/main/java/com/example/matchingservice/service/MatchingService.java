package com.example.matchingservice.service;

import com.example.matchingservice.dto.MatchResponse;
import com.example.matchingservice.dto.RematchResponse;

import java.util.UUID;

public interface MatchingService {

    MatchResponse matchDriver(UUID requestId, String correlationId);

    RematchResponse rematchDriver(UUID requestId, String correlationId);
}
