package com.example.tripservice.service;

import com.example.tripservice.dto.CancelTripRequest;
import com.example.tripservice.dto.CreateTripRequest;
import com.example.tripservice.dto.LocationDto;
import com.example.tripservice.dto.TripResponse;
import com.example.tripservice.entity.RideRequest;
import com.example.tripservice.entity.Trip;
import com.example.tripservice.entity.TripStatus;
import com.example.tripservice.entity.TripStatusHistory;
import com.example.tripservice.event.TripEventPublisher;
import com.example.tripservice.exception.InvalidTripStateException;
import com.example.tripservice.exception.TripNotFoundException;
import com.example.tripservice.mapper.TripMapper;
import com.example.tripservice.repository.RideRequestRepository;
import com.example.tripservice.repository.TripRepository;
import com.example.tripservice.repository.TripStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Spy
    private TripMapper tripMapper = new TripMapper();

    @Mock
    private TripEventPublisher eventPublisher;

    @Mock
    private com.example.tripservice.repository.SagaLogRepository sagaLogRepository;

    @Mock
    private com.example.tripservice.client.PricingServiceClient pricingServiceClient;

    @InjectMocks
    private TripServiceImpl tripService;

    private UUID passengerId;
    private UUID driverId;
    private UUID tripId;
    private CreateTripRequest createTripRequest;

    @BeforeEach
    void setUp() {
        passengerId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        tripId = UUID.randomUUID();

        createTripRequest = CreateTripRequest.builder()
                .passengerId(passengerId)
                .pickup(LocationDto.builder()
                        .latitude(21.0285)
                        .longitude(105.8542)
                        .address("Hoan Kiem, Hanoi")
                        .build())
                .destination(LocationDto.builder()
                        .latitude(21.0188)
                        .longitude(105.8520)
                        .address("Dong Da, Hanoi")
                        .build())
                .estimatedFare(BigDecimal.valueOf(45000))
                .build();
    }

    @Test
    @DisplayName("createTrip_validRequest_success: should create a trip in SEARCHING status")
    void createTrip_validRequest_success() {
        when(rideRequestRepository.save(any(RideRequest.class))).thenAnswer(invocation -> {
            RideRequest r = invocation.getArgument(0);
            if (r.getRequestId() == null) {
                r.setRequestId(UUID.randomUUID());
            }
            return r;
        });

        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> {
            Trip t = invocation.getArgument(0);
            if (t.getTripId() == null) {
                t.setTripId(tripId);
            }
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        TripResponse response = tripService.createTrip(createTripRequest, "corr-001", passengerId);

        assertNotNull(response);
        assertEquals(tripId, response.getTripId());
        assertEquals(TripStatus.SEARCHING, response.getStatus());
        assertEquals(passengerId, response.getPassengerId());
        assertEquals(createTripRequest.getEstimatedFare(), response.getEstimatedFare());

        verify(rideRequestRepository, times(2)).save(any(RideRequest.class));
        verify(tripRepository, times(1)).save(any(Trip.class));
        verify(tripStatusHistoryRepository, times(1)).save(any(TripStatusHistory.class));
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("getTrip_existingId_success: should return trip response when trip exists")
    void getTrip_existingId_success() {
        Trip trip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .status(TripStatus.SEARCHING)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        TripResponse response = tripService.getTrip(tripId);

        assertNotNull(response);
        assertEquals(tripId, response.getTripId());
        assertEquals(TripStatus.SEARCHING, response.getStatus());
        assertEquals(passengerId, response.getPassengerId());
    }

    @Test
    @DisplayName("getTrip_invalidId_notFound: should throw TripNotFoundException when trip does not exist")
    void getTrip_invalidId_notFound() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThrows(TripNotFoundException.class, () -> tripService.getTrip(tripId));
    }

    @Test
    @DisplayName("acceptTrip_validState_success: should transition trip from SEARCHING to ASSIGNED")
    void acceptTrip_validState_success() {
        Trip trip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .status(TripStatus.SEARCHING)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripResponse response = tripService.acceptTrip(tripId, driverId);

        assertNotNull(response);
        assertEquals(TripStatus.ASSIGNED, response.getStatus());
        assertEquals(driverId, response.getDriverId());
        verify(tripStatusHistoryRepository, times(1)).save(any(TripStatusHistory.class));
    }

    @Test
    @DisplayName("startTrip_validState_success: should transition trip from ASSIGNED to IN_PROGRESS")
    void startTrip_validState_success() {
        Trip trip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .driverId(driverId)
                .status(TripStatus.ASSIGNED)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripResponse response = tripService.startTrip(tripId);

        assertNotNull(response);
        assertEquals(TripStatus.IN_PROGRESS, response.getStatus());
        assertNotNull(response.getStartedAt());
        verify(tripStatusHistoryRepository, times(1)).save(any(TripStatusHistory.class));
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("completeTrip_validState_success: should transition trip from IN_PROGRESS to COMPLETED")
    void completeTrip_validState_success() {
        Trip trip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .driverId(driverId)
                .status(TripStatus.IN_PROGRESS)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .estimatedFare(BigDecimal.valueOf(45000))
                .correlationId("corr-001")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .startedAt(Instant.now())
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripResponse response = tripService.completeTrip(tripId);

        assertNotNull(response);
        assertEquals(TripStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getCompletedAt());
        assertNotNull(response.getFinalFare());
        verify(tripStatusHistoryRepository, times(1)).save(any(TripStatusHistory.class));
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("cancelTrip_validState_success: should cancel trip when in SEARCHING or ASSIGNED")
    void cancelTrip_validState_success() {
        Trip trip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .status(TripStatus.SEARCHING)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CancelTripRequest cancelRequest = CancelTripRequest.builder().reason("Change plans").build();
        TripResponse response = tripService.cancelTrip(tripId, cancelRequest);

        assertNotNull(response);
        assertEquals(TripStatus.CANCELLED, response.getStatus());
        assertNotNull(response.getCancelledAt());
        verify(tripStatusHistoryRepository, times(1)).save(any(TripStatusHistory.class));
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("invalidStateTransition_rejected: starting a SEARCHING trip or cancelling COMPLETED trip should fail")
    void invalidStateTransition_rejected() {
        // Case 1: Starting a trip that is still SEARCHING
        Trip searchingTrip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .status(TripStatus.SEARCHING)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(searchingTrip));

        assertThrows(InvalidTripStateException.class, () -> tripService.startTrip(tripId));

        // Case 2: Completing a trip that is not IN_PROGRESS (e.g. ASSIGNED)
        Trip assignedTrip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .driverId(driverId)
                .status(TripStatus.ASSIGNED)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(assignedTrip));

        assertThrows(InvalidTripStateException.class, () -> tripService.completeTrip(tripId));

        // Case 3: Cancelling a COMPLETED trip
        Trip completedTrip = Trip.builder()
                .tripId(tripId)
                .passengerId(passengerId)
                .driverId(driverId)
                .status(TripStatus.COMPLETED)
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .dropoffLat(21.0188)
                .dropoffLng(105.8520)
                .correlationId("corr-001")
                .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(completedTrip));

        assertThrows(InvalidTripStateException.class, () -> tripService.cancelTrip(tripId, null));
    }
}
