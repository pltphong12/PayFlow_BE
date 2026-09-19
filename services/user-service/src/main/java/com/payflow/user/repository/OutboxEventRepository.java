package com.payflow.user.repository;

import com.payflow.user.entity.OutboxEvent;
import com.payflow.user.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(
        OutboxEventStatus status
    );
}
