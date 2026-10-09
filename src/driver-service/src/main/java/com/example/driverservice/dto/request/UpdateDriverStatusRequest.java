package com.example.driverservice.dto.request;

import com.example.driverservice.entity.DriverStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "New availability status; a driver may only switch between OFFLINE and AVAILABLE")
public class UpdateDriverStatusRequest {

    @Schema(description = "Target status", example = "AVAILABLE", allowableValues = {"OFFLINE", "AVAILABLE"})
    @NotNull(message = "Status is required")
    private DriverStatus status;
}
