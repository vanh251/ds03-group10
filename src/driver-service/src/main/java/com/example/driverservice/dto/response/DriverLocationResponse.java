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
@Schema(description = "Driver position after an update")
public class DriverLocationResponse {

    @Schema(description = "Driver id (= the driver's user id)", example = "d0000000-0000-4000-8000-000000000001")
    private UUID id;

    @Schema(description = "Stored position and report time")
    private LocationResponse location;
}
