package com.jobportal.util;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

/**
 * Produces a stable, non-sensitive description for production logs and
 * operational status records. Exception messages frequently include request
 * data or third-party response text, neither of which should be emitted.
 */
public final class SafeErrorSummary {

    private SafeErrorSummary() {
    }

    public static String from(Throwable error) {
        Throwable root = rootCause(error);
        if (root == null) return "Unexpected failure";
        if (root instanceof SocketTimeoutException || root instanceof HttpTimeoutException) {
            return "Operation timed out";
        }
        if (root instanceof ConnectException || root instanceof UnknownHostException) {
            return "External service unavailable";
        }
        if (root instanceof JsonProcessingException) {
            return "Invalid response format";
        }
        if (root instanceof IllegalArgumentException) {
            return "Invalid request or configuration";
        }
        return "Unexpected processing failure";
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
