package com.jobportal.service.scraper;

import com.jobportal.service.scraper.impl.AnthropicScraper;
import com.jobportal.service.scraper.impl.CoinbaseScraper;
import com.jobportal.service.scraper.impl.FigmaScraper;
import com.jobportal.service.scraper.impl.GoogleScraper;
import com.jobportal.service.scraper.impl.RazorpayScraper;
import com.jobportal.service.scraper.impl.StripeScraper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ScraperRegistry {

    private final Map<String, Class<? extends BaseScraper>> registry = new HashMap<>();

    @PostConstruct
    public void registerAll() {
        registry.put("anthropic", AnthropicScraper.class);
        registry.put("google",   GoogleScraper.class);
        registry.put("stripe",   StripeScraper.class);
        registry.put("figma",    FigmaScraper.class);
        registry.put("coinbase", CoinbaseScraper.class);
        registry.put("razorpay", RazorpayScraper.class);

        log.info("Scraper registry initialized with {} scrapers", registry.size());
    }

    public Optional<Class<? extends BaseScraper>> getScraperFor(String companySlug) {
        Class<? extends BaseScraper> scraperClass = registry.get(companySlug);
        if (scraperClass == null) {
            log.warn("No scraper registered for company slug: {}", companySlug);
        }
        return Optional.ofNullable(scraperClass);
    }

    public Set<String> getAllRegisteredSlugs() {
        return registry.keySet();
    }
}
