package com.example.notificationservice.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers the passenger and driver of recent trips from the events already seen.
 * Some event payloads do not name every recipient (docs/events.md: DriverReserved has no passengerId,
 * PaymentSucceeded has no driverId), so the notification for them is addressed from this memory.
 * In memory only, like the notification buffer: the service stays stateless (CODING_CONVENTION Rule 2.1).
 */
@Component
public class TripParticipantRegistry {

    private static final int MAX_TRIPS = 1000;

    private final Map<String, Participants> trips = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Participants> eldest) {
            return size() > MAX_TRIPS;
        }
    };

    public synchronized void remember(String tripId, String passengerId, String driverId) {
        if (isBlank(tripId)) {
            return;
        }
        Participants current = trips.getOrDefault(tripId, new Participants(null, null));
        trips.put(tripId, new Participants(
                isBlank(passengerId) ? current.passengerId() : passengerId,
                isBlank(driverId) ? current.driverId() : driverId));
    }

    public synchronized Optional<String> passengerOf(String tripId) {
        return Optional.ofNullable(trips.get(tripId)).map(Participants::passengerId);
    }

    public synchronized Optional<String> driverOf(String tripId) {
        return Optional.ofNullable(trips.get(tripId)).map(Participants::driverId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Participants(String passengerId, String driverId) {
    }
}
