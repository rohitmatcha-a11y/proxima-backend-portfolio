package com.jobportal.api.controller;

import com.jobportal.api.dto.ScraperStatusResponse;
import com.jobportal.api.dto.TriggerResponse;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.entity.ScraperRun;
import com.jobportal.repository.jpa.ScraperConfigRepository;
import com.jobportal.repository.jpa.ScraperRunRepository;
import com.jobportal.service.scraper.ScraperOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import com.jobportal.domain.entity.Job;
import com.jobportal.domain.document.JobDocument;
import com.jobportal.repository.elastic.JobSearchRepository;
import com.jobportal.service.scraper.mapper.JobDocumentMapper;
import com.jobportal.repository.jpa.JobRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/scrapers")
@RequiredArgsConstructor
@Tag(name = "Scraper", description = "Job scraper management")
public class ScraperController {

    @Value("${app.scraper.reindex-batch-size:500}")
    private int reindexBatchSize;

    private final ScraperOrchestrator orchestrator;
    private final ScraperConfigRepository scraperConfigRepository;
    private final ScraperRunRepository scraperRunRepository;
    private final JobRepository jobRepository;
    private final JobSearchRepository jobSearchRepository;
    private final JobDocumentMapper jobDocumentMapper;

    @GetMapping
    @Operation(summary = "List all scrapers with current status")
    public ResponseEntity<List<ScraperStatusResponse>> listScrapers() {
        List<ScraperConfig> configs = scraperConfigRepository.findAll();
        List<ScraperStatusResponse> response = configs.stream()
            .map(config -> {
                Optional<ScraperRun> latest = scraperRunRepository
                    .findTopByCompanyIdOrderByCreatedAtDesc(
                        config.getCompany().getId());
                ScraperStatusResponse status = ScraperStatusResponse.builder()
                    .slug(config.getCompany().getSlug())
                    .companyName(config.getCompany().getName())
                    .enabled(config.isEnabled())
                    .lastRunAt(latest.map(ScraperRun::getStartedAt).orElse(null))
                    .lastRunStatus(latest.map(r -> r.getStatus().name()).orElse("NEVER_RUN"))
                    .lastJobsFound(latest.map(ScraperRun::getJobsFound).orElse(0))
                    .nextScheduledAt("Every 6 hours (00:00, 06:00, 12:00, 18:00)")
                    .build();
                return status;
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{slug}/trigger")
    @Operation(summary = "Trigger one scraper asynchronously")
    public ResponseEntity<TriggerResponse> triggerOne(@PathVariable String slug) {
        log.info("Triggering scraper for '{}'", slug);
        String runId = orchestrator.triggerAsync(slug);
        return ResponseEntity.accepted().body(
            TriggerResponse.builder()
                .slug(slug)
                .runId(runId)
                .status("TRIGGERED")
                .message("Scraper started asynchronously")
                .triggeredAt(Instant.now())
                .build()
        );
    }

    @PostMapping("/{slug}/stop")
    @Operation(summary = "Stop a currently running scraper")
    public ResponseEntity<Map<String, Object>> stopOne(@PathVariable String slug) {
        ScraperOrchestrator.StopResult result = orchestrator.stopScraper(slug);
        return ResponseEntity.ok(Map.of(
                "slug", result.slug(),
                "stopped", result.stopped(),
                "interruptedRuns", result.interruptedRuns(),
                "message", result.message()
        ));
    }

    @PostMapping("/trigger-all")
    @Operation(summary = "Trigger all enabled scrapers asynchronously")
    public ResponseEntity<Map<String, String>> triggerAll() {
        log.info("Triggering all enabled scrapers");
        Map<String, String> runIds = orchestrator.triggerAllAsync();
        return ResponseEntity.accepted().body(runIds);
    }

    @GetMapping("/{slug}/runs")
    @Operation(summary = "List recent runs for a scraper")
    public ResponseEntity<List<ScraperRun>> getRuns(
            @PathVariable String slug,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
            scraperRunRepository.findByCompanySlugOrderByCreatedAtDesc(
                slug, PageRequest.of(0, limit))
        );
    }

    @GetMapping("/{slug}/runs/latest")
    @Operation(summary = "Get the latest run for a scraper")
    public ResponseEntity<ScraperRun> getLatestRun(@PathVariable String slug) {
        return scraperRunRepository
            .findTopByCompanySlugOrderByCreatedAtDesc(slug, PageRequest.of(0, 1))
            .stream().findFirst()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get a specific scraper run by run ID")
    public ResponseEntity<ScraperRun> getRunById(@PathVariable String runId) {
        return scraperRunRepository.findByRunId(runId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{slug}/reset")
    @Operation(summary = "Reset circuit breaker for a scraper")
    public ResponseEntity<Map<String, String>> reset(@PathVariable String slug) {
        orchestrator.resetCircuitBreaker(slug);
        return ResponseEntity.ok(Map.of(
            "slug", slug,
            "message", "Circuit breaker reset successfully"
        ));
    }

    @PostMapping("/reindex-all")
    @Operation(summary = "Reindex all jobs from DB into Elasticsearch")
    public ResponseEntity<Map<String, Object>> reindexAll() {
        long total = jobRepository.count();
        int pageSize = reindexBatchSize;
        int pages = (int) Math.ceil((double) total / pageSize);
        long indexed = 0;

        for (int i = 0; i < pages; i++) {
            List<Job> jobs = jobRepository.findAll(PageRequest.of(i, pageSize)).getContent();
            List<JobDocument> docs = jobs.stream().map(jobDocumentMapper::toDocument).toList();
            jobSearchRepository.saveAll(docs);
            indexed += docs.size();
        }

        return ResponseEntity.ok(Map.of("indexed", indexed, "total", total));
    }

    @PostMapping("/cleanup-stuck-runs")
    @Operation(summary = "Manually trigger stuck run cleanup",
               description = "Marks any RUNNING scraper runs that have exceeded the timeout as INTERRUPTED. Normally runs automatically every 5 minutes.")
    public ResponseEntity<Map<String, Object>> cleanupStuckRuns() {
        log.info("Manual cleanup of stuck runs triggered via admin API");
        long before = scraperRunRepository.countByStatus(com.jobportal.domain.enums.ScraperRunStatus.RUNNING);
        orchestrator.cleanupStuckRuns();
        long after = scraperRunRepository.countByStatus(com.jobportal.domain.enums.ScraperRunStatus.RUNNING);
        long cleaned = before - after;
        return ResponseEntity.ok(Map.of(
            "cleaned", cleaned,
            "message", cleaned > 0
                ? cleaned + " stuck run(s) marked INTERRUPTED"
                : "No stuck runs found"
        ));
    }
}
