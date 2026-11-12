package com.jobportal.domain.entity;

import com.jobportal.domain.enums.ScraperRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scraper_runs")
public class ScraperRun extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    private ScraperRunStatus status;

    private String triggeredBy;

    private Instant startedAt;

    private Instant completedAt;

    @Builder.Default
    private int jobsFound = 0;

    @Builder.Default
    private int jobsNew = 0;

    @Builder.Default
    private int jobsSkipped = 0;

    @Builder.Default
    private int jobsFailed = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    private int retryCount = 0;

    private String parentRunId;
}
