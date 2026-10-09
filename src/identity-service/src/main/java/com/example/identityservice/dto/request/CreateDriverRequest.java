package com.example.identityservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Body of POST /internal/drivers sent to driver-service during driver registration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDriverRequest {

    private UUID userId;
    private String fullName;
    private String licenseNumber;
    private VehicleRequest vehicle;
}
