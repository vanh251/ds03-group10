package com.example.driverservice.entity;

/**
 * Driver availability state machine (CODING_CONVENTION 12.2). Full transition table: docs/api.md,
 * PATCH /api/drivers/{id}/status.
 */
public enum DriverStatus {
    OFFLINE,
    AVAILABLE,
    RESERVED,
    BUSY
}
