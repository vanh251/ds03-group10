package com.example.identityservice.controller;

import com.example.identityservice.dto.request.DriverRegisterRequest;
import com.example.identityservice.dto.request.LoginRequest;
import com.example.identityservice.dto.request.PassengerRegisterRequest;
import com.example.identityservice.dto.response.DriverRegisterResponse;
import com.example.identityservice.dto.response.ErrorResponse;
import com.example.identityservice.dto.response.LoginResponse;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login APIs")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a passenger",
            description = "Creates a PASSENGER account. The password is stored as a BCrypt hash. Public endpoint, no JWT required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Passenger registered successfully",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or malformed body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register/passenger")
    public ResponseEntity<UserResponse> registerPassenger(@Valid @RequestBody PassengerRegisterRequest request) {
        UserResponse response = authService.registerPassenger(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Register a driver with a vehicle",
            description = "Creates a DRIVER account, then the driver profile and vehicle in driver-service. "
                    + "If driver-service refuses or cannot be reached, the account is removed again. Public endpoint, no JWT required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver registered; the profile starts OFFLINE",
                    content = @Content(schema = @Schema(implementation = DriverRegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (account fields here, vehicle formats in driver-service)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email or license plate already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "driver-service unavailable; nothing was kept, the client may retry",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register/driver")
    public ResponseEntity<DriverRegisterResponse> registerDriver(@Valid @RequestBody DriverRegisterRequest request) {
        DriverRegisterResponse response = authService.registerDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log in",
            description = "Verifies email and password and returns a JWT access token (HS256). Public endpoint, no JWT required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or malformed body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
