package com.payflow.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMerchantRequest(
    @NotBlank(message = "Reject reason is required")
    @Size(max = 500, message = "Reject reason must not exceed 500 characters")
    String reason
) {
}
