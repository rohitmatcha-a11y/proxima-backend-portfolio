package com.jobportal.service.scraper.model;

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
public class ScrapedJob {

    private String externalId;
    private String title;
    private String description;
    private String requirements;
    private String responsibilities;
    private String jobType;
    private String workMode;
    private String experienceLevel;
    private String country;
    private String city;
    private String continent;
    private String locationText;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryCurrency;
    private String salaryPeriod;
    private String applyUrl;
    private String sourceUrl;
    private Instant postedAt;
    private Instant expiresAt;
    private List<String> tags;
    private String rawHtml;
}
