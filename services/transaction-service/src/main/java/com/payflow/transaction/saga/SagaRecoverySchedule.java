package com.payflow.transaction.saga;

import com.payflow.transaction.entity.TransferTransaction;
import com.payflow.transaction.service.SagaRecoveryService;
import com.payflow.transaction.service.TransferSagaStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaRecoverySchedule {

    private final TransferSagaStateService sagaStateService;
    private final SagaRecoveryService sagaRecoveryService;

    @Scheduled(
        fixedDelayString = "${payflow.saga.recovery-interval-ms}"
    )
    public void recoverIncompleteSagas() {
        List<TransferTransaction> transactions =
            sagaStateService.findRecoverableTransactions();
        for (TransferTransaction transaction : transactions) {
            try {
                sagaRecoveryService.resume(transaction);
            } catch (Exception exception) {
                log.error(
                    "[SAGA] step=RECOVERY status=FAILED transactionId={}",
                    transaction.getId(),
                    exception
                );
            }
        }
    }
}
