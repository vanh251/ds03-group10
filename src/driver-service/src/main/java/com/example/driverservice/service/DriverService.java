package com.example.driverservice.service;

import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.response.AvailableDriverResponse;
import com.example.driverservice.dto.response.DriverDetailResponse;
import com.example.driverservice.dto.response.DriverLocationResponse;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.dto.response.DriverStatusResponse;
import com.example.driverservice.dto.response.NearbyDriverResponse;
import com.example.driverservice.entity.DriverStatus;

import java.util.List;
import java.util.UUID;

public interface DriverService {

    DriverProfileResponse createDriver(CreateDriverRequest request);

    void deleteDriverByUserId(UUID userId);

    /** The {@code limit} nearest AVAILABLE drivers within radiusKm of the point, with the version used for reservation. */
    List<AvailableDriverResponse> findAvailableDrivers(double latitude, double longitude, double radiusKm, int limit);

    /** The {@code limit} nearest AVAILABLE drivers within radiusKm of the pickup point, nearest first. */
    List<NearbyDriverResponse> findNearbyDrivers(double latitude, double longitude, double radiusKm, int limit);

    /** Driver-initiated OFFLINE ⇄ AVAILABLE switch; callerId is the user id from the JWT. */
    DriverStatusResponse updateStatus(UUID driverId, DriverStatus target, UUID callerId);

    /** Stores the driver's own position report; callerId is the user id from the JWT. */
    DriverLocationResponse updateLocation(UUID driverId, double latitude, double longitude, UUID callerId);

    /** Profile, active vehicle and position; an OFFLINE driver's position is shown only to the driver (callerId). */
    DriverDetailResponse getDriver(UUID driverId, UUID callerId);
}
