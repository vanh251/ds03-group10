package com.example.driverservice.service;

/**
 * Distance helpers for the simulated driver positions (no PostGIS / Redis GEO, see SRS 29.3).
 */
final class GeoUtils {

    static final double EARTH_RADIUS_KM = 6371.0;
    // Length of one degree of latitude; one degree of longitude is this times cos(latitude)
    static final double KM_PER_DEGREE = 111.32;

    private GeoUtils() {
    }

    /** Great-circle distance in km (Haversine formula). */
    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /**
     * Square around the point that contains the whole radius, used to pre-filter rows in SQL before the exact
     * Haversine check. Its corners are farther than radiusKm, so results must still be filtered by distance.
     */
    static BoundingBox boundingBox(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE;
        double lngDelta = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }

    record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }
}
