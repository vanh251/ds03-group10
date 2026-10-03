package com.example.pricingservice;

import com.example.pricingservice.dto.request.FinalFareRequest;
import com.example.pricingservice.dto.request.LocationDto;
import com.example.pricingservice.dto.request.QuoteRequest;
import com.example.pricingservice.dto.response.FinalFareResponse;
import com.example.pricingservice.dto.response.QuoteResponse;
import com.example.pricingservice.service.PricingService;
import com.example.pricingservice.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingServiceImpl();
    }

    @Test
    @DisplayName("calculateQuote with explicit distance and duration returns exact calculated fare")
    void calculateQuote_explicitDistanceAndDuration_returnsCorrectFare() {
        QuoteRequest request = QuoteRequest.builder()
                .pickup(new LocationDto(21.0285, 105.8542, "Hoan Kiem, Hanoi"))
                .destination(new LocationDto(21.0188, 105.8520, "Dong Da, Hanoi"))
                .distanceKm(2.5)
                .durationMin(12)
                .build();

        QuoteResponse response = pricingService.calculateQuote(request);

        assertNotNull(response);
        assertEquals(2.5, response.getEstimatedDistanceKm());
        assertEquals(12, response.getEstimatedDurationMin());
        assertEquals(15000.0, response.getBaseFare());
        assertEquals(12500.0, response.getDistanceFare()); // 2.5 * 5000
        assertEquals(6000.0, response.getTimeFare());      // 12 * 500
        assertEquals(33500.0, response.getTotalFare());     // 15000 + 12500 + 6000
        assertEquals("VND", response.getCurrency());
    }

    @Test
    @DisplayName("calculateQuote with coordinates only estimates distance and duration")
    void calculateQuote_coordinatesOnly_estimatesDistanceAndFare() {
        QuoteRequest request = QuoteRequest.builder()
                .pickup(new LocationDto(21.0285, 105.8542, "Hoan Kiem, Hanoi"))
                .destination(new LocationDto(21.0188, 105.8520, "Dong Da, Hanoi"))
                .build();

        QuoteResponse response = pricingService.calculateQuote(request);

        assertNotNull(response);
        assertTrue(response.getEstimatedDistanceKm() > 0);
        assertTrue(response.getEstimatedDurationMin() > 0);
        assertEquals(15000.0, response.getBaseFare());
        assertEquals("VND", response.getCurrency());
        assertEquals(response.getBaseFare() + response.getDistanceFare() + response.getTimeFare(), response.getTotalFare());
    }

    @Test
    @DisplayName("calculateFinalFare with valid input returns expected final fare")
    void calculateFinalFare_validRequest_returnsAccurateFinalFare() {
        FinalFareRequest request = FinalFareRequest.builder()
                .tripId("trip-001")
                .actualDistanceKm(2.8)
                .actualDurationMin(15)
                .build();

        FinalFareResponse response = pricingService.calculateFinalFare(request);

        assertNotNull(response);
        assertEquals("trip-001", response.getTripId());
        assertEquals(15000.0, response.getBaseFare());
        assertEquals(14000.0, response.getDistanceFare()); // 2.8 * 5000
        assertEquals(7500.0, response.getTimeFare());      // 15 * 500
        assertEquals(36500.0, response.getTotalFare());     // 15000 + 14000 + 7500
        assertEquals("VND", response.getCurrency());
    }
}
