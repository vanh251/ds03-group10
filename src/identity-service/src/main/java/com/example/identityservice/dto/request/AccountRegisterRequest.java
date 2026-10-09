package com.example.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Account fields shared by passenger and driver registration. The role is not part of the body:
 * each endpoint sets it, so ADMIN can never be self-registered.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@ToString
public abstract class AccountRegisterRequest {

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
}
