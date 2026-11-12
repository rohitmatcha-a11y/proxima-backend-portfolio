package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = com.jobportal.domain.enums.OutboxEventStatus.PENDING AND e.retryCount < e.maxRetries ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = com.jobportal.domain.enums.OutboxEventStatus.FAILED ORDER BY e.createdAt DESC")
    List<OutboxEvent> findFailedEvents();
}
