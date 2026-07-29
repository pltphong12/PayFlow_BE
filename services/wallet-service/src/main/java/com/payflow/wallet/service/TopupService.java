package com.payflow.wallet.service;

import com.payflow.wallet.dto.request.CreateTopupRequest;
import com.payflow.wallet.dto.response.TopupResponse;
import com.payflow.wallet.entity.TopupRequest;
import com.payflow.wallet.repository.TopupRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopupService {

    private final TopupRequestRepository topupRequestRepository;

    @Transactional
    public TopupResponse createTopup(UUID userId, String idempotencyKey, CreateTopupRequest request) {
        // Check if a top-up request with the same idempotency key already exists
        return topupRequestRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toResponse)
                .orElseGet(() -> {
                    // If not, create a new top-up request
                    TopupRequest topupRequest = new TopupRequest(
                            userId,
                            request.amount(),
                            idempotencyKey
                    );
                    TopupRequest savedRequest = topupRequestRepository.save(topupRequest);
                    return toResponse(savedRequest);
                });
    }

    private TopupResponse toResponse(TopupRequest request) {
        return TopupResponse.builder()
            .id(request.getId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .status(request.getStatus())
            .createdAt(request.getCreatedAt())
            .build();
    }
}
