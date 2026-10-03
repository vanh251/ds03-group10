package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String id;
    private RecipientType recipientType;
    private String recipientId;
    private String tripId;
    private String title;
    private String message;
    private String eventType;
    private String correlationId;
    private Instant timestamp;
}
