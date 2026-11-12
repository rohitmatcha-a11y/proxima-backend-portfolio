package com.jobportal.service.scraper.impl;

import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.enums.ExperienceLevel;
import com.jobportal.service.scraper.BaseScraper;
import com.jobportal.service.scraper.model.ScrapedJob;
import com.jobportal.util.SafeErrorSummary;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class GoogleScraper extends BaseScraper {

    private static final String BASE_URL = "https://careers.google.com/jobs/results/";
    private static final String CAREERS_BASE = "https://careers.google.com";
    private static final int PAGE_SIZE = 20;
    private int maxPages = 10000;

    public void setMaxPages(int maxPages) { this.maxPages = maxPages; }

    public GoogleScraper(Company company, ScraperConfig config) {
        super(company, config);
    }

    @Override
    public String getScraperName() {
        return "Google Careers Scraper";
    }

    @Override
    public List<ScrapedJob> scrapeJobs() throws Exception {
        List<ScrapedJob> allJobs = new ArrayList<>();
        int page = 1;

        log.info("[{}] Starting full pagination scrape", getScraperName());

        while (page <= maxPages) {
            String url = page == 1
                ? BASE_URL + "?hl=en_US"
                : BASE_URL + "?hl=en_US&page=" + page;

            log.info("[{}] Scraping page {}", getScraperName(), page);

            List<ScrapedJob> pageJobs;
            try {
                pageJobs = scrapePage(url);
            } catch (Exception e) {
                log.error("[{}] Failed on page {}: {}", getScraperName(), page, SafeErrorSummary.from(e));
                break;
            }

            if (pageJobs.isEmpty()) {
                log.info("[{}] Page {} returned 0 jobs — stopping pagination", getScraperName(), page);
                break;
            }

            allJobs.addAll(pageJobs);
            log.info("[{}] Page {} scraped {} jobs — total so far: {}",
                getScraperName(), page, pageJobs.size(), allJobs.size());

            if (pageJobs.size() < PAGE_SIZE) {
                log.info("[{}] Page {} has {} jobs (< {}) — last page reached",
                    getScraperName(), page, pageJobs.size(), PAGE_SIZE);
                break;
            }

            page++;
            int rateLimit = config != null ? config.getRateLimit() : 2000;
            if (rateLimit > 0) Thread.sleep(rateLimit);
        }

        log.info("[{}] Pagination complete — total jobs scraped: {}", getScraperName(), allJobs.size());
        return allJobs;
    }

    private List<ScrapedJob> scrapePage(String url) throws Exception {
        Document doc = fetchPage(url);
        Elements cards = doc.select("li.lLd3Je");

        if (cards.isEmpty()) {
            log.debug("[{}] No job cards found with selector 'li.lLd3Je'", getScraperName());
            return Collections.emptyList();
        }

        List<ScrapedJob> jobs = new ArrayList<>();
        for (Element card : cards) {
            try {
                ScrapedJob job = parseCard(card, url);
                if (job != null) jobs.add(job);
            } catch (Exception e) {
                log.debug("[{}] Error parsing card: {}", getScraperName(), SafeErrorSummary.from(e));
            }
        }
        return jobs;
    }

    private ScrapedJob parseCard(Element card, String sourceUrl) {
        String title = card.select("h3.QJPWVe").text().trim();
        if (title.isBlank()) return null;

        Element link = card.selectFirst("a.WpHeLc");
        if (link == null) return null;
        String href = link.attr("href").trim();
        if (href.isBlank()) return null;

        String applyUrl;
        if (href.startsWith("http")) {
            applyUrl = href;
        } else if (href.startsWith("/")) {
            applyUrl = CAREERS_BASE + href;
        } else {
            applyUrl = CAREERS_BASE + "/" + href;
        }

        // Deterministic UUID from URL — idempotency key
        String externalId = UUID.nameUUIDFromBytes(
            applyUrl.getBytes(StandardCharsets.UTF_8)).toString();

        String rawLocation = card.select("span.r0wTof").text().trim();
        String locationText = deduplicateLocation(rawLocation);

        return ScrapedJob.builder()
            .externalId(externalId)
            .title(title)
            .applyUrl(applyUrl)
            .sourceUrl(sourceUrl)
            .locationText(locationText)
            .workMode(detectWorkMode(title, locationText))
            .jobType("FULL_TIME")
            .tags(buildTags(title))
            .postedAt(Instant.now())
            .build();
    }

    private String deduplicateLocation(String location) {
        if (location == null || location.isBlank()) return location;
        int mid = location.length() / 2;
        if (location.length() % 2 == 0) {
            String first = location.substring(0, mid).trim();
            String second = location.substring(mid).trim();
            if (first.equals(second)) return first;
        }
        // Also handle "X X" pattern with space separator
        String[] parts = location.split(" ", 2);
        if (parts.length == 2 && parts[0].trim().equals(parts[1].trim())) return parts[0].trim();
        // Handle exact half duplication with space: "Miami, FL, USA Miami, FL, USA"
        int spaceHalf = location.indexOf(location.substring(0, location.length() / 2).trim());
        String half = location.substring(0, (location.length() + 1) / 2).trim();
        if ((half + " " + half).equals(location)) return half;
        return location;
    }

    private List<String> buildTags(String title) {
        List<String> tags = new ArrayList<>();
        tags.add("google");
        String t = title.toLowerCase();
        if (t.contains("software") || t.contains("engineer") || t.contains("developer"))
            tags.add("engineering");
        if (t.contains("ml") || t.contains("ai") || t.contains("machine learning"))
            tags.add("ai-ml");
        if (t.contains("cloud")) tags.add("cloud");
        if (t.contains("android") || t.contains("ios")) tags.add("mobile");
        if (t.contains("data")) tags.add("data");
        if (t.contains("security")) tags.add("security");
        if (t.contains("product")) tags.add("product");
        if (t.contains("sales") || t.contains("account")) tags.add("sales");
        if (t.contains("ux") || t.contains("design")) tags.add("design");
        return tags;
    }
}
