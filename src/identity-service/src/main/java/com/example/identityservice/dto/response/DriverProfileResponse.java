package com.example.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Driver profile created in driver-service")
public class DriverProfileResponse {

    @Schema(description = "Driver identifier in driver-service", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID driverId;

    @Schema(description = "Availability status; a new driver starts OFFLINE", example = "OFFLINE")
    private String status;

    @Schema(description = "Driver license number", example = "012345678901")
    private String licenseNumber;

    @Schema(description = "Vehicle of the driver")
    private VehicleResponse vehicle;
}
