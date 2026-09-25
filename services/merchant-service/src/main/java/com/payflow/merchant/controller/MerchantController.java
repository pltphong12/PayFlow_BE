package com.payflow.merchant.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.common.dto.ApiResponse;
import com.payflow.merchant.config.RequestUser;
import com.payflow.merchant.dto.request.RegisterMerchantRequest;
import com.payflow.merchant.dto.response.MerchantResponse;
import com.payflow.merchant.service.MerchantService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MerchantResponse>> register(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody RegisterMerchantRequest request) {
        RequestUser.requireRole(httpServletRequest, "USER");
        UUID userId = RequestUser.requireUserId(httpServletRequest);
        MerchantResponse response = merchantService.register(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMe(
            HttpServletRequest httpServletRequest) {
        RequestUser.requireRole(httpServletRequest, "USER");
        UUID userId = RequestUser.requireUserId(httpServletRequest);
        MerchantResponse response = merchantService.getMerchant(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
