package com.payflow.merchant.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.payflow.common.exception.BusinessException;

@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false, length = 160)
    private String businessName;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "bank_account_number", nullable = false, length = 64)
    private String bankAccountNumber;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public Merchant(
        UUID userId,
        String businessName,
        String category,
        String bankAccountNumber,
        String bankName
    ) {
        this.userId = userId;
        this.businessName = businessName;
        this.category = category;
        this.bankAccountNumber = bankAccountNumber;
        this.bankName = bankName;
        this.status = MerchantStatus.PENDING_APPROVAL;
    }

    public void approve() {
        ensurePendingApproval();

        this.status = MerchantStatus.APPROVED;
        this.approvedAt = Instant.now();
        this.rejectedReason = null;
    }

    public void reject(String rejectedReason) {
        ensurePendingApproval();

        if (rejectedReason == null || rejectedReason.isBlank()) {
            throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "Reject reason is required"
            );
        }
        this.status = MerchantStatus.REJECTED;
        this.approvedAt = null;
        this.rejectedReason = rejectedReason.trim();
    }

    private void ensurePendingApproval() {
        if (status != MerchantStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                HttpStatus.CONFLICT,
                "Merchant is no longer pending approval"
            );
        }
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}