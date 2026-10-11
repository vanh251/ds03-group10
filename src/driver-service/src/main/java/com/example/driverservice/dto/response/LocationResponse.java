package com.example.driverservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Last known (simulated) position of a driver")
public class LocationResponse {

    @Schema(description = "Latitude", example = "21.0285")
    private Double latitude;

    @Schema(description = "Longitude", example = "105.8542")
    private Double longitude;

    @Schema(description = "When the position was last reported (UTC)", example = "2026-10-09T10:05:00Z")
    private Instant updatedAt;
}
