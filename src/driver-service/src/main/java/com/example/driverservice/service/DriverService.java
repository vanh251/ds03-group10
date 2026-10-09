package com.example.driverservice.service;

import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.response.AvailableDriverResponse;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.dto.response.DriverStatusResponse;
import com.example.driverservice.entity.DriverStatus;

import java.util.List;
import java.util.UUID;

public interface DriverService {

    DriverProfileResponse createDriver(CreateDriverRequest request);

    void deleteDriverByUserId(UUID userId);

    /**
     * AVAILABLE drivers. With a reference point, only those within radiusKm, nearest first;
     * without one, all AVAILABLE drivers with distanceKm null.
     */
    List<AvailableDriverResponse> findAvailableDrivers(Double latitude, Double longitude, double radiusKm);

    /** Driver-initiated OFFLINE ⇄ AVAILABLE switch; callerId is the user id from the JWT. */
    DriverStatusResponse updateStatus(UUID driverId, DriverStatus target, UUID callerId);
}
