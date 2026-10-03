package com.example.matchingservice.dto;

import com.example.matchingservice.entity.MatchStatus;
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
@Schema(description = "Response containing driver match details")
public class MatchResponse {
    @Schema(description = "Ride request ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID requestId;

    @Schema(description = "Status of the matching operation", example = "MATCHED")
    private MatchStatus status;

    @Schema(description = "ID of matched driver if found", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID matchedDriverId;

    @Schema(description = "Descriptive outcome message", example = "Driver matched successfully")
    private String message;
}
