package com.example.driverservice.controller;

import com.example.driverservice.dto.request.UpdateDriverStatusRequest;
import com.example.driverservice.dto.request.UpdateLocationRequest;
import com.example.driverservice.dto.response.AvailableDriverResponse;
import com.example.driverservice.dto.response.DriverDetailResponse;
import com.example.driverservice.dto.response.DriverLocationResponse;
import com.example.driverservice.dto.response.DriverStatusResponse;
import com.example.driverservice.dto.response.ErrorResponse;
import com.example.driverservice.dto.response.NearbyDriverResponse;
import com.example.driverservice.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Drivers", description = "Driver availability, location and profile APIs")
public class DriverController {

    private final DriverService driverService;

    @Operation(summary = "List AVAILABLE drivers near a point",
            description = "The nearest AVAILABLE drivers within radiusKm of the point, nearest first, at most limit. "
                    + "Each item carries the version used for the CAS reservation. "
                    + "Called by matching-service without a token, so the point and the caps keep it from listing the whole fleet.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Available drivers",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AvailableDriverResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Missing or out-of-range parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/available")
    public ResponseEntity<List<AvailableDriverResponse>> getAvailableDrivers(
            @Parameter(description = "Reference point latitude", example = "21.0285")
            @RequestParam
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90") double latitude,
            @Parameter(description = "Reference point longitude", example = "105.8542")
            @RequestParam
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180") double longitude,
            @Parameter(description = "Search radius in km (max 50)", example = "5.0")
            @RequestParam(defaultValue = "5.0")
            @Positive(message = "radiusKm must be greater than 0")
            @DecimalMax(value = "50.0", message = "radiusKm must be at most 50") double radiusKm,
            @Parameter(description = "Maximum number of drivers (1-50)", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be between 1 and 50")
            @Max(value = 50, message = "limit must be between 1 and 50") int limit) {
        return ResponseEntity.ok(driverService.findAvailableDrivers(latitude, longitude, radiusKm, limit));
    }

    @Operation(summary = "Find AVAILABLE drivers near a pickup point",
            description = "The nearest AVAILABLE drivers within radiusKm, nearest first, at most limit. Requires a PASSENGER token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nearby drivers",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NearbyDriverResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Missing or out-of-range parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a PASSENGER token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyDriverResponse>> getNearbyDrivers(
            @Parameter(description = "Pickup latitude", example = "21.0285")
            @RequestParam
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90") double latitude,
            @Parameter(description = "Pickup longitude", example = "105.8542")
            @RequestParam
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180") double longitude,
            @Parameter(description = "Search radius in km (max 50)", example = "5.0")
            @RequestParam(defaultValue = "5.0")
            @Positive(message = "radiusKm must be greater than 0")
            @DecimalMax(value = "50.0", message = "radiusKm must be at most 50") double radiusKm,
            @Parameter(description = "Maximum number of drivers (1-50)", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "limit must be between 1 and 50")
            @Max(value = 50, message = "limit must be between 1 and 50") int limit) {
        return ResponseEntity.ok(driverService.findNearbyDrivers(latitude, longitude, radiusKm, limit));
    }

    @Operation(summary = "Update own availability status",
            description = "A driver switches between OFFLINE and AVAILABLE. RESERVED and BUSY are set by the system only. "
                    + "Requires a DRIVER token whose user id equals {id}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated (or already the requested one)",
                    content = @Content(schema = @Schema(implementation = DriverStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or unknown status value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a DRIVER token, or not the caller's own profile",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The driver was changed concurrently (e.g. just reserved); retry",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Transition not allowed (to/from RESERVED or BUSY)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<DriverStatusResponse> updateStatus(
            @Parameter(description = "Driver id (= the driver's user id)") @PathVariable UUID id,
            @Valid @RequestBody UpdateDriverStatusRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID callerId) {
        return ResponseEntity.ok(driverService.updateStatus(id, request.getStatus(), callerId));
    }

    @Operation(summary = "Report own position (simulated GPS)",
            description = "Stores the driver's current position and heartbeat. Does not change the driver's version, "
                    + "so position reports never make a reservation fail. Requires a DRIVER token whose user id equals {id}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Position stored",
                    content = @Content(schema = @Schema(implementation = DriverLocationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or out-of-range coordinates",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a DRIVER token, or not the caller's own profile",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/location")
    public ResponseEntity<DriverLocationResponse> updateLocation(
            @Parameter(description = "Driver id (= the driver's user id)") @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID callerId) {
        return ResponseEntity.ok(driverService.updateLocation(id, request.getLatitude(), request.getLongitude(), callerId));
    }

    @Operation(summary = "Get driver details",
            description = "Profile, active vehicle and last known position. Any authenticated user; "
                    + "the position of an OFFLINE driver is shown only to that driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver found",
                    content = @Content(schema = @Schema(implementation = DriverDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Id is not a UUID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<DriverDetailResponse> getDriver(
            @Parameter(description = "Driver id (= the driver's user id)") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID callerId) {
        return ResponseEntity.ok(driverService.getDriver(id, callerId));
    }
}
