package com.example.paymentservice.dto.response;

import com.example.paymentservice.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private String paymentId;
    private String tripId;
    private PaymentStatus status;
    private Double amount;
    private String currency;
    private Instant processedAt;
    private Instant refundedAt;
    private String failureReason;
}
