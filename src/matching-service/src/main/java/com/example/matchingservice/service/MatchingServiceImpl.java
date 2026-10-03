package com.example.matchingservice.service;

import com.example.matchingservice.client.DriverServiceClient;
import com.example.matchingservice.dto.*;
import com.example.matchingservice.entity.DriverOffer;
import com.example.matchingservice.entity.MatchRequest;
import com.example.matchingservice.entity.MatchStatus;
import com.example.matchingservice.entity.OfferStatus;
import com.example.matchingservice.event.EventEnvelope;
import com.example.matchingservice.event.MatchingEventPublisher;
import com.example.matchingservice.exception.DriverReservationConflictException;
import com.example.matchingservice.exception.MatchRequestNotFoundException;
import com.example.matchingservice.exception.MatchingFailedException;
import com.example.matchingservice.repository.DriverOfferRepository;
import com.example.matchingservice.repository.MatchRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingServiceImpl implements MatchingService {

    private final MatchRequestRepository matchRequestRepository;
    private final DriverOfferRepository driverOfferRepository;
    private final DriverServiceClient driverServiceClient;
    private final MatchingEventPublisher eventPublisher;

    @Override
    @Transactional
    public MatchResponse matchDriver(UUID requestId, String correlationId) {
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank()) ?
                correlationId : UUID.randomUUID().toString();

        log.info("Starting matching for requestId={}, correlationId={}", requestId, effectiveCorrelationId);

        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseGet(() -> createDefaultMatchRequest(requestId, effectiveCorrelationId));

        matchRequest.setStatus(MatchStatus.MATCHING);
        matchRequestRepository.save(matchRequest);

        List<AvailableDriverDto> availableDrivers = driverServiceClient.getAvailableDrivers(
                matchRequest.getPickupLat(), matchRequest.getPickupLng(), matchRequest.getSearchRadiusKm()
        );

        List<AvailableDriverDto> filteredDrivers = availableDrivers.stream()
                .filter(driver -> "AVAILABLE".equalsIgnoreCase(driver.getStatus()))
                .sorted(Comparator.comparingDouble(d -> d.getDistanceKm() != null ? d.getDistanceKm() : 0.0))
                .collect(Collectors.toList());

        if (filteredDrivers.isEmpty()) {
            log.warn("No available drivers found for requestId={}", requestId);
            matchRequest.setStatus(MatchStatus.NO_DRIVER_FOUND);
            matchRequestRepository.save(matchRequest);
            throw new MatchingFailedException("No available drivers found for requestId: " + requestId);
        }

        UUID reservedDriverId = null;
        int rank = 1;

        for (AvailableDriverDto candidate : filteredDrivers) {
            log.info("Candidate #{} selected for requestId={}: driverId={}, distanceKm={}",
                    rank, requestId, candidate.getId(), candidate.getDistanceKm());

            DriverOffer offer = DriverOffer.builder()
                    .requestId(matchRequest.getRequestId())
                    .driverId(candidate.getId())
                    .tripId(matchRequest.getTripId())
                    .correlationId(effectiveCorrelationId)
                    .status(OfferStatus.PENDING)
                    .driverVersionUsed(candidate.getVersion() != null ? candidate.getVersion() : 1)
                    .distanceKm(candidate.getDistanceKm())
                    .rank(rank++)
                    .offeredAt(Instant.now())
                    .build();
            offer = driverOfferRepository.save(offer);

            try {
                ReserveDriverResponse reservation = driverServiceClient.reserveDriver(
                        candidate.getId(), matchRequest.getTripId(), offer.getDriverVersionUsed()
                );

                if (reservation != null && "RESERVED".equalsIgnoreCase(reservation.getStatus())) {
                    log.info("CAS reservation SUCCESS for driverId={} on requestId={}", candidate.getId(), requestId);
                    offer.setStatus(OfferStatus.ACCEPTED);
                    offer.setRespondedAt(Instant.now());
                    driverOfferRepository.save(offer);

                    matchRequest.setStatus(MatchStatus.MATCHED);
                    matchRequest.setMatchedDriverId(candidate.getId());
                    matchRequestRepository.save(matchRequest);

                    reservedDriverId = candidate.getId();

                    publishDriverReservedEvent(matchRequest, candidate, reservation, effectiveCorrelationId);
                    break;
                } else {
                    log.warn("Reservation returned non-RESERVED status for driverId={}", candidate.getId());
                    offer.setStatus(OfferStatus.REJECTED);
                    offer.setRespondedAt(Instant.now());
                    driverOfferRepository.save(offer);
                }
            } catch (DriverReservationConflictException conflictEx) {
                log.warn("CAS Conflict when reserving driverId={} for requestId={}: {}",
                        candidate.getId(), requestId, conflictEx.getMessage());
                offer.setStatus(OfferStatus.CONFLICT);
                offer.setRespondedAt(Instant.now());
                driverOfferRepository.save(offer);
                // Continue to the next candidate driver in the list
            } catch (Exception ex) {
                log.error("Unexpected error reserving driverId={} for requestId={}: {}",
                        candidate.getId(), requestId, ex.getMessage());
                offer.setStatus(OfferStatus.REJECTED);
                offer.setRespondedAt(Instant.now());
                driverOfferRepository.save(offer);
            }
        }

        if (reservedDriverId == null) {
            log.warn("All available driver reservation attempts failed for requestId={}", requestId);
            matchRequest.setStatus(MatchStatus.NO_DRIVER_FOUND);
            matchRequestRepository.save(matchRequest);
            throw new MatchingFailedException("All candidate drivers failed reservation due to conflict or rejection");
        }

        return MatchResponse.builder()
                .requestId(requestId)
                .status(MatchStatus.MATCHED)
                .matchedDriverId(reservedDriverId)
                .message("Driver successfully matched and reserved")
                .build();
    }

    @Override
    @Transactional
    public RematchResponse rematchDriver(UUID requestId, String correlationId) {
        String effectiveCorrelationId = (correlationId != null && !correlationId.isBlank()) ?
                correlationId : UUID.randomUUID().toString();

        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchRequestNotFoundException(requestId));

        int newAttempt = matchRequest.getAttemptCount() + 1;
        matchRequest.setAttemptCount(newAttempt);
        matchRequest.setStatus(MatchStatus.PENDING);
        matchRequest.setMatchedDriverId(null);
        matchRequestRepository.save(matchRequest);

        MatchResponse matchResponse = matchDriver(requestId, effectiveCorrelationId);

        return RematchResponse.builder()
                .requestId(requestId)
                .status("REMATCHED")
                .attempt(newAttempt)
                .matchedDriverId(matchResponse.getMatchedDriverId())
                .message("Rematch completed successfully")
                .build();
    }

    private MatchRequest createDefaultMatchRequest(UUID requestId, String correlationId) {
        MatchRequest request = MatchRequest.builder()
                .requestId(requestId)
                .tripId(requestId)
                .passengerId(UUID.randomUUID())
                .correlationId(correlationId)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .searchRadiusKm(5.0)
                .status(MatchStatus.PENDING)
                .attemptCount(1)
                .build();
        return matchRequestRepository.save(request);
    }

    private void publishDriverReservedEvent(MatchRequest request, AvailableDriverDto candidate,
                                            ReserveDriverResponse reservation, String correlationId) {
        UUID corrUuid;
        try {
            corrUuid = UUID.fromString(correlationId);
        } catch (Exception e) {
            corrUuid = UUID.randomUUID();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("tripId", request.getTripId());
        payload.put("driverId", candidate.getId());
        payload.put("driverName", candidate.getFullName());
        payload.put("driverLocation", candidate.getLocation());
        payload.put("reservedUntil", reservation.getReservedUntil());

        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType("DriverReserved")
                .aggregateId(request.getTripId().toString())
                .aggregateType("Trip")
                .producer("matching-service")
                .correlationId(corrUuid)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        eventPublisher.publish(envelope);
    }
}
