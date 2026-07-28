package com.payflow.wallet.controller;

import com.payflow.common.exception.GlobalExceptionHandler;
import com.payflow.wallet.config.RequestUser;
import com.payflow.wallet.dto.response.WalletResponse;
import com.payflow.wallet.entity.WalletStatus;
import com.payflow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WalletController.class)
@Import(GlobalExceptionHandler.class)
class WalletControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WalletService walletService;

    @Test
    void getMyWallet_whenHeaderPresent_returnsWallet() throws Exception {
        UUID userId = UUID.randomUUID();

        WalletResponse walletResponse = WalletResponse.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .balance(new BigDecimal("0.00"))
                .currency("VND")
                .status(WalletStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(walletService.getWalletByUserId(userId)).thenReturn(walletResponse);

        mockMvc.perform(get("/api/v1/wallets/me")
                        .header(RequestUser.HEADER_USER_ID, userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.currency").value("VND"));
    }

    @Test
    void getMyWallet_whenHeaderMissing_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Missing X-User-Id"));
    }
}

