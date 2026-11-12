package com.jobportal.api.controller;

import com.jobportal.api.dto.CompanyRegistrationRequest;
import com.jobportal.api.dto.CompanyRegistrationResponse;
import com.jobportal.api.dto.CompanyResponse;
import com.jobportal.domain.entity.Company;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.service.cache.DistributedLockService;
import com.jobportal.service.cache.CacheKeys;
import com.jobportal.service.company.CompanyRegistrationService;
import com.jobportal.service.scraper.ScraperOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.jobportal.config.CacheConfig;
import com.jobportal.exception.ResourceNotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Companies", description = "Company endpoints")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final DistributedLockService distributedLockService;
    private final CompanyRegistrationService companyRegistrationService;
    private final ScraperOrchestrator scraperOrchestrator;

    @GetMapping
    @Operation(summary = "List all active companies with job counts")
    public List<CompanyResponse> getAllCompanies() {
        return distributedLockService.getCachedOrLoad(
                CacheConfig.CACHE_COMPANIES, CacheKeys.activeCompanies(), this::buildAllCompanies);
    }

    private List<CompanyResponse> buildAllCompanies() {
        return companyRepository.findByIsActiveTrue().stream()
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .map(this::buildCompanyResponse)
            .toList();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a single company by slug")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable String slug) {
        CompanyResponse response = distributedLockService.getCachedOrLoad(CacheConfig.CACHE_COMPANIES, CacheKeys.company(slug), () ->
                companyRepository.findBySlug(slug)
                        .map(this::buildCompanyResponse)
                        .orElse(null));
        if (response == null) {
            throw ResourceNotFoundException.company(slug);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a company and create its scraper config",
               description = "Creates a company and automatically detects its public careers platform. The initial scrape runs only when requested; otherwise the enabled scraper waits for the regular six-hour schedule.")
    public ResponseEntity<CompanyRegistrationResponse> registerCompany(
            @Valid @RequestBody CompanyRegistrationRequest request) {
        CompanyRegistrationResponse response = companyRegistrationService.register(request);
        if (response.isScraperEnabled() && request.isTriggerInitialScrape()) {
            String runId = scraperOrchestrator.triggerAsync(response.getCompany().getSlug());
            response.setInitialRunId(runId);
        }
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/enrich")
    @Operation(summary = "Refresh incomplete company profiles",
            description = "Refreshes missing company metadata from public first-party sources without changing scraper schedules or jobs.")
    public ResponseEntity<Map<String, Object>> enrichCompanyProfiles() {
        int updated = companyRegistrationService.enrichIncompleteCompanyProfiles();
        return ResponseEntity.ok(Map.of(
                "updated", updated,
                "message", updated == 1 ? "Updated 1 company profile" : "Updated " + updated + " company profiles"));
    }



    private CompanyResponse buildCompanyResponse(Company c) {
        return CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .website(c.getWebsite())
                .logoUrl(c.getLogoUrl())
                .description(c.getDescription())
                .industry(c.getIndustry())
                .headquartersCity(c.getHeadquartersCity())
                .headquartersCountry(c.getHeadquartersCountry())
                .size(c.getSize())
                .sizeCategory(c.getSizeCategory())
                .linkedinUrl(c.getLinkedinUrl())
                .careersPageUrl(c.getCareersPageUrl())
                .totalJobs(jobRepository.countByCompanyIdAndStatus(c.getId(), com.jobportal.domain.enums.JobStatus.ACTIVE))
                .lastScrapedAt(c.getLastScrapedAt())
                .build();
    }
}
