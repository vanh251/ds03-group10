package com.example.driverservice.dto.response;

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
@Schema(description = "AVAILABLE driver near a pickup point")
public class NearbyDriverResponse {

    @Schema(description = "Driver id (= the driver's user id)", example = "d0000000-0000-4000-8000-000000000001")
    private UUID id;

    @Schema(description = "Full name of the driver", example = "Tran Van Binh")
    private String fullName;

    @Schema(description = "Distance to the pickup point in km (2 decimals)", example = "0.49")
    private Double distanceKm;

    @Schema(description = "Last known position")
    private LocationResponse location;
}
