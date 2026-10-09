package com.example.driverservice.mapper;

import com.example.driverservice.dto.response.AvailableDriverResponse;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.dto.response.DriverStatusResponse;
import com.example.driverservice.dto.response.LocationResponse;
import com.example.driverservice.dto.response.VehicleResponse;
import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class DriverMapper {

    public DriverProfileResponse toProfileResponse(Driver driver, Vehicle vehicle) {
        return DriverProfileResponse.builder()
                .driverId(driver.getDriverId())
                .fullName(driver.getFullName())
                .status(driver.getStatus())
                .licenseNumber(driver.getLicenseNumber())
                .vehicle(toVehicleResponse(vehicle))
                .createdAt(driver.getCreatedAt())
                .build();
    }

    public AvailableDriverResponse toAvailableResponse(Driver driver, Double distanceKm) {
        return AvailableDriverResponse.builder()
                .id(driver.getDriverId())
                .fullName(driver.getFullName())
                .status(driver.getStatus())
                .location(toLocationResponse(driver))
                .distanceKm(distanceKm)
                .version(driver.getVersion())
                .build();
    }

    public DriverStatusResponse toStatusResponse(Driver driver) {
        return DriverStatusResponse.builder()
                .id(driver.getDriverId())
                .status(driver.getStatus())
                .updatedAt(driver.getUpdatedAt())
                .build();
    }

    /** Null when the driver never reported a position. */
    public LocationResponse toLocationResponse(Driver driver) {
        if (driver.getCurrentLat() == null || driver.getCurrentLng() == null) {
            return null;
        }
        return LocationResponse.builder()
                .latitude(driver.getCurrentLat().doubleValue())
                .longitude(driver.getCurrentLng().doubleValue())
                .updatedAt(driver.getLastHeartbeatAt())
                .build();
    }

    public VehicleResponse toVehicleResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .vehicleId(vehicle.getVehicleId())
                .licensePlate(vehicle.getLicensePlate())
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .color(vehicle.getColor())
                .year(vehicle.getYear().intValue())
                .vehicleType(vehicle.getVehicleType())
                .build();
    }
}
