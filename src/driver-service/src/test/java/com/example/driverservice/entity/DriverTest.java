package com.example.driverservice.entity;

import org.hibernate.annotations.DynamicUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverTest {

    @Test
    @DisplayName("Driver updates only changed columns, so a status change never rewrites a newer position report")
    void driver_isDynamicUpdate() {
        // Without it Hibernate writes every column, and the version-neutral PATCH /location could be overwritten
        assertTrue(Driver.class.isAnnotationPresent(DynamicUpdate.class));
    }
}
