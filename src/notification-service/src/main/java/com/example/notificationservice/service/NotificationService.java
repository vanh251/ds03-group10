package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.dto.SendNotificationRequest;

import java.util.List;

public interface NotificationService {

    void sendPassengerNotification(String passengerId, String tripId, String title, String message, String eventType, String correlationId);

    void sendDriverNotification(String driverId, String tripId, String title, String message, String eventType, String correlationId);

    List<NotificationDto> getRecentNotifications();

    List<NotificationDto> getNotificationsForPassenger(String passengerId);

    List<NotificationDto> getNotificationsForDriver(String driverId);

    List<NotificationDto> getNotificationsForTrip(String tripId);

    NotificationDto sendManualNotification(SendNotificationRequest request, String correlationId);
}
