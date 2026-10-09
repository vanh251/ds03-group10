package com.example.driverservice.dto.response;

import com.example.driverservice.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Driver profile with its vehicle")
public class DriverProfileResponse {

    @Schema(description = "Driver identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID driverId;

    @Schema(description = "User id in identity-service", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Availability status; a new profile starts OFFLINE", example = "OFFLINE")
    private DriverStatus status;

    @Schema(description = "Driver license number", example = "012345678901")
    private String licenseNumber;

    @Schema(description = "Vehicle of the driver")
    private VehicleResponse vehicle;

    @Schema(description = "Profile creation timestamp (UTC)", example = "2026-10-09T10:00:00Z")
    private Instant createdAt;
}
