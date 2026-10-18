package com.example.notificationservice.controller;

import com.example.notificationservice.dto.ErrorResponse;
import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.dto.SendNotificationRequest;
import com.example.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management and dispatching APIs")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Get recent notifications", description = "Retrieves the list of all recent dispatched notifications across the platform")
    @ApiResponse(responseCode = "200", description = "List of recent notifications")
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getRecentNotifications() {
        return ResponseEntity.ok(notificationService.getRecentNotifications());
    }

    @Operation(summary = "Get passenger notifications", description = "Retrieves notifications addressed to a specific passenger")
    @ApiResponse(responseCode = "200", description = "List of passenger notifications")
    @GetMapping("/passenger/{passengerId}")
    public ResponseEntity<List<NotificationDto>> getPassengerNotifications(
            @Parameter(description = "ID of passenger", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String passengerId) {
        return ResponseEntity.ok(notificationService.getNotificationsForPassenger(passengerId));
    }

    @Operation(summary = "Get driver notifications", description = "Retrieves notifications addressed to a specific driver")
    @ApiResponse(responseCode = "200", description = "List of driver notifications")
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<List<NotificationDto>> getDriverNotifications(
            @Parameter(description = "ID of driver", example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable String driverId) {
        return ResponseEntity.ok(notificationService.getNotificationsForDriver(driverId));
    }

    @Operation(summary = "Get trip notifications", description = "Retrieves notifications associated with a specific trip")
    @ApiResponse(responseCode = "200", description = "List of trip notifications")
    @GetMapping("/trip/{tripId}")
    public ResponseEntity<List<NotificationDto>> getTripNotifications(
            @Parameter(description = "ID of trip", example = "550e8400-e29b-41d4-a716-446655440002")
            @PathVariable String tripId) {
        return ResponseEntity.ok(notificationService.getNotificationsForTrip(tripId));
    }

    @Operation(summary = "Send notification", description = "Manually dispatches a push notification to a driver or passenger")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification dispatched successfully",
                    content = @Content(schema = @Schema(implementation = NotificationDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid notification payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/send")
    public ResponseEntity<NotificationDto> sendNotification(
            @Parameter(description = "Optional correlation ID for distributed tracing")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationDto notification = notificationService.sendManualNotification(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }
}

