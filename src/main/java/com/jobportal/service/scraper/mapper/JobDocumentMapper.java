package com.jobportal.service.scraper.mapper;

import com.jobportal.domain.document.JobDocument;
import com.jobportal.domain.entity.Job;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class JobDocumentMapper {

    public JobDocument toDocument(Job job) {
        return JobDocument.builder()
                .id(String.valueOf(job.getId()))
                .title(job.getTitle())
                .companyName(job.getCompany() != null ? job.getCompany().getName() : null)
                .companySlug(job.getCompany() != null ? job.getCompany().getSlug() : null)
                .description(job.getDescription())
                .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                .workMode(job.getWorkMode() != null ? job.getWorkMode().name() : null)
                .experienceLevel(job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null)
                .industry(job.getIndustry() != null ? job.getIndustry().name() : null)
                .country(job.getCountry())
                .city(job.getCity())
                .continent(job.getContinent())
                .locationText(job.getLocationText())
                .salaryMin(job.getSalaryMin() != null ? job.getSalaryMin().doubleValue() : null)
                .salaryMax(job.getSalaryMax() != null ? job.getSalaryMax().doubleValue() : null)
                .salaryCurrency(job.getSalaryCurrency())
                .visaStatus(job.getVisaStatus() != null ? job.getVisaStatus().name() : null)
                .tags(job.getTags() != null ? Arrays.asList(job.getTags().split(",")) : List.of())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .isRemoteFriendly(job.isRemoteFriendly())
                .postedAt(job.getPostedAt())
                .applyUrl(job.getApplyUrl())
                .logoUrl(job.getCompany() != null ? job.getCompany().getLogoUrl() : null)
                .companySizeCategory(job.getCompany() != null && job.getCompany().getSizeCategory() != null
                        ? job.getCompany().getSizeCategory().name() : null)
                .build();
    }
}
