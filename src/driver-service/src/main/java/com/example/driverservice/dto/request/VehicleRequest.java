package com.example.driverservice.dto.request;

import com.example.driverservice.entity.VehicleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Vehicle registered together with the driver profile")
public class VehicleRequest {

    // Vietnamese plates: 29A-12345, 29A-123.45, 29A-1234 (old), 29B1-12345 (motorbike), 30LD-12345
    @Schema(description = "License plate; dots are removed and letters upper-cased before saving", example = "29A-123.45")
    @NotBlank(message = "License plate is required")
    @Pattern(regexp = "^[0-9]{2}[A-Z]{1,2}[0-9]?-([0-9]{4,5}|[0-9]{3}\\.[0-9]{2})$",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "License plate format is invalid (e.g. 29A-12345 or 29A-123.45)")
    private String licensePlate;

    @Schema(description = "Manufacturer", example = "Toyota")
    @NotBlank(message = "Vehicle make is required")
    @Size(max = 50, message = "Vehicle make must be at most 50 characters")
    private String make;

    @Schema(description = "Model", example = "Vios")
    @NotBlank(message = "Vehicle model is required")
    @Size(max = 50, message = "Vehicle model must be at most 50 characters")
    private String model;

    @Schema(description = "Color", example = "White")
    @NotBlank(message = "Vehicle color is required")
    @Size(max = 30, message = "Vehicle color must be at most 30 characters")
    private String color;

    @Schema(description = "Manufacturing year (1980 to next year)", example = "2022")
    @NotNull(message = "Vehicle year is required")
    @Min(value = 1980, message = "Vehicle year must be 1980 or later")
    private Integer year;

    @Schema(description = "Vehicle type", example = "SEDAN", allowableValues = {"SEDAN", "SUV", "MOTORBIKE", "VAN"})
    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;
}
