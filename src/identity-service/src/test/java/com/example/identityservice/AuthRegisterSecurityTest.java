package com.example.identityservice;

import com.example.identityservice.client.DriverServiceClient;
import com.example.identityservice.dto.request.CreateDriverRequest;
import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs registration through the real filter chain (Spring Security + CorrelationIdFilter + validation + Jackson)
 * with only the repository and the driver-service client mocked, to prove both endpoints are public and wired end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRegisterSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DriverServiceClient driverServiceClient;

    private void stubSaveAndFlushAssignsId() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID());
            user.setCreatedAt(Instant.now());
            return user;
        });
    }

    @Test
    @DisplayName("POST /api/auth/register/passenger without an Authorization header is allowed and returns 201")
    void registerPassenger_withoutJwt_returns201() throws Exception {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        mockMvc.perform(post("/api/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyen Van A",
                                  "email": "user@example.com",
                                  "password": "S3cure-Pass!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("A role sent in the passenger body (e.g. ADMIN) is ignored: the account is always PASSENGER")
    void registerPassenger_bodyWithRoleAdmin_createsPassenger() throws Exception {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        mockMvc.perform(post("/api/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyen Van A",
                                  "email": "user@example.com",
                                  "password": "S3cure-Pass!",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PASSENGER"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertEquals(UserRole.PASSENGER, captor.getValue().getRole());
    }

    @Test
    @DisplayName("POST /api/auth/register/driver without an Authorization header is allowed and returns 201 with the profile")
    void registerDriver_withoutJwt_returns201() throws Exception {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class))).thenReturn(DriverProfileResponse.builder()
                .driverId(UUID.randomUUID())
                .status("OFFLINE")
                .licenseNumber("012345678901")
                .build());

        mockMvc.perform(post("/api/auth/register/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Tran Van B",
                                  "email": "driver@example.com",
                                  "password": "S3cure-Pass!",
                                  "licenseNumber": "012345678901",
                                  "vehicle": {
                                    "licensePlate": "29A-12345", "make": "Toyota", "model": "Vios",
                                    "color": "White", "year": 2022, "vehicleType": "SEDAN"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.driver.status").value("OFFLINE"));
    }

    @Test
    @DisplayName("The correlationId sent by the gateway is propagated into the error body")
    void registerPassenger_invalidBodyWithCorrelationHeader_returns400WithSameCorrelationId() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    @Test
    @DisplayName("Without X-Correlation-ID the service does not generate one (only the gateway does)")
    void registerPassenger_invalidBodyWithoutCorrelationHeader_returns400WithoutCorrelationId() throws Exception {
        mockMvc.perform(post("/api/auth/register/passenger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register (removed path) returns 404 through the real chain, not 500")
    void register_oldPath_returns404() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
