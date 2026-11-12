package com.jobportal.api.dto;

import com.jobportal.domain.enums.CompanySizeCategory;
import com.jobportal.domain.enums.Industry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse implements Serializable {
    private Long id;
    private String name;
    private String slug;
    private String website;
    private String logoUrl;
    private String description;
    private Industry industry;
    private String headquartersCity;
    private String headquartersCountry;
    private String size;
    private CompanySizeCategory sizeCategory;
    private String linkedinUrl;
    private String careersPageUrl;
    private long totalJobs;
    private Instant lastScrapedAt;
}
