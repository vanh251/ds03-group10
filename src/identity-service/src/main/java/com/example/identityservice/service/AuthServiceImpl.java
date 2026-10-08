package com.example.identityservice.service;

import com.example.identityservice.dto.request.RegisterRequest;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.BusinessException;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.mapper.UserMapper;
import com.example.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Public self-registration may only create PASSENGER or DRIVER accounts (docs/api.md)
        if (request.getRole() == UserRole.ADMIN) {
            throw new BusinessException("Role ADMIN cannot be self-registered",
                    HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered", EMAIL_ALREADY_REGISTERED);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(request.getRole())
                .build();

        User saved;
        try {
            // Flush now so a concurrent registration hitting the unique email index surfaces here as 409, not at commit as 500
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Email already registered", EMAIL_ALREADY_REGISTERED);
        }

        log.info("User registered userId={}, role={}", saved.getUserId(), saved.getRole());
        return userMapper.toResponse(saved);
    }
}
