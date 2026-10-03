package com.example.matchingservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RematchResponse {
    private UUID requestId;
    private String status;
    private Integer attempt;
    private UUID matchedDriverId;
    private String message;
}
