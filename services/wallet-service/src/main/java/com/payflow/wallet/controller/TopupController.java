package com.payflow.wallet.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.config.RequestUser;
import com.payflow.wallet.dto.request.CreateTopupRequest;
import com.payflow.wallet.dto.response.TopupResponse;
import com.payflow.wallet.service.TopupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/topup")
public class TopupController {

    private final TopupService  topupService;

    @PostMapping
    public ResponseEntity<ApiResponse<TopupResponse>> createTopup(
        HttpServletRequest httpServletRequestrequest,
        @RequestHeader (value = "Idempotency-Key", required = false) String  idempotencyKey,
        @Valid @RequestBody CreateTopupRequest request
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Missing Idempotency-Key header");
        }
        UUID userId = RequestUser.requireUserId(httpServletRequestrequest);
        TopupResponse response = this.topupService.createTopup(userId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(response));
    }
}
