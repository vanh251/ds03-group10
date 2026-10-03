package com.example.tripservice.event;

import com.example.tripservice.service.TripService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverReservedConsumerTest {

    @Mock
    private TripService tripService;

    @InjectMocks
    private DriverReservedConsumer consumer;

    @Test
    @DisplayName("handleDriverReserved: should extract tripId and driverId and call tripService.assignDriver")
    void handleDriverReserved_validPayload_assignsDriver() {
        UUID tripId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        String payload = """
                {
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
                  "eventType": "OtherEvent"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("matching-events", 0, 0, "key", payload);

        consumer.handleDriverReserved(record);

        verify(tripService, never()).assignDriver(any(), any());
    }
}
