package com.example.driverservice.mapper;

import com.example.driverservice.dto.response.DriverProfileResponse;
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
