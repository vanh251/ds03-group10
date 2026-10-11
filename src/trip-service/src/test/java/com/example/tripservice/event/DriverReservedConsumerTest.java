package com.example.tripservice.event;

import com.example.tripservice.service.TripService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverReservedConsumerTest {

    @Mock
    private TripService tripService;

    @Mock
    private com.example.tripservice.repository.ProcessedEventRepository processedEventRepository;

    @Spy
    private tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    @InjectMocks
    private DriverReservedConsumer consumer;

    @Test
    @DisplayName("handleDriverReserved: should extract tripId and driverId and call tripService.assignDriver")
    void handleDriverReserved_validPayload_assignsDriver() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        String payload = """
                {
                  "eventId": "50e4a77d-f434-40da-9e45-f09b52a13cc7",
                  "eventType": "DriverReserved",
                  "payload": {
                    "tripId": "%s",
                    "driverId": "%s"
                  }
                }
                """.formatted(tripId, driverId);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("matching-events", 0, 0, tripId.toString(), payload);

        consumer.handleDriverReserved(record);

        verify(tripService, times(1)).assignDriver(tripId, driverId);
    }

    @Test
    @DisplayName("handleDriverReserved: ignores events that are not DriverReserved")
    void handleDriverReserved_otherEvent_ignored() {
        String payload = """
                {
                  "eventId": "50e4a77d-f434-40da-9e45-f09b52a13cc7",
                  "eventType": "OtherEvent"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("matching-events", 0, 0, "key", payload);

        consumer.handleDriverReserved(record);

        verify(tripService, never()).assignDriver(any(), any());
    }
}
