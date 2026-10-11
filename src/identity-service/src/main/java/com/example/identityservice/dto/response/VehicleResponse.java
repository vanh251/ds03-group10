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
@Schema(description = "Vehicle of the driver, as stored by driver-service")
public class VehicleResponse {

    @Schema(description = "Vehicle identifier", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID vehicleId;

    @Schema(description = "Normalized license plate", example = "29A-12345")
    private String licensePlate;

    @Schema(description = "Manufacturer", example = "Toyota")
    private String make;

    @Schema(description = "Model", example = "Vios")
    private String model;

    @Schema(description = "Color", example = "White")
    private String color;

    @Schema(description = "Manufacturing year", example = "2022")
    private Integer year;

    @Schema(description = "Vehicle type", example = "SEDAN")
    private String vehicleType;
}
