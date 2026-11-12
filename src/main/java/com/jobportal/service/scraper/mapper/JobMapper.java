package com.jobportal.service.scraper.mapper;

import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.Job;
import com.jobportal.domain.enums.*;
import com.jobportal.service.scraper.model.ScrapedJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class JobMapper {

    public Job toEntity(ScrapedJob scraped, Company company) {
        return Job.builder()
                .externalId(scraped.getExternalId())
                .title(scraped.getTitle())
                .company(company)
                .description(scraped.getDescription())
                .requirements(scraped.getRequirements())
                .responsibilities(scraped.getResponsibilities())
                .jobType(parseJobType(scraped.getJobType()))
                .workMode(parseWorkMode(scraped.getWorkMode()))
                .experienceLevel(parseExperienceLevel(scraped.getExperienceLevel()))
                .country(scraped.getCountry())
                .city(scraped.getCity())
                .continent(scraped.getContinent())
                .locationText(scraped.getLocationText())
                .salaryMin(scraped.getSalaryMin())
                .salaryMax(scraped.getSalaryMax())
                .salaryCurrency(scraped.getSalaryCurrency())
                .salaryPeriod(scraped.getSalaryPeriod())
                .applyUrl(scraped.getApplyUrl())
                .sourceUrl(scraped.getSourceUrl())
                .postedAt(scraped.getPostedAt())
                .expiresAt(scraped.getExpiresAt())
                .tags(joinTags(scraped.getTags()))
                .status(JobStatus.ACTIVE)
                .visaStatus(VisaStatus.NOT_MENTIONED)
                .build();
    }

    private JobType parseJobType(String value) {
        if (value == null || value.isBlank()) return JobType.FULL_TIME;
        try {
            return JobType.valueOf(value.trim().toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("Unknown JobType '{}', defaulting to FULL_TIME", value);
            return JobType.FULL_TIME;
        }
    }

    private WorkMode parseWorkMode(String value) {
        if (value == null || value.isBlank()) return WorkMode.ONSITE;
        try {
            return WorkMode.valueOf(value.trim().toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("Unknown WorkMode '{}', defaulting to ONSITE", value);
            return WorkMode.ONSITE;
        }
    }

    private ExperienceLevel parseExperienceLevel(String value) {
        if (value == null || value.isBlank()) return ExperienceLevel.MID;
        try {
            return ExperienceLevel.valueOf(value.trim().toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.debug("Unknown ExperienceLevel '{}', defaulting to MID", value);
            return ExperienceLevel.MID;
        }
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return String.join(",", tags);
    }
}
