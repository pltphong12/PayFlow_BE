package com.payflow.wallet.gateway;

import com.payflow.wallet.entity.TopupStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class FakePaymentGatewayClient {

    @Async
    public CompletableFuture<TopupStatus> processPayment() {
        try {
            long delayMillis = ThreadLocalRandom.current().nextLong(1_000, 2_001 );
            Thread.sleep(delayMillis);

            TopupStatus result = ThreadLocalRandom.current().nextBoolean()
                    ? TopupStatus.SUCCESS
                    : TopupStatus.FAILED;
            log.info(
                "Fake payment gateway completed after {} ms with result={}",
                delayMillis,
                result
            );
            return CompletableFuture.completedFuture(result);
        }catch (InterruptedException ex){
            Thread.currentThread().interrupt();
            log.warn("Fake payment gateway was interrupted");
            return CompletableFuture.completedFuture(TopupStatus.FAILED);
        }
    }
}
