package com.example.driverservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal request from identity-service to create the driver profile of a newly registered DRIVER user")
public class CreateDriverRequest {

    @Schema(description = "User id from identity-service", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "User id is required")
    private UUID userId;

    @Schema(description = "Driver license number (12 digits)", example = "012345678901")
    @NotBlank(message = "License number is required")
    @Pattern(regexp = "^[0-9]{12}$", message = "License number must be exactly 12 digits")
    private String licenseNumber;

    @Schema(description = "Vehicle of the driver")
    @NotNull(message = "Vehicle is required")
    @Valid
    private VehicleRequest vehicle;
}
