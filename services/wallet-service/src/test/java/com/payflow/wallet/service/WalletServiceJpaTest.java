package com.payflow.wallet.service;

import com.payflow.wallet.entity.WalletStatus;
import com.payflow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.testcontainers.utility.DockerImageName;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        classes = com.payflow.wallet.WalletServiceApplication.class,
        properties = {
                "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
                "spring.kafka.listener.auto-startup=false"
        }
)
@Testcontainers
class WalletServiceJpaTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("payflow_wallet_db")
            .withUsername("payflow_user")
            .withPassword("payflow_password");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", postgres::getHost);
        registry.add("POSTGRES_PORT", () -> postgres.getMappedPort(5432));
        registry.add("POSTGRES_DB", postgres::getDatabaseName);
        registry.add("POSTGRES_USER", postgres::getUsername);
        registry.add("POSTGRES_PASSWORD", postgres::getPassword);
    }

    @Autowired
    WalletService walletService;

    @Autowired
    WalletRepository walletRepository;

    @Test
    void createWalletIfAbsent_createsNewWalletWithZeroBalance() {
        UUID userId = UUID.randomUUID();

        assertThat(walletRepository.existsByUserId(userId)).isFalse();

        walletService.createWalletIfAbsent(userId);

        assertThat(walletRepository.existsByUserId(userId)).isTrue();

        var wallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getCurrency()).isEqualTo("VND");
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }
}

