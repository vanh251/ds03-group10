package com.example.matchingservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing driver rematching attempt details")
public class RematchResponse {
    @Schema(description = "Ride request ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID requestId;

    @Schema(description = "Outcome status of rematch attempt", example = "REMATCHED")
    private String status;

    @Schema(description = "Current retry attempt count", example = "1")
    private Integer attempt;

    @Schema(description = "ID of newly matched driver if found", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID matchedDriverId;

    @Schema(description = "Descriptive outcome message", example = "Driver rematched successfully")
    private String message;
}
