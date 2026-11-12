package com.jobportal.service.idempotency;

import com.jobportal.domain.entity.ProcessedEvent;
import com.jobportal.repository.jpa.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isAlreadyProcessed(String aggregateId, String eventType) {
        if (processedEventRepository.existsByAggregateIdAndEventType(aggregateId, eventType)) {
            log.debug("Duplicate event skipped: aggregateId={} eventType={}", aggregateId, eventType);
            return true;
        }
        try {
            processedEventRepository.save(ProcessedEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .processedAt(Instant.now())
                    .build());
            return false;
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent duplicate skipped: aggregateId={} eventType={}", aggregateId, eventType);
            return true;
        }
    }
}
