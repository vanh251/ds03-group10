package com.example.tripservice.service;

import com.example.tripservice.dto.CancelTripRequest;
import com.example.tripservice.dto.CreateTripRequest;
import com.example.tripservice.dto.TripResponse;
import com.example.tripservice.entity.RideRequest;
import com.example.tripservice.entity.Trip;
import com.example.tripservice.entity.TripStatus;
import com.example.tripservice.entity.TripStatusHistory;
import com.example.tripservice.event.EventEnvelope;
import com.example.tripservice.event.TripEventPublisher;
import com.example.tripservice.exception.InvalidTripStateException;
import com.example.tripservice.exception.TripNotFoundException;
import com.example.tripservice.mapper.TripMapper;
import com.example.tripservice.repository.RideRequestRepository;
import com.example.tripservice.repository.TripRepository;
import com.example.tripservice.repository.TripStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final RideRequestRepository rideRequestRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final TripMapper tripMapper;
    private final TripEventPublisher eventPublisher;

    @Override
    @Transactional
    public TripResponse createTrip(CreateTripRequest request, String correlationId, UUID passengerId) {
        UUID effectivePassengerId = passengerId != null ? passengerId :
                (request.getPassengerId() != null ? request.getPassengerId() : UUID.randomUUID());

        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank()) ?
                correlationId : UUID.randomUUID().toString();

        double distanceKm = calculateDistanceKm(
                request.getPickup().getLatitude(), request.getPickup().getLongitude(),
                request.getDestination().getLatitude(), request.getDestination().getLongitude()
        );

        BigDecimal estimatedFare = request.getEstimatedFare();
        if (estimatedFare == null) {
            estimatedFare = calculateEstimatedFare(distanceKm);
        }

        RideRequest rideRequest = RideRequest.builder()
                .passengerId(effectivePassengerId)
                .pickupAddress(request.getPickup().getAddress())
                .pickupLat(request.getPickup().getLatitude())
                .pickupLng(request.getPickup().getLongitude())
                .dropoffAddress(request.getDestination().getAddress())
                .dropoffLat(request.getDestination().getLatitude())
                .dropoffLng(request.getDestination().getLongitude())
                .status("PENDING")
                .correlationId(effectiveCorrelationId)
                .build();
        rideRequest = rideRequestRepository.save(rideRequest);

        Trip trip = Trip.builder()
                .rideRequestId(rideRequest.getRequestId())
                .passengerId(effectivePassengerId)
                .status(TripStatus.SEARCHING)
                .pickupAddress(request.getPickup().getAddress())
                .pickupLat(request.getPickup().getLatitude())
                .pickupLng(request.getPickup().getLongitude())
                .dropoffAddress(request.getDestination().getAddress())
                .dropoffLat(request.getDestination().getLatitude())
                .dropoffLng(request.getDestination().getLongitude())
                .distanceKm(distanceKm)
                .estimatedFare(estimatedFare)
                .currency("VND")
                .correlationId(effectiveCorrelationId)
                .build();
        trip = tripRepository.save(trip);

        rideRequest.setTripId(trip.getTripId());
        rideRequest.setStatus("SEARCHING");
        rideRequestRepository.save(rideRequest);

        recordStatusHistory(trip.getTripId(), TripStatus.REQUESTED, TripStatus.SEARCHING, "Ride requested and searching for driver");

        publishTripEvent("RideRequested", trip.getTripId(), effectiveCorrelationId, Map.of(
                "tripId", trip.getTripId(),
                "passengerId", effectivePassengerId,
                "pickup", request.getPickup(),
                "destination", request.getDestination(),
                "estimatedFare", estimatedFare,
                "requestedAt", trip.getCreatedAt()
        ));

        log.info("Trip created: tripId={}, passengerId={}, correlationId={}", trip.getTripId(), effectivePassengerId, effectiveCorrelationId);
        return tripMapper.toResponse(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripResponse getTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        return tripMapper.toResponse(trip);
    }

    @Override
    @Transactional
    public TripResponse acceptTrip(UUID tripId, UUID driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (trip.getStatus() != TripStatus.SEARCHING && trip.getStatus() != TripStatus.ASSIGNED) {
            throw new InvalidTripStateException(trip.getStatus(), TripStatus.ASSIGNED);
        }

        TripStatus previousStatus = trip.getStatus();
        if (trip.getStatus() == TripStatus.SEARCHING) {
            trip.setStatus(TripStatus.ASSIGNED);
            recordStatusHistory(tripId, previousStatus, TripStatus.ASSIGNED, "Driver accepted trip");
        }

        if (driverId != null) {
            trip.setDriverId(driverId);
        }

        trip = tripRepository.save(trip);
        log.info("Trip accepted: tripId={}, driverId={}, status={}", tripId, trip.getDriverId(), trip.getStatus());
        return tripMapper.toResponse(trip);
    }

    @Override
    @Transactional
    public TripResponse startTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (trip.getStatus() != TripStatus.ASSIGNED) {
            throw new InvalidTripStateException(trip.getStatus(), TripStatus.IN_PROGRESS);
        }

        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setStartedAt(Instant.now());
        recordStatusHistory(tripId, TripStatus.ASSIGNED, TripStatus.IN_PROGRESS, "Driver started trip");
        trip = tripRepository.save(trip);

        publishTripEvent("TripStarted", trip.getTripId(), trip.getCorrelationId(), Map.of(
                "tripId", trip.getTripId(),
                "driverId", trip.getDriverId() != null ? trip.getDriverId() : "",
                "startedAt", trip.getStartedAt()
        ));

        log.info("Trip started: tripId={}", tripId);
        return tripMapper.toResponse(trip);
    }

    @Override
    @Transactional
    public TripResponse completeTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new InvalidTripStateException(trip.getStatus(), TripStatus.COMPLETED);
        }

        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(Instant.now());
        if (trip.getFinalFare() == null) {
            trip.setFinalFare(trip.getEstimatedFare() != null ? trip.getEstimatedFare() : BigDecimal.valueOf(30000));
        }

        recordStatusHistory(tripId, TripStatus.IN_PROGRESS, TripStatus.COMPLETED, "Driver completed trip");
        trip = tripRepository.save(trip);

        publishTripEvent("TripCompleted", trip.getTripId(), trip.getCorrelationId(), Map.of(
                "tripId", trip.getTripId(),
                "driverId", trip.getDriverId() != null ? trip.getDriverId() : "",
                "finalFare", trip.getFinalFare(),
                "completedAt", trip.getCompletedAt()
        ));

        log.info("Trip completed: tripId={}, finalFare={}", tripId, trip.getFinalFare());
        return tripMapper.toResponse(trip);
    }

    @Override
    @Transactional
    public TripResponse cancelTrip(UUID tripId, CancelTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (trip.getStatus() != TripStatus.REQUESTED &&
            trip.getStatus() != TripStatus.SEARCHING &&
            trip.getStatus() != TripStatus.ASSIGNED) {
            throw new InvalidTripStateException("Cannot cancel trip in status: " + trip.getStatus());
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.CANCELLED);
        trip.setCancelledAt(Instant.now());
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Cancelled by user";
        trip.setCancellationReason(reason);

        recordStatusHistory(tripId, previousStatus, TripStatus.CANCELLED, reason);
        trip = tripRepository.save(trip);

        if (trip.getRideRequestId() != null) {
            rideRequestRepository.findById(trip.getRideRequestId()).ifPresent(rr -> {
                rr.setStatus("CANCELLED");
                rideRequestRepository.save(rr);
            });
        }

        publishTripEvent("TripCancelled", trip.getTripId(), trip.getCorrelationId(), Map.of(
                "tripId", trip.getTripId(),
                "reason", reason,
                "cancelledAt", trip.getCancelledAt()
        ));

        log.info("Trip cancelled: tripId={}, reason={}", tripId, reason);
        return tripMapper.toResponse(trip);
    }

    @Override
    @Transactional
    public TripResponse assignDriver(UUID tripId, UUID driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (trip.getStatus() != TripStatus.SEARCHING) {
            throw new InvalidTripStateException(trip.getStatus(), TripStatus.ASSIGNED);
        }

        trip.setDriverId(driverId);
        trip.setStatus(TripStatus.ASSIGNED);
        recordStatusHistory(tripId, TripStatus.SEARCHING, TripStatus.ASSIGNED, "Driver reserved and assigned");
        trip = tripRepository.save(trip);

        publishTripEvent("DriverAssigned", trip.getTripId(), trip.getCorrelationId(), Map.of(
                "tripId", trip.getTripId(),
                "driverId", driverId,
                "assignedAt", Instant.now()
        ));

        log.info("Driver assigned to trip: tripId={}, driverId={}", tripId, driverId);
        return tripMapper.toResponse(trip);
    }

    private void recordStatusHistory(UUID tripId, TripStatus fromStatus, TripStatus toStatus, String reason) {
        TripStatusHistory history = TripStatusHistory.builder()
                .tripId(tripId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .changedAt(Instant.now())
                .build();
        tripStatusHistoryRepository.save(history);
    }

    private void publishTripEvent(String eventType, UUID tripId, String correlationId, Object payload) {
        UUID corrUuid;
        try {
            corrUuid = UUID.fromString(correlationId);
        } catch (Exception e) {
            corrUuid = UUID.randomUUID();
        }

        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .aggregateId(tripId.toString())
                .aggregateType("Trip")
                .producer("trip-service")
                .correlationId(corrUuid)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        eventPublisher.publish(envelope);
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 100.0) / 100.0;
    }

    private BigDecimal calculateEstimatedFare(double distanceKm) {
        // Base fare 15,000 VND + 10,000 VND per km
        double fare = 15000 + (distanceKm * 10000);
        return BigDecimal.valueOf(fare).setScale(0, RoundingMode.HALF_UP);
    }
}
