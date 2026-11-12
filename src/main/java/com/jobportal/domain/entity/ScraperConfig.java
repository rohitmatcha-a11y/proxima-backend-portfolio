package com.jobportal.domain.entity;

import com.jobportal.domain.enums.ScraperStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scraper_configs")
public class ScraperConfig extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "company_id")
    private Company company;

    private String scraperClass;

    private String cronExpression;

    @Builder.Default
    private boolean isEnabled = true;

    @Enumerated(EnumType.STRING)
    private ScraperStatus status;

    private Instant lastRunAt;

    private Instant lastSuccessAt;

    private Instant lastErrorAt;

    @Column(columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Builder.Default
    private int totalRuns = 0;

    @Builder.Default
    private int successfulRuns = 0;

    @Builder.Default
    private int failedRuns = 0;

    @Builder.Default
    private int avgJobsPerRun = 0;

    @Builder.Default
    private int rateLimit = 2000;

    @Column(columnDefinition = "TEXT")
    private String customConfig;
}
