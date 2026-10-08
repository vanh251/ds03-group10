package com.example.identityservice.dto.response;

import com.example.identityservice.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Public user profile information")
public class UserResponse {

    @Schema(description = "Unique user identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Full name of the user", example = "Nguyen Van A")
    private String fullName;

    @Schema(description = "Email address of the user", example = "user@example.com")
    private String email;

    @Schema(description = "Account role", example = "PASSENGER")
    private UserRole role;

    @Schema(description = "Account creation timestamp (UTC)", example = "2026-09-30T10:00:00Z")
    private Instant createdAt;
}
