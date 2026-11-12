package com.jobportal.exception;

public class JobPortalException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    public JobPortalException(ErrorCode errorCode, int httpStatus) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public JobPortalException(ErrorCode errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public JobPortalException(ErrorCode errorCode, String message,
                               int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public int getHttpStatus() { return httpStatus; }
}
