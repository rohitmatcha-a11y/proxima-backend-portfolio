package com.jobportal.config;

import com.jobportal.domain.document.JobDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchOperations esOps;

    @PostConstruct
    public void initIndex() {
        IndexOperations indexOps = esOps.indexOps(JobDocument.class);
        if (!indexOps.exists()) {
            log.info("ES index 'jobs' not found — creating with mapping...");
            indexOps.createWithMapping();
            log.info("ES index 'jobs' created with correct field mappings");
        } else {
            log.info("ES index 'jobs' already exists — skipping creation");
        }
    }
}
