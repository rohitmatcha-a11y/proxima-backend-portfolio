package com.jobportal.exception;

public class ScraperException extends JobPortalException {

    public ScraperException(ErrorCode errorCode) {
        super(errorCode, 500);
    }

    public ScraperException(ErrorCode errorCode, String message) {
        super(errorCode, message, 500);
    }

    public ScraperException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, 500, cause);
    }

    public static ScraperException notRegistered(String slug) {
        return new ScraperException(ErrorCode.SCRAPER_NOT_REGISTERED,
                "No scraper registered for company: " + slug);
    }

    public static ScraperException circuitOpen(String slug) {
        return new ScraperException(ErrorCode.SCRAPER_CIRCUIT_OPEN,
                "Circuit breaker is open for company: " + slug);
    }

    public static ScraperException alreadyRunning(String slug) {
        return new ScraperException(ErrorCode.SCRAPER_ALREADY_RUNNING,
                "Scraper is already running for company: " + slug);
    }

    public static ScraperException executionFailed(String slug, Throwable cause) {
        return new ScraperException(ErrorCode.SCRAPER_EXECUTION_FAILED,
                "Scraper execution failed for company: " + slug, cause);
    }
}
