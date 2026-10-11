package com.example.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vehicle sent at driver registration. identity-service only checks that the fields are present;
 * formats (plate, year range, vehicle type) are validated by driver-service, which owns the data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Vehicle of the driver")
public class VehicleRequest {

    @Schema(description = "License plate", example = "29A-123.45")
    @NotBlank(message = "License plate is required")
    private String licensePlate;

    @Schema(description = "Manufacturer", example = "Toyota")
    @NotBlank(message = "Vehicle make is required")
    private String make;

    @Schema(description = "Model", example = "Vios")
    @NotBlank(message = "Vehicle model is required")
    private String model;

    @Schema(description = "Color", example = "White")
    @NotBlank(message = "Vehicle color is required")
    private String color;

    @Schema(description = "Manufacturing year", example = "2022")
    @NotNull(message = "Vehicle year is required")
    private Integer year;

    @Schema(description = "Vehicle type", example = "SEDAN", allowableValues = {"SEDAN", "SUV", "MOTORBIKE", "VAN"})
    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;
}
