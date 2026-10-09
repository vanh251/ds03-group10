package com.example.driverservice.service;

import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.response.DriverProfileResponse;

import java.util.UUID;

public interface DriverService {

    DriverProfileResponse createDriver(CreateDriverRequest request);

    void deleteDriverByUserId(UUID userId);
}
