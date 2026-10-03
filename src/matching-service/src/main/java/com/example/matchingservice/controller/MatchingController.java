package com.example.matchingservice.controller;

import com.example.matchingservice.dto.MatchResponse;
import com.example.matchingservice.dto.RematchResponse;
import com.example.matchingservice.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Matching", description = "Driver matching and rematching APIs")
@SecurityRequirement(name = "bearerAuth")
public class MatchingController {

    private final MatchingService matchingService;

    @Operation(summary = "Match driver", description = "Finds and reserves an available driver for the given ride request")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Matching request accepted and processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameter or missing correlation ID"),
            @ApiResponse(responseCode = "404", description = "Ride request not found"),
            @ApiResponse(responseCode = "409", description = "Ride request is in an invalid state for matching")
    })
    @PostMapping("/{requestId}/match")
    public ResponseEntity<MatchResponse> match(
            @Parameter(description = "ID of the ride request to match", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("requestId") UUID requestId,
            @Parameter(description = "Optional correlation ID for distributed tracing")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Received POST /api/matching/{}/match, correlationId={}", requestId, correlationId);
        MatchResponse response = matchingService.matchDriver(requestId, correlationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Rematch driver", description = "Attempts to re-match another driver when current driver rejects or match times out")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Rematch request accepted and processed"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameter"),
            @ApiResponse(responseCode = "404", description = "Ride request not found"),
            @ApiResponse(responseCode = "409", description = "Max rematch retries exceeded or invalid request state")
    })
    @PostMapping("/{requestId}/rematch")
    public ResponseEntity<RematchResponse> rematch(
            @Parameter(description = "ID of the ride request to rematch", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("requestId") UUID requestId,
            @Parameter(description = "Optional correlation ID for distributed tracing")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        log.info("Received POST /api/matching/{}/rematch, correlationId={}", requestId, correlationId);
        RematchResponse response = matchingService.rematchDriver(requestId, correlationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}

