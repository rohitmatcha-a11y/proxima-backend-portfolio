package com.jobportal.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchRequest {

    private String keyword;

    // Single company (backward compat)
    private String companySlug;
    // Multiple companies
    private List<String> companySlugs;

    // Single location (backward compat)
    private String country;
    private String continent;
    private String city;
    // Multiple locations
    private List<String> countries;
    private List<String> continents;

    private List<String> jobType;
    private List<String> workMode;
    private List<String> experienceLevel;
    private List<String> industry;
    private List<String> visaStatus;
    private List<String> companySizeCategory;

    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryCurrency;

    private Boolean remoteOnly;
    private Boolean visaSponsorshipOnly;

    // Date posted filter: 24h, 3d, 7d, 30d, or custom
    private String datePosted;
    private Instant postedAfter;
    private Instant postedBefore;

    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;

    @Builder.Default
    private String sortBy = "postedAt";
    @Builder.Default
    private String sortDir = "desc";
}
