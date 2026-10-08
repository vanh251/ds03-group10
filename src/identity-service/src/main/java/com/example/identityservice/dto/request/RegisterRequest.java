package com.example.identityservice.dto.request;

import com.example.identityservice.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to register a new passenger or driver account")
public class RegisterRequest {

    @Schema(description = "Full name of the user", example = "Nguyen Van A")
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    @Schema(description = "Email address used to log in (must be unique)", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    // BCrypt only accepts up to 72 bytes, so longer passwords are rejected here instead of failing in the encoder
    @Schema(description = "Account password (8-72 characters)", example = "<your-password>", minLength = 8, maxLength = 72)
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @ToString.Exclude
    private String password;

    @Schema(description = "Account role: PASSENGER or DRIVER", example = "PASSENGER", allowableValues = {"PASSENGER", "DRIVER"})
    @NotNull(message = "Role is required")
    private UserRole role;
}
