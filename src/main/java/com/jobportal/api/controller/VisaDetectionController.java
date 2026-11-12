package com.jobportal.api.controller;

import com.jobportal.api.dto.VisaDetectionRequest;
import com.jobportal.domain.enums.VisaStatus;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.service.enrichment.VisaDetectionResult;
import com.jobportal.service.enrichment.VisaDetectionService;
import com.jobportal.service.scraper.ScraperConstants;
import com.jobportal.kafka.producer.ScraperEventProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/visa")
@Tag(name = "Visa Detection", description = "AI-powered visa sponsorship detection")
@RequiredArgsConstructor
public class VisaDetectionController {

    private final VisaDetectionService visaDetectionService;
    private final JobRepository jobRepository;
    private final ScraperEventProducer scraperEventProducer;

    @PostMapping("/detect")
    @Operation(summary = "Detect visa sponsorship from a job description",
               description = "Calls Claude Haiku to analyze the provided job description. Use for testing.")
    public ResponseEntity<VisaDetectionResult> detect(@RequestBody VisaDetectionRequest request) {
        log.info("Manual visa detection requested");
        VisaDetectionResult result = visaDetectionService.detectVisa(
                request.getJobId(),
                request.getJobTitle(),
                request.getCompanyName(),
                request.getDescription()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reprocess/{jobId}")
    @Operation(summary = "Re-run visa detection for an existing job",
               description = "Fetches job from DB, runs visa detection, saves and returns the result.")
    public ResponseEntity<VisaDetectionResult> reprocess(@PathVariable Long jobId) {
        return jobRepository.findById(jobId).map(job -> {
            String companyName = job.getCompany() != null ? job.getCompany().getName() : null;

            VisaDetectionResult result = visaDetectionService.detectVisa(
                    job.getId(),
                    job.getTitle(),
                    companyName,
                    job.getDescription()
            );

            job.setVisaStatus(result.getVisaStatus());
            job.setVisaDetails(result.getReason());
            jobRepository.save(job);
            String companySlug = job.getCompany() != null ? job.getCompany().getSlug() : null;
            scraperEventProducer.publishJobIndexingRequested(job.getId(), companySlug, ScraperConstants.ACTION_VISA_UPDATE);

            log.info("Reprocessed visa detection for job {} — status: {}", jobId, result.getVisaStatus());
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    @Operation(summary = "Visa detection statistics",
               description = "Returns job counts grouped by visa status.")
    public ResponseEntity<Map<String, Long>> stats() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (VisaStatus status : VisaStatus.values()) {
            counts.put(status.name(), jobRepository.countByVisaStatus(status));
        }
        return ResponseEntity.ok(counts);
    }
}
