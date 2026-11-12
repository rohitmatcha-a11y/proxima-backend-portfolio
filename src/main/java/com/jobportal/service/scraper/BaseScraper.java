package com.jobportal.service.scraper;

import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.Job;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.entity.ScraperRun;
import com.jobportal.domain.enums.ScraperRunStatus;
import com.jobportal.domain.enums.JobStatus;
import com.jobportal.repository.jpa.JobRepository;
import com.jobportal.repository.jpa.ScraperRunRepository;
import com.jobportal.service.scraper.mapper.JobMapper;
import com.jobportal.service.scraper.model.ScrapeResult;
import com.jobportal.service.scraper.model.ScrapedJob;
import com.jobportal.client.geocoding.GeocodingClient;
import com.jobportal.client.geocoding.GeoLocation;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.domain.enums.ExperienceLevel;
import com.jobportal.kafka.producer.ScraperEventProducer;
import com.jobportal.service.outbox.OutboxEventService;
import com.jobportal.kafka.KafkaTopics;
import com.jobportal.kafka.event.JobIndexingRequestedEvent;
import com.jobportal.kafka.event.VisaDetectionRequestedEvent;
import com.jobportal.observability.ScraperMetrics;
import com.jobportal.util.SafeErrorSummary;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

@Slf4j
public abstract class BaseScraper {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobPortalBot/1.0)";
    private int fetchTimeoutMs = 10_000; // default, overridden by ScraperFactory

    protected final Company company;
    protected final ScraperConfig config;

    private JobRepository jobRepository;
    private JobMapper jobMapper;
    private ScraperRunRepository scraperRunRepository;
    private OutboxEventService outboxEventService;
    private ScraperMetrics scraperMetrics;
    protected ObjectMapper objectMapper;
    protected GeocodingClient geocodingClient;

    private String greenhouseBaseUrl;
    private String boardSlug;
    private int scraperRunTimeoutMinutes = 120; // default, overridden by ScraperFactory
    private BooleanSupplier cancellationRequested = () -> false;

    public void setBoardSlug(String boardSlug) {
        this.boardSlug = boardSlug;
    }

    public void setScraperRunTimeoutMinutes(int scraperRunTimeoutMinutes) {
        this.scraperRunTimeoutMinutes = scraperRunTimeoutMinutes;
    }

    public void setFetchTimeoutMs(int fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    public void setCancellationRequested(BooleanSupplier cancellationRequested) {
        this.cancellationRequested = cancellationRequested != null ? cancellationRequested : () -> false;
    }

    protected String getBoardSlug() {
        return boardSlug;
    }

    // per-run state, reset on each execute()
    private List<Long> newJobIds;

    protected BaseScraper(Company company, ScraperConfig config) {
        this.company = company;
        this.config = config;
    }

    // --- abstract contract ---

    public abstract List<ScrapedJob> scrapeJobs() throws Exception;

    public abstract String getScraperName();

    // --- setter injection (called by ScraperFactory) ---

    public void setJobRepository(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public void setJobMapper(JobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    public void setScraperRunRepository(ScraperRunRepository scraperRunRepository) {
        this.scraperRunRepository = scraperRunRepository;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    public void setGeocodingClient(GeocodingClient geocodingClient) {
        this.geocodingClient = geocodingClient;
    }

    public void setOutboxEventService(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    public void setScraperMetrics(ScraperMetrics scraperMetrics) {
        this.scraperMetrics = scraperMetrics;
    }

    public void setGreenhouseBaseUrl(String greenhouseBaseUrl) {
        this.greenhouseBaseUrl = greenhouseBaseUrl;
    }


    // --- orchestration ---

    public ScrapeResult execute(ScraperRun run) {
        newJobIds = new ArrayList<>();
        Instant start = Instant.now();
        long startTime = System.currentTimeMillis();

        // Check for zombie runs from prior executions
        Optional<ScraperRun> zombieOpt = scraperRunRepository
                .findTopByCompanyIdOrderByCreatedAtDesc(company.getId());
        if (zombieOpt.isPresent()) {
            ScraperRun zombie = zombieOpt.get();
            if (zombie.getStatus() == ScraperRunStatus.RUNNING
                    && !zombie.getRunId().equals(run.getRunId())
                    && zombie.getStartedAt() != null) {
                long minutesRunning = ChronoUnit.MINUTES.between(
                        zombie.getStartedAt(), Instant.now());
                if (minutesRunning > scraperRunTimeoutMinutes) {
                    log.warn("[{}] Found zombie run {} running for {} minutes — marking INTERRUPTED",
                            getScraperName(), zombie.getRunId(), minutesRunning);
                    zombie.setStatus(ScraperRunStatus.INTERRUPTED);
                    zombie.setErrorMessage("Interrupted by new run after " + minutesRunning + " minutes");
                    scraperRunRepository.save(zombie);
                }
            }
        }

        run.setStatus(ScraperRunStatus.RUNNING);
        run.setStartedAt(start);
        scraperRunRepository.save(run);

        log.info("[{}] Scrape started for company '{}' runId={}",
                getScraperName(), company.getSlug(), run.getRunId());

        try {
            throwIfCancellationRequested();
            List<ScrapedJob> scraped = scrapeJobs();
            throwIfCancellationRequested();
            run.setJobsFound(scraped.size());

            for (ScrapedJob job : scraped) {
                throwIfCancellationRequested();
                try {
                    saveJob(job, run.getRunId(), run);
                } catch (Exception e) {
                    run.setJobsFailed(run.getJobsFailed() + 1);
                    log.warn("[{}] Failed to save job '{}': {}", getScraperName(), job.getTitle(), SafeErrorSummary.from(e));
                }
            }

            throwIfCancellationRequested();
            int jobsExpired = expireJobsNoLongerInSource(run, scraped);

            run.setStatus(ScraperRunStatus.COMPLETED);

            if (jobsExpired > 0) {
                log.info("[{}] Marked {} jobs as expired because they are no longer in the source", getScraperName(), jobsExpired);
            }

            return ScrapeResult.success(company.getSlug(),
                    run.getJobsFound(), run.getJobsNew(), 0,
                    run.getJobsSkipped(), start, newJobIds);

        } catch (Exception e) {
            if (isCancellationRequested() || run.getStatus() == ScraperRunStatus.INTERRUPTED) {
                run.setStatus(ScraperRunStatus.INTERRUPTED);
                run.setErrorMessage("Stopped manually by an administrator");
                log.info("[{}] Scrape interrupted by administrator", getScraperName());
            } else {
                run.setStatus(ScraperRunStatus.FAILED);
                String errorSummary = SafeErrorSummary.from(e);
                run.setErrorMessage(errorSummary);
                log.error("[{}] Scrape failed: {}", getScraperName(), errorSummary);
            }
            throw new RuntimeException(e);

        } finally {
            run.setCompletedAt(Instant.now());
            scraperRunRepository.save(run);

            long durationMs = System.currentTimeMillis() - startTime;
            long durationSec = durationMs / 1000;
            long minutes = durationSec / 60;
            long seconds = durationSec % 60;
            if (scraperMetrics != null) {
                try {
                    scraperMetrics.recordRun(getScraperName(), run, durationMs);
                } catch (Exception metricsException) {
                    // Observability must never affect scraping or persistence.
                    log.warn("[{}] Failed to record scraper metrics: {}", getScraperName(), SafeErrorSummary.from(metricsException));
                }
            }
            log.info("[{}] Scrape complete — company={} found={} new={} skipped={} failed={} duration={}s status={}",
                    getScraperName(), company.getName(), run.getJobsFound(), run.getJobsNew(),
                    run.getJobsSkipped(), run.getJobsFailed(), durationSec, run.getStatus());
        }
    }


    // ── Description helpers ──

    protected String stripHtml(String html) {
        if (html == null || html.isBlank()) return null;
        String clean = html
            .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
            .replaceAll("(?i)<(br|p|div|li|tr|h[1-6])[^>]*>", "\n")
            .replaceAll("<[^>]*>?", " ")
            .replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&nbsp;", " ")
            .replace("&ndash;", "-")
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
        return clean.length() > 8000
            ? clean.substring(0, 8000) + "..." : clean;
    }

    protected String fetchGreenhouseDescription(
            String boardSlug, String jobId) {
        try {
            throwIfCancellationRequested();
            String url = greenhouseBaseUrl + "/" + boardSlug + "/jobs/" + jobId + "?content=true";
            String json = fetchJson(url);
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.get("content");
            if (content != null && !content.isNull()) {
                String raw = content.textValue();
                if (raw == null) return null;
                // Greenhouse API returns HTML entities — decode them
                return raw
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&nbsp;", " ")
                    .replace("&mdash;", "-")
                    .replace("&ndash;", "-");
            }
        } catch (ScrapeCancelledException e) {
            throw e;
        } catch (Exception e) {
            log.debug("[{}] Could not fetch description for job {}: {}",
                getScraperName(), jobId, SafeErrorSummary.from(e));
        }
        return null;
    }

    // --- protected helpers ---

    protected Document fetchPage(String url) {
        try {
            throwIfCancellationRequested();
            int rateLimit = config != null ? config.getRateLimit() : 2000;
            if (rateLimit > 0) {
                Thread.sleep(rateLimit);
            }
            throwIfCancellationRequested();

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(fetchTimeoutMs)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "HTTP " + response.statusCode() + " fetching URL: " + url);
            }

            return response.parse();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during rate-limit sleep", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch page: " + url, e);
        }
    }

    protected String extractText(Element element, String cssSelector) {
        try {
            Element found = element.selectFirst(cssSelector);
            return found != null ? found.text() : "";
        } catch (Exception e) {
            log.debug("extractText failed for selector '{}': {}", cssSelector, SafeErrorSummary.from(e));
            return "";
        }
    }

    protected String extractAttr(Element element, String cssSelector, String attr) {
        try {
            Element found = element.selectFirst(cssSelector);
            return found != null ? found.attr(attr) : "";
        } catch (Exception e) {
            log.debug("extractAttr failed for selector '{}' attr '{}': {}", cssSelector, attr, SafeErrorSummary.from(e));
            return "";
        }
    }

    protected String fetchJson(String urlStr) throws Exception {
        throwIfCancellationRequested();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; JobPortal/1.0)");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("HTTP " + status + " from: " + urlStr);
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                throwIfCancellationRequested();
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    protected ExperienceLevel detectExperienceLevel(String title) {
        String t = title.toLowerCase();
        if (t.contains("manager") || t.contains("director") ||
                t.contains("head of") || t.contains("vp ") ||
                t.contains("vice president"))
            return ExperienceLevel.LEAD;
        if (t.contains("senior") || t.contains("staff") ||
                t.contains("lead") || t.contains("principal"))
            return ExperienceLevel.SENIOR;
        if (t.contains("junior") || t.contains("associate") ||
                t.contains("entry"))
            return ExperienceLevel.JUNIOR;
        if (t.contains("intern"))
            return ExperienceLevel.INTERN;
        return null;
    }

    protected String detectWorkMode(String title, String location) {
        String combined = (title + " " + (location != null ? location : "")).toLowerCase();
        if (combined.contains("remote")) return "REMOTE";
        if (combined.contains("hybrid")) return "HYBRID";
        if (combined.contains("onsite") || combined.contains("on-site")
                || combined.contains("in-office") || combined.contains("in office"))
            return "ONSITE";
        return null;
    }

    // --- private helpers ---

    private void saveJob(ScrapedJob scrapedJob, String runId, ScraperRun run) {
        throwIfCancellationRequested();
        Optional<Job> existing = jobRepository.findByExternalIdAndCompanyId(scrapedJob.getExternalId(), company.getId());
        if (existing.isPresent()) {
            Job job = existing.get();
            boolean wasInactive = job.getStatus() != JobStatus.ACTIVE;
            boolean applicationLinkChanged = !java.util.Objects.equals(job.getApplyUrl(), scrapedJob.getApplyUrl())
                    || !java.util.Objects.equals(job.getSourceUrl(), scrapedJob.getSourceUrl());

            // An ATS can change where an application starts while retaining the same
            // job identifier. Refresh those links without creating a duplicate job.
            if (applicationLinkChanged) {
                job.setApplyUrl(scrapedJob.getApplyUrl());
                job.setSourceUrl(scrapedJob.getSourceUrl());
            }
            job.setScraperRunId(runId);
            job.setStatus(JobStatus.ACTIVE);
            job.setExpiresAt(null);
            jobRepository.save(job);
            run.setJobsSkipped(run.getJobsSkipped() + 1);
            if (wasInactive || applicationLinkChanged) {
                publishIndexingEvent(job, ScraperConstants.ACTION_INDEX);
            }
            log.debug("[{}] Reconfirmed existing job — externalId='{}'", getScraperName(), scrapedJob.getExternalId());
            return;
        }

        Job job = jobMapper.toEntity(scrapedJob, company);
        job.setScraperRunId(runId);
        jobRepository.save(job);
        run.setJobsNew(run.getJobsNew() + 1);
        newJobIds.add(job.getId());

        // Publish Kafka events async (non-blocking)
        publishJobEventsAsync(job);

        log.debug("[{}] Saved new job id={} title='{}'",
                getScraperName(), job.getId(), scrapedJob.getTitle());
    }

    private void publishJobEventsAsync(Job job) {
        if (outboxEventService == null) return;
        try {
            publishIndexingEvent(job, ScraperConstants.ACTION_INDEX);
            // Event 2: Geocoding — consumer resolves city/country/continent/workMode async
            outboxEventService.saveEvent(
                String.valueOf(job.getId()),
                "JOB_GEOCODING_REQUESTED",
                KafkaTopics.JOB_GEOCODING_REQUESTED,
                com.jobportal.kafka.event.JobGeocodingRequestedEvent.builder()
                    .jobId(job.getId())
                    .locationText(job.getLocationText())
                    .title(job.getTitle())
                    .companySlug(company.getSlug())
                    .build()
            );
            // Event 3: Visa detection — consumer calls Claude API async
            outboxEventService.saveEvent(
                String.valueOf(job.getId()),
                "JOB_VISA_DETECTION_REQUESTED",
                KafkaTopics.JOB_VISA_DETECTION_REQUESTED,
                VisaDetectionRequestedEvent.builder()
                    .jobId(job.getId())
                    .jobTitle(job.getTitle())
                    .companySlug(company.getSlug())
                    .jobDescription(job.getDescription())
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to save outbox events for job {}: {}", job.getId(), SafeErrorSummary.from(e));
        }
    }

    private int expireJobsNoLongerInSource(ScraperRun run, List<ScrapedJob> scraped) {
        if (scraped.isEmpty() || run.getJobsFailed() > 0) {
            log.warn("[{}] Skipping stale-job reconciliation because the scrape was empty or had {} save failures",
                    getScraperName(), run.getJobsFailed());
            return 0;
        }

        List<Job> staleJobs = jobRepository.findActiveJobsNotSeenInRun(company.getId(), run.getRunId());
        Instant now = Instant.now();
        for (Job job : staleJobs) {
            throwIfCancellationRequested();
            job.setStatus(JobStatus.EXPIRED);
            job.setExpiresAt(now);
        }
        jobRepository.saveAll(staleJobs);
        staleJobs.forEach(job -> publishIndexingEvent(job, ScraperConstants.ACTION_DELETE));
        return staleJobs.size();
    }

    protected final boolean isCancellationRequested() {
        return Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean();
    }

    protected final void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new ScrapeCancelledException();
        }
    }

    private void publishIndexingEvent(Job job, String action) {
        if (outboxEventService == null) return;
        try {
            outboxEventService.saveEvent(
                    String.valueOf(job.getId()),
                    "JOB_INDEXING_REQUESTED_" + action,
                    KafkaTopics.JOB_INDEXING_REQUESTED,
                    JobIndexingRequestedEvent.builder()
                            .jobId(job.getId())
                            .companySlug(company.getSlug())
                            .action(action)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to save {} indexing event for job {}: {}", action, job.getId(), SafeErrorSummary.from(e));
        }
    }

    // ── Greenhouse shared helpers ──

    protected com.jobportal.client.geocoding.GeoLocation resolveGreenhouseLocation(JsonNode jobNode) {
        JsonNode offices = jobNode.path("offices");
        if (offices.isArray() && offices.size() > 0) {
            String officeLocation = offices.get(0).path("location").asText("").trim();
            if (!officeLocation.isBlank()) {
                return resolveFromOfficeLocation(officeLocation);
            }
            String officeName = offices.get(0).path("name").asText("").trim();
            if (!officeName.isBlank() && !officeName.equalsIgnoreCase("remote")) {
                return geocodingClient.resolveLocation(officeName);
            }
        }
        String locationName = jobNode.path("location").path("name").asText("").trim();
        if (!locationName.isBlank()) {
            String first = locationName.split(";")[0].trim();
            return geocodingClient.resolveLocation(first);
        }
        return com.jobportal.client.geocoding.GeoLocation.empty();
    }

    private com.jobportal.client.geocoding.GeoLocation resolveFromOfficeLocation(String officeLocation) {
        String[] parts = officeLocation.split(",");
        if (parts.length >= 2) {
            String city = parts[0].trim();
            String country = parts[parts.length - 1].trim();
            com.jobportal.client.geocoding.GeoLocation resolved = geocodingClient.resolveLocation(country);
            String continent = resolved.continent();
            return new com.jobportal.client.geocoding.GeoLocation(city, country, continent);
        }
        return geocodingClient.resolveLocation(officeLocation);
    }

    protected String resolveGreenhouseWorkMode(JsonNode jobNode, String title, String locationText) {
        JsonNode metadata = jobNode.path("metadata");
        if (metadata.isArray()) {
            for (JsonNode meta : metadata) {
                String name = meta.path("name").asText("").trim();
                if ("Location Type".equalsIgnoreCase(name)) {
                    String value = meta.path("value").asText("").trim();
                    if (!value.isBlank() && !value.equals("null")) {
                        String upper = value.toUpperCase();
                        if (upper.contains("REMOTE"))  return "REMOTE";
                        if (upper.contains("HYBRID"))  return "HYBRID";
                        if (upper.contains("ON-SITE") || upper.contains("ONSITE") || upper.contains("ON_SITE")) return "ONSITE";
                    }
                }
            }
        }
        return detectWorkMode(title, locationText);
    }
}
