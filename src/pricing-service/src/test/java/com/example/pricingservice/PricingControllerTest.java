package com.example.pricingservice;

import com.example.pricingservice.controller.PricingController;
import com.example.pricingservice.dto.request.FinalFareRequest;
import com.example.pricingservice.dto.request.QuoteRequest;
import com.example.pricingservice.dto.response.FinalFareResponse;
import com.example.pricingservice.dto.response.QuoteResponse;
import com.example.pricingservice.exception.GlobalExceptionHandler;
import com.example.pricingservice.service.PricingService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PricingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private PricingController pricingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pricingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/pricing/quote with valid payload returns 200 and quote")
    void getQuote_validRequest_returns200AndQuote() throws Exception {
        String jsonRequest = """
                {
                  "pickup": { "latitude": 21.0285, "longitude": 105.8542, "address": "Hoan Kiem" },
                  "destination": { "latitude": 21.0188, "longitude": 105.8520, "address": "Dong Da" }
                }
                """;
        QuoteResponse response = QuoteResponse.builder()
                .estimatedDistanceKm(2.5)
                .estimatedDurationMin(12)
                .baseFare(new BigDecimal("15000"))
                .distanceFare(new BigDecimal("12500"))
                .timeFare(new BigDecimal("6000"))
                .totalFare(new BigDecimal("33500"))
                .currency("VND")
                .build();
        when(pricingService.calculateQuote(any(QuoteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFare").value(33500))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.estimatedDistanceKm").value(2.5));
    }

    @Test
    @DisplayName("POST /api/pricing/quote without pickup returns 400 VALIDATION_ERROR")
    void getQuote_missingPickup_returns400BadRequest() throws Exception {
        String jsonRequest = """
                { "destination": { "latitude": 21.0188, "longitude": 105.8520 } }
                """;

        mockMvc.perform(post("/api/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/pricing/quote with malformed JSON returns 400 MALFORMED_REQUEST instead of 500")
    void getQuote_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"pickup\": { \"latitude\": \"abc\" "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("GET /api/pricing/quote (unsupported method) returns 405 instead of 500")
    void getQuote_wrongMethod_returns405() throws Exception {
        mockMvc.perform(get("/api/pricing/quote"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("POST /api/pricing/final with valid payload returns 200 and final fare")
    void getFinalFare_validRequest_returns200AndFinalFare() throws Exception {
        String jsonRequest = """
                { "tripId": "trip-001", "actualDistanceKm": 2.8, "actualDurationMin": 15 }
                """;
        FinalFareResponse response = FinalFareResponse.builder()
                .tripId("trip-001")
                .baseFare(new BigDecimal("15000"))
                .distanceFare(new BigDecimal("14000"))
                .timeFare(new BigDecimal("7500"))
                .totalFare(new BigDecimal("36500"))
                .currency("VND")
                .build();
        when(pricingService.calculateFinalFare(any(FinalFareRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/pricing/final")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value("trip-001"))
                .andExpect(jsonPath("$.totalFare").value(36500));
    }
}
