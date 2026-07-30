package com.payflow.wallet.event;

import com.payflow.wallet.entity.TopupStatus;
import com.payflow.wallet.gateway.FakePaymentGatewayClient;
import com.payflow.wallet.service.TopupCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TopupPaymentStarter {

    private final FakePaymentGatewayClient fakePaymentGatewayClient;
    private final TopupCompletionService topupCompletionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTopupCreated(TopupCreated event) {
        log.info(
            "Starting fake payment for topupRequestId={}",
            event.topupRequestId()
        );
        fakePaymentGatewayClient.processPayment()
            .whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error(
                        "Fake payment gateway failed, topupRequestId={}",
                        event.topupRequestId(),
                        exception
                    );
                    topupCompletionService.completeTopup(
                        event.topupRequestId(),
                        TopupStatus.FAILED
                    );
                    return;
                }
                topupCompletionService.completeTopup(
                    event.topupRequestId(),
                    result
                );
            });
    }
}
