package com.payflow.transaction.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.transaction.TransactionServiceApplication;
import com.payflow.transaction.client.WalletServiceClient;
import com.payflow.transaction.client.dto.WalletLookupResponse;
import com.payflow.transaction.client.dto.WalletMutationResponse;
import com.payflow.transaction.dto.response.TransferResponse;
import com.payflow.transaction.entity.SagaStepName;
import com.payflow.transaction.entity.SagaStepStatus;
import com.payflow.transaction.entity.TransactionStatus;
import com.payflow.transaction.repository.OutboxEventRepository;
import com.payflow.transaction.repository.SagaStepRepository;
import com.payflow.transaction.repository.TransferTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest(
        classes = TransactionServiceApplication.class,
        properties = {
                "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
                "WALLET_SERVICE_URI=http://localhost:8082",
                "spring.task.scheduling.enabled=false",
                "payflow.outbox.poller-enabled=false"
        }
)
@Testcontainers
class SagaOrchestratorIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("payflow_transaction_db")
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
    SagaOrchestratorService sagaOrchestratorService;

    @Autowired
    TransferTransactionRepository transactionRepository;

    @Autowired
    SagaStepRepository sagaStepRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    TransferSagaStateService sagaStateService;

    @MockBean
    WalletServiceClient walletServiceClient;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        sagaStepRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void transfer_whenWalletOperationsSucceed_completesSaga() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10000");
        String idempotencyKey = UUID.randomUUID().toString();

        when(walletServiceClient.getWalletByUserId(senderUserId))
                .thenReturn(new WalletLookupResponse(
                        senderWalletId,
                        senderUserId,
                        new BigDecimal("50000"),
                        "VND",
                        "ACTIVE"
                ));
        when(walletServiceClient.getWalletByUserId(receiverUserId))
                .thenReturn(new WalletLookupResponse(
                        receiverWalletId,
                        receiverUserId,
                        BigDecimal.ZERO,
                        "VND",
                        "ACTIVE"
                ));
        when(walletServiceClient.debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                senderWalletId,
                new BigDecimal("40000"),
                1
        ));
        when(walletServiceClient.credit(
                eq(receiverWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                receiverWalletId,
                amount,
                1
        ));

        var response = sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );

        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);

        var transaction = transactionRepository.findById(response.id()).orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        var debitStep = sagaStepRepository.findByTransactionIdAndStepName(
                response.id(),
                SagaStepName.DEBIT_SENDER
        ).orElseThrow();
        var creditStep = sagaStepRepository.findByTransactionIdAndStepName(
                response.id(),
                SagaStepName.CREDIT_RECEIVER
        ).orElseThrow();

        assertThat(debitStep.getStatus()).isEqualTo(SagaStepStatus.SUCCESS);
        assertThat(creditStep.getStatus()).isEqualTo(SagaStepStatus.SUCCESS);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void transfer_whenSenderHasInsufficientBalance_marksSagaFailedWithoutCredit() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        when(walletServiceClient.debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenThrow(conflict("Insufficient wallet balance"));

        assertThatThrownBy(() -> sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        )).isInstanceOf(BusinessException.class);

        var transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(stepStatus(transaction.getId(), SagaStepName.DEBIT_SENDER))
                .isEqualTo(SagaStepStatus.FAILED);
        assertThat(stepStatus(transaction.getId(), SagaStepName.CREDIT_RECEIVER))
                .isEqualTo(SagaStepStatus.PENDING);
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        verify(walletServiceClient, times(0)).credit(
                eq(receiverWalletId),
                any(UUID.class),
                eq(amount)
        );
    }

    @Test
    void transfer_whenReceiverCreditFails_refundsSenderAndMarksSagaFailed() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );

        when(walletServiceClient.debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                senderWalletId,
                new BigDecimal("40000"),
                1
        ));

        when(walletServiceClient.credit(
                eq(receiverWalletId),
                any(UUID.class),
                eq(amount)
        )).thenThrow(conflict("Receiver wallet is frozen"));

        when(walletServiceClient.credit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                senderWalletId,
                new BigDecimal("50000"),
                2
        ));

        assertThatThrownBy(() -> sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        )).isInstanceOf(BusinessException.class);

        var transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(stepStatus(transaction.getId(), SagaStepName.DEBIT_SENDER))
                .isEqualTo(SagaStepStatus.COMPENSATED);
        assertThat(stepStatus(transaction.getId(), SagaStepName.CREDIT_RECEIVER))
                .isEqualTo(SagaStepStatus.FAILED);
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        verify(walletServiceClient).credit(
                eq(senderWalletId),
                eq(transaction.getId()),
                eq(amount)
        );
    }

    @Test
    void transfer_whenIdempotencyKeyIsReused_doesNotRunSagaAgain() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        stubSuccessfulMutations(
                senderWalletId,
                receiverWalletId,
                amount
        );

        var first = sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );
        var replay = sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(sagaStepRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        verify(walletServiceClient, times(1)).debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        );
        verify(walletServiceClient, times(1)).credit(
                eq(receiverWalletId),
                any(UUID.class),
                eq(amount)
        );
    }

    @Test
    void transfer_whenIdempotencyKeyIsReusedWithDifferentPayload_returnsConflict() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        stubSuccessfulMutations(
                senderWalletId,
                receiverWalletId,
                amount
        );

        var first = sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );

        assertThatThrownBy(() -> sagaOrchestratorService.transfer(
                senderUserId,
                UUID.randomUUID(),
                amount,
                idempotencyKey
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getStatus()
                ).isEqualTo(HttpStatus.CONFLICT));

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(first.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transfer_whenDebitTimesOut_marksSagaRecoverableAndReturns503() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        when(walletServiceClient.debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getStatus()
                ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        var transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(stepStatus(transaction.getId(), SagaStepName.DEBIT_SENDER))
                .isEqualTo(SagaStepStatus.PENDING);
        assertThat(outboxEventRepository.count()).isEqualTo(0);
    }

    @Test
    void transfer_whenCompensationTimesOut_keepsCompensatingAndReturns503() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        when(walletServiceClient.debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                senderWalletId,
                new BigDecimal("40000"),
                1
        ));
        when(walletServiceClient.credit(
                eq(receiverWalletId),
                any(UUID.class),
                eq(amount)
        )).thenThrow(conflict("Receiver wallet is frozen"));
        when(walletServiceClient.credit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getStatus()
                ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        var transaction = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPENSATING);
        assertThat(stepStatus(transaction.getId(), SagaStepName.DEBIT_SENDER))
                .isEqualTo(SagaStepStatus.SUCCESS);
        assertThat(stepStatus(transaction.getId(), SagaStepName.CREDIT_RECEIVER))
                .isEqualTo(SagaStepStatus.FAILED);
        assertThat(outboxEventRepository.count()).isEqualTo(0);
    }

    @Test
    void transfer_whenCalledConcurrentlyWithSameIdempotencyKey_createsOneTransaction() throws Exception {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        stubSuccessfulMutations(
                senderWalletId,
                receiverWalletId,
                amount
        );

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TransferResponse> first = executor.submit(() -> runConcurrentTransfer(
                    senderUserId,
                    receiverUserId,
                    amount,
                    idempotencyKey,
                    readyLatch,
                    startLatch
            ));
            Future<TransferResponse> second = executor.submit(() -> runConcurrentTransfer(
                    senderUserId,
                    receiverUserId,
                    amount,
                    idempotencyKey,
                    readyLatch,
                    startLatch
            ));
            readyLatch.await();
            startLatch.countDown();

            var response1 = first.get();
            var response2 = second.get();

            assertThat(response1.id()).isEqualTo(response2.id());
            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            verify(walletServiceClient, times(1)).debit(
                    eq(senderWalletId),
                    any(UUID.class),
                    eq(amount)
            );
            verify(walletServiceClient, times(1)).credit(
                    eq(receiverWalletId),
                    any(UUID.class),
                    eq(amount)
            );
        }
    }

    @Test
    void markCreditSuccess_whenAlreadyCompleted_doesNotCreateDuplicateOutbox() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        stubSuccessfulMutations(
                senderWalletId,
                receiverWalletId,
                amount
        );

        var response = sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        sagaStateService.markCreditSuccessAndComplete(response.id());

        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(
                transactionRepository.findById(response.id()).orElseThrow().getStatus()
        ).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transfer_whenSelfTransferRequested_rejectsWithBadRequest() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        assertThatThrownBy(() -> sagaOrchestratorService.transfer(
                userId,
                userId,
                new BigDecimal("10000"),
                idempotencyKey
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getStatus()
                ).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(transactionRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void getTransfer_whenRequesterIsNotParticipant_rejectsWithForbidden() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("10000");

        stubWalletLookups(
                senderUserId,
                receiverUserId,
                senderWalletId,
                receiverWalletId
        );
        stubSuccessfulMutations(
                senderWalletId,
                receiverWalletId,
                amount
        );
        var transfer = sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );

        assertThatThrownBy(() -> sagaOrchestratorService.getTransfer(
                UUID.randomUUID(),
                transfer.id()
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getStatus()
                ).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void stubWalletLookups(
            UUID senderUserId,
            UUID receiverUserId,
            UUID senderWalletId,
            UUID receiverWalletId
    ) {
        when(walletServiceClient.getWalletByUserId(senderUserId))
                .thenReturn(new WalletLookupResponse(
                        senderWalletId,
                        senderUserId,
                        new BigDecimal("50000"),
                        "VND",
                        "ACTIVE"
                ));
        when(walletServiceClient.getWalletByUserId(receiverUserId))
                .thenReturn(new WalletLookupResponse(
                        receiverWalletId,
                        receiverUserId,
                        BigDecimal.ZERO,
                        "VND",
                        "ACTIVE"
                ));
    }

    private void stubSuccessfulMutations(
            UUID senderWalletId,
            UUID receiverWalletId,
            BigDecimal amount
    ) {
        when(walletServiceClient.debit(
                eq(senderWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                senderWalletId,
                new BigDecimal("40000"),
                1
        ));
        when(walletServiceClient.credit(
                eq(receiverWalletId),
                any(UUID.class),
                eq(amount)
        )).thenReturn(new WalletMutationResponse(
                receiverWalletId,
                amount,
                1
        ));
    }

    private SagaStepStatus stepStatus(
            UUID transactionId,
            SagaStepName stepName
    ) {
        return sagaStepRepository.findByTransactionIdAndStepName(
                transactionId,
                stepName
        ).orElseThrow().getStatus();
    }

    private HttpClientErrorException conflict(String message) {
        return HttpClientErrorException.create(
                HttpStatus.CONFLICT,
                message,
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }

    private TransferResponse runConcurrentTransfer(
            UUID senderUserId,
            UUID receiverUserId,
            BigDecimal amount,
            String idempotencyKey,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        readyLatch.countDown();
        try {
            startLatch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
        return sagaOrchestratorService.transfer(
                senderUserId,
                receiverUserId,
                amount,
                idempotencyKey
        );
    }
}
