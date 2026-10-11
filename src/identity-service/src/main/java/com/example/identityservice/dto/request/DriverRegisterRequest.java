package com.example.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@Schema(description = "Request payload to register a driver account together with the driver's vehicle")
public class DriverRegisterRequest extends AccountRegisterRequest {

    @Schema(description = "Driver license number (12 digits, validated by driver-service)", example = "012345678901")
    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @Schema(description = "Vehicle of the driver")
    @NotNull(message = "Vehicle is required")
    @Valid
    private VehicleRequest vehicle;
}
