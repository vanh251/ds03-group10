package com.example.identityservice.service;

import com.example.identityservice.dto.request.RegisterRequest;
import com.example.identityservice.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);
}
