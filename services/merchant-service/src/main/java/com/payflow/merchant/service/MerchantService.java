package com.payflow.merchant.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.common.exception.BusinessException;
import com.payflow.merchant.dto.request.RegisterMerchantRequest;
import com.payflow.merchant.dto.request.RejectMerchantRequest;
import com.payflow.merchant.dto.response.MerchantResponse;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.entity.MerchantBalance;
import com.payflow.merchant.entity.MerchantStatus;
import com.payflow.merchant.repository.MerchantBalanceRepository;
import com.payflow.merchant.repository.MerchantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantBalanceRepository merchantBalanceRepository;

    @Transactional
    public MerchantResponse register(
            UUID userId,
            RegisterMerchantRequest request) {
        // Check if merchant already registered
        if (merchantRepository.existsByUserId(userId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Merchant already registered");
        }
        // Create Merchant
        Merchant merchant = new Merchant(
                userId,
                request.businessName().trim(),
                request.category().trim(),
                request.bankAccountNumber().trim(),
                request.bankName().trim());

        try {
            merchantRepository.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "Merchant already registered");
        }

        return MerchantResponse.from(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchant(
            UUID userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Merchant not found"));
        return MerchantResponse.from(merchant);
    }

    @Transactional(readOnly = true)
    public Page<MerchantResponse> getMerchantsByStatus(
            MerchantStatus status,
            Pageable pageable) {
        Page<Merchant> merchants = merchantRepository.findByStatus(status, pageable);
        return merchants.map(MerchantResponse::from);
    }

    @Transactional
    public MerchantResponse approve(
            UUID merchantId) {
        Merchant merchant = findMerchantForUpdate(merchantId);
        merchant.approve();
        merchantBalanceRepository.saveAndFlush(new MerchantBalance(merchant.getId()));
        return MerchantResponse.from(merchant);
    }

    @Transactional
    public MerchantResponse reject(
            UUID merchantId,
            RejectMerchantRequest request) {
        Merchant merchant = findMerchantForUpdate(merchantId);
        merchant.reject(request.reason());
        return MerchantResponse.from(merchant);
    }

    private Merchant findMerchantForUpdate(UUID merchantId) {
        return merchantRepository.findByIdForUpdate(merchantId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Merchant not found"));
    }
}