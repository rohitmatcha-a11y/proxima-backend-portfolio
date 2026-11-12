package com.jobportal.service.scraper;

import com.jobportal.domain.entity.Company;
import com.jobportal.domain.entity.ScraperConfig;
import com.jobportal.domain.entity.ScraperRun;
import com.jobportal.domain.enums.ScraperRunStatus;
import com.jobportal.domain.enums.ScraperStatus;
import com.jobportal.exception.JobPortalException;
import com.jobportal.exception.ScraperException;
import com.jobportal.kafka.event.ScrapeCompletedEvent;
import com.jobportal.kafka.producer.ScraperEventProducer;
import com.jobportal.repository.jpa.CompanyRepository;
import com.jobportal.repository.jpa.ScraperConfigRepository;
import com.jobportal.repository.jpa.ScraperRunRepository;
import com.jobportal.service.scraper.model.ScrapeResult;
import com.jobportal.service.cache.DistributedLockService;
import com.jobportal.util.SafeErrorSummary;
import org.redisson.api.RLock;
import com.jobportal.config.CacheConfig;
import org.springframework.cache.CacheManager;
import jakarta.annotation.PreDestroy;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperOrchestrator {

    private final ScraperFactory factory;
    private final ScraperRegistry registry;
    private final ScraperConfigRepository scraperConfigRepository;
    private final ScraperRunRepository scraperRunRepository;
    private final CompanyRepository companyRepository;
    private final ScraperEventProducer scraperEventProducer;
    private final org.springframework.cache.CacheManager cacheManager;
    private final DistributedLockService distributedLockService;

    @Value("${app.scraper.enabled}")
    private boolean scraperEnabled;

    @Value("${app.scraper.max-failures:3}")
    private int maxConsecutiveFailures;

    @Value("${app.scraper.retry-delays:5,15,30}")
    private String retryDelays;

    @Value("${app.scraper.run-timeout-minutes:10}")
    private int scraperRunTimeoutMinutes;

    @Value("${app.scraper.thread-pool-size:10}")
    private int threadPoolSize;

    private ExecutorService scraperExecutor;

    /** Tracks only work submitted by this JVM so admins can interrupt an active run. */
    private final ConcurrentMap<String, Future<?>> activeScraperTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Thread> activeScraperThreads = new ConcurrentHashMap<>();
    private final Set<String> stopRequested = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingRestarts = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService retryExecutor =
            Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void initExecutor() {
        scraperExecutor = Executors.newFixedThreadPool(threadPoolSize);
        log.info("Scraper thread pool initialized with {} threads", threadPoolSize);
    }

    // -------------------------------------------------------------------------
    // Scheduled entry points
    // -------------------------------------------------------------------------

    @Scheduled(cron = "${app.scraper.cron:0 0 */6 * * *}")
    public void scheduledDailyRun() {
        if (!scraperEnabled) return;
        log.info("Scheduled daily scrape run started");
        triggerAllAsync();
    }

    @Transactional
    @Scheduled(fixedDelay = 300_000)
    public void cleanupStuckRuns() {
        Instant cutoff = Instant.now().minusSeconds(scraperRunTimeoutMinutes * 60L);
        List<ScraperRun> stuck = scraperRunRepository.findStuckRunsWithCompany(cutoff);

        for (ScraperRun run : stuck) {
            log.warn("Marking stuck run {} as INTERRUPTED — started at {}, company={}",
                    run.getRunId(), run.getStartedAt(),
                    run.getCompany() != null ? run.getCompany().getSlug() : "unknown");
            run.setStatus(ScraperRunStatus.INTERRUPTED);
            run.setCompletedAt(Instant.now());
            run.setErrorMessage("Run timed out — marked INTERRUPTED by cleanup job");
            scraperRunRepository.save(run);
        }

        if (!stuck.isEmpty()) {
            log.info("Cleanup complete — {} stuck run(s) marked INTERRUPTED", stuck.size());
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public ScrapeResult runScraper(String companySlug) {
        return runScraperInternal(companySlug, ScraperConstants.TRIGGERED_BY_MANUAL, 0, null);
    }

    /**
     * Trigger a single scraper asynchronously.
     * Returns immediately with a runId.
     */
    public String triggerAsync(String slug) {
        String runId = UUID.randomUUID().toString();
        submitScraperTask(slug, runId, () -> {
            evictAllCaches();
            log.info("[{}] All caches cleared after single scrape", slug);
        });
        return runId;
    }

    /**
     * Trigger ALL enabled scrapers in parallel asynchronously.
     * Returns immediately with map of slug → runId.
     */
    public Map<String, String> triggerAllAsync() {
        List<ScraperConfig> configs = scraperConfigRepository.findByIsEnabledTrue();
        Map<String, String> runIds = new LinkedHashMap<>();
        AtomicInteger remaining = new AtomicInteger(configs.size());

        for (ScraperConfig config : configs) {
            String slug = config.getCompany().getSlug();
            String runId = UUID.randomUUID().toString();
            runIds.put(slug, runId);
            submitScraperTask(slug, runId, () -> {
                if (remaining.decrementAndGet() == 0) {
                    evictAllCaches();
                    log.info("All caches cleared after all {} scrapers completed", configs.size());
                }
            });
            log.info("[{}] Submitted to thread pool — runId={}", slug, runId);
        }

        if (configs.isEmpty()) evictAllCaches();

        log.info("Triggered {} scrapers in parallel: {}", configs.size(), runIds.keySet());
        return runIds;
    }

    /**
     * Requests cancellation for a single active scraper. If the run belongs to a
     * previous JVM (for example after a restart), its persisted RUNNING record is
     * still transitioned to INTERRUPTED so it cannot block future triggers.
     */
    @Transactional
    public StopResult stopScraper(String companySlug) {
        List<ScraperRun> runningRuns = scraperRunRepository
                .findByCompanySlugAndStatus(companySlug, ScraperRunStatus.RUNNING);
        Future<?> task = activeScraperTasks.get(companySlug);
        Thread worker = activeScraperThreads.get(companySlug);
        boolean workerActive = worker != null && worker.isAlive();
        boolean taskQueued = task != null && !task.isDone() && !workerActive;

        if (!workerActive && !taskQueued && runningRuns.isEmpty()) {
            return new StopResult(companySlug, false, 0, "No active scraper run found");
        }

        stopRequested.add(companySlug);
        boolean interruptSent;
        if (workerActive) {
            worker.interrupt();
            interruptSent = true;
        } else {
            // A task still waiting in the executor has no worker or Redis lock yet.
            interruptSent = taskQueued && task.cancel(false);
            if (taskQueued) activeScraperTasks.remove(companySlug, task);
        }
        boolean lockReleased = distributedLockService.forceReleaseScraperLock(companySlug);
        Instant stoppedAt = Instant.now();
        for (ScraperRun run : runningRuns) {
            run.setStatus(ScraperRunStatus.INTERRUPTED);
            run.setCompletedAt(stoppedAt);
            run.setErrorMessage("Stopped manually by an administrator");
        }
        if (!runningRuns.isEmpty()) scraperRunRepository.saveAll(runningRuns);

        // A task cancelled before it starts has no run record and will not execute
        // its completion cleanup, so clear the request immediately in that case.
        if (!workerActive) stopRequested.remove(companySlug);

        String message = interruptSent
                ? "Stop requested; the active scraper is being interrupted"
                : "Stale running record cleared; the scraper is no longer active in this server";
        if (lockReleased) message += "; scraper lock released";
        log.info("[{}] {}", companySlug, message);
        return new StopResult(companySlug, true, runningRuns.size(), message);
    }

    public record StopResult(String slug, boolean stopped, int interruptedRuns, String message) {}

    private void submitScraperTask(String slug, String runId, Runnable onCompletion) {
        Future<?> existing = activeScraperTasks.get(slug);
        Thread existingWorker = activeScraperThreads.get(slug);
        if (existingWorker != null && existingWorker.isAlive()) {
            if (stopRequested.contains(slug) && existing != null && pendingRestarts.add(slug)) {
                log.info("[{}] Queuing trigger until the stopped scraper exits", slug);
                scraperExecutor.execute(() -> {
                    try {
                        existing.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception ignored) {
                        // The stopped worker has already recorded its own outcome.
                    } finally {
                        pendingRestarts.remove(slug);
                    }
                    submitScraperTask(slug, runId, onCompletion);
                });
                return;
            }
            log.info("[{}] Async scraper is already queued or running", slug);
            onCompletion.run();
            return;
        }
        if (existing != null && !existing.isDone()) {
            log.info("[{}] Async scraper is already queued or running", slug);
            onCompletion.run();
            return;
        }
        if (existing != null) activeScraperTasks.remove(slug, existing);

        FutureTask<Void> task = new FutureTask<>(() -> {
            activeScraperThreads.put(slug, Thread.currentThread());
            log.info("[{}] Async scrape started — runId={}", slug, runId);
            try {
                ScrapeResult result = runScraper(slug);
                if (result.isSuccess()) {
                    log.info("[{}] Async scrape complete — found={} new={} skipped={}",
                            slug, result.getJobsFound(), result.getJobsNew(), result.getJobsSkipped());
                } else {
                    log.warn("[{}] Async scrape did not complete: {}", slug, result.getErrorMessage());
                }
            } catch (Exception e) {
                if (stopRequested.contains(slug) || Thread.currentThread().isInterrupted()) {
                    log.info("[{}] Async scrape stopped", slug);
                } else {
                    log.error("[{}] Async scrape failed: {}", slug, SafeErrorSummary.from(e));
                }
            } finally {
                activeScraperThreads.remove(slug, Thread.currentThread());
                activeScraperTasks.remove(slug);
                stopRequested.remove(slug);
                onCompletion.run();
            }
            return null;
        });

        activeScraperTasks.put(slug, task);
        scraperExecutor.execute(task);
    }

    public void resetCircuitBreaker(String companySlug) {
        findConfig(companySlug).ifPresentOrElse(config -> {
            config.setStatus(ScraperStatus.ACTIVE);
            scraperConfigRepository.save(config);
            log.info("Circuit breaker RESET for {}", companySlug);
        }, () -> log.warn("Cannot reset circuit breaker — config not found for {}", companySlug));
    }

    // -------------------------------------------------------------------------
    // Core execution
    // -------------------------------------------------------------------------

    private ScrapeResult runScraperInternal(String companySlug, String triggeredBy,
                                             int retryCount, String parentRunId) {
        Instant runStart = Instant.now();

        // --- circuit breaker check ---
        Optional<ScraperConfig> configOpt = findConfig(companySlug);
        if (configOpt.isPresent()) {
            ScraperConfig config = configOpt.get();
            Company company = config.getCompany();
            if (company != null) {
                long recentFailures = scraperRunRepository.countRecentFailures(
                        company.getId(), Instant.now().minusSeconds(86400));
                if (recentFailures >= maxConsecutiveFailures) {
                    config.setStatus(ScraperStatus.FAILING);
                    scraperConfigRepository.save(config);
                    log.warn("Circuit breaker OPEN for {} — {} consecutive failures",
                            companySlug, recentFailures);
                    throw ScraperException.circuitOpen(companySlug);
                }
            }
        }

        // --- distributed lock guard ---
        RLock scraperLock = distributedLockService.tryAcquireScraperLock(companySlug);
        if (scraperLock == null && canRecoverStaleScraperLock(companySlug)) {
            log.warn("[{}] Recovering stale scraper lock with no active run record", companySlug);
            if (distributedLockService.forceReleaseScraperLock(companySlug)) {
                scraperLock = distributedLockService.tryAcquireScraperLock(companySlug);
            }
        }
        if (scraperLock == null) {
            log.info("[{}] Scraper already running — skipping duplicate trigger", companySlug);
            return ScrapeResult.skipped(companySlug);
        }
        // --- create and persist the run record up front ---
        try {
        if (stopRequested.contains(companySlug)) {
            log.info("[{}] Scrape start cancelled before execution", companySlug);
            return ScrapeResult.failure(companySlug, "Stopped manually by an administrator", runStart);
        }
        Company company = companyRepository.findBySlug(companySlug).orElse(null);
        ScraperRun run = ScraperRun.builder()
                .runId(UUID.randomUUID().toString())
                .company(company)
                .status(ScraperRunStatus.RUNNING)
                .triggeredBy(triggeredBy)
                .retryCount(retryCount)
                .parentRunId(parentRunId)
                .build();
        scraperRunRepository.save(run);

        // --- create scraper (throws ScraperException / ResourceNotFoundException on failure) ---
        BaseScraper scraper;
        try {
            scraper = factory.createScraper(companySlug).orElseThrow(
                    () -> ScraperException.notRegistered(companySlug));
            scraper.setCancellationRequested(() -> stopRequested.contains(companySlug));
        } catch (JobPortalException e) {
            run.setStatus(ScraperRunStatus.FAILED);
            run.setErrorMessage(SafeErrorSummary.from(e));
            run.setCompletedAt(Instant.now());
            scraperRunRepository.save(run);
            throw e;
        }

        // --- execute ---
        try {
            ScrapeResult result = scraper.execute(run);
            updateConfigAfterRun(companySlug, result);
            publishKafkaEvents(result);
            companyRepository.findBySlug(companySlug).ifPresent(c -> {
                c.setLastScrapedAt(Instant.now());
                companyRepository.save(c);
            });
            return result;

        } catch (Exception e) {
            if (stopRequested.contains(companySlug)
                    || run.getStatus() == ScraperRunStatus.INTERRUPTED
                    || Thread.currentThread().isInterrupted()) {
                run.setStatus(ScraperRunStatus.INTERRUPTED);
                run.setCompletedAt(Instant.now());
                run.setErrorMessage("Stopped manually by an administrator");
                scraperRunRepository.save(run);
                log.info("[{}] Scraper run {} interrupted by administrator", companySlug, run.getRunId());
                return ScrapeResult.failure(companySlug, run.getErrorMessage(), runStart);
            }
            String msg = SafeErrorSummary.from(e);
            ScrapeResult failure = ScrapeResult.failure(companySlug, msg, runStart);
            updateConfigOnFailure(companySlug, runStart, msg);
            scheduleRetry(companySlug, triggeredBy, run, retryCount);
            return failure;
        }
        } finally {
            distributedLockService.releaseScraperLock(scraperLock);
        }
    }

    // -------------------------------------------------------------------------
    // Retry logic
    // -------------------------------------------------------------------------

    private void scheduleRetry(String companySlug, String triggeredBy,
                                ScraperRun failedRun, int currentRetryCount) {
        int[] delays = parseRetryDelays();
        if (currentRetryCount >= delays.length) {
            log.warn("Max retries reached for {} — giving up after {} attempts",
                    companySlug, currentRetryCount);
            findConfig(companySlug).ifPresent(config -> {
                config.setStatus(ScraperStatus.FAILING);
                scraperConfigRepository.save(config);
            });
            return;
        }

        int delayMinutes = delays[currentRetryCount];
        int nextRetryCount = currentRetryCount + 1;
        log.info("Scheduling retry {} for {} in {} minutes (runId={})",
                nextRetryCount, companySlug, delayMinutes, failedRun.getRunId());

        retryExecutor.schedule(() -> {
            log.info("Executing retry {} for {}", nextRetryCount, companySlug);
            runScraperInternal(companySlug, triggeredBy, nextRetryCount, failedRun.getRunId());
        }, delayMinutes, TimeUnit.MINUTES);
    }

    private int[] parseRetryDelays() {
        try {
            return Arrays.stream(retryDelays.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .toArray();
        } catch (Exception e) {
            log.warn("Failed to parse retry-delays '{}', using defaults [5,15,30]", retryDelays);
            return new int[]{5, 15, 30};
        }
    }

    // -------------------------------------------------------------------------
    // Kafka event publishing
    // -------------------------------------------------------------------------

    private void publishKafkaEvents(ScrapeResult result) {
        ScrapeCompletedEvent completed = ScrapeCompletedEvent.builder()
                .companySlug(result.getCompanySlug())
                .success(result.isSuccess())
                .jobsFound(result.getJobsFound())
                .jobsNew(result.getJobsNew())
                .jobsUpdated(result.getJobsUpdated())
                .errorMessage(result.getErrorMessage())
                .completedAt(result.getCompletedAt())
                .newJobIds(result.getNewJobIds())
                .build();
        try {
            scraperEventProducer.publishScrapeCompleted(completed);
        } catch (Exception e) {
            log.warn("Failed to publish scrape completed event: {}", SafeErrorSummary.from(e));
        }
        // Per-job indexing and visa events are published in BaseScraper.saveJob() asynchronously
    }

    // -------------------------------------------------------------------------
    // Config update helpers
    // -------------------------------------------------------------------------

    private void updateConfigAfterRun(String companySlug, ScrapeResult result) {
        findConfig(companySlug).ifPresent(config -> {
            config.setLastRunAt(result.getCompletedAt());
            config.setTotalRuns(config.getTotalRuns() + 1);

            if (result.isSuccess()) {
                config.setSuccessfulRuns(config.getSuccessfulRuns() + 1);
                config.setLastSuccessAt(result.getCompletedAt());
                config.setStatus(ScraperStatus.ACTIVE);
                int runs = config.getTotalRuns();
                if (runs > 0) {
                    int avg = (config.getAvgJobsPerRun() * (runs - 1) + result.getJobsFound()) / runs;
                    config.setAvgJobsPerRun(avg);
                }
            } else {
                config.setFailedRuns(config.getFailedRuns() + 1);
                config.setLastErrorAt(result.getCompletedAt());
                config.setLastErrorMessage(result.getErrorMessage());
                config.setStatus(ScraperStatus.FAILING);
            }

            scraperConfigRepository.save(config);
        });
    }

    private void updateConfigOnFailure(String companySlug, Instant runStart, String message) {
        updateConfigAfterRun(companySlug, ScrapeResult.failure(companySlug, message, runStart));
    }

    private Optional<ScraperConfig> findConfig(String companySlug) {
        return scraperConfigRepository.findByCompanySlug(companySlug);
    }

    private boolean canRecoverStaleScraperLock(String companySlug) {
        Thread localWorker = activeScraperThreads.get(companySlug);
        if (localWorker != null && localWorker.isAlive()) return false;
        return scraperRunRepository
                .findByCompanySlugAndStatus(companySlug, ScraperRunStatus.RUNNING)
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PreDestroy
    public void shutdown() {
        scraperExecutor.shutdown();
        retryExecutor.shutdownNow();
        pendingRestarts.clear();
    }

    private void evictAllCaches() {
        try {
            for (String cacheName : java.util.List.of(
                CacheConfig.CACHE_JOB_SEARCH,
                CacheConfig.CACHE_JOB_COMPANY,
                CacheConfig.CACHE_JOB_DETAIL,
                CacheConfig.CACHE_JOB_SIMILAR,
                CacheConfig.CACHE_STATS,
                CacheConfig.CACHE_TICKER,
                CacheConfig.CACHE_COMPANIES,
                CacheConfig.CACHE_FILTER_OPTIONS,
                CacheConfig.CACHE_COUNTRIES
            )) {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) cache.clear();
            }
            log.info("All caches cleared successfully");
        } catch (Exception e) {
            log.warn("Cache eviction failed (non-critical): {}", SafeErrorSummary.from(e));
        }
    }
}
