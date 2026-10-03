package com.example.matchingservice.dto;

import com.example.matchingservice.entity.MatchStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatchResponse {
    private UUID requestId;
    private MatchStatus status;
    private UUID matchedDriverId;
    private String message;
}
