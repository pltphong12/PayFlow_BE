package com.payflow.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterMerchantRequest(
    @NotBlank(message = "Business name is required") @Size(max = 160, message = "Business name must not exceed 160 characters")
    String businessName,
    @NotBlank(message = "Category is required") @Size(max = 100, message = "Category must not exceed 100 characters")
    String category,
    @NotBlank(message = "Bank account number is required") @Size(max = 64, message = "Bank account number must not exceed 64 characters")
    String bankAccountNumber,
    @NotBlank(message = "Bank name is required") @Size(max = 120, message = "Bank name must not exceed 120 characters")
    String bankName
) {
}