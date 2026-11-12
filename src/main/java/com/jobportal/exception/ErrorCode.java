package com.jobportal.exception;

public enum ErrorCode {

    // General
    INTERNAL_SERVER_ERROR("ERR_001", "An unexpected error occurred"),
    VALIDATION_FAILED("ERR_002", "Request validation failed"),
    INVALID_REQUEST("ERR_003", "Invalid request parameters"),

    // Resource errors
    JOB_NOT_FOUND("ERR_101", "Job not found"),
    COMPANY_NOT_FOUND("ERR_102", "Company not found"),
    SCRAPER_CONFIG_NOT_FOUND("ERR_103", "Scraper configuration not found"),
    SCRAPER_RUN_NOT_FOUND("ERR_104", "Scraper run not found"),
    FEEDBACK_NOT_FOUND("ERR_105", "Feedback submission not found"),

    // Scraper errors
    SCRAPER_NOT_REGISTERED("ERR_201", "No scraper registered for this company"),
    SCRAPER_ALREADY_RUNNING("ERR_202", "Scraper is already running for this company"),
    SCRAPER_CIRCUIT_OPEN("ERR_203", "Scraper circuit breaker is open — too many failures"),
    SCRAPER_DISABLED("ERR_204", "Scraper is disabled for this company"),
    SCRAPER_EXECUTION_FAILED("ERR_205", "Scraper execution failed"),

    // Visa detection errors
    VISA_DETECTION_FAILED("ERR_301", "Visa detection service failed"),
    VISA_DETECTION_DISABLED("ERR_302", "Visa detection is disabled"),

    // Search errors
    INVALID_SORT_FIELD("ERR_401", "Invalid sort field specified"),
    INVALID_PAGINATION("ERR_402", "Invalid pagination parameters"),
    SEARCH_FAILED("ERR_403", "Search operation failed"),

    // Kafka errors
    EVENT_PUBLISH_FAILED("ERR_501", "Failed to publish event to Kafka"),
    EVENT_PROCESSING_FAILED("ERR_502", "Failed to process Kafka event"),

    // External service errors
    EXTERNAL_SERVICE_UNAVAILABLE("ERR_601", "External service is unavailable"),
    RATE_LIMIT_EXCEEDED("ERR_602", "Rate limit exceeded for external service"),

    // Authentication and authorization errors
    AUTHENTICATION_REQUIRED("ERR_701", "Authentication is required"),
    ACCESS_DENIED("ERR_702", "You do not have permission to perform this action"),
    INVALID_CREDENTIALS("ERR_703", "Email or password is incorrect"),
    EMAIL_ALREADY_REGISTERED("ERR_704", "An account already exists for this email address"),
    ACCOUNT_DISABLED("ERR_705", "This account is disabled");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
