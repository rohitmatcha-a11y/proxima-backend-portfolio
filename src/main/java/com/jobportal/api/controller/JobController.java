package com.jobportal.api.controller;

import com.jobportal.api.dto.*;
import com.jobportal.domain.enums.*;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.service.search.JobSearchService;
import com.jobportal.service.cache.CacheKeys;
import com.jobportal.service.cache.DistributedLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import com.jobportal.config.CacheConfig;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job search and retrieval")
@RequiredArgsConstructor
public class JobController {

    private final JobSearchService jobSearchService;
    private final DistributedLockService distributedLockService;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @GetMapping
    @Operation(summary = "Search and filter jobs")
    public ResponseEntity<PagedResponse<JobResponse>> searchJobs(
            @ModelAttribute JobSearchRequest request) {
        return ResponseEntity.ok(jobSearchService.searchJobs(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full job details by ID")
    public ResponseEntity<JobDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jobSearchService.getJobById(id));
    }

    @GetMapping("/{id}/similar")
    @Operation(summary = "Get similar jobs for a given job ID")
    public ResponseEntity<List<JobResponse>> getSimilarJobs(@PathVariable Long id) {
        return ResponseEntity.ok(jobSearchService.getSimilarJobs(id));
    }

    @GetMapping("/company/{slug}")
    @Operation(summary = "Get all active jobs for a company")
    public ResponseEntity<PagedResponse<JobResponse>> getByCompany(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(jobSearchService.getJobsByCompany(slug, page, size));
    }

    @GetMapping("/filters/options")
    @Operation(summary = "Get all available filter options")
    public FilterOptionsResponse filterOptions() {
        return distributedLockService.getCachedOrLoad(
                CacheConfig.CACHE_FILTER_OPTIONS, CacheKeys.filterOptions(), this::buildFilterOptions);
    }

    private FilterOptionsResponse buildFilterOptions() {
        List<String> continents = jobRepository.findDistinctContinents().stream()
                .filter(c -> c != null && !c.isBlank() && !c.equals("Global"))
                .sorted()
                .collect(Collectors.toList());

        List<String> countries = jobRepository.findDistinctCountries().stream()
                .filter(c -> c != null && !c.isBlank())
                .sorted()
                .collect(Collectors.toList());

        List<CompanyOption> companies = companyRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.isActive()))
                .sorted(Comparator.comparing(c -> c.getName()))
                .map(c -> CompanyOption.builder()
                        .slug(c.getSlug())
                        .name(c.getName())
                        .logoUrl(c.getLogoUrl())
                        .build())
                .collect(Collectors.toList());

        FilterOptionsResponse options = FilterOptionsResponse.builder()
                .continents(continents)
                .countries(countries)
                .countriesByContinent(buildCountriesByContinent())
                .companies(companies)
                .workModes(Arrays.stream(WorkMode.values()).map(Enum::name).toList())
                .jobTypes(Arrays.stream(JobType.values()).map(Enum::name).toList())
                .experienceLevels(Arrays.stream(ExperienceLevel.values()).map(Enum::name).toList())
                .industries(Arrays.stream(Industry.values()).map(Enum::name).toList())
                .visaStatuses(Arrays.stream(VisaStatus.values()).map(Enum::name).toList())
                .companySizeCategories(Arrays.stream(CompanySizeCategory.values()).map(Enum::name).toList())
                .build();

        return options;
    }

    @GetMapping("/stats")
    @Operation(summary = "Job portal statistics")
    public Map<String, Object> stats() {
        return distributedLockService.getCachedOrLoad(CacheConfig.CACHE_STATS, CacheKeys.jobStats(), this::buildStats);
    }

    private Map<String, Object> buildStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalJobs", jobRepository.countByStatus(JobStatus.ACTIVE));
        stats.put("totalCompanies", companyRepository.count());
        stats.put("companyNames", companyRepository.findByIsActiveTrue().stream()
                .map(company -> company.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());

        Map<String, Long> byVisa = new LinkedHashMap<>();
        for (VisaStatus s : VisaStatus.values()) {
            byVisa.put(s.name(), jobRepository.countByVisaStatus(s));
        }
        stats.put("jobsByVisaStatus", byVisa);

        Map<String, Long> byWorkMode = new LinkedHashMap<>();
        for (WorkMode m : WorkMode.values()) {
            byWorkMode.put(m.name(), jobRepository.countByWorkMode(m));
        }
        stats.put("jobsByWorkMode", byWorkMode);

        long totalCountries = jobRepository.countDistinctCountries();
        stats.put("totalCountries", totalCountries);

        return stats;
    }

    @GetMapping("/countries")
    @Operation(summary = "Paginated job counts by country")
    public ResponseEntity<PagedResponse<CountryJobCount>> getJobsByCountry(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String cacheKey = CacheKeys.countries(page, size);
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConfig.CACHE_COUNTRIES);
        if (cache != null) {
            @SuppressWarnings("unchecked")
            PagedResponse<CountryJobCount> cached = cache.get(cacheKey, PagedResponse.class);
            if (cached != null) return ResponseEntity.ok(cached);
        }
        org.springframework.data.domain.Page<Object[]> result =
            jobRepository.countByCountryPaged(
                org.springframework.data.domain.PageRequest.of(page, size));
        org.springframework.data.domain.Page<CountryJobCount> mapped = result.map(row ->
            new CountryJobCount((String) row[0], ((Number) row[1]).longValue()));
        PagedResponse<CountryJobCount> response = PagedResponse.of(mapped, null);
        if (cache != null) cache.put(cacheKey, response);
        return ResponseEntity.ok(response);
    }

    record CountryJobCount(String country, long count) {}

    @GetMapping("/ticker")
    @Operation(summary = "Get ticker items showing recent job counts by company")
    public List<String> getTicker() {
        return distributedLockService.getCachedOrLoad(CacheConfig.CACHE_TICKER, CacheKeys.ticker(), this::buildTicker);
    }

    private List<String> buildTicker() {
        Instant since = Instant.now().minusSeconds(30L * 86400);

        List<Object[]> results = jobRepository.findRecentJobCountsByCompany(since);

        List<String> items = results.stream()
                .filter(row -> row[0] != null && row[1] != null && ((Number) row[1]).longValue() > 0)
                .map(row -> {
                    String company = (String) row[0];
                    Long count = ((Number) row[1]).longValue();
                    return "✦ " + company + " posted " + count + " new jobs";
                })
                .collect(Collectors.toList());


        return items;
    }

    private Map<String, List<String>> buildCountriesByContinent() {
        List<Object[]> rows = jobRepository.findContinentCountryPairs();
        Map<String, List<String>> map = new TreeMap<>();
        for (Object[] row : rows) {
            String continent = (String) row[0];
            String country = (String) row[1];
            if (continent == null || continent.isBlank() || continent.equals("Global") ||
                    country == null || country.isBlank()) continue;
            map.computeIfAbsent(continent, k -> new ArrayList<>()).add(country);
        }
        map.values().forEach(Collections::sort);
        return map;
    }
}
