package com.payflow.merchant.persistence;

import com.payflow.merchant.MerchantServiceApplication;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.entity.MerchantBalance;
import com.payflow.merchant.entity.MerchantStatus;
import com.payflow.merchant.repository.MerchantBalanceRepository;
import com.payflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MerchantServiceApplication.class)
@ActiveProfiles("test")
@Testcontainers 
class MerchantPersistenceJpaTest {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("payflow_merchant_db")
            .withUsername("payflow_user")
            .withPassword("payflow_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add(
            "POSTGRES_PORT",
            () -> POSTGRES.getMappedPort(5432)
        );
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);

        // Redis chưa được sử dụng trong các test persistence này.
        registry.add("REDIS_HOST", () -> "localhost");
        registry.add("REDIS_PORT", () -> 6379);

        registry.add(
            "QR_HMAC_SECRET",
            () -> "test-qr-hmac-secret-minimum-256-bits"
        );
    }

    @Autowired
    MerchantRepository merchantRepository;

    @Autowired
    MerchantBalanceRepository balanceRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        balanceRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void flywayAndJpaMapping_persistMerchantAndBalance() {
        Integer migrationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
            Integer.class
        );

        Merchant merchant = merchantRepository.saveAndFlush(
            new Merchant(
                UUID.randomUUID(),
                "PayFlow Shop",
                "RETAIL",
                "0123456789",
                "PayFlow Bank"
            )
        );

        MerchantBalance balance = balanceRepository.saveAndFlush(
            new MerchantBalance(merchant.getId())
        );

        assertThat(migrationCount).isEqualTo(1);
        assertThat(merchant.getStatus())
            .isEqualTo(MerchantStatus.PENDING_APPROVAL);
        assertThat(balance.getPendingBalance())
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getSettledBalance())
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getVersion()).isZero();
    }

    @Test
    void duplicateUserId_isRejected() {
        UUID userId = UUID.randomUUID();

        merchantRepository.saveAndFlush(new Merchant(
            userId,
            "First Shop",
            "RETAIL",
            "111111",
            "Bank A"
        ));

        assertThatThrownBy(() ->
            merchantRepository.saveAndFlush(new Merchant(
                userId,
                "Second Shop",
                "FOOD",
                "222222",
                "Bank B"
            ))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateBalanceForMerchant_isRejected() {
        Merchant merchant = merchantRepository.saveAndFlush(
            new Merchant(
                UUID.randomUUID(),
                "PayFlow Shop",
                "RETAIL",
                "0123456789",
                "PayFlow Bank"
            )
        );

        balanceRepository.saveAndFlush(
            new MerchantBalance(merchant.getId())
        );

        assertThatThrownBy(() ->
            balanceRepository.saveAndFlush(
                new MerchantBalance(merchant.getId())
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativePendingBalance_isRejectedByDatabase() {
        Merchant merchant = merchantRepository.saveAndFlush(
            new Merchant(
                UUID.randomUUID(),
                "PayFlow Shop",
                "RETAIL",
                "0123456789",
                "PayFlow Bank"
            )
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO merchant_balances (
                id,
                merchant_id,
                pending_balance,
                settled_balance,
                version
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            merchant.getId(),
            new BigDecimal("-1.00"),
            BigDecimal.ZERO,
            0
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void balanceForUnknownMerchant_isRejectedByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO merchant_balances (
                id,
                merchant_id,
                pending_balance,
                settled_balance,
                version
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}