package com.payflow.transaction.repository;

import com.payflow.transaction.entity.TransactionStatus;
import com.payflow.transaction.entity.TransferTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferTransactionRepository extends JpaRepository<TransferTransaction, UUID> {

    Optional<TransferTransaction> findByIdempotencyKey(String idempotencyKey);

    List<TransferTransaction> findTop100ByStatusInOrderByCreatedAtAsc(
        Collection<TransactionStatus> statuses
    );
}
