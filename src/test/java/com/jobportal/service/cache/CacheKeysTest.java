package com.jobportal.service.cache;

import com.jobportal.api.dto.JobSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeysTest {

    @Test
    void jobSearchIncludesOnlySuppliedFiltersAndRequiredPaging() {
        JobSearchRequest request = JobSearchRequest.builder()
                .companySlugs(List.of("coinbase"))
                .page(0)
                .size(20)
                .sortBy("postedAt")
                .sortDir("desc")
                .build();

        assertThat(CacheKeys.jobSearch(request))
                .isEqualTo("query:companySlugs=coinbase&page=0&size=20&sortBy=postedAt&sortDir=desc")
                .doesNotContain("null");
    }

    @Test
    void cacheKeysDescribeTheResourceTheyContain() {
        assertThat(CacheKeys.jobDetail(127L)).isEqualTo("job-id=127");
        assertThat(CacheKeys.similarJobs(127L)).isEqualTo("source-job-id=127");
        assertThat(CacheKeys.companyJobs("coinbase", 2, 20))
                .isEqualTo("company=coinbase&page=2&size=20");
        assertThat(CacheKeys.company("coinbase")).isEqualTo("slug=coinbase");
        assertThat(CacheKeys.countries(1, 10)).isEqualTo("page=1&size=10");
        assertThat(CacheKeys.filterOptions()).isEqualTo("scope=all");
        assertThat(CacheKeys.jobStats()).isEqualTo("scope=all");
        assertThat(CacheKeys.ticker()).isEqualTo("window=30d");
    }
}
