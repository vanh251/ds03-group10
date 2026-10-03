package com.example.matchingservice.controller;

import com.example.matchingservice.dto.MatchResponse;
import com.example.matchingservice.dto.RematchResponse;
import com.example.matchingservice.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @PostMapping("/{requestId}/match")
    public ResponseEntity<MatchResponse> match(
            @PathVariable("requestId") UUID requestId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Received POST /api/matching/{}/match, correlationId={}", requestId, correlationId);
        MatchResponse response = matchingService.matchDriver(requestId, correlationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{requestId}/rematch")
    public ResponseEntity<RematchResponse> rematch(
            @PathVariable("requestId") UUID requestId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Received POST /api/matching/{}/rematch, correlationId={}", requestId, correlationId);
        RematchResponse response = matchingService.rematchDriver(requestId, correlationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
