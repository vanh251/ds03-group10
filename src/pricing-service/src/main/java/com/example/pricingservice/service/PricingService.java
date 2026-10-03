package com.example.pricingservice.service;

import com.example.pricingservice.dto.request.FinalFareRequest;
import com.example.pricingservice.dto.request.QuoteRequest;
import com.example.pricingservice.dto.response.FinalFareResponse;
import com.example.pricingservice.dto.response.QuoteResponse;

public interface PricingService {

    QuoteResponse calculateQuote(QuoteRequest request);

    FinalFareResponse calculateFinalFare(FinalFareRequest request);
}
