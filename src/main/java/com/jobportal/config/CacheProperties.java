package com.jobportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cache.ttl")
public class CacheProperties {
    private long jobSearch = 3;
    private long jobCompany = 10;
    private long jobDetail = 60;
    private long jobSimilar = 30;
    private long filterOptions = 360;
    private long jobStats = 360;
    private long jobTicker = 10;
}
