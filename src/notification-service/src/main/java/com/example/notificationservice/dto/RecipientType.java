package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Target recipient classification")
public enum RecipientType {
    PASSENGER,
    DRIVER
}

