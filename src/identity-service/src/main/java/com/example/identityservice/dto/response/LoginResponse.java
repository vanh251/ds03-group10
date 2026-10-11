package com.example.identityservice.dto.response;

import com.example.identityservice.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Access token issued after a successful login")
public class LoginResponse {

    @Schema(description = "Signed JWT (HS256) to send as 'Authorization: Bearer <token>'",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC4uLiJ9.signature")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token lifetime in seconds", example = "86400")
    private long expiresIn;

    @Schema(description = "Identifier of the logged-in user", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Role of the logged-in user", example = "PASSENGER")
    private UserRole role;
}
