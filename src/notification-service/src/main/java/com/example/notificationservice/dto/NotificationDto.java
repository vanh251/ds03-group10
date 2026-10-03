package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing notification record details")
public class NotificationDto {

    @Schema(description = "Notification ID", example = "550e8400-e29b-41d4-a716-446655440050")
    private String id;

    @Schema(description = "Recipient type (PASSENGER or DRIVER)", example = "PASSENGER")
    private RecipientType recipientType;

    @Schema(description = "Recipient user ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String recipientId;

    @Schema(description = "Associated trip ID", example = "550e8400-e29b-41d4-a716-446655440001")
    private String tripId;

    @Schema(description = "Notification title", example = "Driver Arrived")
    private String title;

    @Schema(description = "Notification message body", example = "Your driver has arrived at the pickup location.")
    private String message;

    @Schema(description = "Underlying trigger event type", example = "TRIP_ACCEPTED")
    private String eventType;

    @Schema(description = "Correlation ID for request tracing", example = "c8f331f4-0012-4299-9b57-c37f0001")
    private String correlationId;

    @Schema(description = "Timestamp when notification was created", example = "2026-10-03T10:00:00Z")
    private Instant timestamp;
}

