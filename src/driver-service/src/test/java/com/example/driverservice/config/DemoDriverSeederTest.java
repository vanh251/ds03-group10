package com.example.driverservice.config;

import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.request.VehicleRequest;
import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.DriverStatus;
import com.example.driverservice.entity.Vehicle;
import com.example.driverservice.repository.DriverRepository;
import com.example.driverservice.repository.VehicleRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoDriverSeederTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private DemoDriverSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoDriverSeeder(driverRepository, vehicleRepository, TransactionOperations.withoutTransaction());
    }

    @Test
    @DisplayName("driver ids are the fixed ids of identity-service's demo DRIVER accounts")
    void driverIds_matchIdentityDemoAccounts() {
        assertEquals(UUID.fromString("d0000000-0000-4000-8000-000000000001"), DemoDriverSeeder.driverId(1));
        assertEquals(UUID.fromString("d0000000-0000-4000-8000-000000000010"), DemoDriverSeeder.driverId(10));
        assertEquals(10, DemoDriverSeeder.DEMO_DRIVERS.size());
        assertEquals(10, DemoDriverSeeder.DEMO_DRIVERS.stream().map(DemoDriverSeeder.DemoDriver::number).distinct().count());
        assertEquals(10, DemoDriverSeeder.DEMO_DRIVERS.stream().map(d -> d.vehicle().licensePlate()).distinct().count());
    }

    @Test
    @DisplayName("every demo profile would also pass the POST /internal/drivers validation")
    void demoDrivers_passApiValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (DemoDriverSeeder.DemoDriver demo : DemoDriverSeeder.DEMO_DRIVERS) {
                CreateDriverRequest request = CreateDriverRequest.builder()
                        .userId(DemoDriverSeeder.driverId(demo.number()))
                        .fullName(demo.fullName())
                        .licenseNumber(String.format("0123456780%02d", demo.number()))
                        .vehicle(VehicleRequest.builder()
                                .licensePlate(demo.vehicle().licensePlate())
                                .make(demo.vehicle().make())
                                .model(demo.vehicle().model())
                                .color(demo.vehicle().color())
                                .year(demo.vehicle().year())
                                .vehicleType(demo.vehicle().type())
                                .build())
                        .build();
                assertEquals(0, validator.validate(request).size(), () -> "invalid demo driver " + demo);
            }
        }
    }

    @Test
    @DisplayName("run on an empty database saves 10 profiles with their vehicles, statuses and positions")
    void run_emptyDatabase_savesAllProfiles() {
        when(driverRepository.existsById(any())).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(null);

        ArgumentCaptor<Driver> drivers = ArgumentCaptor.forClass(Driver.class);
        ArgumentCaptor<Vehicle> vehicles = ArgumentCaptor.forClass(Vehicle.class);
        verify(driverRepository, times(10)).saveAndFlush(drivers.capture());
        verify(vehicleRepository, times(10)).saveAndFlush(vehicles.capture());

        Driver first = drivers.getAllValues().get(0);
        assertEquals(DemoDriverSeeder.driverId(1), first.getDriverId());
        assertEquals("Tran Van Binh", first.getFullName());
        assertEquals(DriverStatus.AVAILABLE, first.getStatus());
        assertEquals("012345678001", first.getLicenseNumber());
        assertNotNull(first.getCurrentLat());
        assertNotNull(first.getLastHeartbeatAt());
        assertSame(first, vehicles.getAllValues().get(0).getDriver());
        assertEquals("30A-10001", vehicles.getAllValues().get(0).getLicensePlate());

        Driver last = drivers.getAllValues().get(9);
        assertEquals(DriverStatus.OFFLINE, last.getStatus());
        assertNull(last.getCurrentLat());
        assertNull(last.getLastHeartbeatAt());

        List<Driver> available = drivers.getAllValues().stream().filter(d -> d.getStatus() == DriverStatus.AVAILABLE).toList();
        assertEquals(8, available.size());
    }

    @Test
    @DisplayName("run skips profiles that already exist, so restarting the service creates no duplicates")
    void run_existingProfiles_onlyCreatesMissingOnes() {
        when(driverRepository.existsById(any())).thenReturn(true);
        when(driverRepository.existsById(DemoDriverSeeder.driverId(3))).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(null);

        verify(driverRepository, times(1)).saveAndFlush(any(Driver.class));
        verify(vehicleRepository, times(1)).saveAndFlush(any(Vehicle.class));
    }

    @Test
    @DisplayName("run continues with the next profile when another instance inserted one first")
    void run_concurrentInsert_continues() {
        when(driverRepository.existsById(any())).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"drivers_pkey\""))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> seeder.run(null));

        verify(driverRepository, times(10)).saveAndFlush(any(Driver.class));
        verify(vehicleRepository, times(9)).saveAndFlush(any(Vehicle.class));
    }
}
