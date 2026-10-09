package com.example.driverservice.dto.response;

import com.example.driverservice.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Driver details: profile, active vehicle and last known position")
public class DriverDetailResponse {

    @Schema(description = "Driver id (= the driver's user id)", example = "d0000000-0000-4000-8000-000000000001")
    private UUID id;

    @Schema(description = "Full name of the driver", example = "Tran Van Binh")
    private String fullName;

    @Schema(description = "Availability status", example = "AVAILABLE")
    private DriverStatus status;

    @Schema(description = "Active vehicle; null if the driver has none")
    private VehicleResponse vehicle;

    @Schema(description = "Last known position; null if the driver never reported one")
    private LocationResponse location;
}
