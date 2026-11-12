package com.jobportal.service.scraper;

/**
 * Constants for scraper action types and trigger sources.
 */
public final class ScraperConstants {

    private ScraperConstants() {}

    // Indexing actions
    public static final String ACTION_INDEX       = "INDEX";
    public static final String ACTION_VISA_UPDATE = "VISA_UPDATE";
    public static final String ACTION_DELETE      = "DELETE";
    public static final String ACTION_GEO_UPDATE  = "GEO_UPDATE";

    // Trigger sources
    public static final String TRIGGERED_BY_MANUAL    = "MANUAL";
    public static final String TRIGGERED_BY_SCHEDULED = "SCHEDULED";
    public static final String TRIGGERED_BY_KAFKA     = "KAFKA";
}
