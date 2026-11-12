package com.jobportal.service.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.service.scraper.BaseScraper;
import com.jobportal.service.scraper.CareerSiteProvider;
import com.jobportal.service.scraper.CareerSiteProviderDetector;
import com.jobportal.service.scraper.model.ScrapedJob;
import com.jobportal.util.SafeErrorSummary;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A configuration-driven scraper for public careers sites. It covers the public
 * Greenhouse, Lever, and Ashby job APIs, then falls back to schema.org JobPosting
 * markup for other careers pages. Adding a company therefore does not require a
 * bespoke scraper class.
 */
@Slf4j
public class UniversalCareerSiteScraper extends BaseScraper {

    public UniversalCareerSiteScraper(Company company, ScraperConfig config) {
        super(company, config);
    }

    @Override
    public String getScraperName() {
        return company.getName() + " Career Site Scraper";
    }

    @Override
    public List<ScrapedJob> scrapeJobs() throws Exception {
        String careersUrl = company.getCareersPageUrl();
        CareerSiteProviderDetector.Detection detection = configuredDetection(careersUrl);

        if (detection.provider() == CareerSiteProvider.GREENHOUSE && hasBoard(detection)) {
            return scrapeGreenhouse(detection.board());
        }
        if (detection.provider() == CareerSiteProvider.LEVER && hasBoard(detection)) {
            return scrapeLever(detection.board());
        }
        if (detection.provider() == CareerSiteProvider.ASHBY && hasBoard(detection)) {
            return scrapeAshby(detection.board());
        }
        // A company-owned careers URL may embed a supported ATS board. Resolve
        // that at scrape time before using the standards-based fallback.
        Document careersPage = fetchPage(careersUrl);
        CareerSiteProviderDetector.Detection landingPage = CareerSiteProviderDetector.detect(careersPage.location());
        if (landingPage.provider() != CareerSiteProvider.STRUCTURED_DATA && hasBoard(landingPage)) {
            log.info("[{}] Detected {} after careers URL redirect", getScraperName(), landingPage.provider());
            return switch (landingPage.provider()) {
                case GREENHOUSE -> scrapeGreenhouse(landingPage.board());
                case LEVER -> scrapeLever(landingPage.board());
                case ASHBY -> scrapeAshby(landingPage.board());
                case STRUCTURED_DATA -> scrapeStructuredData(careersPage);
            };
        }
        var embedded = CareerSiteProviderDetector.detectEmbedded(careersPage);
        if (embedded.isPresent()) {
            CareerSiteProviderDetector.Detection source = embedded.get();
            log.info("[{}] Detected embedded {} board '{}'", getScraperName(), source.provider(), source.board());
            return switch (source.provider()) {
                case GREENHOUSE -> scrapeGreenhouse(source.board());
                case LEVER -> scrapeLever(source.board());
                case ASHBY -> scrapeAshby(source.board());
                case STRUCTURED_DATA -> scrapeStructuredData(careersPage);
            };
        }

        return scrapeStructuredData(careersPage);
    }

    private CareerSiteProviderDetector.Detection configuredDetection(String careersUrl) {
        CareerSiteProviderDetector.Detection inferred = CareerSiteProviderDetector.detect(careersUrl);
        if (config.getCustomConfig() == null || config.getCustomConfig().isBlank()) return inferred;

        try {
            JsonNode custom = objectMapper.readTree(config.getCustomConfig());
            CareerSiteProvider provider = CareerSiteProvider.valueOf(
                    custom.path("provider").asText(inferred.provider().name()));
            String board = text(custom, "board");
            if (board == null) board = text(custom, "greenhouseBoardSlug");
            if (board == null) board = inferred.board();
            return new CareerSiteProviderDetector.Detection(provider, board);
        } catch (Exception e) {
            log.warn("[{}] Invalid scraper config; re-detecting provider: {}", getScraperName(), SafeErrorSummary.from(e));
            return inferred;
        }
    }

    private List<ScrapedJob> scrapeGreenhouse(String board) throws Exception {
        JsonNode jobs = objectMapper.readTree(fetchJson(
                "https://boards-api.greenhouse.io/v1/boards/" + board + "/jobs")).path("jobs");
        if (!jobs.isArray()) return Collections.emptyList();

        List<ScrapedJob> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            throwIfCancellationRequested();
            String title = text(job, "title");
            String applyUrl = text(job, "absolute_url");
            if (blank(title) || blank(applyUrl)) continue;

            String id = text(job, "id");
            String description = blank(id) ? null : fetchGreenhouseDescription(board, id);
            String location = job.path("location").path("name").asText("");
            result.add(job(id, title, description, applyUrl, location,
                    text(job, "updated_at"), "FULL_TIME", detectWorkMode(title, location)));
        }
        log.info("[{}] Imported {} Greenhouse jobs", getScraperName(), result.size());
        return result;
    }

    private List<ScrapedJob> scrapeLever(String board) throws Exception {
        JsonNode jobs = objectMapper.readTree(fetchJson(
                "https://api.lever.co/v0/postings/" + board + "?mode=json"));
        if (!jobs.isArray()) return Collections.emptyList();

        List<ScrapedJob> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            throwIfCancellationRequested();
            String title = text(job, "text");
            String applyUrl = first(text(job, "applyUrl"), text(job, "hostedUrl"));
            if (blank(title) || blank(applyUrl)) continue;

            String location = textAt(job, "/categories/location");
            String description = first(text(job, "descriptionPlain"), text(job, "description"));
            result.add(job(text(job, "id"), title, description, applyUrl, location,
                    job.path("createdAt"), textAt(job, "/categories/commitment"),
                    first(text(job, "workplaceType"), detectWorkMode(title, location))));
        }
        log.info("[{}] Imported {} Lever jobs", getScraperName(), result.size());
        return result;
    }

    private List<ScrapedJob> scrapeAshby(String board) throws Exception {
        if (!isActiveAshbyBoard(board)) {
            log.warn("[{}] Ashby board '{}' is not publicly active; ignoring stale API postings", getScraperName(), board);
            return Collections.emptyList();
        }

        JsonNode jobs = objectMapper.readTree(fetchJson(
                "https://api.ashbyhq.com/posting-api/job-board/" + board)).path("jobs");
        if (!jobs.isArray()) return Collections.emptyList();

        List<ScrapedJob> result = new ArrayList<>();
        for (JsonNode job : jobs) {
            throwIfCancellationRequested();
            String title = text(job, "title");
            // Ashby's /application endpoint is not consistently public, whereas jobUrl
            // is the stable public job page containing the employer's Apply action.
            String applyUrl = absoluteUrl(first(text(job, "jobUrl"), text(job, "applyUrl")),
                    "https://jobs.ashbyhq.com/" + board);
            if (blank(title) || blank(applyUrl)) continue;

            String location = first(text(job, "location"), text(job, "locationName"));
            String description = first(text(job, "descriptionHtml"), text(job, "description"));
            result.add(job(first(text(job, "id"), text(job, "jobId")), title, description, applyUrl, location,
                    first(text(job, "publishedAt"), text(job, "createdAt")), text(job, "employmentType"),
                    first(text(job, "workplaceType"), detectWorkMode(title, location))));
        }
        log.info("[{}] Imported {} Ashby jobs", getScraperName(), result.size());
        return result;
    }

    /**
     * Ashby's public API can retain jobs after a board has been retired or moved.
     * Confirm that the public board still exposes organization data before accepting
     * those records, otherwise users would be sent to Ashby's 404 page.
     */
    private boolean isActiveAshbyBoard(String board) throws Exception {
        Document boardPage = fetchPage("https://jobs.ashbyhq.com/" + board);
        return boardPage.select("script").stream()
                .map(Element::data)
                .anyMatch(script -> script.contains("window.__appData")
                        && !script.contains("\"organization\":null"));
    }

    private List<ScrapedJob> scrapeStructuredData(Document document) {
        List<JsonNode> postings = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                collectJobPostings(objectMapper.readTree(script.data()), postings);
            } catch (Exception e) {
                log.debug("[{}] Ignoring invalid JSON-LD block: {}", getScraperName(), SafeErrorSummary.from(e));
            }
        }

        List<ScrapedJob> result = new ArrayList<>();
        for (JsonNode posting : postings) {
            throwIfCancellationRequested();
            String title = first(text(posting, "title"), text(posting, "name"));
            String applyUrl = absoluteUrl(text(posting, "url"), company.getCareersPageUrl());
            if (blank(title) || blank(applyUrl)) continue;

            String location = schemaLocation(posting.path("jobLocation"));
            result.add(job(first(identifier(posting.path("identifier")), text(posting, "@id")), title,
                    text(posting, "description"), applyUrl, location, text(posting, "datePosted"),
                    text(posting, "employmentType"), detectWorkMode(title, location)));
        }
        log.info("[{}] Imported {} schema.org JobPosting entries", getScraperName(), result.size());
        return result;
    }

    private void collectJobPostings(JsonNode node, List<JsonNode> postings) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(child -> collectJobPostings(child, postings));
            return;
        }
        if (!node.isObject()) return;
        if (isJobPosting(node.path("@type"))) postings.add(node);
        node.elements().forEachRemaining(child -> collectJobPostings(child, postings));
    }

    private boolean isJobPosting(JsonNode type) {
        if (type.isTextual()) return "jobposting".equalsIgnoreCase(type.asText());
        if (type.isArray()) {
            for (JsonNode value : type) {
                if ("jobposting".equalsIgnoreCase(value.asText())) return true;
            }
        }
        return false;
    }

    private ScrapedJob job(String externalKey, String title, String description, String applyUrl, String location,
                            JsonNode postedAt, String jobType, String workMode) {
        Instant posted = postedAt != null && postedAt.isNumber() ? instant(postedAt.asLong()) : null;
        return job(externalKey, title, description, applyUrl, location,
                posted == null ? null : posted.toString(), jobType, workMode);
    }

    private ScrapedJob job(String externalKey, String title, String description, String applyUrl, String location,
                            String postedAt, String jobType, String workMode) {
        String sourceUrl = absoluteUrl(applyUrl, company.getCareersPageUrl());
        String idSeed = first(externalKey, sourceUrl);
        return ScrapedJob.builder()
                .externalId(UUID.nameUUIDFromBytes((company.getSlug() + ":" + idSeed)
                        .getBytes(StandardCharsets.UTF_8)).toString())
                .title(title.trim())
                .description(blank(description) ? "Job details are available on the application page." : stripHtml(description))
                .applyUrl(sourceUrl)
                .sourceUrl(sourceUrl)
                .locationText(blank(location) ? null : location.trim())
                .workMode(blank(workMode) ? detectWorkMode(title, location) : workMode)
                .experienceLevel(detectExperienceLevel(title) == null ? null : detectExperienceLevel(title).name())
                .jobType(normalizeJobType(jobType))
                .tags(List.of(company.getSlug(), "auto-imported"))
                .postedAt(parseInstant(postedAt))
                .build();
    }

    private String schemaLocation(JsonNode locations) {
        JsonNode location = locations.isArray() && !locations.isEmpty() ? locations.get(0) : locations;
        JsonNode address = location.path("address");
        if (address.isTextual()) return address.asText();
        return join(
                text(address, "addressLocality"),
                text(address, "addressRegion"),
                text(address, "addressCountry"));
    }

    private String identifier(JsonNode identifier) {
        if (identifier.isTextual()) return identifier.asText();
        return first(text(identifier, "value"), text(identifier, "name"));
    }

    private Instant parseInstant(String value) {
        if (blank(value)) return Instant.now();
        try { return Instant.parse(value); } catch (Exception ignored) {}
        try { return OffsetDateTime.parse(value).toInstant(); } catch (Exception ignored) {}
        return Instant.now();
    }

    private Instant instant(long epoch) {
        return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
    }

    private String normalizeJobType(String value) {
        if (blank(value)) return "FULL_TIME";
        String type = value.toUpperCase().replace('-', '_').replace(' ', '_');
        if (type.contains("PART")) return "PART_TIME";
        if (type.contains("INTERN")) return "INTERNSHIP";
        if (type.contains("CONTRACT")) return "CONTRACT";
        if (type.contains("FREELANCE")) return "FREELANCE";
        if (type.contains("TEMP")) return "TEMPORARY";
        return "FULL_TIME";
    }

    private String absoluteUrl(String value, String baseUrl) {
        if (blank(value)) return baseUrl;
        try { return URI.create(baseUrl).resolve(value).toString(); }
        catch (Exception ignored) { return value; }
    }

    private boolean hasBoard(CareerSiteProviderDetector.Detection detection) {
        return !blank(detection.board());
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? null : value;
    }

    private String textAt(JsonNode node, String pointer) {
        JsonNode target = node.at(pointer);
        String value = target.asText("").trim();
        return value.isBlank() ? null : value;
    }

    private String first(String... values) {
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private String join(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) if (!blank(value)) parts.add(value.trim());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
