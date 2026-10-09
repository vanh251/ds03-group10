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
@Schema(description = "Driver status after an update")
public class DriverStatusResponse {

    @Schema(description = "Driver id (= the driver's user id)", example = "d0000000-0000-4000-8000-000000000001")
    private UUID id;

    @Schema(description = "Current status", example = "AVAILABLE")
    private DriverStatus status;

    @Schema(description = "Last update timestamp (UTC)", example = "2026-10-09T10:10:00Z")
    private Instant updatedAt;
}
