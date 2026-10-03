package com.example.notificationservice.controller;

import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.dto.SendNotificationRequest;
import com.example.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getRecentNotifications() {
        return ResponseEntity.ok(notificationService.getRecentNotifications());
    }

    @GetMapping("/passenger/{passengerId}")
    public ResponseEntity<List<NotificationDto>> getPassengerNotifications(@PathVariable String passengerId) {
        return ResponseEntity.ok(notificationService.getNotificationsForPassenger(passengerId));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<List<NotificationDto>> getDriverNotifications(@PathVariable String driverId) {
        return ResponseEntity.ok(notificationService.getNotificationsForDriver(driverId));
    }

    @GetMapping("/trip/{tripId}")
    public ResponseEntity<List<NotificationDto>> getTripNotifications(@PathVariable String tripId) {
        return ResponseEntity.ok(notificationService.getNotificationsForTrip(tripId));
    }

    @PostMapping("/send")
    public ResponseEntity<NotificationDto> sendNotification(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationDto notification = notificationService.sendManualNotification(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }
}
