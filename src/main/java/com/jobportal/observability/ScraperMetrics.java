package com.jobportal.observability;

import com.jobportal.domain.entity.ScraperRun;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Records low-cardinality scraper metrics for Prometheus. Company names are
 * intentionally not metric tags: administrators can add companies over time,
 * and unbounded tags make a metrics system slow and expensive.
 */
@Component
@RequiredArgsConstructor
public class ScraperMetrics {

    private final MeterRegistry meterRegistry;

    public void recordRun(String scraperName, ScraperRun run, long durationMs) {
        String scraper = metricValue(scraperName);
        String outcome = run.getStatus() == null ? "unknown"
                : run.getStatus().name().toLowerCase(Locale.ROOT);
        Tags runTags = Tags.of("scraper", scraper, "outcome", outcome);

        Counter.builder("jobz.scraper.runs")
                .description("Completed scraper runs by scraper type and outcome")
                .tags(runTags)
                .register(meterRegistry)
                .increment();
        Timer.builder("jobz.scraper.run.duration")
                .description("End-to-end duration of scraper runs")
                .tags(runTags)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        recordJobs(scraper, "found", run.getJobsFound());
        recordJobs(scraper, "new", run.getJobsNew());
        recordJobs(scraper, "skipped", run.getJobsSkipped());
        recordJobs(scraper, "failed", run.getJobsFailed());
    }

    private void recordJobs(String scraper, String result, int count) {
        if (count <= 0) return;
        Counter.builder("jobz.scraper.jobs")
                .description("Jobs observed by scraper runs")
                .tags("scraper", scraper, "result", result)
                .register(meterRegistry)
                .increment(count);
    }

    private String metricValue(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("(^_+|_+$)", "");
    }
}
