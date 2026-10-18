package com.example.paymentservice;

import com.example.paymentservice.controller.PaymentController;
import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.PaymentResponse;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.GlobalExceptionHandler;
import com.example.paymentservice.exception.PaymentDeclinedException;
import com.example.paymentservice.service.PaymentService;
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
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PAYMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    private static final String CHARGE_BODY = """
            {
              "passengerId": "550e8400-e29b-41d4-a716-446655440001",
              "amount": 36500,
              "currency": "VND"
            }
            """;

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/charge returns 201 Created and payment details")
    void chargePayment_validRequest_returns201Created() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(PAYMENT_ID)
                .tripId(TRIP_ID)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("36500"))
                .currency("VND")
                .processedAt(Instant.now())
                .build();
        when(paymentService.charge(eq(TRIP_ID), any(PaymentChargeRequest.class), eq("trip-001-charge-001"), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments/" + TRIP_ID + "/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHARGE_BODY)
                        .header("X-Idempotency-Key", "trip-001-charge-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.tripId").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(36500));
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/charge declined by the gateway returns 402 PAYMENT_FAILED")
    void chargePayment_declined_returns402() throws Exception {
        when(paymentService.charge(eq(TRIP_ID), any(PaymentChargeRequest.class), any(), any()))
                .thenThrow(new PaymentDeclinedException("Payment charge failed: SIMULATED_FAILURE"));

        mockMvc.perform(post("/api/payments/" + TRIP_ID + "/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHARGE_BODY)
                        .header("X-Idempotency-Key", "trip-001-charge-001"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/charge with malformed JSON returns 400 MALFORMED_REQUEST")
    void chargePayment_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/payments/" + TRIP_ID + "/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": ")
                        .header("X-Idempotency-Key", "trip-001-charge-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/charge with a non-UUID passengerId returns 400 MALFORMED_REQUEST")
    void chargePayment_invalidPassengerId_returns400() throws Exception {
        mockMvc.perform(post("/api/payments/" + TRIP_ID + "/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"passengerId\": \"passenger-001\", \"amount\": 36500 }")
                        .header("X-Idempotency-Key", "trip-001-charge-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/charge with a negative amount returns 400 VALIDATION_ERROR")
    void chargePayment_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/payments/" + TRIP_ID + "/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"passengerId\": \"550e8400-e29b-41d4-a716-446655440001\", \"amount\": -1 }")
                        .header("X-Idempotency-Key", "trip-001-charge-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/payments/{tripId} returns 200 OK and payment details")
    void getPayment_existingTrip_returns200Ok() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(PAYMENT_ID)
                .tripId(TRIP_ID)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("36500"))
                .currency("VND")
                .processedAt(Instant.now())
                .build();
        when(paymentService.getPaymentByTripId(TRIP_ID)).thenReturn(response);

        mockMvc.perform(get("/api/payments/" + TRIP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /api/payments/{tripId} with a non-UUID trip ID returns 400 INVALID_PARAMETER")
    void getPayment_invalidTripId_returns400() throws Exception {
        mockMvc.perform(get("/api/payments/trip-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/refund returns 200 OK and refunded status")
    void refundPayment_existingTrip_returns200Ok() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(PAYMENT_ID)
                .tripId(TRIP_ID)
                .status(PaymentStatus.REFUNDED)
                .refundedAt(Instant.now())
                .build();
        when(paymentService.refund(eq(TRIP_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/payments/" + TRIP_ID + "/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("DELETE /api/payments/{tripId} (unsupported method) returns 405 instead of 500")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/payments/" + TRIP_ID))
                .andExpect(status().isMethodNotAllowed());
    }
}
