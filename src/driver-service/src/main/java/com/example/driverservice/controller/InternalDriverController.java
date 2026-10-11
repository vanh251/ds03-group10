package com.example.driverservice.controller;

import com.example.driverservice.config.InternalApiKeyInterceptor;
import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.dto.response.ErrorResponse;
import com.example.driverservice.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service APIs used by identity-service during driver registration (docs/api.md, Internal APIs).
 * Not routed by the API Gateway.
 */
@RestController
@RequestMapping("/internal/drivers")
@RequiredArgsConstructor
@Tag(name = "Internal - Drivers", description = "Called by identity-service only. Requires the X-Internal-Api-Key header.")
public class InternalDriverController {

    private final DriverService driverService;

    @Operation(summary = "Create a driver profile",
            description = "Creates the driver profile (status OFFLINE) and its vehicle for a user just registered in identity-service.")
    @Parameter(name = InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, in = ParameterIn.HEADER, required = true,
            description = "Shared internal API key (INTERNAL_API_KEY)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver profile created",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or malformed body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid internal API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Profile already exists for the user, or license plate already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<DriverProfileResponse> createDriver(@Valid @RequestBody CreateDriverRequest request) {
        DriverProfileResponse response = driverService.createDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Delete a driver profile by user id (compensation)",
            description = "Removes an unused (OFFLINE, not reserved) profile and its vehicle. Idempotent: 204 even when no profile exists.")
    @Parameter(name = InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, in = ParameterIn.HEADER, required = true,
            description = "Shared internal API key (INTERNAL_API_KEY)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profile deleted, or there was nothing to delete"),
            @ApiResponse(responseCode = "400", description = "userId missing or not a UUID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid internal API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Profile is in use (not OFFLINE or reserved)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteDriverByUserId(
            @Parameter(description = "User id in identity-service", example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam UUID userId) {
        driverService.deleteDriverByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}
