package com.payflow.user.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.common.dto.ApiResponse;
import com.payflow.user.dto.request.LoginRequest;
import com.payflow.user.dto.request.RegisterRequest;
import com.payflow.user.dto.response.LoginResponse;
import com.payflow.user.dto.response.RegisterResponse;
import com.payflow.user.service.AuthService;
import com.payflow.user.service.AuthTokenPair;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Value("${payflow.auth.refresh-cookie.secure}")
    private boolean refreshCookieSecure;

    @Value("${payflow.auth.refresh-cookie.same-site}")
    private String refreshCookieSameSite;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenPair tokens = authService.login(request);
        return tokenResponse(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        AuthTokenPair tokens = authService.refresh(refreshToken);
        return tokenResponse(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString())
                .body(ApiResponse.ok(null));
    }

    private ResponseEntity<ApiResponse<LoginResponse>> tokenResponse(AuthTokenPair tokens) {
        LoginResponse response = LoginResponse.builder()
                .accessToken(tokens.accessToken())
                .tokenType("Bearer")
                .expiresIn(tokens.accessExpiresIn())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(tokens.refreshToken(), tokens.refreshExpiresIn()).toString())
                .body(ApiResponse.ok(response));
    }

    private ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
