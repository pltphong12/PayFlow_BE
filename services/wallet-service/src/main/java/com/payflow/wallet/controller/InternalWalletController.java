package com.payflow.wallet.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.wallet.dto.request.WalletMutationRequest;
import com.payflow.wallet.dto.response.WalletMutationResponse;
import com.payflow.wallet.dto.response.WalletResponse;
import com.payflow.wallet.service.WalletMutationService;
import com.payflow.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets/internal")
public class InternalWalletController {
    private final WalletService walletService;
    private final WalletMutationService walletMutationService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletByUserId(
        @PathVariable("userId") UUID userId
    ) {
        WalletResponse walletResponse = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(walletResponse));
    }

    @PutMapping("/{walletId}/debit")
    public ResponseEntity<ApiResponse<WalletMutationResponse>> debit(
        @PathVariable("walletId") UUID walletId,
        @Valid @RequestBody WalletMutationRequest request
    ){
        WalletMutationResponse response = walletMutationService.debit(
            walletId,
            request.transactionId(),
            request.amount()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{walletId}/credit")
    public ResponseEntity<ApiResponse<WalletMutationResponse>> credit(
        @PathVariable("walletId") UUID walletId,
        @Valid @RequestBody WalletMutationRequest request
    ){
        WalletMutationResponse response = walletMutationService.credit(
            walletId,
            request.transactionId(),
            request.amount()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
