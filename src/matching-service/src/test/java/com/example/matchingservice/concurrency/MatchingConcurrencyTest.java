package com.example.matchingservice.concurrency;

import com.example.matchingservice.client.DriverServiceClient;
import com.example.matchingservice.dto.AvailableDriverDto;
import com.example.matchingservice.dto.MatchResponse;
import com.example.matchingservice.dto.ReserveDriverResponse;
import com.example.matchingservice.entity.DriverOffer;
import com.example.matchingservice.entity.MatchRequest;
import com.example.matchingservice.entity.MatchStatus;
import com.example.matchingservice.event.MatchingEventPublisher;
import com.example.matchingservice.exception.DriverReservationConflictException;
import com.example.matchingservice.exception.MatchingFailedException;
import com.example.matchingservice.repository.DriverOfferRepository;
import com.example.matchingservice.repository.MatchRequestRepository;
import com.example.matchingservice.service.MatchingServiceImpl;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingConcurrencyTest {

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

    private UUID requestAId;
    private UUID requestBId;
    private UUID sharedDriverId;
    private MatchRequest requestA;
    private MatchRequest requestB;

    @BeforeEach
    void setUp() {
        requestAId = UUID.randomUUID();
        requestBId = UUID.randomUUID();
        sharedDriverId = UUID.randomUUID();

        requestA = MatchRequest.builder()
                .requestId(requestAId)
                .tripId(requestAId)
                .passengerId(UUID.randomUUID())
                .correlationId("corr-A")
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .searchRadiusKm(5.0)
                .status(MatchStatus.PENDING)
                .attemptCount(1)
                .build();

        requestB = MatchRequest.builder()
                .requestId(requestBId)
                .tripId(requestBId)
                .passengerId(UUID.randomUUID())
                .correlationId("corr-B")
                .pickupLat(21.0285)
                .pickupLng(105.8542)
                .searchRadiusKm(5.0)
                .status(MatchStatus.PENDING)
                .attemptCount(1)
                .build();
    }

    @Test
    @DisplayName("Concurrency: Request A and Request B competing for same driver -> 1 SUCCESS, 1 FAILURE (no double booking)")
    void concurrentMatching_sameDriver_onlyOneSucceeds() throws Exception {
        when(matchRequestRepository.findById(requestAId)).thenReturn(Optional.of(requestA));
        when(matchRequestRepository.findById(requestBId)).thenReturn(Optional.of(requestB));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(driverOfferRepository.save(any(DriverOffer.class))).thenAnswer(i -> i.getArgument(0));

        AvailableDriverDto sharedDriver = AvailableDriverDto.builder()
                .id(sharedDriverId)
                .fullName("Nguyen Van Driver")
                .status("AVAILABLE")
                .distanceKm(0.8)
                .version(1)
                .build();

        // Both requests see the same available driver
        when(driverServiceClient.getAvailableDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(sharedDriver));

        // CAS simulation: atomic check-and-set
        // Only the first thread that executes reserveDriver wins the CAS. The second thread gets 409 Conflict.
        AtomicBoolean isReserved = new AtomicBoolean(false);

        when(driverServiceClient.reserveDriver(eq(sharedDriverId), any(UUID.class), eq(1)))
                .thenAnswer(invocation -> {
                    // Simulate atomic CAS: compareAndSet(false, true)
                    boolean casSuccess = isReserved.compareAndSet(false, true);
                    if (casSuccess) {
                        return ReserveDriverResponse.builder()
                                .driverId(sharedDriverId)
                                .status("RESERVED")
                                .reservedForTrip(invocation.getArgument(1))
                                .version(2)
                                .build();
                    } else {
                        // CAS Failed: driver already reserved
                        throw new DriverReservationConflictException(sharedDriverId);
                    }
                });

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Thread 1: Request A
        executor.submit(() -> {
            try {
                startLatch.await();
                MatchResponse response = matchingService.matchDriver(requestAId, "corr-A");
                if (response != null && response.getStatus() == MatchStatus.MATCHED) {
                    successCount.incrementAndGet();
                }
            } catch (MatchingFailedException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Request B
        executor.submit(() -> {
            try {
                startLatch.await();
                MatchResponse response = matchingService.matchDriver(requestBId, "corr-B");
                if (response != null && response.getStatus() == MatchStatus.MATCHED) {
                    successCount.incrementAndGet();
                }
            } catch (MatchingFailedException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Release both threads simultaneously to create race condition
        startLatch.countDown();
        boolean completedInTime = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completedInTime, "Concurrent requests should complete within timeout");

        // Verify concurrency invariant: exactly 1 SUCCESS and 1 CONFLICT/FAILURE
        assertEquals(1, successCount.get(), "Exactly ONE request must succeed in acquiring the reservation");
        assertEquals(1, failureCount.get(), "Exactly ONE request must fail due to CAS conflict");
        assertNotEquals(2, successCount.get(), "Double booking MUST NOT occur!");
    }
}
