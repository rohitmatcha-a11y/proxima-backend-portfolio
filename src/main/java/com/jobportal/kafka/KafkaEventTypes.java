package com.jobportal.kafka;

/**
 * Constants for Kafka event types used in idempotency checks.
 * Format: {TOPIC}_{ACTION}
 */
public final class KafkaEventTypes {

    private KafkaEventTypes() {}

    // Job Indexing
    public static final String JOB_INDEXING_INDEX       = "JOB_INDEXING_REQUESTED_INDEX";       // from BaseScraper
    public static final String JOB_INDEXING_GEO_UPDATE  = "JOB_INDEXING_REQUESTED_GEO_UPDATE";
    public static final String JOB_INDEXING_VISA_UPDATE = "JOB_INDEXING_REQUESTED_VISA_UPDATE";
    public static final String JOB_INDEXING_DELETE      = "JOB_INDEXING_REQUESTED_DELETE";

    // Visa Detection
    public static final String JOB_VISA_DETECTION       = "JOB_VISA_DETECTION_REQUESTED";
    public static final String JOB_GEOCODING            = "JOB_GEOCODING_REQUESTED";
}
