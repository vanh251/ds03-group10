package com.example.identityservice.mapper;

import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.dto.response.DriverRegisterResponse;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public DriverRegisterResponse toDriverRegisterResponse(User user, DriverProfileResponse driverProfile) {
        return DriverRegisterResponse.builder()
                .id(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .driver(driverProfile)
                .build();
    }
}
