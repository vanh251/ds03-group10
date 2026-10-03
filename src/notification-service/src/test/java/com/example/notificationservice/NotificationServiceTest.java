package com.example.notificationservice;

import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.dto.RecipientType;
import com.example.notificationservice.dto.SendNotificationRequest;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl();
    }

    @Test
    @DisplayName("sendPassengerNotification adds notification to buffer with PASSENGER type")
    void sendPassengerNotification_validInput_addsToBuffer() {
        notificationService.sendPassengerNotification(
                "passenger-001",
                "trip-001",
                "Ride Requested",
                "Searching for a driver...",
                "RideRequested",
                "corr-001"
        );

        List<NotificationDto> recent = notificationService.getRecentNotifications();
        assertEquals(1, recent.size());

        NotificationDto notif = recent.get(0);
        assertEquals(RecipientType.PASSENGER, notif.getRecipientType());
        assertEquals("passenger-001", notif.getRecipientId());
        assertEquals("trip-001", notif.getTripId());
        assertEquals("Ride Requested", notif.getTitle());
        assertEquals("corr-001", notif.getCorrelationId());
    }

    @Test
    @DisplayName("sendDriverNotification adds notification with DRIVER type")
    void sendDriverNotification_validInput_addsToBuffer() {
        notificationService.sendDriverNotification(
                "driver-001",
                "trip-001",
                "New Trip",
                "You have a new trip assigned",
                "DriverAssigned",
                "corr-001"
        );

        List<NotificationDto> driverNotifs = notificationService.getNotificationsForDriver("driver-001");
        assertEquals(1, driverNotifs.size());
        assertEquals(RecipientType.DRIVER, driverNotifs.get(0).getRecipientType());
        assertEquals("driver-001", driverNotifs.get(0).getRecipientId());
    }

    @Test
    @DisplayName("getNotificationsForPassenger filters out other recipients")
    void getNotificationsForPassenger_filtersCorrectly() {
        notificationService.sendPassengerNotification("p1", "t1", "Title 1", "Msg 1", "Evt1", "c1");
        notificationService.sendPassengerNotification("p2", "t2", "Title 2", "Msg 2", "Evt2", "c2");
        notificationService.sendDriverNotification("d1", "t1", "Title 3", "Msg 3", "Evt3", "c3");

        List<NotificationDto> p1Notifs = notificationService.getNotificationsForPassenger("p1");
        assertEquals(1, p1Notifs.size());
        assertEquals("p1", p1Notifs.get(0).getRecipientId());
    }

    @Test
    @DisplayName("getNotificationsForTrip returns all notifications matching tripId")
    void getNotificationsForTrip_returnsTripNotifications() {
        notificationService.sendPassengerNotification("p1", "t1", "Title 1", "Msg 1", "Evt1", "c1");
        notificationService.sendDriverNotification("d1", "t1", "Title 2", "Msg 2", "Evt2", "c1");
        notificationService.sendPassengerNotification("p2", "t2", "Title 3", "Msg 3", "Evt3", "c2");

        List<NotificationDto> t1Notifs = notificationService.getNotificationsForTrip("t1");
        assertEquals(2, t1Notifs.size());
    }

    @Test
    @DisplayName("sendManualNotification dispatches notification correctly")
    void sendManualNotification_validRequest_dispatches() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .recipientType(RecipientType.PASSENGER)
                .recipientId("p-test")
                .tripId("t-test")
                .title("Promo")
                .message("You got 20% discount!")
                .build();

        NotificationDto dto = notificationService.sendManualNotification(request, "corr-manual");

        assertNotNull(dto);
        assertEquals("p-test", dto.getRecipientId());
        assertEquals("Promo", dto.getTitle());
        assertEquals("corr-manual", dto.getCorrelationId());
    }
}
