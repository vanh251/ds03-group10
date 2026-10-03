package com.example.matchingservice.controller;

import com.example.matchingservice.dto.MatchResponse;
import com.example.matchingservice.dto.RematchResponse;
import com.example.matchingservice.entity.MatchStatus;
import com.example.matchingservice.exception.GlobalExceptionHandler;
import com.example.matchingservice.exception.MatchRequestNotFoundException;
import com.example.matchingservice.exception.MatchingFailedException;
import com.example.matchingservice.service.MatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MatchingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private MatchingController matchingController;

    private UUID requestId;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(matchingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        requestId = UUID.randomUUID();
        driverId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/matching/{requestId}/match - Success 202 Accepted")
    void match_success() throws Exception {
        MatchResponse response = MatchResponse.builder()
                .requestId(requestId)
                .status(MatchStatus.MATCHED)
                .matchedDriverId(driverId)
                .message("Driver successfully matched")
                .build();

        when(matchingService.matchDriver(eq(requestId), any())).thenReturn(response);

        mockMvc.perform(post("/api/matching/{requestId}/match", requestId)
                        .header("X-Correlation-ID", "corr-001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchedDriverId").value(driverId.toString()));
    }

    @Test
    @DisplayName("POST /api/matching/{requestId}/match - Conflict 409 when no driver found")
    void match_failed_conflict() throws Exception {
        when(matchingService.matchDriver(eq(requestId), any()))
                .thenThrow(new MatchingFailedException("No available drivers"));

        mockMvc.perform(post("/api/matching/{requestId}/match", requestId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("No available drivers"));
    }

    @Test
    @DisplayName("POST /api/matching/{requestId}/rematch - Success 202 Accepted")
    void rematch_success() throws Exception {
        RematchResponse response = RematchResponse.builder()
                .requestId(requestId)
                .status("REMATCHING")
                .attempt(2)
                .matchedDriverId(driverId)
                .message("Rematch initiated")
                .build();

        when(matchingService.rematchDriver(eq(requestId), any())).thenReturn(response);

        mockMvc.perform(post("/api/matching/{requestId}/rematch", requestId)
                        .header("X-Correlation-ID", "corr-002"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("REMATCHING"))
                .andExpect(jsonPath("$.attempt").value(2));
    }

    @Test
    @DisplayName("POST /api/matching/{requestId}/rematch - Not Found 404 when request not found")
    void rematch_notFound() throws Exception {
        when(matchingService.rematchDriver(eq(requestId), any()))
                .thenThrow(new MatchRequestNotFoundException(requestId));

        mockMvc.perform(post("/api/matching/{requestId}/rematch", requestId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
