package com.jobportal.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.client.geocoding.GeoLocation;
import com.jobportal.client.geocoding.GeocodingClient;
import com.jobportal.domain.enums.ExperienceLevel;
import com.jobportal.domain.enums.WorkMode;
import com.jobportal.kafka.KafkaEventTypes;
import com.jobportal.kafka.KafkaMessage;
import com.jobportal.kafka.KafkaTopics;
import com.jobportal.kafka.event.JobGeocodingRequestedEvent;
import com.jobportal.kafka.event.JobIndexingRequestedEvent;
import com.jobportal.repository.jpa.JobRepository;
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
public class GeocodingConsumer {

    private final JobRepository jobRepository;
    private final IdempotencyService idempotencyService;
    private final GeocodingClient geocodingClient;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.JOB_GEOCODING_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onGeocodingRequested(String message) {
        try {
            KafkaMessage<JobGeocodingRequestedEvent> kafkaMessage = objectMapper.readValue(
                    message, new TypeReference<>() {});
            JobGeocodingRequestedEvent event = kafkaMessage.getPayload();

            String aggregateId = String.valueOf(event.getJobId());
            if (idempotencyService.isAlreadyProcessed(aggregateId, KafkaEventTypes.JOB_GEOCODING)) return;

            jobRepository.findByIdWithCompany(event.getJobId()).ifPresentOrElse(job -> {
                // Resolve location
                GeoLocation geoLocation = geocodingClient.resolveLocation(event.getLocationText());
                job.setCity(geoLocation.city());
                job.setCountry(geoLocation.country());
                job.setContinent(geoLocation.continent());

                // Resolve workMode if not already set by scraper
                if (job.getWorkMode() == null) {
                    resolveWorkMode(event.getTitle(), event.getLocationText())
                        .ifPresent(job::setWorkMode);
                }

                // Resolve experienceLevel if not already set by scraper
                if (job.getExperienceLevel() == null) {
                    job.setExperienceLevel(resolveExperienceLevel(event.getTitle()));
                }

                jobRepository.save(job);

                log.info("[GeocodingConsumer] Job {} resolved — city={} country={} continent={}",
                        job.getId(), geoLocation.city(), geoLocation.country(), geoLocation.continent());

                // Publish re-index event via outbox — keeps ES in sync with DB
                String companySlug = job.getCompany() != null
                        ? job.getCompany().getSlug() : event.getCompanySlug();
                outboxEventService.saveEvent(
                    aggregateId,
                    "JOB_INDEXING_REQUESTED_GEO_UPDATE",
                    KafkaTopics.JOB_INDEXING_REQUESTED,
                    JobIndexingRequestedEvent.builder()
                        .jobId(job.getId())
                        .companySlug(companySlug)
                        .action(ScraperConstants.ACTION_GEO_UPDATE)
                        .build()
                );

            }, () -> log.warn("[GeocodingConsumer] Job {} not found", event.getJobId()));

        } catch (Exception e) {
            log.error("[GeocodingConsumer] Failed to process message: {}", SafeErrorSummary.from(e));
        }
    }

    private java.util.Optional<WorkMode> resolveWorkMode(String title, String locationText) {
        String combined = (title + " " + (locationText != null ? locationText : "")).toLowerCase();
        if (combined.contains("remote"))  return java.util.Optional.of(WorkMode.REMOTE);
        if (combined.contains("hybrid"))  return java.util.Optional.of(WorkMode.HYBRID);
        if (combined.contains("onsite") || combined.contains("on-site")
                || combined.contains("in-office") || combined.contains("in office"))
            return java.util.Optional.of(WorkMode.ONSITE);
        return java.util.Optional.empty();
    }

    private ExperienceLevel resolveExperienceLevel(String title) {
        if (title == null) return null;
        String t = title.toLowerCase();
        if (t.contains("manager") || t.contains("director") ||
                t.contains("head of") || t.contains("vp ") ||
                t.contains("vice president")) return ExperienceLevel.LEAD;
        if (t.contains("senior") || t.contains("staff") ||
                t.contains("lead") || t.contains("principal")) return ExperienceLevel.SENIOR;
        if (t.contains("junior") || t.contains("associate") ||
                t.contains("entry")) return ExperienceLevel.JUNIOR;
        if (t.contains("intern")) return ExperienceLevel.INTERN;
        return null;
    }
}
