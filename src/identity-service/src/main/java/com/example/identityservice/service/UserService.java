package com.example.identityservice.service;

import com.example.identityservice.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getUserById(UUID id);
}
