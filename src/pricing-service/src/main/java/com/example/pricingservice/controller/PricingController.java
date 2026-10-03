package com.example.pricingservice.controller;

import com.example.pricingservice.dto.request.FinalFareRequest;
import com.example.pricingservice.dto.request.QuoteRequest;
import com.example.pricingservice.dto.response.FinalFareResponse;
import com.example.pricingservice.dto.response.QuoteResponse;
import com.example.pricingservice.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> getQuote(@Valid @RequestBody QuoteRequest request) {
        QuoteResponse response = pricingService.calculateQuote(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/final")
    public ResponseEntity<FinalFareResponse> getFinalFare(@Valid @RequestBody FinalFareRequest request) {
        FinalFareResponse response = pricingService.calculateFinalFare(request);
        return ResponseEntity.ok(response);
    }
}
