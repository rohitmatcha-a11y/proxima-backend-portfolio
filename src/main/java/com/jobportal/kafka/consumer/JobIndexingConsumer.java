package com.jobportal.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import jakarta.annotation.PostConstruct;
import com.jobportal.domain.document.JobDocument;
import com.jobportal.domain.entity.Job;
import com.jobportal.kafka.KafkaMessage;
import com.jobportal.kafka.KafkaEventTypes;
import com.jobportal.kafka.KafkaTopics;
import com.jobportal.service.scraper.ScraperConstants;
import com.jobportal.kafka.event.JobIndexingRequestedEvent;
import com.jobportal.repository.elastic.JobSearchRepository;
import com.jobportal.service.scraper.mapper.JobDocumentMapper;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.service.idempotency.IdempotencyService;
import com.jobportal.util.SafeErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class JobIndexingConsumer {

    private final JobRepository jobRepository;
    private final JobSearchRepository jobSearchRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations esOps;
    private final JobDocumentMapper jobDocumentMapper;

    @PostConstruct
    public void ensureIndex() {
        IndexOperations indexOps = esOps.indexOps(com.jobportal.domain.document.JobDocument.class);
        if (!indexOps.exists()) {
            log.info("ES index missing — creating with correct mapping...");
            indexOps.createWithMapping();
        }
        log.info("ES index ready");
    }

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.JOB_INDEXING_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onJobIndexingRequested(String message) {
        try {
            KafkaMessage<JobIndexingRequestedEvent> kafkaMessage = objectMapper.readValue(
                    message, new TypeReference<>() {});
            JobIndexingRequestedEvent event = kafkaMessage.getPayload();

            String aggregateId = String.valueOf(event.getJobId());
            String action = event.getAction() != null ? event.getAction() : ScraperConstants.ACTION_INDEX;
            String eventType = "JOB_INDEXING_REQUESTED_" + action;
            if (idempotencyService.isAlreadyProcessed(aggregateId, eventType)) return;

            jobRepository.findByIdWithCompany(event.getJobId()).ifPresentOrElse(job -> {
                if (ScraperConstants.ACTION_DELETE.equals(event.getAction())) {
                    jobSearchRepository.deleteById(String.valueOf(job.getId()));
                    log.info("Deleted job {} from index", job.getId());
                } else {
                    JobDocument doc = jobDocumentMapper.toDocument(job);
                    jobSearchRepository.save(doc);
                    log.info("Indexed job {} ({})", job.getId(), job.getTitle());
                }
            }, () -> log.warn("Job {} not found for indexing", event.getJobId()));

        } catch (Exception e) {
            log.error("Error processing job indexing message: {}", SafeErrorSummary.from(e));
        }
    }

}
