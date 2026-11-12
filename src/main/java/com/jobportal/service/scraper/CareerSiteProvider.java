package com.jobportal.service.scraper;

/**
 * Public career-site systems that expose a reusable jobs feed. STRUCTURED_DATA is
 * the standards-based fallback for any site that publishes schema.org JobPosting
 * entries rather than an ATS API.
 */
public enum CareerSiteProvider {
    GREENHOUSE,
    LEVER,
    ASHBY,
    STRUCTURED_DATA
}
