package com.example.tripservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Optional request body for trip cancellation")
public class CancelTripRequest {

    @Schema(description = "Reason for cancelling the trip", example = "Changed plans")
    private String reason;
}
