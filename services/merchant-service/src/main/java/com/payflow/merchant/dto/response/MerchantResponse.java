package com.payflow.merchant.dto.response;

import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.entity.MerchantStatus;

import java.time.Instant;
import java.util.UUID;

public record MerchantResponse (
    UUID id,
    UUID userId,
    String businessName,
    String category,
    String bankAccountNumber,
    String bankName,
    MerchantStatus status,
    String rejectedReason,
    Instant createdAt,
    Instant approvedAt
){
    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
            merchant.getId(),
            merchant.getUserId(),
            merchant.getBusinessName(),
            merchant.getCategory(),
            merchant.getBankAccountNumber(),
            merchant.getBankName(),
            merchant.getStatus(),
            merchant.getRejectedReason(),
            merchant.getCreatedAt(),
            merchant.getApprovedAt()
        );
    }
}
