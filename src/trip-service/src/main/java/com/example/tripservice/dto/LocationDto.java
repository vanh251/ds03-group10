package com.example.tripservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Geographic coordinates and address details")
public class LocationDto {

    @NotNull(message = "Latitude is required")
    @Schema(description = "Latitude of location", example = "21.0285")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Schema(description = "Longitude of location", example = "105.8542")
    private Double longitude;

    @Schema(description = "Human-readable street or area address", example = "Hoan Kiem, Hanoi")
    private String address;
}
