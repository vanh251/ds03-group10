package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String message, String code) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, code);
    }
}
