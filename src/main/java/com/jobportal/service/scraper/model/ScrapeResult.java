package com.jobportal.service.scraper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeResult {

    private String companySlug;
    private boolean success;
    private int jobsFound;
    private int jobsNew;
    private int jobsUpdated;
    private int jobsSkipped;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private long durationMs;
    private List<Long> newJobIds;

    public static ScrapeResult success(String companySlug, int found, int newJobs,
                                       int updated, int skipped, Instant start,
                                       List<Long> newJobIds) {
        Instant now = Instant.now();
        return ScrapeResult.builder()
                .companySlug(companySlug)
                .success(true)
                .jobsFound(found)
                .jobsNew(newJobs)
                .jobsUpdated(updated)
                .jobsSkipped(skipped)
                .startedAt(start)
                .completedAt(now)
                .durationMs(ChronoUnit.MILLIS.between(start, now))
                .newJobIds(newJobIds)
                .build();
    }

    public static ScrapeResult failure(String companySlug, String errorMessage, Instant start) {
        Instant now = Instant.now();
        return ScrapeResult.builder()
                .companySlug(companySlug)
                .success(false)
                .errorMessage(errorMessage)
                .startedAt(start)
                .completedAt(now)
                .durationMs(ChronoUnit.MILLIS.between(start, now))
                .build();
    }
    public static ScrapeResult skipped(String companySlug) {
        Instant now = Instant.now();
        return ScrapeResult.builder()
                .companySlug(companySlug)
                .success(true)
                .errorMessage("Skipped — scraper already running")
                .startedAt(now)
                .completedAt(now)
                .durationMs(0)
                .build();
    }
}