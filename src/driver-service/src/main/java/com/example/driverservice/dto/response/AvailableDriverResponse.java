package com.example.driverservice.dto.response;

import com.example.driverservice.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Item of GET /api/drivers/available. Field names match matching-service's AvailableDriverDto.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AVAILABLE driver, optionally with its distance to a reference point")
public class AvailableDriverResponse {

    @Schema(description = "Driver id (= the driver's user id)", example = "d0000000-0000-4000-8000-000000000001")
    private UUID id;

    @Schema(description = "Full name of the driver", example = "Tran Van Binh")
    private String fullName;

    @Schema(description = "Always AVAILABLE in this list", example = "AVAILABLE")
    private DriverStatus status;

    @Schema(description = "Last known position; null if the driver never reported one")
    private LocationResponse location;

    @Schema(description = "Distance to the reference point in km (2 decimals); null when no point was given", example = "1.08")
    private Double distanceKm;

    @Schema(description = "Current version, passed as expectedVersion to POST /api/drivers/{id}/reserve (CAS)", example = "3")
    private Integer version;
}
