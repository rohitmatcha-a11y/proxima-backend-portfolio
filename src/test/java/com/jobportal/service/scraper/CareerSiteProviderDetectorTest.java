package com.jobportal.service.scraper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerSiteProviderDetectorTest {

    @Test
    void detectsGreenhouseBoardsFromPublicBoardUrls() {
        var detected = CareerSiteProviderDetector.detect("https://boards.greenhouse.io/figma/jobs/12345");

        assertThat(detected.provider()).isEqualTo(CareerSiteProvider.GREENHOUSE);
        assertThat(detected.board()).isEqualTo("figma");
    }

    @Test
    void detectsLeverAndAshbyBoardNames() {
        var lever = CareerSiteProviderDetector.detect("https://jobs.lever.co/netlify");
        var ashby = CareerSiteProviderDetector.detect("https://jobs.ashbyhq.com/linear");

        assertThat(lever.provider()).isEqualTo(CareerSiteProvider.LEVER);
        assertThat(lever.board()).isEqualTo("netlify");
        assertThat(ashby.provider()).isEqualTo(CareerSiteProvider.ASHBY);
        assertThat(ashby.board()).isEqualTo("linear");
    }

    @Test
    void usesStructuredDataAsTheSafeGenericFallback() {
        var detected = CareerSiteProviderDetector.detect("https://careers.example.com/open-roles");

        assertThat(detected.provider()).isEqualTo(CareerSiteProvider.STRUCTURED_DATA);
        assertThat(detected.board()).isNull();
    }
}
