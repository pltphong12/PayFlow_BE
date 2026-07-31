package com.payflow.wallet.service;

import com.payflow.wallet.dto.request.CreateTopupRequest;
import com.payflow.wallet.entity.TopupRequest;
import com.payflow.wallet.entity.TopupStatus;
import com.payflow.wallet.gateway.FakePaymentGatewayClient;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.OutboxEventRepository;
import com.payflow.wallet.repository.TopupRequestRepository;
import com.payflow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        classes = com.payflow.wallet.WalletServiceApplication.class,
        properties = {
                "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
                "spring.kafka.listener.auto-startup=false",
                "spring.task.scheduling.enabled=false",
                "payflow.outbox.poller-enabled=false"
        }
)
@Testcontainers
class TopupFlowJpaTest {

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
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", postgres::getHost);
        registry.add("POSTGRES_PORT", () -> postgres.getMappedPort(5432));
        registry.add("POSTGRES_DB", postgres::getDatabaseName);
        registry.add("POSTGRES_USER", postgres::getUsername);
        registry.add("POSTGRES_PASSWORD", postgres::getPassword);
    }

    @Autowired
    TopupService topupService;

    @Autowired
    TopupCompletionService topupCompletionService;

    @Autowired
    WalletService walletService;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    TopupRequestRepository topupRequestRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @MockBean
    FakePaymentGatewayClient fakePaymentGatewayClient;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        outboxEventRepository.deleteAll();
        topupRequestRepository.deleteAll();
        walletRepository.deleteAll();

        when(fakePaymentGatewayClient.processPayment())
                .thenReturn(new CompletableFuture<>());
    }

    @Test
    void createTopup_withSameIdempotencyKey_createsOnlyOneRequest() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        var firstResponse = topupService.createTopup(
                userId,
                idempotencyKey,
                new CreateTopupRequest(new BigDecimal("100000"))
        );

        var retryResponse = topupService.createTopup(
                userId,
                idempotencyKey,
                new CreateTopupRequest(new BigDecimal("200000"))
        );

        assertThat(retryResponse.id()).isEqualTo(firstResponse.id());
        assertThat(retryResponse.amount())
                .isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(topupRequestRepository.count()).isEqualTo(1);
        assertThat(firstResponse.status()).isEqualTo(TopupStatus.PENDING);

        verify(fakePaymentGatewayClient, times(1)).processPayment();
    }

    @Test
    void completeTopup_successCallbackTwice_creditsWalletOnlyOnce() {
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100000");

        walletService.createWalletIfAbsent(userId);

        TopupRequest topupRequest = topupRequestRepository.save(
                new TopupRequest(
                        userId,
                        amount,
                        UUID.randomUUID().toString()
                )
        );

        topupCompletionService.completeTopup(
                topupRequest.getId(),
                TopupStatus.SUCCESS
        );

        topupCompletionService.completeTopup(
                topupRequest.getId(),
                TopupStatus.SUCCESS
        );

        var wallet = walletRepository.findByUserId(userId).orElseThrow();
        var savedTopupRequest = topupRequestRepository
                .findById(topupRequest.getId())
                .orElseThrow();

        assertThat(savedTopupRequest.getStatus())
                .isEqualTo(TopupStatus.SUCCESS);
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(amount);
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void completeTopup_failedCallback_doesNotChangeWalletOrCreateEvents() {
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100000");

        walletService.createWalletIfAbsent(userId);

        TopupRequest topupRequest = topupRequestRepository.save(
                new TopupRequest(
                        userId,
                        amount,
                        UUID.randomUUID().toString()
                )
        );

        topupCompletionService.completeTopup(
                topupRequest.getId(),
                TopupStatus.FAILED
        );

        var wallet = walletRepository.findByUserId(userId).orElseThrow();
        var savedTopupRequest = topupRequestRepository
                .findById(topupRequest.getId())
                .orElseThrow();

        assertThat(savedTopupRequest.getStatus())
                .isEqualTo(TopupStatus.FAILED);
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ledgerEntryRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }
}
