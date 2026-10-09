package com.example.driverservice.service;

import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.request.VehicleRequest;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.DriverStatus;
import com.example.driverservice.entity.Vehicle;
import com.example.driverservice.entity.VehicleType;
import com.example.driverservice.exception.BusinessException;
import com.example.driverservice.exception.ConflictException;
import com.example.driverservice.mapper.DriverMapper;
import com.example.driverservice.repository.DriverRepository;
import com.example.driverservice.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Spy
    private DriverMapper driverMapper = new DriverMapper();

    @InjectMocks
    private DriverServiceImpl driverService;

    private CreateDriverRequest request(String licensePlate, int year) {
        return CreateDriverRequest.builder()
                .userId(USER_ID)
                .licenseNumber("012345678901")
                .vehicle(VehicleRequest.builder()
                        .licensePlate(licensePlate)
                        .make(" Toyota ")
                        .model("Vios")
                        .color("White")
                        .year(year)
                        .vehicleType(VehicleType.SEDAN)
                        .build())
                .build();
    }

    /** Simulates the database assigning ids and timestamps on insert. */
    private void stubSavesAssignIds() {
        when(driverRepository.saveAndFlush(any(Driver.class))).thenAnswer(invocation -> {
            Driver driver = invocation.getArgument(0);
            driver.setDriverId(UUID.randomUUID());
            driver.setCreatedAt(Instant.now());
            return driver;
        });
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            vehicle.setVehicleId(UUID.randomUUID());
            return vehicle;
        });
    }

    private static DataIntegrityViolationException uniqueViolation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    @DisplayName("createDriver saves an OFFLINE driver and its vehicle with the plate normalized")
    void createDriver_validRequest_savesOfflineDriverAndVehicle() {
        when(driverRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate("29A-12345")).thenReturn(false);
        stubSavesAssignIds();

        DriverProfileResponse response = driverService.createDriver(request("29a-123.45", 2022));

        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).saveAndFlush(vehicleCaptor.capture());
        Vehicle saved = vehicleCaptor.getValue();
        assertEquals("29A-12345", saved.getLicensePlate());
        assertEquals("Toyota", saved.getMake());
        assertEquals((short) 2022, saved.getYear());
        assertEquals(saved.getDriver().getDriverId(), response.getDriverId());

        assertEquals(USER_ID, response.getUserId());
        assertEquals(DriverStatus.OFFLINE, response.getStatus());
        assertEquals("012345678901", response.getLicenseNumber());
        assertEquals("29A-12345", response.getVehicle().getLicensePlate());
        assertEquals(VehicleType.SEDAN, response.getVehicle().getVehicleType());
        assertNotNull(response.getVehicle().getVehicleId());
    }

    @Test
    @DisplayName("createDriver for a user that already has a profile throws 409 DRIVER_PROFILE_EXISTS without saving")
    void createDriver_userAlreadyHasProfile_throwsConflict() {
        when(driverRepository.existsByUserId(USER_ID)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> driverService.createDriver(request("29A-12345", 2022)));

        assertEquals("DRIVER_PROFILE_EXISTS", ex.getCode());
        verify(driverRepository, never()).saveAndFlush(any());
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createDriver with a plate already registered (in another notation) throws 409 LICENSE_PLATE_ALREADY_REGISTERED")
    void createDriver_plateAlreadyRegistered_throwsConflict() {
        when(driverRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate("29A-12345")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> driverService.createDriver(request("29A-123.45", 2022)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("LICENSE_PLATE_ALREADY_REGISTERED", ex.getCode());
        verify(driverRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createDriver that loses a race on the plate unique index throws 409 instead of 500")
    void createDriver_concurrentPlateInsert_throwsConflict() {
        when(driverRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate("29A-12345")).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenThrow(uniqueViolation(Vehicle.UQ_LICENSE_PLATE));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> driverService.createDriver(request("29A-12345", 2022)));

        assertEquals("LICENSE_PLATE_ALREADY_REGISTERED", ex.getCode());
    }

    @Test
    @DisplayName("createDriver that loses a race on the user_id unique index throws 409 DRIVER_PROFILE_EXISTS")
    void createDriver_concurrentUserInsert_throwsConflict() {
        when(driverRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate("29A-12345")).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class))).thenThrow(uniqueViolation(Driver.UQ_USER_ID));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> driverService.createDriver(request("29A-12345", 2022)));

        assertEquals("DRIVER_PROFILE_EXISTS", ex.getCode());
    }

    @Test
    @DisplayName("createDriver rethrows an integrity violation it does not recognise")
    void createDriver_otherIntegrityViolation_rethrows() {
        when(driverRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate("29A-12345")).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class))).thenThrow(uniqueViolation("some_other_constraint"));

        assertThrows(DataIntegrityViolationException.class, () -> driverService.createDriver(request("29A-12345", 2022)));
    }

    @Test
    @DisplayName("createDriver accepts next year's model and rejects anything later with 400")
    void createDriver_yearAfterNextYear_throwsValidationError() {
        int nextYear = Year.now(ZoneOffset.UTC).getValue() + 1;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> driverService.createDriver(request("29A-12345", nextYear + 1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verifyNoInteractions(driverRepository, vehicleRepository);

        when(driverRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate("29A-12345")).thenReturn(false);
        stubSavesAssignIds();
        assertEquals(nextYear, driverService.createDriver(request("29A-12345", nextYear)).getVehicle().getYear());
    }

    @Test
    @DisplayName("deleteDriverByUserId without a profile does nothing (idempotent compensation)")
    void deleteDriverByUserId_noProfile_doesNothing() {
        when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> driverService.deleteDriverByUserId(USER_ID));

        verify(driverRepository, never()).delete(any());
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    @DisplayName("deleteDriverByUserId removes the vehicles first, then the OFFLINE driver")
    void deleteDriverByUserId_offlineProfile_deletesVehiclesThenDriver() {
        Driver driver = Driver.builder().driverId(UUID.randomUUID()).userId(USER_ID).status(DriverStatus.OFFLINE).build();
        List<Vehicle> vehicles = List.of(Vehicle.builder().driver(driver).licensePlate("29A-12345").build());
        when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriver(driver)).thenReturn(vehicles);

        driverService.deleteDriverByUserId(USER_ID);

        InOrder inOrder = inOrder(vehicleRepository, driverRepository);
        inOrder.verify(vehicleRepository).deleteAll(vehicles);
        inOrder.verify(driverRepository).delete(driver);
    }

    @Test
    @DisplayName("deleteDriverByUserId refuses to delete a driver that is not OFFLINE")
    void deleteDriverByUserId_availableDriver_throwsConflict() {
        Driver driver = Driver.builder().userId(USER_ID).status(DriverStatus.AVAILABLE).build();
        when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));

        ConflictException ex = assertThrows(ConflictException.class, () -> driverService.deleteDriverByUserId(USER_ID));

        assertEquals("DRIVER_PROFILE_IN_USE", ex.getCode());
        verify(driverRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteDriverByUserId refuses to delete a driver reserved for a trip")
    void deleteDriverByUserId_reservedDriver_throwsConflict() {
        Driver driver = Driver.builder().userId(USER_ID).status(DriverStatus.OFFLINE).reservedForTrip(UUID.randomUUID()).build();
        when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver));

        assertThrows(ConflictException.class, () -> driverService.deleteDriverByUserId(USER_ID));

        verify(driverRepository, never()).delete(any());
    }
}
