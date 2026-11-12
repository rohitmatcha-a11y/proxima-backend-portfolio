package com.jobportal.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.io.Serializable;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse implements Serializable {

    private Long id;
    private String title;
    private String companyName;
    private String companySlug;
    private String companyLogoUrl;
    private String jobType;
    private String workMode;
    private String experienceLevel;
    private String industry;
    private String country;
    private String city;
    private String continent;
    /** The source location, retained when it cannot be reliably normalized into city/country. */
    private String locationText;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryCurrency;
    private String salaryPeriod;
    private String visaStatus;
    private String visaDetails;
    private String applyUrl;
    private boolean remoteFlag;
    private List<String> tags;
    private Instant postedAt;
    private Instant createdAt;
}
