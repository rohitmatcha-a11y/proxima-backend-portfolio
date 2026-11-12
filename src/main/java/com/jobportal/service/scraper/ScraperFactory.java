package com.jobportal.service.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.repository.jpa.ScraperConfigRepository;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ScraperException;
import com.jobportal.repository.jpa.ScraperRunRepository;
import com.jobportal.service.scraper.mapper.JobMapper;
import com.jobportal.service.outbox.OutboxEventService;
import com.jobportal.observability.ScraperMetrics;
import com.jobportal.util.SafeErrorSummary;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.client.geocoding.GeocodingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperFactory {

    private final ScraperRegistry registry;
    private final CompanyRepository companyRepository;
    private final ScraperConfigRepository scraperConfigRepository;
    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final ScraperRunRepository scraperRunRepository;
    private final OutboxEventService outboxEventService;
    private final ScraperMetrics scraperMetrics;

    @Value("${external.greenhouse.base-url}")
    private String greenhouseBaseUrl;


    @Value("${scrapers.greenhouse.boards.anthropic:anthropic}")
    private String boardAnthropic;

    @Value("${scrapers.greenhouse.boards.coinbase:coinbase}")
    private String boardCoinbase;

    @Value("${scrapers.greenhouse.boards.stripe:stripe}")
    private String boardStripe;

    @Value("${scrapers.greenhouse.boards.figma:figma}")
    private String boardFigma;

    @Value("${scrapers.greenhouse.boards.razorpay:razorpaysoftwareprivatelimited}")
    private String boardRazorpay;

    @Value("${scrapers.google.max-pages:1000}")
    private int googleMaxPages;

    @Value("${app.scraper.run-timeout-minutes:120}")
    private int scraperRunTimeoutMinutes;

    @Value("${app.scraper.fetch-timeout-ms:10000}")
    private int fetchTimeoutMs;
    private final ObjectMapper objectMapper;
    private final GeocodingClient geocodingClient;

    public Optional<BaseScraper> createScraper(String companySlug) {
        Optional<Company> companyOpt = companyRepository.findBySlug(companySlug);
        if (companyOpt.isEmpty()) {
            throw ResourceNotFoundException.company(companySlug);
        }

        Optional<ScraperConfig> configOpt = scraperConfigRepository.findByCompanyId(companyOpt.get().getId());
        if (configOpt.isEmpty()) {
            throw ResourceNotFoundException.scraperConfig(companySlug);
        }

        try {
            ScraperConfig config = configOpt.get();
            Class<? extends BaseScraper> scraperClass = resolveScraperClass(companySlug, config);
            Constructor<? extends BaseScraper> constructor =
                    scraperClass.getConstructor(Company.class, ScraperConfig.class);

            BaseScraper scraper = constructor.newInstance(companyOpt.get(), config);
            scraper.setJobRepository(jobRepository);
            scraper.setJobMapper(jobMapper);
            scraper.setScraperRunRepository(scraperRunRepository);
            scraper.setOutboxEventService(outboxEventService);
            scraper.setScraperMetrics(scraperMetrics);
            scraper.setGreenhouseBaseUrl(greenhouseBaseUrl);
            scraper.setObjectMapper(objectMapper);
            scraper.setGeocodingClient(geocodingClient);
            scraper.setScraperRunTimeoutMinutes(scraperRunTimeoutMinutes);
            scraper.setFetchTimeoutMs(fetchTimeoutMs);
            Map<String, String> boardSlugs = Map.of(
                "anthropic", boardAnthropic,
                "coinbase", boardCoinbase,
                "stripe", boardStripe,
                "figma", boardFigma,
                "razorpay", boardRazorpay
            );
            if (boardSlugs.containsKey(companySlug)) {
                scraper.setBoardSlug(boardSlugs.get(companySlug));
            }
            resolveBoardSlugFromConfig(config).ifPresent(scraper::setBoardSlug);
            if ("google".equals(companySlug) && scraper instanceof com.jobportal.service.scraper.impl.GoogleScraper gs) {
                gs.setMaxPages(googleMaxPages);
            }

            log.debug("Created scraper '{}' for company '{}'", scraperClass.getSimpleName(), companySlug);
            return Optional.of(scraper);

        } catch (Exception e) {
            log.error("Failed to instantiate scraper for '{}': {}", companySlug, SafeErrorSummary.from(e));
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends BaseScraper> resolveScraperClass(String companySlug, ScraperConfig config)
            throws ClassNotFoundException {
        if (config.getScraperClass() != null && !config.getScraperClass().isBlank()) {
            Class<?> configuredClass = Class.forName(config.getScraperClass());
            if (!BaseScraper.class.isAssignableFrom(configuredClass)) {
                throw new IllegalArgumentException(
                        "Configured scraperClass does not extend BaseScraper: " + config.getScraperClass());
            }
            return (Class<? extends BaseScraper>) configuredClass;
        }

        return registry.getScraperFor(companySlug)
                .orElseThrow(() -> ScraperException.notRegistered(companySlug));
    }

    private Optional<String> resolveBoardSlugFromConfig(ScraperConfig config) {
        if (config.getCustomConfig() == null || config.getCustomConfig().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(config.getCustomConfig());
            String boardSlug = root.path("greenhouseBoardSlug").asText("");
            return boardSlug.isBlank() ? Optional.empty() : Optional.of(boardSlug);
        } catch (Exception e) {
            log.warn("Failed to parse scraper customConfig id={}: {}", config.getId(), SafeErrorSummary.from(e));
            return Optional.empty();
        }
    }
}
