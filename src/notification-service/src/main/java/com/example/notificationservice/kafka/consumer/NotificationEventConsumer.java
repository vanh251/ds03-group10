package com.example.notificationservice.kafka.consumer;

import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"trip-events", "matching-events", "payment-events"},
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void consumeDomainEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("eventType").asText();
            String correlationId = root.path("correlationId").asText();
            JsonNode payload = root.path("payload");

            log.info("Notification Service received event: type={}, correlationId={}", eventType, correlationId);

            switch (eventType) {
                case "RideRequested", "trip.requested" -> handleRideRequested(payload, correlationId);
                case "DriverReserved", "driver.reserved" -> handleDriverReserved(payload, correlationId);
                case "DriverAssigned", "driver.assigned" -> handleDriverAssigned(payload, correlationId);
                case "TripStarted", "trip.started" -> handleTripStarted(payload, correlationId);
                case "TripCompleted", "trip.completed" -> handleTripCompleted(payload, correlationId);
                case "PaymentSucceeded", "payment.success" -> handlePaymentSucceeded(payload, correlationId);
                case "PaymentFailed", "payment.failed" -> handlePaymentFailed(payload, correlationId);
                case "TripCancelled", "trip.cancelled" -> handleTripCancelled(payload, correlationId);
                default -> log.debug("Unhandled event type in Notification Service: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process event in Notification Service: {}", message, e);
        }
    }

    private void handleRideRequested(JsonNode payload, String correlationId) {
        String passengerId = payload.path("passengerId").asText();
        String tripId = payload.path("tripId").asText();
        notificationService.sendPassengerNotification(passengerId, tripId,
                "Ride Requested", "Searching for a driver nearby...", "RideRequested", correlationId);
    }

    private void handleDriverReserved(JsonNode payload, String correlationId) {
        String tripId = payload.path("tripId").asText();
        String driverName = payload.path("driverName").asText("Your driver");
        String passengerId = payload.path("passengerId").asText("");
        notificationService.sendPassengerNotification(passengerId, tripId,
                "Driver Reserved", "Driver " + driverName + " is heading to your pickup location.", "DriverReserved", correlationId);
    }

    private void handleDriverAssigned(JsonNode payload, String correlationId) {
        String driverId = payload.path("driverId").asText();
        String tripId = payload.path("tripId").asText();
        notificationService.sendDriverNotification(driverId, tripId,
                "New Trip Assigned", "You have been assigned trip " + tripId, "DriverAssigned", correlationId);
    }

    private void handleTripStarted(JsonNode payload, String correlationId) {
        String passengerId = payload.path("passengerId").asText();
        String tripId = payload.path("tripId").asText();
        notificationService.sendPassengerNotification(passengerId, tripId,
                "Trip Started", "Your ride has started. Enjoy your journey!", "TripStarted", correlationId);
    }

    private void handleTripCompleted(JsonNode payload, String correlationId) {
        String passengerId = payload.path("passengerId").asText();
        String tripId = payload.path("tripId").asText();
        double fare = payload.path("finalFare").asDouble();
        String currency = payload.path("currency").asText("VND");
        notificationService.sendPassengerNotification(passengerId, tripId,
                "Trip Completed", "Trip completed. Final fare: " + fare + " " + currency, "TripCompleted", correlationId);
    }

    private void handlePaymentSucceeded(JsonNode payload, String correlationId) {
        String passengerId = payload.path("passengerId").asText();
        String tripId = payload.path("tripId").asText();
        double amount = payload.path("amount").asDouble();
        String currency = payload.path("currency").asText("VND");

        notificationService.sendPassengerNotification(passengerId, tripId,
                "Payment Succeeded", "Payment of " + amount + " " + currency + " was successful. Thank you for riding!", "PaymentSucceeded", correlationId);

        String driverId = payload.path("driverId").asText(null);
        if (driverId != null && !driverId.isBlank()) {
            notificationService.sendDriverNotification(driverId, tripId,
                    "Payment Received", "Payment of " + amount + " " + currency + " received for trip " + tripId, "PaymentSucceeded", correlationId);
        }
    }

    private void handlePaymentFailed(JsonNode payload, String correlationId) {
        String passengerId = payload.path("passengerId").asText();
        String tripId = payload.path("tripId").asText();
        notificationService.sendPassengerNotification(passengerId, tripId,
                "Payment Failed", "Payment failed for trip " + tripId + ". Please retry or update payment method.", "PaymentFailed", correlationId);
    }

    private void handleTripCancelled(JsonNode payload, String correlationId) {
        String passengerId = payload.path("passengerId").asText();
        String driverId = payload.path("driverId").asText(null);
        String tripId = payload.path("tripId").asText();
        String reason = payload.path("reason").asText("Trip was cancelled");

        notificationService.sendPassengerNotification(passengerId, tripId,
                "Trip Cancelled", "Your trip was cancelled: " + reason, "TripCancelled", correlationId);

        if (driverId != null && !driverId.isBlank()) {
            notificationService.sendDriverNotification(driverId, tripId,
                    "Trip Cancelled", "Trip " + tripId + " was cancelled: " + reason, "TripCancelled", correlationId);
        }
    }
}
