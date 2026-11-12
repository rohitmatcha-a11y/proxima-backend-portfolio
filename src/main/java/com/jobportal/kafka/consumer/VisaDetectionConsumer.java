package com.jobportal.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.kafka.KafkaEventTypes;
import com.jobportal.kafka.KafkaMessage;
import com.jobportal.kafka.KafkaTopics;
import com.jobportal.kafka.event.JobIndexingRequestedEvent;
import com.jobportal.kafka.event.VisaDetectionRequestedEvent;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.service.enrichment.VisaDetectionResult;
import com.jobportal.service.enrichment.VisaDetectionService;
import com.jobportal.service.idempotency.IdempotencyService;
import com.jobportal.service.outbox.OutboxEventService;
import com.jobportal.service.scraper.ScraperConstants;
import com.jobportal.util.SafeErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisaDetectionConsumer {

    private final JobRepository jobRepository;
    private final IdempotencyService idempotencyService;
    private final VisaDetectionService visaDetectionService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.JOB_VISA_DETECTION_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onVisaDetectionRequested(String message) {
        try {
            KafkaMessage<VisaDetectionRequestedEvent> kafkaMessage = objectMapper.readValue(
                    message, new TypeReference<>() {});
            VisaDetectionRequestedEvent event = kafkaMessage.getPayload();

            String aggregateId = String.valueOf(event.getJobId());
            if (idempotencyService.isAlreadyProcessed(aggregateId, KafkaEventTypes.JOB_VISA_DETECTION)) return;

            jobRepository.findByIdWithCompany(event.getJobId()).ifPresentOrElse(job -> {
                String companyName = job.getCompany() != null ? job.getCompany().getName() : null;
                String companySlug = job.getCompany() != null
                        ? job.getCompany().getSlug() : event.getCompanySlug();

                VisaDetectionResult result = visaDetectionService.detectVisa(
                        job.getId(),
                        job.getTitle(),
                        companyName,
                        job.getDescription()
                );

                job.setVisaStatus(result.getVisaStatus());
                job.setVisaDetails(result.getReason());
                jobRepository.save(job);

                log.info("[VisaDetectionConsumer] Job {} visa={} confidence={}",
                        job.getId(), result.getVisaStatus(), result.getConfidence());

                // Publish re-index event via outbox — keeps ES in sync with DB
                outboxEventService.saveEvent(
                    aggregateId,
                    "JOB_INDEXING_REQUESTED_VISA_UPDATE",
                    KafkaTopics.JOB_INDEXING_REQUESTED,
                    JobIndexingRequestedEvent.builder()
                        .jobId(job.getId())
                        .companySlug(companySlug)
                        .action(ScraperConstants.ACTION_VISA_UPDATE)
                        .build()
                );

            }, () -> log.warn("[VisaDetectionConsumer] Job {} not found", event.getJobId()));

        } catch (Exception e) {
            log.error("[VisaDetectionConsumer] Failed to process message: {}", SafeErrorSummary.from(e));
        }
    }
}
