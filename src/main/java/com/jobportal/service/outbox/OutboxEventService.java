package com.jobportal.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.domain.entity.OutboxEvent;
import com.jobportal.kafka.KafkaMessage;
import com.jobportal.domain.enums.OutboxEventStatus;
import com.jobportal.repository.jpa.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jobportal.util.SafeErrorSummary;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    @Value("${outbox.max-retries:3}")
    private int maxRetries;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveEvent(String aggregateId, String eventType, String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(KafkaMessage.of(topic, payload));
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(json)
                    .status(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(maxRetries)
                    .createdAt(Instant.now())
                    .build();
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to save outbox event for aggregateId={}: {}", aggregateId, SafeErrorSummary.from(e));
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval-ms:5000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        log.debug("Processing {} pending outbox events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setProcessedAt(Instant.now());
                log.debug("Published outbox event id={} type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                String errorSummary = SafeErrorSummary.from(e);
                event.setErrorMessage(errorSummary);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus(OutboxEventStatus.FAILED);
                    log.error("Outbox event id={} type={} FAILED after {} retries: {}",
                            event.getId(), event.getEventType(), event.getMaxRetries(), errorSummary);
                } else {
                    log.warn("Outbox event id={} type={} retry {}/{}: {}",
                            event.getId(), event.getEventType(),
                            event.getRetryCount(), event.getMaxRetries(), errorSummary);
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
