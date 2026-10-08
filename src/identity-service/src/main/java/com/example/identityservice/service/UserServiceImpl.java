package com.example.identityservice.service;

import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.exception.ResourceNotFoundException;
import com.example.identityservice.mapper.UserMapper;
import com.example.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", USER_NOT_FOUND));
    }
}
