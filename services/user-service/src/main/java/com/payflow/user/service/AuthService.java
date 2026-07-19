package com.payflow.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.jwt.JwtProperties;
import com.payflow.common.jwt.JwtUtil;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.LoginResponse;
import com.payflow.user.dto.RegisterRequest;
import com.payflow.user.dto.RegisterResponse;
import com.payflow.user.entity.User;
import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;
import com.payflow.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email already exists");
        }
        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                UserRole.USER,
                UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        return RegisterResponse.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .fullName(saved.getFullName())
                .role(saved.getRole())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        String token = jwtUtil.generateAccessToken(user.getId(), user.getRole().name());
        return LoginResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(jwtProperties.accessTokenExpirationMinutes())
            .build();
    }
}
