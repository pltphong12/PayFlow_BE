package com.payflow.transaction.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.exception.BusinessException;
import com.payflow.transaction.config.RequestUser;
import com.payflow.transaction.dto.request.CreateTransferRequest;
import com.payflow.transaction.dto.response.TransferResponse;
import com.payflow.transaction.service.SagaOrchestratorService;
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
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final SagaOrchestratorService sagaOrchestratorService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> createTransfer(
            HttpServletRequest httpServletRequest,
            @RequestHeader(
                value = "Idempotency-Key",
                required = false
            ) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "Missing Idempotency-Key header"
            );
        }

        UUID senderId = RequestUser.requireUserId(httpServletRequest);

        TransferResponse response = sagaOrchestratorService.transfer(
            senderId,
            request.receiverUserId(),
            request.amount(),
            idempotencyKey
        );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransferResponse>> getTransfer(
        HttpServletRequest httpServletRequest,
        @PathVariable("transactionId") UUID transactionId
    ) {
        UUID requesterUserId = RequestUser.requireUserId(httpServletRequest);

        TransferResponse response = sagaOrchestratorService.getTransfer(
            requesterUserId,
            transactionId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
