package com.jobportal.util;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class SafeErrorSummaryTest {

    @Test
    void neverIncludesAnExceptionMessageInTheSummary() {
        String secretLikeMessage = "token=do-not-log-this-value";

        assertThat(SafeErrorSummary.from(new RuntimeException(secretLikeMessage)))
                .isEqualTo("Unexpected processing failure")
                .doesNotContain(secretLikeMessage);
    }

    @Test
    void classifiesExpectedOperationalFailuresWithoutTheirDetails() {
        assertThat(SafeErrorSummary.from(new ConnectException("host=private.internal")))
                .isEqualTo("External service unavailable")
                .doesNotContain("private.internal");
    }
}
