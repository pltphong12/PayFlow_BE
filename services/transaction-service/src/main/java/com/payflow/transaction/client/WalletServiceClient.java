package com.payflow.transaction.client;

import com.payflow.common.dto.ApiResponse;
import com.payflow.transaction.client.dto.WalletLookupResponse;
import com.payflow.transaction.client.dto.WalletMutationRequest;
import com.payflow.transaction.client.dto.WalletMutationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class WalletServiceClient {

    private final RestClient restClient;

    public WalletServiceClient(
        @Value("${WALLET_SERVICE_URI}") String walletServiceUrl
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(walletServiceUrl)
            .build();
    }

    public WalletLookupResponse getWalletByUserId(UUID userId) {
        ApiResponse<WalletLookupResponse> response = restClient.get()
            .uri("/api/v1/wallets/internal/{userId}", userId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return requireData(response);
    }

    public WalletMutationResponse debit(
        UUID walletId,
        UUID transactionId,
        BigDecimal amount
    ) {
        return mutate(walletId, "debit", transactionId, amount);
    }
    public WalletMutationResponse credit(
        UUID walletId,
        UUID transactionId,
        BigDecimal amount
    ) {
        return mutate(walletId, "credit", transactionId, amount);
    }

    private WalletMutationResponse mutate(
        UUID walletId,
        String operation,
        UUID transactionId,
        BigDecimal amount
    ) {
        ApiResponse<WalletMutationResponse> response = restClient.put()
            .uri(
                "/api/v1/wallets/internal/{walletId}/{operation}",
                walletId,
                operation
            )
            .body(new WalletMutationRequest(transactionId, amount))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return requireData(response);
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException(
                "Wallet service returned an invalid response"
            );
        }
        return response.data();
    }
}
