package com.payflow.wallet.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.wallet.config.RequestUser;
import com.payflow.wallet.dto.response.LedgerEntryResponse;
import com.payflow.wallet.dto.response.WalletResponse;
import com.payflow.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(HttpServletRequest request) {
        UUID userId = RequestUser.requireUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(walletService.getWalletByUserId(userId)));
    }

    @GetMapping("/me/ledger")
    public ResponseEntity<ApiResponse<Page<LedgerEntryResponse>>> getMyLedger(
            HttpServletRequest request,
            @PageableDefault(size = 20 ) Pageable pageable) {
        UUID userId = RequestUser.requireUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(walletService.getLedgerByUserId(userId, pageable)));
    }
}
