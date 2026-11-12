package com.jobportal.service.cache;

import com.jobportal.api.dto.JobSearchRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds stable, readable Redis cache keys.
 *
 * <p>Only supplied search filters are included. This keeps cache entries easy to
 * inspect and avoids keys such as {@code null_null_[company]_null}.</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String jobSearch(JobSearchRequest request) {
        List<String> parts = new ArrayList<>();
        add(parts, "keyword", request.getKeyword());
        add(parts, "companySlug", request.getCompanySlug());
        addList(parts, "companySlugs", request.getCompanySlugs());
        add(parts, "country", request.getCountry());
        addList(parts, "countries", request.getCountries());
        add(parts, "continent", request.getContinent());
        addList(parts, "continents", request.getContinents());
        add(parts, "city", request.getCity());
        addList(parts, "jobType", request.getJobType());
        addList(parts, "workMode", request.getWorkMode());
        addList(parts, "experienceLevel", request.getExperienceLevel());
        addList(parts, "industry", request.getIndustry());
        addList(parts, "visaStatus", request.getVisaStatus());
        addList(parts, "companySizeCategory", request.getCompanySizeCategory());
        add(parts, "salaryMin", request.getSalaryMin());
        add(parts, "salaryMax", request.getSalaryMax());
        add(parts, "salaryCurrency", request.getSalaryCurrency());
        if (Boolean.TRUE.equals(request.getRemoteOnly())) add(parts, "remoteOnly", true);
        if (Boolean.TRUE.equals(request.getVisaSponsorshipOnly())) add(parts, "visaSponsorshipOnly", true);
        add(parts, "datePosted", request.getDatePosted());
        add(parts, "postedAfter", request.getPostedAfter());
        add(parts, "postedBefore", request.getPostedBefore());
        add(parts, "page", request.getPage());
        add(parts, "size", request.getSize());
        add(parts, "sortBy", request.getSortBy());
        add(parts, "sortDir", request.getSortDir());
        return "query:" + String.join("&", parts);
    }

    public static String jobDetail(Long jobId) {
        return "job-id=" + jobId;
    }

    public static String similarJobs(Long jobId) {
        return "source-job-id=" + jobId;
    }

    public static String companyJobs(String companySlug, int page, int size) {
        return "company=" + encode(companySlug) + "&page=" + page + "&size=" + size;
    }

    public static String activeCompanies() {
        return "scope=active";
    }

    public static String company(String slug) {
        return "slug=" + encode(slug);
    }

    public static String filterOptions() {
        return "scope=all";
    }

    public static String jobStats() {
        return "scope=all";
    }

    public static String countries(int page, int size) {
        return "page=" + page + "&size=" + size;
    }

    public static String ticker() {
        return "window=30d";
    }

    private static void add(List<String> parts, String name, Object value) {
        if (value == null) return;
        if (value instanceof String string && string.isBlank()) return;
        parts.add(name + "=" + encode(String.valueOf(value)));
    }

    private static void addList(List<String> parts, String name, List<String> values) {
        if (values == null || values.isEmpty()) return;
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(CacheKeys::encode)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (!joined.isEmpty()) parts.add(name + "=" + joined);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }
}
