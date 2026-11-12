package com.jobportal.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ScraperStatusResponse {
    private String slug;
    private String companyName;
    private boolean enabled;
    private Instant lastRunAt;
    private String lastRunStatus;
    private int lastJobsFound;
    private String nextScheduledAt;
}
