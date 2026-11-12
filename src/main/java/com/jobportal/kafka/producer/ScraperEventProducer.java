package com.jobportal.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.kafka.KafkaMessage;
import com.jobportal.kafka.KafkaTopics;
import com.jobportal.kafka.event.JobIndexingRequestedEvent;
import com.jobportal.kafka.event.ScrapeCompletedEvent;
import com.jobportal.kafka.event.ScrapeRequestedEvent;
import com.jobportal.kafka.event.VisaDetectionRequestedEvent;
import com.jobportal.util.SafeErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishScrapeRequested(String companySlug, String triggeredBy) {
        ScrapeRequestedEvent event = ScrapeRequestedEvent.builder()
                .companySlug(companySlug)
                .triggeredBy(triggeredBy)
                .requestedAt(Instant.now())
                .build();

        send(KafkaTopics.SCRAPE_JOB_REQUESTED, companySlug,
                KafkaMessage.of(KafkaTopics.SCRAPE_JOB_REQUESTED, event));
    }

    public void publishScrapeCompleted(ScrapeCompletedEvent event) {
        send(KafkaTopics.SCRAPE_JOB_COMPLETED, event.getCompanySlug(),
                KafkaMessage.of(KafkaTopics.SCRAPE_JOB_COMPLETED, event));
    }

    public void publishVisaDetectionRequested(Long jobId, String title,
                                              String companySlug, String description) {
        VisaDetectionRequestedEvent event = VisaDetectionRequestedEvent.builder()
                .jobId(jobId)
                .jobTitle(title)
                .companySlug(companySlug)
                .jobDescription(description)
                .build();

        send(KafkaTopics.JOB_VISA_DETECTION_REQUESTED, companySlug,
                KafkaMessage.of(KafkaTopics.JOB_VISA_DETECTION_REQUESTED, event));
    }

    public void publishJobIndexingRequested(Long jobId, String companySlug, String action) {
        JobIndexingRequestedEvent event = JobIndexingRequestedEvent.builder()
                .jobId(jobId)
                .companySlug(companySlug)
                .action(action)
                .build();

        send(KafkaTopics.JOB_INDEXING_REQUESTED, companySlug,
                KafkaMessage.of(KafkaTopics.JOB_INDEXING_REQUESTED, event));
    }

    private <T> void send(String topic, String key, KafkaMessage<T> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka publish failed for topic='{}' key='{}': {}", topic, key, SafeErrorSummary.from(ex));
                        } else {
                            log.debug("Sent message to topic='{}' key='{}'", topic, key);
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for topic '{}': {}", topic, SafeErrorSummary.from(e));
        }
    }
}
