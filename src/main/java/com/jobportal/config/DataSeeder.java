package com.jobportal.config;

import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.enums.CompanySizeCategory;
import com.jobportal.domain.enums.Industry;
import com.jobportal.domain.enums.ScraperStatus;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.ScraperConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.jobportal.service.company.CompanyDescriptionService;
import com.jobportal.service.company.CompanyRegistrationService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final CompanyDescriptionService companyDescriptionService;

    private final CompanyRepository companyRepository;
    private final ScraperConfigRepository scraperConfigRepository;
    private final CacheManager cacheManager;
    private final CompanyRegistrationService companyRegistrationService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("DataSeeder: checking seed data...");

        seedCompany(CompanySeed.builder()
                .name("Google").slug("google")
                .wikipediaSlug("Google")
                .logoUrl("https://www.google.com/favicon.ico")
                .linkedinUrl("https://www.linkedin.com/company/google")
                .website("https://google.com")
                .careersPageUrl("https://careers.google.com/jobs/results/")
                .industry(Industry.TECHNOLOGY)
                .headquartersCountry("USA").headquartersCity("Mountain View")
                .size("10000+")
                .scraperClass("com.jobportal.service.scraper.impl.GoogleScraper")
                .rateLimit(3000)
                .build());

        seedCompany(CompanySeed.builder()
                .name("Stripe").slug("stripe")
                .wikipediaSlug("Stripe_(company)")
                .logoUrl("https://stripe.com/favicon.ico")
                .linkedinUrl("https://www.linkedin.com/company/stripe")
                .website("https://stripe.com")
                .careersPageUrl("https://stripe.com/jobs/search")
                .industry(Industry.FINTECH)
                .headquartersCountry("USA").headquartersCity("San Francisco")
                .size("5000-10000")
                .scraperClass("com.jobportal.service.scraper.impl.StripeScraper")
                .rateLimit(2000)
                .build());

        seedCompany(CompanySeed.builder()
                .name("Figma").slug("figma")
                .wikipediaSlug("Figma_(software)")
                .logoUrl("https://static.figma.com/app/icon/1/favicon.ico")
                .linkedinUrl("https://www.linkedin.com/company/figma")
                .website("https://figma.com")
                .careersPageUrl("https://boards-api.greenhouse.io/v1/boards/figma/jobs")
                .industry(Industry.TECHNOLOGY)
                .headquartersCountry("USA").headquartersCity("San Francisco")
                .size("1000-5000")
                .scraperClass("com.jobportal.service.scraper.impl.FigmaScraper")
                .rateLimit(1000)
                .build());

        seedCompany(CompanySeed.builder()
                .name("Coinbase").slug("coinbase")
                .wikipediaSlug("Coinbase")
                .logoUrl("https://www.coinbase.com/favicon.ico")
                .linkedinUrl("https://www.linkedin.com/company/coinbase")
                .website("https://coinbase.com")
                .careersPageUrl("https://www.coinbase.com/careers/positions")
                .industry(Industry.FINTECH)
                .headquartersCountry("USA").headquartersCity("San Francisco")
                .size("5000-10000")
                .scraperClass("com.jobportal.service.scraper.impl.CoinbaseScraper")
                .rateLimit(2000)
                .build());

        seedCompany(CompanySeed.builder()
                .name("Anthropic")
                .slug("anthropic")
                .website("https://anthropic.com")
                .careersPageUrl("https://boards-api.greenhouse.io/v1/boards/anthropic/jobs")
                .industry(Industry.TECHNOLOGY)
                .headquartersCity("San Francisco")
                .headquartersCountry("USA")
                .size("1000-5000")
                .logoUrl("https://anthropic.com/favicon.ico")
                .linkedinUrl("https://www.linkedin.com/company/anthropic")
                .wikipediaSlug("Anthropic")
                .build());

        seedCompany(CompanySeed.builder()
                .name("Razorpay").slug("razorpay")
                .wikipediaSlug("Razorpay")
                .logoUrl("https://razorpay.com/favicon.ico")
                .linkedinUrl("https://www.linkedin.com/company/razorpay")
                .website("https://razorpay.com")
                .careersPageUrl("https://razorpay.com/jobs/")
                .industry(Industry.FINTECH)
                .headquartersCountry("India").headquartersCity("Bengaluru")
                .size("1000-5000")
                .scraperClass("com.jobportal.service.scraper.impl.RazorpayScraper")
                .rateLimit(2000)
                .build());

        // Applies the same automatic metadata discovery to companies that were
        // registered before enrichment existed (for example, Linear).
        companyRegistrationService.enrichIncompleteCompanyProfiles();
        clearCompanyCache();
        log.info("DataSeeder: seed complete");
    }


    private void seedCompany(CompanySeed seed) {
        String resolvedDescription = companyDescriptionService.resolve(seed.name, seed.wikipediaSlug, null);

        if (companyRepository.existsBySlug(seed.slug)) {
            // Update existing company with latest data
            companyRepository.findBySlug(seed.slug).ifPresent(existing -> {
                existing.setDescription(resolvedDescription);
                existing.setLogoUrl(seed.logoUrl);
                existing.setLinkedinUrl(seed.linkedinUrl);
                existing.setWebsite(seed.website);
                existing.setSize(seed.size);
                existing.setSizeCategory(deriveSizeCategory(seed.size));
                existing.setHeadquartersCity(seed.headquartersCity);
                existing.setHeadquartersCountry(seed.headquartersCountry);
                companyRepository.save(existing);
                log.debug("DataSeeder: '{}' updated", seed.slug);
            });
            return;
        }

        Company company = Company.builder()
                .name(seed.name)
                .slug(seed.slug)
                .website(seed.website)
                .careersPageUrl(seed.careersPageUrl)
                .industry(seed.industry)
                .headquartersCountry(seed.headquartersCountry)
                .headquartersCity(seed.headquartersCity)
                .size(seed.size)
                .sizeCategory(deriveSizeCategory(seed.size))
                .description(resolvedDescription)
                .logoUrl(seed.logoUrl)
                .linkedinUrl(seed.linkedinUrl)
                .isActive(true)
                .build();

        company = companyRepository.save(company);
        log.info("DataSeeder: seeded company '{}'", seed.slug);

        ScraperConfig config = ScraperConfig.builder()
                .company(company)
                .scraperClass(seed.scraperClass)
                .cronExpression("0 0 */6 * * *")
                .isEnabled(true)
                .status(ScraperStatus.ACTIVE)
                .rateLimit(seed.rateLimit)
                .build();

        scraperConfigRepository.save(config);
        log.info("DataSeeder: seeded ScraperConfig for '{}'", seed.slug);
    }

    /** Internal value object to keep seedCompany() calls readable. */
    @lombok.Builder
    private static class CompanySeed {
        String name, slug, website, careersPageUrl;
        Industry industry;
        String headquartersCountry, headquartersCity, size;
        String logoUrl, linkedinUrl, wikipediaSlug;
        String scraperClass;
        int rateLimit;
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

    /** The seed may refresh a stored company description, so don't serve the old card for its six-hour TTL. */
    private void clearCompanyCache() {
        Cache companies = cacheManager.getCache(CacheConfig.CACHE_COMPANIES);
        if (companies != null) {
            companies.clear();
        }
    }
}
