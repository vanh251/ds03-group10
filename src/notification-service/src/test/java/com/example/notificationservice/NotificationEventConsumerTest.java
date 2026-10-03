package com.example.notificationservice;

import com.example.notificationservice.kafka.consumer.NotificationEventConsumer;
import com.example.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(notificationService, new JsonMapper());
    }

    @Test
    @DisplayName("consumeDomainEvent handles RideRequested event")
    void consumeDomainEvent_rideRequested_sendsPassengerNotification() {
        String message = """
                {
                  "eventId": "evt-001",
                  "eventType": "RideRequested",
                  "aggregateId": "trip-001",
                  "correlationId": "corr-001",
                  "payload": {
                    "tripId": "trip-001",
                    "passengerId": "passenger-001"
                  }
                }
                """;

        consumer.consumeDomainEvent(message);

        verify(notificationService).sendPassengerNotification(
                eq("passenger-001"),
                eq("trip-001"),
                eq("Ride Requested"),
                anyString(),
                eq("RideRequested"),
                eq("corr-001")
        );
    }

    @Test
    @DisplayName("consumeDomainEvent handles DriverAssigned event")
    void consumeDomainEvent_driverAssigned_sendsDriverNotification() {
        String message = """
                {
                  "eventId": "evt-003",
                  "eventType": "DriverAssigned",
                  "aggregateId": "trip-001",
                  "correlationId": "corr-001",
                  "payload": {
                    "tripId": "trip-001",
                    "driverId": "driver-001",
                    "passengerId": "passenger-001"
                  }
                }
                """;

        consumer.consumeDomainEvent(message);

        verify(notificationService).sendDriverNotification(
                eq("driver-001"),
                eq("trip-001"),
                eq("New Trip Assigned"),
                anyString(),
                eq("DriverAssigned"),
                eq("corr-001")
        );
    }

    @Test
    @DisplayName("consumeDomainEvent handles PaymentSucceeded event")
    void consumeDomainEvent_paymentSucceeded_sendsPassengerAndDriverNotifications() {
        String message = """
                {
                  "eventId": "evt-006",
                  "eventType": "PaymentSucceeded",
                  "aggregateId": "pay-001",
                  "correlationId": "corr-001",
                  "payload": {
                    "paymentId": "pay-001",
                    "tripId": "trip-001",
                    "passengerId": "passenger-001",
                    "driverId": "driver-001",
                    "amount": 47500.0,
                    "currency": "VND"
                  }
                }
                """;

        consumer.consumeDomainEvent(message);

        verify(notificationService).sendPassengerNotification(
                eq("passenger-001"),
                eq("trip-001"),
                eq("Payment Succeeded"),
                anyString(),
                eq("PaymentSucceeded"),
                eq("corr-001")
        );

        verify(notificationService).sendDriverNotification(
                eq("driver-001"),
                eq("trip-001"),
                eq("Payment Received"),
                anyString(),
                eq("PaymentSucceeded"),
                eq("corr-001")
        );
    }
}
