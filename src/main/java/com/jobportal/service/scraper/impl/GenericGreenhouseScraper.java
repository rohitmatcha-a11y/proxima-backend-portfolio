package com.jobportal.service.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.service.scraper.BaseScraper;
import com.jobportal.service.scraper.model.ScrapedJob;
import com.jobportal.util.SafeErrorSummary;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class GenericGreenhouseScraper extends BaseScraper {

    private static final DateTimeFormatter GH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public GenericGreenhouseScraper(Company company, ScraperConfig config) {
        super(company, config);
    }

    @Override
    public String getScraperName() {
        return company.getName() + " Greenhouse Scraper";
    }

    @Override
    public List<ScrapedJob> scrapeJobs() throws Exception {
        String boardSlug = getBoardSlug();
        if (boardSlug == null || boardSlug.isBlank()) {
            throw new IllegalArgumentException("Greenhouse board slug is required for " + company.getSlug());
        }

        log.info("[{}] Fetching Greenhouse board: {}", getScraperName(), boardSlug);
        String json = fetchJson("https://boards-api.greenhouse.io/v1/boards/" + boardSlug + "/jobs");
        JsonNode jobsNode = objectMapper.readTree(json).get("jobs");
        if (jobsNode == null || !jobsNode.isArray()) {
            log.warn("[{}] No jobs array in Greenhouse response", getScraperName());
            return Collections.emptyList();
        }

        List<ScrapedJob> result = new ArrayList<>();
        for (JsonNode node : jobsNode) {
            throwIfCancellationRequested();
            try {
                ScrapedJob job = parseJob(node, boardSlug);
                if (job != null) result.add(job);
            } catch (Exception e) {
                log.debug("[{}] Error parsing Greenhouse job: {}", getScraperName(), SafeErrorSummary.from(e));
            }
        }
        log.info("[{}] Fetched {} jobs", getScraperName(), result.size());
        return result;
    }

    private ScrapedJob parseJob(JsonNode node, String boardSlug) throws InterruptedException {
        String title = node.path("title").asText("").trim();
        String applyUrl = node.path("absolute_url").asText("").trim();
        if (title.isBlank() || applyUrl.isBlank()) return null;

        String description = null;
        String jobId = node.path("id").asText(null);
        if (jobId != null && !jobId.isBlank()) {
            description = fetchGreenhouseDescription(boardSlug, jobId);
            Thread.sleep(300);
        }

        String locationText = node.path("location").path("name").asText("").trim();
        String externalId = UUID.nameUUIDFromBytes(applyUrl.getBytes(StandardCharsets.UTF_8)).toString();

        return ScrapedJob.builder()
                .externalId(externalId)
                .title(title)
                .description(description)
                .applyUrl(applyUrl)
                .sourceUrl(applyUrl)
                .locationText(locationText)
                .workMode(resolveGreenhouseWorkMode(node, title, locationText))
                .experienceLevel(detectExperienceLevel(title) != null ? detectExperienceLevel(title).name() : null)
                .jobType("FULL_TIME")
                .tags(buildTags(title))
                .postedAt(resolvePostedAt(node))
                .build();
    }

    private Instant resolvePostedAt(JsonNode node) {
        String updatedAt = node.path("updated_at").asText("");
        if (!updatedAt.isBlank()) {
            try {
                return java.time.LocalDateTime
                        .parse(updatedAt.substring(0, 19), GH_FORMATTER)
                        .toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
        }
        return Instant.now();
    }

    private List<String> buildTags(String title) {
        List<String> tags = new ArrayList<>();
        tags.add(company.getSlug());
        if (company.getIndustry() != null) {
            tags.add(company.getIndustry().name().toLowerCase().replace('_', '-'));
        }
        String t = title.toLowerCase();
        if (t.contains("engineer") || t.contains("developer")) tags.add("engineering");
        if (t.contains("design") || t.contains("ux")) tags.add("design");
        if (t.contains("product")) tags.add("product");
        if (t.contains("data")) tags.add("data");
        if (t.contains("security")) tags.add("security");
        if (t.contains("sales")) tags.add("sales");
        if (t.contains("marketing")) tags.add("marketing");
        return tags;
    }
}
