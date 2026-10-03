package com.example.matchingservice.service;

import com.example.matchingservice.client.DriverServiceClient;
import com.example.matchingservice.dto.AvailableDriverDto;
import com.example.matchingservice.dto.MatchResponse;
import com.example.matchingservice.dto.RematchResponse;
import com.example.matchingservice.dto.ReserveDriverResponse;
import com.example.matchingservice.entity.DriverOffer;
import com.example.matchingservice.entity.MatchRequest;
import com.example.matchingservice.entity.MatchStatus;
import com.example.matchingservice.event.MatchingEventPublisher;
import com.example.matchingservice.exception.DriverReservationConflictException;
import com.example.matchingservice.exception.MatchRequestNotFoundException;
import com.example.matchingservice.exception.MatchingFailedException;
import com.example.matchingservice.repository.DriverOfferRepository;
import com.example.matchingservice.repository.MatchRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @Mock
    private DriverOfferRepository driverOfferRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private MatchingEventPublisher eventPublisher;

    @InjectMocks
    private MatchingServiceImpl matchingService;

    private UUID requestId;
    private UUID tripId;
    private UUID driverId1;
    private UUID driverId2;
    private MatchRequest matchRequest;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        driverId1 = UUID.randomUUID();
        driverId2 = UUID.randomUUID();

        matchRequest = MatchRequest.builder()
                .requestId(requestId)
                .tripId(tripId)
                .passengerId(UUID.randomUUID())
                .correlationId("corr-001")
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .searchRadiusKm(5.0)
                .status(MatchStatus.PENDING)
                .attemptCount(1)
                .build();
    }

    @Test
    @DisplayName("match_availableDriver_success: should match and reserve available driver")
    void match_availableDriver_success() {
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(matchRequest));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(driverOfferRepository.save(any(DriverOffer.class))).thenAnswer(i -> {
            DriverOffer o = i.getArgument(0);
            if (o.getOfferId() == null) {
                o.setOfferId(UUID.randomUUID());
            }
            return o;
        });

        AvailableDriverDto candidate = AvailableDriverDto.builder()
                .id(driverId1)
                .fullName("Tran Van B")
                .status("AVAILABLE")
                .distanceKm(1.2)
                .version(1)
                .build();

        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(candidate));

        ReserveDriverResponse reserveResp = ReserveDriverResponse.builder()
                .driverId(driverId1)
                .status("RESERVED")
                .reservedForTrip(tripId)
                .reservedUntil(Instant.now().plusSeconds(30))
                .version(2)
                .build();

        when(driverServiceClient.reserveDriver(eq(driverId1), eq(tripId), eq(1)))
                .thenReturn(reserveResp);

        MatchResponse response = matchingService.matchDriver(requestId, "corr-001");

        assertNotNull(response);
        assertEquals(requestId, response.getRequestId());
        assertEquals(MatchStatus.MATCHED, response.getStatus());
        assertEquals(driverId1, response.getMatchedDriverId());

        verify(driverServiceClient, times(1)).reserveDriver(driverId1, tripId, 1);
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("match_noAvailableDriver_failure: should throw MatchingFailedException when no drivers available")
    void match_noAvailableDriver_failure() {
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(matchRequest));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));

        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        assertThrows(MatchingFailedException.class, () -> matchingService.matchDriver(requestId, "corr-001"));
        assertEquals(MatchStatus.NO_DRIVER_FOUND, matchRequest.getStatus());
    }

    @Test
    @DisplayName("match_unavailableDriver_rejected: only AVAILABLE status drivers are considered")
    void match_unavailableDriver_rejected() {
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(matchRequest));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));

        AvailableDriverDto busyDriver = AvailableDriverDto.builder()
                .id(driverId1)
                .status("BUSY")
                .distanceKm(0.5)
                .build();

        AvailableDriverDto offlineDriver = AvailableDriverDto.builder()
                .id(driverId2)
                .status("OFFLINE")
                .distanceKm(0.8)
                .build();

        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(busyDriver, offlineDriver));

        assertThrows(MatchingFailedException.class, () -> matchingService.matchDriver(requestId, "corr-001"));
        verify(driverServiceClient, never()).reserveDriver(any(), any(), any());
    }

    @Test
    @DisplayName("reserveDriver_success: atomic reservation succeeds and updates offer and match request")
    void reserveDriver_success() {
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(matchRequest));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(driverOfferRepository.save(any(DriverOffer.class))).thenAnswer(i -> i.getArgument(0));

        AvailableDriverDto candidate = AvailableDriverDto.builder()
                .id(driverId1)
                .status("AVAILABLE")
                .distanceKm(1.0)
                .version(3)
                .build();

        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(candidate));

        when(driverServiceClient.reserveDriver(driverId1, tripId, 3))
                .thenReturn(ReserveDriverResponse.builder()
                        .driverId(driverId1)
                        .status("RESERVED")
                        .version(4)
                        .build());

        MatchResponse response = matchingService.matchDriver(requestId, "corr-001");

        assertEquals(MatchStatus.MATCHED, response.getStatus());
        assertEquals(driverId1, response.getMatchedDriverId());
    }

    @Test
    @DisplayName("reserveDriver_conflict_failure: conflict on 1st driver triggers fallback to 2nd driver")
    void reserveDriver_conflict_failure() {
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(matchRequest));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(driverOfferRepository.save(any(DriverOffer.class))).thenAnswer(i -> i.getArgument(0));

        AvailableDriverDto driver1 = AvailableDriverDto.builder()
                .id(driverId1)
                .status("AVAILABLE")
                .distanceKm(1.0)
                .version(1)
                .build();

        AvailableDriverDto driver2 = AvailableDriverDto.builder()
                .id(driverId2)
                .status("AVAILABLE")
                .distanceKm(2.0)
                .version(1)
                .build();

        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(driver1, driver2));

        // Driver 1 fails with CAS Conflict
        when(driverServiceClient.reserveDriver(eq(driverId1), eq(tripId), eq(1)))
                .thenThrow(new DriverReservationConflictException(driverId1));

        // Driver 2 succeeds
        when(driverServiceClient.reserveDriver(eq(driverId2), eq(tripId), eq(1)))
                .thenReturn(ReserveDriverResponse.builder()
                        .driverId(driverId2)
                        .status("RESERVED")
                        .version(2)
                        .build());

        MatchResponse response = matchingService.matchDriver(requestId, "corr-001");

        assertNotNull(response);
        assertEquals(MatchStatus.MATCHED, response.getStatus());
        assertEquals(driverId2, response.getMatchedDriverId());

        verify(driverServiceClient, times(1)).reserveDriver(driverId1, tripId, 1);
        verify(driverServiceClient, times(1)).reserveDriver(driverId2, tripId, 1);
    }

    @Test
    @DisplayName("rematch_success: rematchDriver increments attempt and reserves available driver")
    void rematch_success() {
        matchRequest.setAttemptCount(1);
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(matchRequest));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(driverOfferRepository.save(any(DriverOffer.class))).thenAnswer(i -> i.getArgument(0));

        AvailableDriverDto driver = AvailableDriverDto.builder()
                .id(driverId1)
                .status("AVAILABLE")
                .distanceKm(1.5)
                .version(1)
                .build();

        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(driver));

        when(driverServiceClient.reserveDriver(eq(driverId1), eq(tripId), eq(1)))
                .thenReturn(ReserveDriverResponse.builder()
                        .driverId(driverId1)
                        .status("RESERVED")
                        .version(2)
                        .build());

        RematchResponse response = matchingService.rematchDriver(requestId, "corr-001");

        assertNotNull(response);
        assertEquals(requestId, response.getRequestId());
        assertEquals(2, response.getAttempt());
        assertEquals(driverId1, response.getMatchedDriverId());
    }

    @Test
    @DisplayName("rematch_notFound: throws MatchRequestNotFoundException when request does not exist")
    void rematch_notFound() {
        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThrows(MatchRequestNotFoundException.class, () -> matchingService.rematchDriver(requestId, "corr-001"));
    }
}
