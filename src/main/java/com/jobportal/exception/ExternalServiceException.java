package com.jobportal.exception;

public class ExternalServiceException extends JobPortalException {

    public ExternalServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message, 503);
    }

    public ExternalServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, 503, cause);
    }

    public static ExternalServiceException anthropicUnavailable(Throwable cause) {
        return new ExternalServiceException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "Anthropic AI service is unavailable", cause);
    }

    public static ExternalServiceException rateLimitExceeded(String service) {
        return new ExternalServiceException(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "Rate limit exceeded for service: " + service);
    }
}
