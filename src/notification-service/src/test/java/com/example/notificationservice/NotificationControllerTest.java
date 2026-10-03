package com.example.notificationservice;

import com.example.notificationservice.controller.NotificationController;
import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.dto.RecipientType;
import com.example.notificationservice.dto.SendNotificationRequest;
import com.example.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
    }

    @Test
    @DisplayName("GET /api/notifications returns 200 and list of notifications")
    void getRecentNotifications_returns200AndList() throws Exception {
        NotificationDto notif = NotificationDto.builder()
                .id("notif-1")
                .recipientType(RecipientType.PASSENGER)
                .recipientId("p1")
                .tripId("t1")
                .title("Trip Update")
                .message("Driver arrived")
                .timestamp(Instant.now())
                .build();

        when(notificationService.getRecentNotifications()).thenReturn(List.of(notif));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("notif-1"))
                .andExpect(jsonPath("$[0].title").value("Trip Update"));
    }

    @Test
    @DisplayName("POST /api/notifications/send with valid body returns 201 Created")
    void sendNotification_validBody_returns201Created() throws Exception {
        String jsonRequest = """
                {
                  "recipientType": "PASSENGER",
                  "recipientId": "p-123",
                  "tripId": "trip-123",
                  "title": "Welcome",
                  "message": "Welcome aboard!"
                }
                """;

        NotificationDto notif = NotificationDto.builder()
                .id("notif-new")
                .recipientType(RecipientType.PASSENGER)
                .recipientId("p-123")
                .tripId("trip-123")
                .title("Welcome")
                .message("Welcome aboard!")
                .timestamp(Instant.now())
                .build();

        when(notificationService.sendManualNotification(any(SendNotificationRequest.class), any()))
                .thenReturn(notif);

        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("notif-new"))
                .andExpect(jsonPath("$.recipientId").value("p-123"));
    }
}
