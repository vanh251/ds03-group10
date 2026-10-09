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

/**
 * Same top-level fields as {@link UserResponse}, plus the driver profile created in driver-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Registered driver account and its driver profile")
public class DriverRegisterResponse {

    @Schema(description = "Unique user identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Full name of the user", example = "Tran Van B")
    private String fullName;

    @Schema(description = "Email address of the user", example = "driver@example.com")
    private String email;

    @Schema(description = "Account role, always DRIVER", example = "DRIVER")
    private UserRole role;

    @Schema(description = "Account creation timestamp (UTC)", example = "2026-10-09T10:00:00Z")
    private Instant createdAt;

    @Schema(description = "Driver profile and vehicle")
    private DriverProfileResponse driver;
}
