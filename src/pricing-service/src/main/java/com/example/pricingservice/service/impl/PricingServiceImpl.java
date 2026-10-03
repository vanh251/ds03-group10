package com.example.pricingservice.service.impl;

import com.example.pricingservice.dto.request.FinalFareRequest;
import com.example.pricingservice.dto.request.LocationDto;
import com.example.pricingservice.dto.request.QuoteRequest;
import com.example.pricingservice.dto.response.FinalFareResponse;
import com.example.pricingservice.dto.response.QuoteResponse;
import com.example.pricingservice.service.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class PricingServiceImpl implements PricingService {

    public static final double BASE_FARE = 15000.0;
    public static final double DISTANCE_RATE_PER_KM = 5000.0;
    public static final double TIME_RATE_PER_MIN = 500.0;
    public static final String CURRENCY = "VND";
    private static final double AVERAGE_CITY_SPEED_KMH = 20.0;

    @Override
    public QuoteResponse calculateQuote(QuoteRequest request) {
        log.info("Calculating estimated fare quote for pickup: ({}, {}) -> destination: ({}, {})",
                request.getPickup().getLatitude(), request.getPickup().getLongitude(),
                request.getDestination().getLatitude(), request.getDestination().getLongitude());

        double distanceKm;
        if (request.getDistanceKm() != null && request.getDistanceKm() > 0) {
            distanceKm = round(request.getDistanceKm(), 1);
        } else {
            distanceKm = calculateHaversineDistance(request.getPickup(), request.getDestination());
        }

        int durationMin;
        if (request.getDurationMin() != null && request.getDurationMin() > 0) {
            durationMin = request.getDurationMin();
        } else {
            durationMin = estimateDurationMinutes(distanceKm);
        }

        double distanceFare = round(distanceKm * DISTANCE_RATE_PER_KM, 0);
        double timeFare = round(durationMin * TIME_RATE_PER_MIN, 0);
        double totalFare = BASE_FARE + distanceFare + timeFare;

        log.info("Quote result: distance={} km, duration={} min, totalFare={} {}",
                distanceKm, durationMin, totalFare, CURRENCY);

        return QuoteResponse.builder()
                .estimatedDistanceKm(distanceKm)
                .estimatedDurationMin(durationMin)
                .baseFare(BASE_FARE)
                .distanceFare(distanceFare)
                .timeFare(timeFare)
                .totalFare(totalFare)
                .currency(CURRENCY)
                .build();
    }

    @Override
    public FinalFareResponse calculateFinalFare(FinalFareRequest request) {
        log.info("Calculating final fare for trip {}: actualDistance={} km, actualDuration={} min",
                request.getTripId(), request.getActualDistanceKm(), request.getActualDurationMin());

        double distanceKm = round(request.getActualDistanceKm(), 1);
        int durationMin = request.getActualDurationMin();

        double distanceFare = round(distanceKm * DISTANCE_RATE_PER_KM, 0);
        double timeFare = round(durationMin * TIME_RATE_PER_MIN, 0);
        double totalFare = BASE_FARE + distanceFare + timeFare;

        log.info("Final fare result for trip {}: totalFare={} {}", request.getTripId(), totalFare, CURRENCY);

        return FinalFareResponse.builder()
                .tripId(request.getTripId())
                .baseFare(BASE_FARE)
                .distanceFare(distanceFare)
                .timeFare(timeFare)
                .totalFare(totalFare)
                .currency(CURRENCY)
                .build();
    }

    private double calculateHaversineDistance(LocationDto from, LocationDto to) {
        final int earthRadiusKm = 6371;
        double lat1 = Math.toRadians(from.getLatitude());
        double lon1 = Math.toRadians(from.getLongitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double lon2 = Math.toRadians(to.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadiusKm * c;

        // Apply a routing detour factor (1.25x) to account for street network versus straight line
        double estimatedRoadDistance = distance * 1.25;

        // Minimum distance of 0.5 km
        return Math.max(0.5, round(estimatedRoadDistance, 1));
    }

    private int estimateDurationMinutes(double distanceKm) {
        double hours = distanceKm / AVERAGE_CITY_SPEED_KMH;
        int minutes = (int) Math.round(hours * 60);
        return Math.max(1, minutes);
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
