package com.example.driverservice.service;

import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.request.VehicleRequest;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.DriverStatus;
import com.example.driverservice.entity.Vehicle;
import com.example.driverservice.exception.BusinessException;
import com.example.driverservice.exception.ConflictException;
import com.example.driverservice.mapper.DriverMapper;
import com.example.driverservice.repository.DriverRepository;
import com.example.driverservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private static final String DRIVER_PROFILE_EXISTS = "DRIVER_PROFILE_EXISTS";
    private static final String LICENSE_PLATE_ALREADY_REGISTERED = "LICENSE_PLATE_ALREADY_REGISTERED";

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverMapper driverMapper;

    @Override
    @Transactional
    public DriverProfileResponse createDriver(CreateDriverRequest request) {
        VehicleRequest vehicleRequest = request.getVehicle();
        int maxYear = Year.now(ZoneOffset.UTC).getValue() + 1;
        if (vehicleRequest.getYear() > maxYear) {
            throw new BusinessException("Vehicle year must be " + maxYear + " or earlier",
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }

        String licensePlate = normalizeLicensePlate(vehicleRequest.getLicensePlate());
        if (driverRepository.existsByUserId(request.getUserId())) {
            throw new ConflictException("Driver profile already exists for this user", DRIVER_PROFILE_EXISTS);
        }
        if (vehicleRepository.existsByLicensePlate(licensePlate)) {
            throw new ConflictException("License plate already registered", LICENSE_PLATE_ALREADY_REGISTERED);
        }

        Driver driver = Driver.builder()
                .userId(request.getUserId())
                .licenseNumber(request.getLicenseNumber())
                .status(DriverStatus.OFFLINE)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .licensePlate(licensePlate)
                .make(vehicleRequest.getMake().trim())
                .model(vehicleRequest.getModel().trim())
                .color(vehicleRequest.getColor().trim())
                .year(vehicleRequest.getYear().shortValue())
                .vehicleType(vehicleRequest.getVehicleType())
                .build();

        try {
            // Flush now so a concurrent insert hitting a unique index surfaces here as 409, not at commit as 500
            driverRepository.saveAndFlush(driver);
            vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (detail.contains(Vehicle.UQ_LICENSE_PLATE)) {
                throw new ConflictException("License plate already registered", LICENSE_PLATE_ALREADY_REGISTERED);
            }
            if (detail.contains(Driver.UQ_USER_ID)) {
                throw new ConflictException("Driver profile already exists for this user", DRIVER_PROFILE_EXISTS);
            }
            throw ex;
        }

        log.info("Driver profile created driverId={}, userId={}", driver.getDriverId(), driver.getUserId());
        return driverMapper.toProfileResponse(driver, vehicle);
    }

    @Override
    @Transactional
    public void deleteDriverByUserId(UUID userId) {
        Optional<Driver> found = driverRepository.findByUserId(userId);
        if (found.isEmpty()) {
            // Idempotent: the compensation may run when the create never reached this service
            log.info("No driver profile to delete for userId={}", userId);
            return;
        }

        Driver driver = found.get();
        // Only a profile that was never used can be removed; this is a compensation, not a general delete
        if (driver.getStatus() != DriverStatus.OFFLINE || driver.getReservedForTrip() != null) {
            throw new ConflictException("Driver profile is in use and cannot be deleted", "DRIVER_PROFILE_IN_USE");
        }

        vehicleRepository.deleteAll(vehicleRepository.findByDriver(driver));
        driverRepository.delete(driver);
        log.info("Driver profile deleted driverId={}, userId={}", driver.getDriverId(), userId);
    }

    // 29a-123.45 and 29A-12345 are the same plate, so store one canonical form
    private String normalizeLicensePlate(String licensePlate) {
        return licensePlate.trim().toUpperCase(Locale.ROOT).replace(".", "");
    }
}
