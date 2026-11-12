package com.jobportal.exception;

public class ResourceNotFoundException extends JobPortalException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode, 404);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message, 404);
    }

    public static ResourceNotFoundException job(Long id) {
        return new ResourceNotFoundException(ErrorCode.JOB_NOT_FOUND,
                "Job not found with id: " + id);
    }

    public static ResourceNotFoundException company(String slug) {
        return new ResourceNotFoundException(ErrorCode.COMPANY_NOT_FOUND,
                "Company not found with slug: " + slug);
    }

    public static ResourceNotFoundException scraperConfig(String slug) {
        return new ResourceNotFoundException(ErrorCode.SCRAPER_CONFIG_NOT_FOUND,
                "Scraper config not found for company: " + slug);
    }

    public static ResourceNotFoundException feedback(Long id) {
        return new ResourceNotFoundException(ErrorCode.FEEDBACK_NOT_FOUND,
                "Feedback submission not found with id: " + id);
    }
}
