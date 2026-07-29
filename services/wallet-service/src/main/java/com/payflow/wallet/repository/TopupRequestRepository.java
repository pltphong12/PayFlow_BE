package com.payflow.wallet.repository;

import com.payflow.wallet.entity.TopupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopupRequestRepository extends JpaRepository<TopupRequest, Long> {

    Optional<TopupRequest> findByIdempotencyKey(String idempotencyKey);
}
