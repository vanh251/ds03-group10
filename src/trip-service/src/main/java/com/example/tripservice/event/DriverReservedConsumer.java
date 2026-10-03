package com.example.tripservice.event;

import com.example.tripservice.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.kafka.annotation.KafkaListener")
public class DriverReservedConsumer {

    private final TripService tripService;

    private static final Pattern TRIP_ID_PATTERN = Pattern.compile("\"tripId\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");
    private static final Pattern DRIVER_ID_PATTERN = Pattern.compile("\"driverId\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");

    @KafkaListener(topics = "matching-events", groupId = "${spring.kafka.consumer.group-id:trip-service-group}")
    public void handleDriverReserved(ConsumerRecord<String, String> record) {
        log.info("Received matching event from Kafka: topic={}, partition={}, key={}",
                record.topic(), record.partition(), record.key());

        String message = record.value();
        if (message == null || !message.contains("DriverReserved")) {
            log.debug("Ignoring non-DriverReserved event: {}", message);
            return;
        }

        try {
            Matcher tripMatcher = TRIP_ID_PATTERN.matcher(message);
            Matcher driverMatcher = DRIVER_ID_PATTERN.matcher(message);

            if (tripMatcher.find() && driverMatcher.find()) {
                UUID tripId = UUID.fromString(tripMatcher.group(1));
                UUID driverId = UUID.fromString(driverMatcher.group(1));

                log.info("Processing DriverReserved event: assigning driverId={} to tripId={}", driverId, tripId);
                tripService.assignDriver(tripId, driverId);
            } else {
                log.warn("Could not extract tripId or driverId from DriverReserved event: {}", message);
            }
        } catch (Exception e) {
            log.error("Error processing DriverReserved event: {}", e.getMessage(), e);
        }
    }
}
