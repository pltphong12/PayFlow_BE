package com.payflow.merchant.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.common.dto.ApiResponse;
import com.payflow.merchant.config.RequestUser;
import com.payflow.merchant.dto.request.RejectMerchantRequest;
import com.payflow.merchant.dto.response.MerchantResponse;
import com.payflow.merchant.entity.MerchantStatus;
import com.payflow.merchant.service.MerchantService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/merchants")
public class AdminMerchantController {

    private final MerchantService merchantService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<MerchantResponse>>> getMerchants(
            HttpServletRequest httpServletRequest,
            @RequestParam(defaultValue = "PENDING_APPROVAL") MerchantStatus merchantStatus,
            @PageableDefault(size = 20) Pageable pageable) {
        RequestUser.requireRole(httpServletRequest, "ADMIN");
        Page<MerchantResponse> response = merchantService.getMerchantsByStatus(merchantStatus, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{merchantId}/approve")
    public ResponseEntity<ApiResponse<MerchantResponse>> approve(
        HttpServletRequest httpServletRequest,
        @PathVariable UUID merchantId
    ) {
        RequestUser.requireRole(httpServletRequest, "ADMIN");
        MerchantResponse response = merchantService.approve(merchantId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{merchantId}/reject")
    public ResponseEntity<ApiResponse<MerchantResponse>> reject(
        HttpServletRequest httpServletRequest,
        @Valid @RequestBody RejectMerchantRequest request,
        @PathVariable UUID merchantId
    ) {
        RequestUser.requireRole(httpServletRequest, "ADMIN");
        MerchantResponse response = merchantService.reject(merchantId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
