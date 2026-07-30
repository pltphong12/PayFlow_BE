package com.payflow.wallet.repository;

import com.payflow.wallet.entity.TopupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TopupRequestRepository extends JpaRepository<TopupRequest, UUID> {

    Optional<TopupRequest> findByIdempotencyKey(String idempotencyKey);
}
