package com.example.tripservice.controller;

import com.example.tripservice.dto.CancelTripRequest;
import com.example.tripservice.dto.CreateTripRequest;
import com.example.tripservice.dto.TripResponse;
import com.example.tripservice.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {

        log.info("Received POST /api/trips request, correlationId={}, userId={}", correlationId, userId);
        TripResponse response = tripService.createTrip(request, correlationId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(@PathVariable("id") UUID id) {
        log.info("Received GET /api/trips/{}", id);
        TripResponse response = tripService.getTrip(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<TripResponse> acceptTrip(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-User-Id", required = false) UUID driverId,
            @RequestParam(value = "driverId", required = false) UUID queryDriverId) {

        UUID effectiveDriverId = driverId != null ? driverId : queryDriverId;
        log.info("Received POST /api/trips/{}/accept, driverId={}", id, effectiveDriverId);
        TripResponse response = tripService.acceptTrip(id, effectiveDriverId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<TripResponse> startTrip(@PathVariable("id") UUID id) {
        log.info("Received POST /api/trips/{}/start", id);
        TripResponse response = tripService.startTrip(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TripResponse> completeTrip(@PathVariable("id") UUID id) {
        log.info("Received POST /api/trips/{}/complete", id);
        TripResponse response = tripService.completeTrip(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TripResponse> cancelTrip(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) CancelTripRequest request) {

        log.info("Received POST /api/trips/{}/cancel", id);
        TripResponse response = tripService.cancelTrip(id, request);
        return ResponseEntity.ok(response);
    }
}
