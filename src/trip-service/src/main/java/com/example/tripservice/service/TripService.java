package com.example.tripservice.service;

import com.example.tripservice.dto.CancelTripRequest;
import com.example.tripservice.dto.CreateTripRequest;
import com.example.tripservice.dto.TripResponse;

import java.util.UUID;

public interface TripService {

    TripResponse createTrip(CreateTripRequest request, String correlationId, UUID passengerId);

    TripResponse getTrip(UUID tripId);

    TripResponse acceptTrip(UUID tripId, UUID driverId);

    TripResponse startTrip(UUID tripId);

    TripResponse completeTrip(UUID tripId);

    TripResponse cancelTrip(UUID tripId, CancelTripRequest request);

    TripResponse assignDriver(UUID tripId, UUID driverId);
}
