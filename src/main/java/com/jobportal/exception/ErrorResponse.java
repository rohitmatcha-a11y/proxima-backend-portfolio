package com.jobportal.exception;

import lombok.Builder;
import lombok.Data;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ErrorResponse {

    private String errorCode;
    private String error;
    private String message;
    private int status;
    private String path;
    private Instant timestamp;
    private Map<String, String> validationErrors;
    private String traceId;

    public static ErrorResponse of(ErrorCode errorCode, int status, String path) {
        return ErrorResponse.builder()
                .errorCode(errorCode.getCode())
                .error(errorCode.name())
                .message(errorCode.getDefaultMessage())
                .status(status)
                .path(path)
                .timestamp(Instant.now())
                .traceId(currentTraceId())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String customMessage,
                                    int status, String path) {
        return ErrorResponse.builder()
                .errorCode(errorCode.getCode())
                .error(errorCode.name())
                .message(customMessage)
                .status(status)
                .path(path)
                .timestamp(Instant.now())
                .traceId(currentTraceId())
                .build();
    }

    public static String currentTraceId() {
        String requestId = MDC.get("requestId");
        return requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
    }
}
