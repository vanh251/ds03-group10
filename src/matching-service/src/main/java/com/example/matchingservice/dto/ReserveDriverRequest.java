package com.example.matchingservice.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveDriverRequest {
    private UUID tripId;
    private Integer expectedVersion;
}
