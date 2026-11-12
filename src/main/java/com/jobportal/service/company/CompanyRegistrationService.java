package com.jobportal.service.company;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.api.dto.CompanyRegistrationRequest;
import com.jobportal.api.dto.CompanyRegistrationResponse;
import com.jobportal.api.dto.CompanyResponse;
import com.jobportal.config.CacheConfig;
import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.enums.CompanySizeCategory;
import com.jobportal.domain.enums.Industry;
import com.jobportal.domain.enums.ScraperStatus;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.repository.jpa.ScraperConfigRepository;
import com.jobportal.service.scraper.CareerSiteProvider;
import com.jobportal.service.scraper.CareerSiteProviderDetector;
import com.jobportal.service.scraper.impl.UniversalCareerSiteScraper;
import com.jobportal.service.company.CompanyProfileEnrichmentService.CompanyProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyRegistrationService {

    private static final String DEFAULT_CRON = "0 0 */6 * * *";

    private final CompanyRepository companyRepository;
    private final ScraperConfigRepository scraperConfigRepository;
    private final JobRepository jobRepository;
    private final CompanyDescriptionService companyDescriptionService;
    private final CompanyProfileEnrichmentService companyProfileEnrichmentService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Transactional
    public CompanyRegistrationResponse register(CompanyRegistrationRequest request) {
        String careersPageUrl = normalizeCareersUrl(request.getCareersPageUrl());
        String slug = normalizeSlug(request.getSlug(), request.getName());
        if (companyRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Company slug already exists: " + slug);
        }
        if (companyRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new IllegalArgumentException("Company name already exists: " + request.getName().trim());
        }

        CareerSiteProviderDetector.Detection detection = resolveProvider(request, careersPageUrl);
        CompanyProfile profile = companyProfileEnrichmentService.enrich(request.getName().trim(), careersPageUrl);
        String size = firstNonBlank(request.getSize(), profile.size());
        String description = companyDescriptionService.resolve(request.getName().trim(), null,
                firstNonBlank(request.getDescription(), profile.description()));
        Industry inferredIndustry = firstNonNull(profile.industry(), companyProfileEnrichmentService.inferIndustry(description));

        Company company = Company.builder()
                .name(request.getName().trim())
                .slug(slug)
                .website(firstNonBlank(request.getWebsite(), profile.website(), websiteFromCareersUrl(careersPageUrl)))
                .careersPageUrl(careersPageUrl)
                .industry(request.getIndustry() != null ? request.getIndustry() : inferredIndustry != null ? inferredIndustry : Industry.OTHER)
                .headquartersCountry(firstNonBlank(request.getHeadquartersCountry(), profile.headquartersCountry()))
                .headquartersCity(firstNonBlank(request.getHeadquartersCity(), profile.headquartersCity()))
                .size(size)
                .sizeCategory(deriveSizeCategory(size))
                .description(description)
                .logoUrl(firstNonBlank(request.getLogoUrl(), profile.logoUrl()))
                .linkedinUrl(firstNonBlank(request.getLinkedinUrl(), profile.linkedinUrl()))
                .isActive(true)
                .build();

        company = companyRepository.save(company);

        ScraperConfig config = ScraperConfig.builder()
                .company(company)
                .scraperClass(UniversalCareerSiteScraper.class.getName())
                .cronExpression(DEFAULT_CRON)
                .isEnabled(request.isEnableScraper())
                .status(ScraperStatus.ACTIVE)
                .rateLimit(750)
                .customConfig(buildCustomConfig(detection, careersPageUrl))
                .build();
        scraperConfigRepository.save(config);

        evictRegistrationCaches();
        log.info("Registered company '{}' with automatic {} scraper board='{}'",
                slug, detection.provider(), detection.board());

        return CompanyRegistrationResponse.builder()
                .company(toCompanyResponse(company))
                .scraperProvider(detection.provider().name())
                .scraperClass(config.getScraperClass())
                .greenhouseBoardSlug(detection.provider() == CareerSiteProvider.GREENHOUSE ? detection.board() : null)
                .scraperEnabled(config.isEnabled())
                .build();
    }

    /** Backfills older registrations that were created before automatic profile enrichment. */
    @Transactional
    public int enrichIncompleteCompanyProfiles() {
        int updated = 0;
        for (Company company : companyRepository.findAll()) {
            if (!needsEnrichment(company)) continue;

            CompanyProfile profile = companyProfileEnrichmentService.enrich(company.getName(), company.getCareersPageUrl());
            if (applyMissingProfileFields(company, profile)) {
                companyRepository.save(company);
                updated++;
            }
        }
        if (updated > 0) {
            evictRegistrationCaches();
            log.info("Enriched public profiles for {} existing compan{}", updated, updated == 1 ? "y" : "ies");
        }
        return updated;
    }

    private CareerSiteProviderDetector.Detection resolveProvider(
            CompanyRegistrationRequest request, String careersPageUrl) {
        String legacyBoardSlug = blankToNull(request.getGreenhouseBoardSlug());
        if (legacyBoardSlug != null) {
            return new CareerSiteProviderDetector.Detection(
                    CareerSiteProvider.GREENHOUSE, sanitizeBoardSlug(legacyBoardSlug));
        }
        return CareerSiteProviderDetector.detect(careersPageUrl);
    }

    private String buildCustomConfig(CareerSiteProviderDetector.Detection detection, String careersPageUrl) {
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("provider", detection.provider().name());
            config.put("board", detection.board());
            config.put("careersPageUrl", careersPageUrl);
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize scraper config", e);
        }
    }

    private String normalizeSlug(String requestedSlug, String name) {
        String raw = blankToNull(requestedSlug);
        if (raw == null) raw = name;
        String slug = raw.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Company slug must contain letters or numbers");
        }
        return slug;
    }

    private String sanitizeBoardSlug(String boardSlug) {
        String sanitized = boardSlug.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Greenhouse board slug is invalid");
        }
        return sanitized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean needsEnrichment(Company company) {
        return blankToNull(company.getLogoUrl()) == null
                || isAtsWordmarkLogo(company.getLogoUrl())
                || blankToNull(company.getWebsite()) == null
                || isAtsWebsite(company.getWebsite())
                || isCareerSubdomain(company.getWebsite())
                || blankToNull(company.getLinkedinUrl()) == null
                || company.getIndustry() == null
                || company.getIndustry() == Industry.OTHER
                || companyDescriptionService.needsImprovement(company.getName(), company.getDescription());
    }

    private boolean applyMissingProfileFields(Company company, CompanyProfile profile) {
        boolean changed = false;
        if ((blankToNull(company.getWebsite()) == null || isAtsWebsite(company.getWebsite()) || isCareerSubdomain(company.getWebsite()))
                && blankToNull(profile.website()) != null) {
            company.setWebsite(profile.website());
            changed = true;
        }
        if ((blankToNull(company.getLogoUrl()) == null || isAtsWordmarkLogo(company.getLogoUrl()))
                && blankToNull(profile.logoUrl()) != null
                && !profile.logoUrl().equals(company.getLogoUrl())) {
            company.setLogoUrl(profile.logoUrl());
            changed = true;
        }
        if (blankToNull(company.getLinkedinUrl()) == null && blankToNull(profile.linkedinUrl()) != null) {
            company.setLinkedinUrl(profile.linkedinUrl());
            changed = true;
        }
        if (blankToNull(company.getHeadquartersCity()) == null && blankToNull(profile.headquartersCity()) != null) {
            company.setHeadquartersCity(profile.headquartersCity());
            changed = true;
        }
        if (blankToNull(company.getHeadquartersCountry()) == null && blankToNull(profile.headquartersCountry()) != null) {
            company.setHeadquartersCountry(profile.headquartersCountry());
            changed = true;
        }
        if (blankToNull(company.getSize()) == null && blankToNull(profile.size()) != null) {
            company.setSize(profile.size());
            company.setSizeCategory(deriveSizeCategory(profile.size()));
            changed = true;
        }
        if (companyDescriptionService.needsImprovement(company.getName(), company.getDescription())) {
            String description = companyDescriptionService.resolve(company.getName(), null, profile.description());
            if (!description.equals(company.getDescription())) {
                company.setDescription(description);
                changed = true;
            }
        }
        Industry inferredIndustry = firstNonNull(profile.industry(), companyProfileEnrichmentService.inferIndustry(company.getDescription()));
        if ((company.getIndustry() == null || company.getIndustry() == Industry.OTHER) && inferredIndustry != null) {
            company.setIndustry(inferredIndustry);
            changed = true;
        }
        return changed;
    }

    private boolean isAtsWebsite(String value) {
        if (blankToNull(value) == null) return false;
        try {
            String host = URI.create(value).getHost();
            if (host == null) return false;
            String normalized = host.toLowerCase();
            return normalized.contains("ashbyhq.com")
                    || normalized.contains("greenhouse.io")
                    || normalized.contains("lever.co")
                    || normalized.contains("myworkdayjobs.com")
                    || normalized.contains("smartrecruiters.com")
                    || normalized.contains("workable.com")
                    || normalized.contains("bamboohr.com")
                    || normalized.contains("jobvite.com")
                    || normalized.contains("icims.com")
                    || normalized.contains("applytojob.com")
                    || normalized.contains("recruitee.com")
                    || normalized.contains("teamtailor.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isAtsWordmarkLogo(String value) {
        String logo = blankToNull(value);
        if (logo == null) return false;
        String normalized = logo.toLowerCase();
        return normalized.contains("ashbyhq.com")
                && (normalized.contains("wordmark") || normalized.contains("org-theme-wordmark"));
    }

    private boolean isCareerSubdomain(String value) {
        if (blankToNull(value) == null) return false;
        try {
            String host = URI.create(value).getHost();
            if (host == null) return false;
            String normalized = host.toLowerCase();
            return normalized.startsWith("jobs.") || normalized.startsWith("careers.") || normalized.startsWith("work.");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (blankToNull(value) != null) return value.trim();
        }
        return null;
    }

    private <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }

    private String normalizeCareersUrl(String rawUrl) {
        String value = blankToNull(rawUrl);
        if (value == null) throw new IllegalArgumentException("Careers page URL is required");
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Careers page URL must be a valid http(s) URL");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Careers page URL must be a valid http(s) URL");
        }
    }

    private String websiteFromCareersUrl(String careersPageUrl) {
        try {
            URI uri = URI.create(careersPageUrl);
            if (isAtsWebsite(careersPageUrl)) return null;
            String host = uri.getHost();
            if (host == null) return null;
            String normalizedHost = host.toLowerCase();
            for (String prefix : java.util.List.of("jobs.", "careers.", "work.")) {
                if (normalizedHost.startsWith(prefix) && normalizedHost.length() > prefix.length()) {
                    normalizedHost = normalizedHost.substring(prefix.length());
                    break;
                }
            }
            return uri.getScheme() + "://" + normalizedHost;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CompanySizeCategory deriveSizeCategory(String size) {
        if (size == null || size.isBlank()) return null;
        String s = size.toLowerCase().replace(",", "").replace(" ", "");
        if (s.contains("10000+") || s.contains("10000plus")) return CompanySizeCategory.ENTERPRISE;
        if (s.contains("5000-10000") || s.contains("5001-10000")) return CompanySizeCategory.LARGE;
        if (s.contains("1000-5000") || s.contains("1001-5000")) return CompanySizeCategory.MID_SIZE;
        if (s.contains("201-1000") || s.contains("200-1000")) return CompanySizeCategory.GROWTH;
        if (s.contains("51-200") || s.contains("50-200")) return CompanySizeCategory.EARLY_STAGE;
        return CompanySizeCategory.STARTUP;
    }

    private CompanyResponse toCompanyResponse(Company c) {
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

    private void evictRegistrationCaches() {
        for (String cacheName : java.util.List.of(
                CacheConfig.CACHE_COMPANIES,
                CacheConfig.CACHE_FILTER_OPTIONS,
                CacheConfig.CACHE_STATS,
                CacheConfig.CACHE_JOB_SEARCH,
                CacheConfig.CACHE_JOB_COMPANY,
                CacheConfig.CACHE_JOB_DETAIL,
                CacheConfig.CACHE_JOB_SIMILAR
        )) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        }
    }
}
