package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to manually dispatch a push notification")
public class SendNotificationRequest {

    @Schema(description = "Type of recipient (PASSENGER or DRIVER)", example = "PASSENGER")
    @NotNull(message = "Recipient type is required")
    private RecipientType recipientType;

    @Schema(description = "Identifier of recipient user", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    @Schema(description = "Associated trip ID (optional)", example = "550e8400-e29b-41d4-a716-446655440001")
    private String tripId;

    @Schema(description = "Notification title", example = "Driver Arrived")
    @NotBlank(message = "Title is required")
    private String title;

    @Schema(description = "Detailed notification body", example = "Your driver has arrived at the pickup location.")
    @NotBlank(message = "Message is required")
    private String message;
}

