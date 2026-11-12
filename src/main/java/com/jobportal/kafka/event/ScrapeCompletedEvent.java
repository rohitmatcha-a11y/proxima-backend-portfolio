package com.jobportal.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeCompletedEvent {

    private String companySlug;
    private boolean success;
    private int jobsFound;
    private int jobsNew;
    private int jobsUpdated;
    private String errorMessage;
    private Instant completedAt;
    private List<Long> newJobIds;
}
