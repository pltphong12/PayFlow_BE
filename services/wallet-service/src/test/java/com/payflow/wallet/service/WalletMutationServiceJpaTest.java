package com.payflow.wallet.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.WalletServiceApplication;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = WalletServiceApplication.class,
        properties = {
                "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
                "spring.kafka.listener.auto-startup=false",
                "spring.task.scheduling.enabled=false",
                "payflow.outbox.poller-enabled=false"
        }
)
@Testcontainers
class WalletMutationServiceJpaTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("payflow_wallet_db")
                    .withUsername("payflow_user")
                    .withPassword("payflow_password");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", postgres::getHost);
        registry.add("POSTGRES_PORT", () -> postgres.getMappedPort(5432));
        registry.add("POSTGRES_DB", postgres::getDatabaseName);
        registry.add("POSTGRES_USER", postgres::getUsername);
        registry.add("POSTGRES_PASSWORD", postgres::getPassword);
    }

    @Autowired
    WalletService walletService;

    @Autowired
    WalletMutationService walletMutationService;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @BeforeEach
    void cleanDatabase() {
        ledgerEntryRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void credit_withSameTransactionAndEntryType_appliesOnlyOnce() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100000");

        walletService.createWalletIfAbsent(userId);
        UUID walletId = walletRepository.findByUserId(userId).orElseThrow().getId();

        walletMutationService.credit(walletId, transactionId, amount);
        walletMutationService.credit(walletId, transactionId, amount);

        var wallet = walletRepository.findById(walletId).orElseThrow();

        assertThat(wallet.getBalance()).isEqualByComparingTo(amount);
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentDebits_neverProduceNegativeBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID fundingTransactionId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("100000");
        BigDecimal debitAmount = new BigDecimal("70000");

        walletService.createWalletIfAbsent(userId);
        UUID walletId = walletRepository.findByUserId(userId).orElseThrow().getId();
        walletMutationService.credit(
                walletId,
                fundingTransactionId,
                initialBalance
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Throwable>> debitTasks = List.of(
                    () -> debitAndCaptureException(
                            walletId,
                            UUID.randomUUID(),
                            debitAmount
                    ),
                    () -> debitAndCaptureException(
                            walletId,
                            UUID.randomUUID(),
                            debitAmount
                    )
            );

            List<Future<Throwable>> results = executor.invokeAll(debitTasks);
            long successfulDebits = results.stream()
                    .filter(this::completedWithoutException)
                    .count();
            long insufficientBalanceFailures = results.stream()
                    .map(this::getException)
                    .filter(BusinessException.class::isInstance)
                    .count();

            var wallet = walletRepository.findById(walletId).orElseThrow();

            assertThat(successfulDebits).isEqualTo(1);
            assertThat(insufficientBalanceFailures).isEqualTo(1);
            assertThat(wallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("30000"));
            assertThat(wallet.getBalance().signum()).isGreaterThanOrEqualTo(0);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable debitAndCaptureException(
            UUID walletId,
            UUID transactionId,
            BigDecimal amount
    ) {
        try {
            walletMutationService.debit(walletId, transactionId, amount);
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private boolean completedWithoutException(Future<Throwable> future) {
        return getException(future) == null;
    }

    private Throwable getException(Future<Throwable> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for debit", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Debit task failed unexpectedly", exception);
        }
    }
}
