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
public class AnthropicScraper extends BaseScraper {


    private static final DateTimeFormatter GH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public AnthropicScraper(Company company, ScraperConfig config) {
        super(company, config);
    }

    @Override
    public String getScraperName() { return "Anthropic Careers Scraper"; }

    @Override
    public List<ScrapedJob> scrapeJobs() throws Exception {
        log.info("[{}] Fetching Greenhouse API for board: {}", getScraperName(), getBoardSlug());
        String url = "https://boards-api.greenhouse.io/v1/boards/" + getBoardSlug() + "/jobs";
        String json = fetchJson(url);
        JsonNode root = objectMapper.readTree(json);
        JsonNode jobsNode = root.get("jobs");
        if (jobsNode == null || !jobsNode.isArray()) {
            log.warn("[{}] No jobs array in response", getScraperName());
            return Collections.emptyList();
        }
        List<ScrapedJob> result = new ArrayList<>();
        for (JsonNode node : jobsNode) {
            throwIfCancellationRequested();
            try {
                ScrapedJob job = parseJob(node);
                if (job != null) {
                    String jobId = node.path("id").asText(null);
                    if (jobId != null && !jobId.isBlank()) {
                        job.setDescription(fetchGreenhouseDescription(getBoardSlug(), jobId));
                        Thread.sleep(300);
                    }
                    result.add(job);
                }
            } catch (Exception e) {
                log.debug("[{}] Error parsing job: {}", getScraperName(), SafeErrorSummary.from(e));
            }
        }
        log.info("[{}] Fetched {} jobs", getScraperName(), result.size());
        return result;
    }

    private ScrapedJob parseJob(JsonNode node) {
        String title    = node.path("title").asText("").trim();
        String applyUrl = node.path("absolute_url").asText("").trim();
        if (title.isBlank() || applyUrl.isBlank()) return null;
        String locationText = node.path("location").path("name").asText("").trim();
        Instant postedAt = Instant.now();
        String updatedAt = node.path("updated_at").asText("");
        if (!updatedAt.isBlank()) {
            try {
                postedAt = java.time.LocalDateTime
                        .parse(updatedAt.substring(0, 19), GH_FORMATTER)
                        .toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
        }
        String externalId = UUID.nameUUIDFromBytes(
                applyUrl.getBytes(StandardCharsets.UTF_8)).toString();
        List<String> tags = new ArrayList<>();
        tags.add("anthropic");
        tags.add("ai");
        tags.add("safety");
        String t = title.toLowerCase();
        if (t.contains("engineer") || t.contains("developer")) tags.add("engineering");
        if (t.contains("research"))                            tags.add("research");
        if (t.contains("ml") || t.contains("ai"))             tags.add("ai-ml");
        if (t.contains("policy") || t.contains("safety"))     tags.add("policy");
        if (t.contains("product"))                             tags.add("product");
        return ScrapedJob.builder()
                .externalId(externalId).title(title)
                .applyUrl(applyUrl).sourceUrl(applyUrl)
                .locationText(locationText)
                .workMode(resolveGreenhouseWorkMode(node, title, locationText))
                .jobType("FULL_TIME")
                .tags(tags).postedAt(postedAt).build();
    }
}
