package com.example.driverservice.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message, String code) {
        super(message, HttpStatus.NOT_FOUND, code);
    }
}
