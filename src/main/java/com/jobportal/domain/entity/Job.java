package com.jobportal.domain.entity;

import com.jobportal.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "jobs",
    indexes = {
        @Index(name = "idx_jobs_country",     columnList = "country"),
        @Index(name = "idx_jobs_status",      columnList = "status"),
        @Index(name = "idx_jobs_visa_status", columnList = "visaStatus"),
        @Index(name = "idx_jobs_job_type",    columnList = "jobType"),
        @Index(name = "idx_jobs_work_mode",   columnList = "workMode")
    }
)
public class Job extends BaseEntity {

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    @Enumerated(EnumType.STRING)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    private Industry industry;

    private String country;

    private String city;

    private String continent;

    @Column(name = "location_text", length = 500)
    private String locationText;

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private String salaryCurrency;

    private String salaryPeriod;

    @Enumerated(EnumType.STRING)
    private VisaStatus visaStatus;

    private String visaDetails;

    private String applyUrl;

    private String sourceUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.ACTIVE;

    private Instant postedAt;

    private Instant expiresAt;

    private boolean isRemoteFriendly;

    private String tags;

    private String scraperVersion;

    private String scraperRunId;
}
