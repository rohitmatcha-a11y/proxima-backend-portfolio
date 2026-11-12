package com.jobportal.api.dto;

import com.jobportal.domain.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class ProfileJobResponse {
    Long id;
    String title;
    String companyName;
    String companySlug;
    String companyLogoUrl;
    String country;
    String city;
    String workMode;
    String jobType;
    String visaStatus;
    String applyUrl;
    BigDecimal salaryMin;
    BigDecimal salaryMax;
    String salaryCurrency;
    List<String> tags;
    Instant postedAt;
    Instant savedAt;
    Instant trackedAt;
    Instant appliedAt;
    ApplicationStatus applicationStatus;
}
