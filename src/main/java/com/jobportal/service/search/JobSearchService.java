package com.jobportal.service.search;

import com.jobportal.api.dto.*;
import com.jobportal.domain.document.JobDocument;
import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.Job;
import com.jobportal.domain.enums.Industry;
import com.jobportal.domain.enums.JobStatus;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.SearchException;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import com.jobportal.config.CacheConfig;
import com.jobportal.service.cache.CacheKeys;
import com.jobportal.service.cache.DistributedLockService;
import com.jobportal.util.SafeErrorSummary;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ElasticsearchJobSearchService esSearchService;
    private final DistributedLockService distributedLockService;

    public PagedResponse<JobResponse> searchJobs(JobSearchRequest request) {
        return distributedLockService.getCachedOrLoad(
                CacheConfig.CACHE_JOB_SEARCH,
                CacheKeys.jobSearch(request),
                () -> doSearchJobs(request));
    }

    private PagedResponse<JobResponse> doSearchJobs(JobSearchRequest request) {
        validatePagination(request);
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            Pageable pageable = buildPageable(request);
            Page<Job> page = jobRepository.findAll(JobSpecification.buildSpec(request), pageable);
            Page<JobResponse> mapped = page.map(this::mapToResponse);
            return PagedResponse.of(mapped, buildFilterMap(request));
        }
        try {
            SearchHits<JobDocument> hits = esSearchService.search(request);
            List<JobDocument> documents = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
            List<JobResponse> results = mapDocumentsToResponsesWithSourceLocations(documents);
            long totalHits = hits.getTotalHits();
            int totalPages = (int) Math.ceil((double) totalHits / request.getSize());
            return PagedResponse.<JobResponse>builder()
                    .content(results)
                    .page(request.getPage())
                    .size(request.getSize())
                    .totalElements(totalHits)
                    .totalPages(totalPages)
                    .first(request.getPage() == 0)
                    .last(request.getPage() >= totalPages - 1)
                    .filters(buildFilterMap(request))
                    .build();
        } catch (Exception e) {
            log.warn("ES search failed, falling back to DB: {}", SafeErrorSummary.from(e));
            Pageable pageable = buildPageable(request);
            Page<Job> page = jobRepository.findAll(JobSpecification.buildSpec(request), pageable);
            Page<JobResponse> mapped = page.map(this::mapToResponse);
            return PagedResponse.of(mapped, buildFilterMap(request));
        }
    }

    private JobResponse mapDocToResponse(JobDocument doc) {
        return mapDocToResponse(doc, doc.getLocationText());
    }

    private List<JobResponse> mapDocumentsToResponsesWithSourceLocations(List<JobDocument> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }

        Map<String, String> sourceLocations = jobRepository.findAllById(
                        documents.stream().map(doc -> Long.valueOf(doc.getId())).toList())
                .stream()
                .collect(Collectors.toMap(job -> String.valueOf(job.getId()), Job::getLocationText));

        return documents.stream()
                .map(doc -> mapDocToResponse(doc,
                        sourceLocations.getOrDefault(doc.getId(), doc.getLocationText())))
                .toList();
    }

    /**
     * Older Elasticsearch documents may not yet have locationText. The database remains
     * the source of truth for that display field until the next index refresh.
     */
    private JobResponse mapDocToResponse(JobDocument doc, String locationText) {
        return JobResponse.builder()
                .id(Long.valueOf(doc.getId()))
                .title(doc.getTitle())
                .companyName(doc.getCompanyName())
                .companySlug(doc.getCompanySlug())
                .companyLogoUrl(doc.getLogoUrl())
                .jobType(doc.getJobType())
                .workMode(doc.getWorkMode())
                .experienceLevel(doc.getExperienceLevel())
                .industry(doc.getIndustry())
                .country(doc.getCountry())
                .city(doc.getCity())
                .continent(doc.getContinent())
                .locationText(locationText)
                .salaryMin(doc.getSalaryMin() != null ? BigDecimal.valueOf(doc.getSalaryMin()) : null)
                .salaryMax(doc.getSalaryMax() != null ? BigDecimal.valueOf(doc.getSalaryMax()) : null)
                .salaryCurrency(doc.getSalaryCurrency())
                .visaStatus(doc.getVisaStatus())
                .applyUrl(doc.getApplyUrl())
                .tags(doc.getTags() != null ? doc.getTags() : List.of())
                .postedAt(doc.getPostedAt())
                .build();
    }

    private void validatePagination(JobSearchRequest request) {
        if (request.getPage() < 0)
            throw SearchException.invalidPagination("Page index must be >= 0");
        if (request.getSize() < 1 || request.getSize() > 100)
            throw SearchException.invalidPagination("Page size must be between 1 and 100");
        if (!List.of("postedAt", "salary", "relevance", "title").contains(request.getSortBy()))
            throw SearchException.invalidSortField(request.getSortBy());
        if ("relevance".equals(request.getSortBy()) && (request.getKeyword() == null || request.getKeyword().isBlank()))
            throw SearchException.relevanceRequiresKeyword();
    }

    public JobDetailResponse getJobById(Long id) {
        return distributedLockService.getCachedOrLoad(
                CacheConfig.CACHE_JOB_DETAIL, CacheKeys.jobDetail(id), () -> doGetJobById(id));
    }

    private JobDetailResponse doGetJobById(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.job(id));

        Company company = job.getCompany();
        return JobDetailResponse.builder()
                // base fields
                .id(job.getId())
                .title(job.getTitle())
                .companyName(company != null ? company.getName() : null)
                .companySlug(company != null ? company.getSlug() : null)
                .companyLogoUrl(company != null ? company.getLogoUrl() : null)
                .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                .workMode(job.getWorkMode() != null ? job.getWorkMode().name() : null)
                .experienceLevel(job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null)
                .industry(job.getIndustry() != null ? job.getIndustry().name() : null)
                .country(job.getCountry())
                .city(job.getCity())
                .continent(job.getContinent())
                .locationText(job.getLocationText())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .salaryCurrency(job.getSalaryCurrency())
                .salaryPeriod(job.getSalaryPeriod())
                .visaStatus(job.getVisaStatus() != null ? job.getVisaStatus().name() : null)
                .visaDetails(job.getVisaDetails())
                .applyUrl(job.getApplyUrl())
                .remoteFlag(job.isRemoteFriendly())
                .tags(parseTags(job.getTags()))
                .postedAt(job.getPostedAt())
                .createdAt(job.getCreatedAt())
                // detail-only fields
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .responsibilities(job.getResponsibilities())
                .companyWebsite(company != null ? company.getWebsite() : null)
                .companyDescription(company != null ? company.getDescription() : null)
                .companySize(company != null ? company.getSize() : null)
                .companyHeadquarters(company != null
                        ? buildHeadquarters(company.getHeadquartersCity(), company.getHeadquartersCountry())
                        : null)
                .build();
    }

    public List<JobResponse> getSimilarJobs(Long id) {
        return distributedLockService.getCachedOrLoad(
                CacheConfig.CACHE_JOB_SIMILAR, CacheKeys.similarJobs(id), () -> doGetSimilarJobs(id));
    }

    private List<JobResponse> doGetSimilarJobs(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.job(id));
        try {
            List<JobDocument> documents = esSearchService.findSimilarDocuments(
                    String.valueOf(id),
                    job.getTitle(),
                    job.getCompany() != null ? job.getCompany().getSlug() : null,
                    job.getWorkMode() != null ? job.getWorkMode().name() : null);
            return mapDocumentsToResponsesWithSourceLocations(documents);
        } catch (Exception e) {
            log.warn("ES similar jobs failed for id {}: {}", id, SafeErrorSummary.from(e));
            return List.of();
        }
    }

    public PagedResponse<JobResponse> getJobsByCompany(String companySlug, int page, int size) {
        return distributedLockService.getCachedOrLoad(
                CacheConfig.CACHE_JOB_COMPANY,
                CacheKeys.companyJobs(companySlug, page, size),
                () -> doGetJobsByCompany(companySlug, page, size));
    }

    private PagedResponse<JobResponse> doGetJobsByCompany(String companySlug, int page, int size) {
        Company company = companyRepository.findBySlug(companySlug)
                .orElseThrow(() -> ResourceNotFoundException.company(companySlug));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postedAt"));
        Page<Job> jobs = jobRepository.findByCompanyIdAndStatus(company.getId(), JobStatus.ACTIVE, pageable);
        Page<JobResponse> mapped = jobs.map(this::mapToResponse);
        return PagedResponse.of(mapped, Map.of("companySlug", companySlug));
    }

    public JobResponse mapToResponse(Job job) {
        Company company = job.getCompany();
        return JobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(company != null ? company.getName() : null)
                .companySlug(company != null ? company.getSlug() : null)
                .companyLogoUrl(company != null ? company.getLogoUrl() : null)
                .jobType(job.getJobType() != null ? job.getJobType().name() : null)
                .workMode(job.getWorkMode() != null ? job.getWorkMode().name() : null)
                .experienceLevel(job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null)
                .industry(job.getIndustry() != null ? job.getIndustry().name() : null)
                .country(job.getCountry())
                .city(job.getCity())
                .continent(job.getContinent())
                .locationText(job.getLocationText())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .salaryCurrency(job.getSalaryCurrency())
                .salaryPeriod(job.getSalaryPeriod())
                .visaStatus(job.getVisaStatus() != null ? job.getVisaStatus().name() : null)
                .visaDetails(job.getVisaDetails())
                .applyUrl(job.getApplyUrl())
                .remoteFlag(job.isRemoteFriendly())
                .tags(parseTags(job.getTags()))
                .postedAt(job.getPostedAt())
                .createdAt(job.getCreatedAt())
                .build();
    }

    private Map<String, Object> buildFilterMap(JobSearchRequest request) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (request.getKeyword() != null)              filters.put("keyword", request.getKeyword());
        if (request.getCompanySlug() != null)          filters.put("companySlug", request.getCompanySlug());
        if (request.getCompanySlug() != null)         filters.put("companySlug", request.getCompanySlug());
        if (request.getCountry() != null)              filters.put("country", request.getCountry());
        if (request.getCountry() != null)            filters.put("country", request.getCountry());
        if (request.getContinent() != null)            filters.put("continent", request.getContinent());
        if (request.getContinent() != null)           filters.put("continent", request.getContinent());
        if (request.getCity() != null)                 filters.put("city", request.getCity());
        if (request.getJobType() != null)             filters.put("jobType", request.getJobType());
        if (request.getWorkMode() != null)            filters.put("workMode", request.getWorkMode());
        if (request.getExperienceLevel() != null)     filters.put("experienceLevel", request.getExperienceLevel());
        if (request.getIndustry() != null)           filters.put("industry", request.getIndustry());
        if (request.getVisaStatus() != null)         filters.put("visaStatus", request.getVisaStatus());
        if (request.getCompanySizeCategory() != null)  filters.put("companySizeCategory", request.getCompanySizeCategory());
        if (request.getSalaryMin() != null)            filters.put("salaryMin", request.getSalaryMin());
        if (request.getSalaryMax() != null)            filters.put("salaryMax", request.getSalaryMax());
        if (request.getSalaryCurrency() != null)       filters.put("salaryCurrency", request.getSalaryCurrency());
        if (request.getDatePosted() != null)           filters.put("datePosted", request.getDatePosted());
        if (request.getPostedAfter() != null)          filters.put("postedAfter", request.getPostedAfter());
        if (request.getPostedBefore() != null)         filters.put("postedBefore", request.getPostedBefore());
        if (Boolean.TRUE.equals(request.getRemoteOnly()))           filters.put("remoteOnly", true);
        if (Boolean.TRUE.equals(request.getVisaSponsorshipOnly()))  filters.put("visaSponsorshipOnly", true);
        return filters;
    }

    private Pageable buildPageable(JobSearchRequest request) {
        Sort sort = switch (request.getSortBy()) {
            case "salary" -> Sort.by(
                    "desc".equalsIgnoreCase(request.getSortDir())
                            ? Sort.Order.desc("salaryMax")
                            : Sort.Order.asc("salaryMin")
            );
            case "relevance" -> Sort.unsorted();
            case "title" -> Sort.by(
                    "desc".equalsIgnoreCase(request.getSortDir())
                            ? Sort.Direction.DESC
                            : Sort.Direction.ASC,
                    "title"
            );
            default -> Sort.by(
                    "desc".equalsIgnoreCase(request.getSortDir())
                            ? Sort.Order.desc("postedAt")
                            : Sort.Order.asc("postedAt"),
                    Sort.Order.desc("id")
            );
        };
        return PageRequest.of(request.getPage(), request.getSize(), sort);
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    private String buildHeadquarters(String city, String country) {
        if (city != null && country != null) return city + ", " + country;
        if (city != null) return city;
        return country;
    }
}
