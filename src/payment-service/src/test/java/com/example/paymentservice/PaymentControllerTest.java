package com.example.paymentservice;

import com.example.paymentservice.controller.PaymentController;
import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.PaymentResponse;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.GlobalExceptionHandler;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

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
        String jsonRequest = """
                {
                  "passengerId": "passenger-001",
                  "amount": 36500.0,
                  "currency": "VND",
                  "idempotencyKey": "trip-001-charge-001"
                }
                """;

        PaymentResponse response = PaymentResponse.builder()
                .paymentId("pay-001")
                .tripId("trip-001")
                .status(PaymentStatus.SUCCESS)
                .amount(36500.0)
                .currency("VND")
                .processedAt(Instant.now())
                .build();

        when(paymentService.charge(eq("trip-001"), any(PaymentChargeRequest.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments/trip-001/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest)
                        .header("X-Idempotency-Key", "trip-001-charge-001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("pay-001"))
                .andExpect(jsonPath("$.tripId").value("trip-001"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(36500.0));
    }

    @Test
    @DisplayName("GET /api/payments/{tripId} returns 200 OK and payment details")
    void getPayment_existingTrip_returns200Ok() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId("pay-001")
                .tripId("trip-001")
                .status(PaymentStatus.SUCCESS)
                .amount(36500.0)
                .currency("VND")
                .processedAt(Instant.now())
                .build();

        when(paymentService.getPaymentByTripId("trip-001")).thenReturn(response);

        mockMvc.perform(get("/api/payments/trip-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("pay-001"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/payments/{tripId}/refund returns 200 OK and refunded status")
    void refundPayment_existingTrip_returns200Ok() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId("pay-001")
                .tripId("trip-001")
                .status(PaymentStatus.REFUNDED)
                .refundedAt(Instant.now())
                .build();

        when(paymentService.refund(eq("trip-001"), any())).thenReturn(response);

        mockMvc.perform(post("/api/payments/trip-001/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }
}
