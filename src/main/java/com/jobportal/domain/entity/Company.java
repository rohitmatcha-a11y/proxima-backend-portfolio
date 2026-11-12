package com.jobportal.domain.entity;

import com.jobportal.domain.enums.Industry;
import com.jobportal.domain.enums.CompanySizeCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "companies")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Company extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String website;

    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Industry industry;

    private String headquartersCountry;

    private String headquartersCity;

    private String size;

    @Enumerated(EnumType.STRING)
    private CompanySizeCategory sizeCategory;

    private String linkedinUrl;

    @Column(nullable = false)
    private String careersPageUrl;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private int totalJobsScraped = 0;

    private Instant lastScrapedAt;
}
