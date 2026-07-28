package com.payflow.wallet.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.wallet.dto.response.WalletResponse;
import com.payflow.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets/internal")
public class InternalWalletController {
    private final WalletService walletService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletByUserId(@PathVariable UUID userId) {
        WalletResponse walletResponse = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(walletResponse));
    }
}
