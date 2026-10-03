package com.example.tripservice;

import com.example.tripservice.event.TripEventPublisher;
import com.example.tripservice.repository.RideRequestRepository;
import com.example.tripservice.repository.TripRepository;
import com.example.tripservice.repository.TripStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TripServiceApplicationTests {

    @MockitoBean
    private TripRepository tripRepository;

    @MockitoBean
    private RideRequestRepository rideRequestRepository;

    @MockitoBean
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @MockitoBean
    private TripEventPublisher tripEventPublisher;

    @Test
    void contextLoads() {
    }
}
