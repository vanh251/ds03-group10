package com.example.identityservice.service;

import com.example.identityservice.dto.request.DriverRegisterRequest;
import com.example.identityservice.dto.request.LoginRequest;
import com.example.identityservice.dto.request.PassengerRegisterRequest;
import com.example.identityservice.dto.response.DriverRegisterResponse;
import com.example.identityservice.dto.response.LoginResponse;
import com.example.identityservice.dto.response.UserResponse;

public interface AuthService {

    UserResponse registerPassenger(PassengerRegisterRequest request);

    DriverRegisterResponse registerDriver(DriverRegisterRequest request);

    LoginResponse login(LoginRequest request);
}
