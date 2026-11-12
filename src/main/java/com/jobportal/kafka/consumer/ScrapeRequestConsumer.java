package com.jobportal.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.kafka.KafkaMessage;
import com.jobportal.kafka.KafkaTopics;
import com.jobportal.kafka.event.ScrapeCompletedEvent;
import com.jobportal.kafka.event.ScrapeRequestedEvent;
import com.jobportal.kafka.producer.ScraperEventProducer;
import com.jobportal.service.scraper.ScraperOrchestrator;
import com.jobportal.service.scraper.model.ScrapeResult;
import com.jobportal.util.SafeErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeRequestConsumer {

    private final ScraperOrchestrator scraperOrchestrator;
    private final ScraperEventProducer scraperEventProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.SCRAPE_JOB_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onScrapeRequested(String message) {
        try {
            KafkaMessage<ScrapeRequestedEvent> kafkaMessage = objectMapper.readValue(
                    message, new TypeReference<>() {});
            ScrapeRequestedEvent event = kafkaMessage.getPayload();
            String companySlug = event.getCompanySlug();

            log.info("Received scrape request for company: {}", companySlug);

            ScrapeResult result = scraperOrchestrator.runScraper(companySlug);

            ScrapeCompletedEvent completed = ScrapeCompletedEvent.builder()
                    .companySlug(companySlug)
                    .success(result.isSuccess())
                    .jobsFound(result.getJobsFound())
                    .jobsNew(result.getJobsNew())
                    .jobsUpdated(result.getJobsUpdated())
                    .errorMessage(result.getErrorMessage())
                    .completedAt(result.getCompletedAt())
                    .newJobIds(result.getNewJobIds())
                    .build();

            scraperEventProducer.publishScrapeCompleted(completed);

            // Visa detection is handled by BaseScraper via outbox — no need to publish here

        } catch (Exception e) {
            log.error("Error processing scrape request message: {}", SafeErrorSummary.from(e));
        }
    }
}
