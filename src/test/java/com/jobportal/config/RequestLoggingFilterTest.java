package com.jobportal.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void propagatesAnAcceptedRequestIdToLogsAndTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "release-check-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("release-check-1234");
            assertThat(MDC.get("requestPath")).isEqualTo("/api/v1/jobs");
            ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_CREATED);
        });

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("release-check-1234");
        assertThat(MDC.get("requestId")).isNull();
    }
}
