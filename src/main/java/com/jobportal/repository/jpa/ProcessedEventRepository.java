package com.jobportal.repository.jpa;

import com.jobportal.domain.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByAggregateIdAndEventType(String aggregateId, String eventType);
}
