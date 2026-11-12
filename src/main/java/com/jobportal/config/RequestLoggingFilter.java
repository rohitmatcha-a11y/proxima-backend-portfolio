package com.jobportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String HTTP_METHOD_MDC_KEY = "httpMethod";
    private static final String REQUEST_PATH_MDC_KEY = "requestPath";
    private static final String HTTP_STATUS_MDC_KEY = "httpStatus";
    private static final String REQUEST_DURATION_MDC_KEY = "requestDurationMs";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        long startTime = System.currentTimeMillis();
        String requestId = requestId(request);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(HTTP_METHOD_MDC_KEY, request.getMethod());
        // Deliberately log only the URI path: query strings can contain OAuth codes,
        // searches, or other user-supplied data that do not belong in production logs.
        MDC.put(REQUEST_PATH_MDC_KEY, request.getRequestURI());

        log.info("HTTP request started");

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            MDC.put(HTTP_STATUS_MDC_KEY, String.valueOf(response.getStatus()));
            MDC.put(REQUEST_DURATION_MDC_KEY, String.valueOf(duration));
            if (response.getStatus() >= 500) {
                log.error("HTTP request completed");
            } else if (response.getStatus() >= 400) {
                log.warn("HTTP request completed");
            } else {
                log.info("HTTP request completed");
            }
            MDC.clear();
        }
    }

    private String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && incoming.matches("[A-Za-z0-9-]{8,64}")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
