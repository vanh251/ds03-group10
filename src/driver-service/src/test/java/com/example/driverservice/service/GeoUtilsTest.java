package com.example.driverservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    // Hoan Kiem Lake, Hanoi
    private static final double LAT = 21.0285;
    private static final double LNG = 105.8542;

    @Test
    @DisplayName("haversineKm matches known distances around Hanoi")
    void haversineKm_knownPoints_returnsExpectedDistance() {
        assertEquals(0.0, GeoUtils.haversineKm(LAT, LNG, LAT, LNG), 1e-9);
        assertEquals(0.49, GeoUtils.haversineKm(LAT, LNG, 21.0250, 105.8570), 0.01);   // Trang Tien
        assertEquals(1.08, GeoUtils.haversineKm(LAT, LNG, 21.0190, 105.8520), 0.01);   // Hang Bai
        assertEquals(6.68, GeoUtils.haversineKm(LAT, LNG, 21.0368, 105.7905), 0.01);   // Cau Giay
    }

    @Test
    @DisplayName("haversineKm is symmetric")
    void haversineKm_swappedPoints_sameDistance() {
        assertEquals(GeoUtils.haversineKm(LAT, LNG, 21.0368, 105.7905),
                GeoUtils.haversineKm(21.0368, 105.7905, LAT, LNG), 1e-9);
    }

    @Test
    @DisplayName("boundingBox contains every point at exactly radiusKm north, south, east and west")
    void boundingBox_radius_containsCardinalPointsAtRadius() {
        double radiusKm = 5.0;
        GeoUtils.BoundingBox box = GeoUtils.boundingBox(LAT, LNG, radiusKm);

        assertEquals(LAT + radiusKm / GeoUtils.KM_PER_DEGREE, box.maxLat(), 1e-9);
        assertEquals(LAT - radiusKm / GeoUtils.KM_PER_DEGREE, box.minLat(), 1e-9);
        // Longitude degrees are shorter away from the equator, so the box is wider in degrees than in latitude
        assertTrue(box.maxLng() - LNG > box.maxLat() - LAT);
        assertEquals(radiusKm, GeoUtils.haversineKm(LAT, LNG, LAT, box.maxLng()), 0.05);
        assertEquals(radiusKm, GeoUtils.haversineKm(LAT, LNG, box.maxLat(), LNG), 0.05);
    }
}
