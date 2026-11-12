package com.jobportal.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.jobportal.util.SafeErrorSummary;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobPortalException.class)
    public ResponseEntity<ErrorResponse> handleJobPortalException(
            JobPortalException ex, HttpServletRequest request) {
        log.warn("Request rejected with error code={} name={}", ex.getErrorCode().getCode(),
                ex.getErrorCode().name());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(),
                        ex.getHttpStatus(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                fieldErrors.put(e.getField(), e.getDefaultMessage()));
        log.warn("Request validation failed for {}", request.getRequestURI());
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_FAILED.getCode())
                .error("Validation Failed")
                .message("Request validation failed — check validationErrors for details")
                .status(400)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .traceId(ErrorResponse.currentTraceId())
                .validationErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                violations.put(v.getPropertyPath().toString(), v.getMessage()));
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_FAILED.getCode())
                .error("Constraint Violation")
                .message("Constraint validation failed")
                .status(400)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .traceId(ErrorResponse.currentTraceId())
                .validationErrors(violations)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter '{}' for {}", ex.getParameterName(), request.getRequestURI());
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ErrorCode.INVALID_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                400, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format(
                "Parameter '%s' should be of type '%s'",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Request parameter type mismatch at {}", request.getRequestURI());
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(ErrorCode.INVALID_REQUEST, message, 400, request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String message = String.format(
                "Method '%s' not allowed for '%s'. Allowed: %s",
                ex.getMethod(), request.getRequestURI(),
                String.join(", ", ex.getSupportedMethods() != null
                        ? ex.getSupportedMethods() : new String[]{}));
        return ResponseEntity.status(405).body(
                ErrorResponse.of(ErrorCode.INVALID_REQUEST, message, 405, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Unreadable request body for {}", request.getRequestURI());
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ErrorCode.INVALID_REQUEST,
                "Request body is missing or malformed JSON",
                400, request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument at {}", request.getRequestURI());
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(ErrorCode.INVALID_REQUEST, ex.getMessage(), 400, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        // Ignore favicon and static resource errors
        if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            return ResponseEntity.notFound().build();
        }
        log.error("Unexpected error at {}: {}", request.getRequestURI(), SafeErrorSummary.from(ex));
        return ResponseEntity.status(500).body(
                ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, 500, request.getRequestURI()));
    }
}
