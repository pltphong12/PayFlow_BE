package com.payflow.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.common.event.UserRegistered;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.jwt.JwtProperties;
import com.payflow.common.jwt.JwtUtil;
import com.payflow.user.dto.request.LoginRequest;
import com.payflow.user.dto.request.RegisterRequest;
import com.payflow.user.dto.response.RegisterResponse;
import com.payflow.user.entity.RefreshToken;
import com.payflow.user.entity.User;
import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;
import com.payflow.user.outbox.UserRegistrationOutboxService;
import com.payflow.user.repository.RefreshTokenRepository;
import com.payflow.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRegistrationOutboxService userRegistrationOutboxService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHashService tokenHashService;
    private final RefreshTokenRepository refreshTokenRepository;

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

        userRegistrationOutboxService.enqueueUserRegistered(new UserRegistered(
            UUID.randomUUID(), 
            saved.getId(), 
            saved.getEmail(), 
            saved.getFullName(), 
            saved.getCreatedAt()
        ));

        return RegisterResponse.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .fullName(saved.getFullName())
                .role(saved.getRole())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public AuthTokenPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return issueTokenPair(user);
    }

    @Transactional
    public AuthTokenPair refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }

        String tokenHash = tokenHashService.hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!stored.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }

        stored.revoke();
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = tokenHashService.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(RefreshToken::revoke);
    }

    private AuthTokenPair issueTokenPair(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole().name());
        String rawRefreshToken = jwtUtil.generateRefreshToken();
        String tokenHash = tokenHashService.hash(rawRefreshToken);
        Instant expiresAt = Instant.now().plus(
                jwtProperties.refreshTokenExpirationMinutes(), ChronoUnit.MINUTES);

        refreshTokenRepository.save(new RefreshToken(user, tokenHash, expiresAt));
        return new AuthTokenPair(
                accessToken,
                rawRefreshToken,
                jwtProperties.accessTokenExpirationMinutes() * 60L,
                jwtProperties.refreshTokenExpirationMinutes() * 60L);
    }
}
